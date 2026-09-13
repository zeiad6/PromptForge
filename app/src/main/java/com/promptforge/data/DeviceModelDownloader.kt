package com.promptforge.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** One downloadable on-device model (un-gated Hugging Face repo, direct file). */
data class DeviceDownloadSpec(
    val id: String,
    val sizeBytes: Long,
    val url: String,
    val fileName: String,
    val repoUrl: String,
)

/** UI state of a single download. */
data class DlUi(
    val percent: Int = 0,
    val running: Boolean = false,
    val paused: Boolean = false,
    val failed: Boolean = false,
    val done: Boolean = false,
    val downloadedBytes: Long = 0,
)

/**
 * In-app downloader for on-device models — no browser, no PC.
 * Streams straight from Hugging Face into app storage with resume support
 * (HTTP Range + .part file). Survives navigation and process restarts:
 * a partial download simply shows as paused and can be resumed.
 */
class DeviceModelDownloader(private val registry: DeviceModelRegistry) {

    companion object {
        private const val HF = "https://huggingface.co/litert-community"

        /** Verified un-gated models (license-free download; sizes exact). */
        val CATALOG = listOf(
            DeviceDownloadSpec(
                id = "smollm2-135m",
                sizeBytes = 142_819_328L, // 143 MB — instant test model
                url = "$HF/SmolLM2-135M-Instruct/resolve/main/SmolLM2_135M_Instruct.litertlm",
                fileName = "SmolLM2_135M_Instruct.litertlm",
                repoUrl = "$HF/SmolLM2-135M-Instruct",
            ),
            DeviceDownloadSpec(
                id = "olmo2-1b",
                sizeBytes = 931_241_056L, // 931 MB — balanced
                url = "$HF/OLMo-2-1B-Instruct/resolve/main/OLMo-2-1B-Instruct_q4_block32_ekv4096.litertlm",
                fileName = "OLMo-2-1B-Instruct_q4_block32_ekv4096.litertlm",
                repoUrl = "$HF/OLMo-2-1B-Instruct",
            ),
            DeviceDownloadSpec(
                id = "qwen25-15b",
                sizeBytes = 1_567_364_648L, // 1.57 GB — strongest
                url = "$HF/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_seq128_q8_ekv1280.task",
                fileName = "Qwen2.5-1.5B-Instruct_seq128_q8_ekv1280.task",
                repoUrl = "$HF/Qwen2.5-1.5B-Instruct",
            ),
            // ── Heavy reasoning tier (all verified un-gated, direct 206) ──
            DeviceDownloadSpec(
                id = "deepseek-r1-15b",
                sizeBytes = 1_833_000_000L, // 1.83 GB — DEEP REASONING (R1 distill, <think> mode)
                url = "$HF/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
                fileName = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
                repoUrl = "$HF/DeepSeek-R1-Distill-Qwen-1.5B",
            ),
            DeviceDownloadSpec(
                id = "lfm25-26b",
                sizeBytes = 1_668_000_000L, // 1.67 GB — LiquidAI LFM2.5, very capable & efficient
                url = "$HF/LFM2.5-2.6B/resolve/main/LFM2.5-2.6B_int4.litertlm",
                fileName = "LFM2.5-2.6B_int4.litertlm",
                repoUrl = "$HF/LFM2.5-2.6B",
            ),
            DeviceDownloadSpec(
                id = "gemma4-e2b",
                sizeBytes = 2_588_000_000L, // 2.59 GB — Google Gemma 4 E2B (flagship, multimodal-capable)
                url = "$HF/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
                fileName = "gemma-4-E2B-it.litertlm",
                repoUrl = "$HF/gemma-4-E2B-it-litert-lm",
            ),
            DeviceDownloadSpec(
                id = "qwen35-4b",
                sizeBytes = 2_754_000_000L, // 2.75 GB — Qwen 3.5 4B, strongest generalist
                url = "$HF/Qwen3.5-4B/resolve/main/Qwen3.5-4B_mixed_int4.litertlm",
                fileName = "Qwen3.5-4B_mixed_int4.litertlm",
                repoUrl = "$HF/Qwen3.5-4B",
            ),
            DeviceDownloadSpec(
                id = "phi4-mini",
                sizeBytes = 3_910_000_000L, // 3.91 GB — Microsoft Phi-4-mini 3.8B (top-tier reasoning + code)
                url = "$HF/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm",
                fileName = "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm",
                repoUrl = "$HF/Phi-4-mini-instruct",
            ),
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val calls = ConcurrentHashMap<String, Call>()
    private val _progress = MutableStateFlow<Map<String, DlUi>>(emptyMap())
    val progress: StateFlow<Map<String, DlUi>> = _progress

    init {
        // A previous download that outlived the process shows up as paused.
        CATALOG.forEach { spec ->
            runCatching {
                if (registry.find(spec.fileName.substringBeforeLast('.')) == null) {
                    val final = registry.fileFor(spec.fileName)
                    if (final.exists() && final.length() > 0) registry.adopt(final, spec.fileName)
                }
                val part = registry.partialFor(spec.fileName)
                if (part.exists() && part.length() > 0) {
                    _progress.value = _progress.value + (spec.id to DlUi(
                        percent = pct(part.length(), spec.sizeBytes),
                        paused = true,
                        downloadedBytes = part.length(),
                    ))
                }
            }
        }
    }

    fun isRunning(id: String): Boolean = _progress.value[id]?.running == true

    /** Starts (or resumes) a download. Progress is published to [progress]. */
    fun download(scope: CoroutineScope, spec: DeviceDownloadSpec, onFinished: (Boolean, String?) -> Unit) {
        if (isRunning(spec.id)) return
        val part = registry.partialFor(spec.fileName)
        val already = if (part.exists()) part.length() else 0L
        _progress.value = _progress.value + (spec.id to DlUi(
            percent = pct(already, spec.sizeBytes), running = true, downloadedBytes = already,
        ))
        scope.launch(Dispatchers.IO) {
            var ok = false
            var name: String? = null
            val call = client.newCall(
                Request.Builder().url(spec.url)
                    .header("Range", "bytes=$already-")
                    .build()
            )
            calls[spec.id] = call
            try {
                call.execute().use { resp ->
                    val body = resp.body ?: throw IOException("empty_body")
                    val resumed = resp.code == 206 && already > 0
                    if (!resumed) { // server ignored Range (or fresh start)
                        if (part.exists()) part.delete()
                        part.createNewFile()
                    }
                    var written = if (resumed) already else 0L
                    val total = (body.contentLength().takeIf { it > 0 }?.let { if (resumed) it + already else it })
                        ?: spec.sizeBytes
                    FileOutputStream(part, resumed).use { out ->
                        val src = body.byteStream()
                        val buf = ByteArray(256 * 1024)
                        var lastPct = -1
                        while (true) {
                            val n = src.read(buf)
                            if (n == -1) break
                            out.write(buf, 0, n)
                            written += n
                            val p = pct(written, total)
                            if (p != lastPct) {
                                lastPct = p
                                _progress.value = _progress.value + (spec.id to DlUi(
                                    percent = p, running = true, downloadedBytes = written,
                                ))
                            }
                        }
                        out.flush()
                    }
                    val final = registry.fileFor(spec.fileName)
                    if (final.exists()) final.delete()
                    if (!part.renameTo(final)) {
                        final.outputStream().use { o -> part.inputStream().use { i -> i.copyTo(o) } }
                        part.delete()
                    }
                    name = registry.adopt(final, spec.fileName).name
                    ok = true
                }
                _progress.value = _progress.value + (spec.id to DlUi(percent = 100, done = true))
            } catch (e: Exception) {
                val pausedNow = calls[spec.id]?.isCanceled() == true
                val len = part.takeIf { it.exists() }?.length() ?: 0L
                _progress.value = _progress.value + (spec.id to DlUi(
                    percent = pct(len, spec.sizeBytes),
                    paused = pausedNow,
                    failed = !pausedNow,
                    downloadedBytes = len,
                ))
            } finally {
                calls.remove(spec.id)
            }
            onFinished(ok, name)
        }
    }

    /** Pauses the active download; the .part file is kept for resume. */
    fun pause(id: String) {
        calls[id]?.cancel()
    }

    private fun pct(part: Long, total: Long): Int =
        if (total > 0) (part * 100 / total).toInt().coerceIn(0, 100) else 0
}
