package com.promptforge.data

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runs LLMs fully on-device via Google AI Edge (MediaPipe GenAI).
 * One hot instance at a time (they're memory-hungry).
 *
 * Self-healing: if GPU load or generation fails, it transparently reloads on
 * CPU and retries once; if the prompt exceeds the model context, it throws a
 * distinct [java.io.IOException] with message "prompt_too_long" so callers
 * can trim the conversation and retry.
 */
class DeviceLlmEngine(private val context: Context) {

    private val mutex = Mutex()
    private var hot: Pair<String, LlmInference>? = null

    fun unload() {
        synchronized(this) {
            hot?.second?.close()
            hot = null
        }
    }

    private fun keyOf(m: DeviceModel) = "path|${'$'}{m.maxTokens}|${'$'}{m.topK}|${'$'}{m.backend}"

    /**
     * Streams a completion for [prompt] using the model's own tuned settings.
     * [onPartial] receives the accumulated text so far; the final full text
     * is returned.
     */
    suspend fun chat(
        model: DeviceModel,
        prompt: String,
        temperature: Float = 0.7f,
        onPartial: (String) -> Unit = {},
    ): String = withContext(Dispatchers.Default) {
        mutex.withLock {
            generateWithRecovery(model, prompt, onPartial, allowRetry = true)
        }
    }

    private fun generateWithRecovery(
        model: DeviceModel,
        prompt: String,
        onPartial: (String) -> Unit,
        allowRetry: Boolean,
    ): String {
        val firstError: Throwable
        try {
            return generateOnce(model, prompt, onPartial)
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            firstError = e
        }
        // Recovery path: fresh CPU instance, one retry.
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
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw e
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

        // auto/gpu → try GPU first; cpu → CPU directly. The caller's
        // recovery path retries on CPU if GPU is impossible.
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
