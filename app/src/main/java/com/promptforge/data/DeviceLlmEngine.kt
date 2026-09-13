package com.promptforge.data

import android.content.Context
import com.google.ai.edge.litertlm.Backend as LtBackend
import com.google.ai.edge.litertlm.ConversationConfig as LtConvConfig
import com.google.ai.edge.litertlm.Engine as LtEngine
import com.google.ai.edge.litertlm.EngineConfig as LtEngineConfig
import com.google.ai.edge.litertlm.SamplerConfig as LtSamplerConfig
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Dual-engine on-device runner:
 *  - `.task` / `.bin` bundles → MediaPipe LLM Inference (tasks-genai)
 *  - `.litertlm` containers   → LiteRT-LM (com.google.ai.edge.litertlm)
 *
 * Loading a .litertlm in MediaPipe fails with "SentencePiece tokenizer is not
 * found" — dispatching by file extension is the fix used by Google's own
 * AI Edge Gallery app.
 *
 * Both engines are self-healing: GPU failures fall back to CPU with one
 * automatic retry; MediaPipe prompts that exceed the model context throw a
 * distinct "prompt_too_long" so callers can trim and retry.
 */
class DeviceLlmEngine(private val context: Context) {

    private val mutex = Mutex()
    private var hot: Pair<String, LlmInference>? = null
    private var ltlmHot: Pair<String, LtEngine>? = null

    @Volatile
    private var mpFuture: java.util.concurrent.Future<String>? = null

    /** User pressed stop. Deliberately LOCK-FREE: this runs on the UI
     * thread, and taking the engine monitor here could block it for the
     * whole model-load (seconds) — that was the stop-freezes-app bug.
     * Future.cancel is thread-safe on its own; all cleanup happens on the
     * worker thread afterwards. */
    fun abort() {
        runCatching { mpFuture?.cancel(true) }
    }

    fun unload() {
        synchronized(this) {
            hot?.second?.close()
            hot = null
            closeLtlm()
        }
    }

    suspend fun chat(
        model: DeviceModel,
        prompt: String,
        temperature: Float = 0.7f,
        onPartial: (String) -> Unit = {},
    ): String = withContext(Dispatchers.Default) {
        if (model.path.endsWith(".litertlm")) {
            chatLitertlm(model, prompt, temperature, onPartial)
        } else {
            val job = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
            mutex.withLock {
                job?.ensureActive() // user may have pressed stop while queued
                generateWithRecovery(model, prompt, onPartial, allowRetry = true) {
                    job?.isActive != true
                }
            }
        }
    }

    // ───────────────────────── LiteRT-LM (.litertlm) ─────────────────────────

    private suspend fun chatLitertlm(
        model: DeviceModel,
        prompt: String,
        temperature: Float,
        onPartial: (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        var lastErr: Throwable? = null
        var wantGpu = model.backend != "cpu"
        repeat(2) {
            try {
                return@withContext runLitertlm(model, prompt, temperature, onPartial, wantGpu)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastErr = e
                if (e.message == "prompt_too_long" || e.message == "empty_response") throw e
                wantGpu = false // next attempt: fresh CPU engine
                closeLtlm()
            }
        }
        throw lastErr ?: java.io.IOException("generation_failed")
    }

    private suspend fun runLitertlm(
        model: DeviceModel,
        prompt: String,
        temperature: Float,
        onPartial: (String) -> Unit,
        wantGpu: Boolean,
    ): String {
        val engine = ensureLtlm(model, wantGpu)
        val conv = engine.createConversation(
            LtConvConfig(
                samplerConfig = LtSamplerConfig(
                    topK = model.topK,
                    topP = 0.95,
                    temperature = temperature.toDouble(),
                ),
            )
        )
        try {
            val sb = StringBuilder()
            try {
                withTimeout(15 * 60_000L) {
                    conv.sendMessageAsync(prompt).collect { msg ->
                        val t = msg.toString()
                        if (t.isNotEmpty()) {
                            sb.append(t)
                            onPartial(sb.toString())
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                throw java.io.IOException("generation_timeout")
            }
            return sb.toString().trim().ifEmpty { throw IllegalStateException("empty_response") }
        } finally {
            runCatching { conv.close() }
        }
    }

    private fun ensureLtlm(model: DeviceModel, wantGpu: Boolean): LtEngine = synchronized(this) {
        val key = model.path + "|" + if (wantGpu) "gpu" else "cpu"
        ltlmHot?.let { (k, e) -> if (k == key) return e }
        closeLtlm()
        runCatching { unload() } // free the MediaPipe instance's RAM before loading LiteRT-LM

        // Pre-compiled model cache: dramatically faster warm starts
        // (encodeInitialPromptParams runs once, then reuses the graph).
        val cfg = LtEngineConfig(
            modelPath = model.path,
            backend = if (wantGpu) LtBackend.GPU() else LtBackend.CPU(),
            cacheDir = context.cacheDir.absolutePath,
            maxNumTokens = model.maxTokens,
        )
        val e = LtEngine(cfg)
        try {
            e.initialize() // can take ~10s on big models
        } catch (t: Throwable) {
            runCatching { e.close() }
            throw java.io.IOException("init: ${t.message}", t)
        }
        ltlmHot = key to e
        e
    }

    private fun closeLtlm() = synchronized(this) {
        ltlmHot?.second?.let { runCatching { it.close() } }
        ltlmHot = null
    }

    // ─────────────────────── MediaPipe (.task / .bin) ───────────────────────

    private fun keyOf(m: DeviceModel) = "path|${m.maxTokens}|${m.topK}|${m.backend}"

    private fun generateWithRecovery(
        model: DeviceModel,
        prompt: String,
        onPartial: (String) -> Unit,
        allowRetry: Boolean,
        cancelled: () -> Boolean = { false },
    ): String {
        val firstError: Throwable
        try {
            return generateOnce(model, prompt, onPartial)
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            firstError = e
        }
        // Recovery path: fresh CPU instance, one retry — never after a stop.
        if (cancelled()) throw kotlinx.coroutines.CancellationException("aborted")
        if (allowRetry && firstError.message != "prompt_too_long" &&
            firstError.message != "empty_response"
        ) {
            runCatching { unload() }
            try {
                return generateOnce(model.copy(backend = "cpu"), prompt, onPartial)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                unload()
                throw e
            }
        }
        if (firstError.message == "prompt_too_long") unload()
        throw firstError
    }

    private fun generateOnce(
        model: DeviceModel,
        prompt: String,
        onPartial: (String) -> Unit,
    ): String {
        val llm = ensureLoaded(model)
        val future = try {
            llm.generateResponseAsync(prompt) { partial, _ ->
                if (!partial.isNullOrEmpty()) onPartial(partial)
            }
        } catch (e: Throwable) {
            throw java.io.IOException(e.message ?: "load_failed", e)
        }
        mpFuture = future
        try {
            val finalText = future.get(15, java.util.concurrent.TimeUnit.MINUTES)
            return finalText.trim().ifEmpty { throw IllegalStateException("empty_response") }
        } catch (e: java.util.concurrent.TimeoutException) {
            future.cancel(true)
            throw java.io.IOException("generation_timeout")
        } catch (e: java.util.concurrent.ExecutionException) {
            val cause = e.cause ?: e
            val msg = (cause.message ?: "").lowercase()
            throw if (
                msg.contains("sequence") || msg.contains("token") ||
                msg.contains("length") || msg.contains("exceed") ||
                msg.contains("too long") || msg.contains("context")
            ) java.io.IOException("prompt_too_long") else java.io.IOException(cause.message ?: "generation_failed", cause)
        } catch (e: java.io.IOException) {
            throw e
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException ||
                e is java.util.concurrent.CancellationException
            ) {
                // Stopped by the user: tear the hot instance down HERE on the
                // worker thread, so a half-cancelled native session is never
                // reused — and the UI never waits on it.
                runCatching { unload() }
                throw kotlinx.coroutines.CancellationException("aborted", e)
            }
            throw e
        } finally {
            mpFuture = null
        }
    }

    private fun ensureLoaded(model: DeviceModel): LlmInference = synchronized(this) {
        val key = keyOf(model)
        hot?.let { (k, llm) -> if (k == key) return llm }
        hot?.second?.close()
        hot = null

        fun options(backend: LlmInference.Backend): LlmInference.LlmInferenceOptions =
            LlmInference.LlmInferenceOptions.builder()
                .setModelPath(model.path)
                .setMaxTokens(model.maxTokens)
                .setMaxTopK(model.topK)
                .setPreferredBackend(backend)
                .build()

        if (model.backend == "cpu") {
            return synchronized(this) {
                LlmInference.createFromOptions(context, options(LlmInference.Backend.CPU))
                    .also { hot = key to it }
            }
        }
        try {
            LlmInference.createFromOptions(context, options(LlmInference.Backend.GPU))
                .also { hot = key to it }
        } catch (e: Exception) {
            LlmInference.createFromOptions(context, options(LlmInference.Backend.CPU))
                .also { hot = key to it }
        }
    }
}
