package com.promptforge.data

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runs LLMs fully on-device via Google AI Edge (MediaPipe GenAI).
 * One hot instance at a time (they're memory-hungry); GPU first,
 * automatic CPU fallback for older devices.
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

    /**
     * Streams a completion for [prompt]. [onPartial] receives the accumulated
     * text so far; the final full text is returned.
     */
    suspend fun chat(
        modelPath: String,
        prompt: String,
        temperature: Float = 0.7f,
        maxTokens: Int = 1024,
        onPartial: (String) -> Unit = {},
    ): String = withContext(Dispatchers.Default) {
        mutex.withLock {
            val llm = ensureLoaded(modelPath)
            val future = llm.generateResponseAsync(prompt) { partial, _ ->
                if (!partial.isNullOrEmpty()) onPartial(partial)
            }
            try {
                val finalText = future.get(15, java.util.concurrent.TimeUnit.MINUTES)
                finalText.trim().ifEmpty { throw IllegalStateException("empty_response") }
            } catch (e: java.util.concurrent.TimeoutException) {
                future.cancel(true)
                unload() // wedged instance — force a clean reload next time
                throw java.io.IOException("generation_timeout")
            } catch (e: java.util.concurrent.ExecutionException) {
                unload()
                throw (e.cause ?: e)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                unload()
                throw e
            }
        }
    }

    private fun ensureLoaded(modelPath: String): LlmInference = synchronized(this) {
        hot?.let { (path, llm) -> if (path == modelPath) return llm }
        hot?.second?.close()
        hot = null

        // GPU first (much faster on modern phones); CPU fallback everywhere.
        try {
            val opts = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .setPreferredBackend(LlmInference.Backend.GPU)
                .build()
            LlmInference.createFromOptions(context, opts).also { hot = modelPath to it }
        } catch (e: Exception) {
            val opts = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .setPreferredBackend(LlmInference.Backend.CPU)
                .build()
            LlmInference.createFromOptions(context, opts).also { hot = modelPath to it }
        }
    }
}
