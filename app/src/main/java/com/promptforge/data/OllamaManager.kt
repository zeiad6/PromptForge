package com.promptforge.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

@Serializable
data class ODetails(
    val family: String? = null,
    val parameter_size: String? = null,
    val quantization_level: String? = null,
)

@Serializable
data class OTag(val name: String = "", val size: Long = 0, val details: ODetails? = null)

@Serializable
data class OTagsResponse(val models: List<OTag> = emptyList())

@Serializable
data class OPullRequest(val name: String, val stream: Boolean = true)

@Serializable
data class OPullEvent(
    val status: String? = null,
    val total: Long? = null,
    val completed: Long? = null,
    val error: String? = null,
)

/** One locally-installed model as shown in the manager UI. */
data class OllamaModel(
    val name: String,
    val sizeBytes: Long,
    val family: String?,
    val parameterSize: String?,
    val quantization: String?,
)

/**
 * Talks to a machine running Ollama over the network (native API):
 *  - GET  /api/tags    installed models + sizes + details
 *  - POST /api/pull    streaming download (JSON lines) — resumable by re-pulling
 *  - POST /api/delete  remove a model
 * "Pause" cancels the HTTP call; Ollama keeps partial blobs and the next
 * pull resumes exactly where it stopped.
 */
class OllamaManager(private val json: Json) {

    private val quick = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val streaming = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // data flows continuously during a pull
        .build()

    /** Aggressive short-timeout client used only for LAN scanning. */
    private val scanClient = OkHttpClient.Builder()
        .connectTimeout(350, TimeUnit.MILLISECONDS)
        .readTimeout(600, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Scans the device's own /24 subnet for machines answering on the
     * Ollama port (11434). Returns reachable API base URLs.
     */
    suspend fun discover(): List<String> = withContext(Dispatchers.IO) {
        val myIp = runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull()
        val prefix = myIp?.substringBeforeLast('.') ?: return@withContext emptyList()
        val sem = Semaphore(24)
        suspend fun probe(host: String): String? = try {
            sem.withPermit {
                val req = Request.Builder().url("http://$host:11434/api/version").get().build()
                scanClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) "http://$host:11434" else null
                }
            }
        } catch (e: Exception) {
            null
        }
        val results = withTimeoutOrNull(9000) {
            coroutineScope {
                (1..254).map { i -> async { probe("$prefix.$i") } }.awaitAll().filterNotNull()
            }
        }
        results.orEmpty().distinct()
    }

    private val activeCalls = mutableMapOf<String, okhttp3.Call>()

    companion object {
        /**
         * Canonical API base, tolerating whatever the user pastes:
         * "192.168.1.60:11434" | "http://ip:11434/v1/" | "http://ip:11434"
         * → "http://192.168.1.60:11434"
         */
        fun apiBase(raw: String): String {
            var s = raw.trim()
            if (s.isBlank()) s = "http://localhost:11434"
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
            s = s.trimEnd('/').removeSuffix("/v1")
            val rest = s.substringAfter("://")
            val hostPort = rest.substringBefore('/')
            val scheme = if (s.startsWith("https://")) "https://" else "http://"
            return scheme + hostPort
        }

        /** Canonical OpenAI-compatible base, always with a trailing slash. */
        fun normalizeV1(raw: String): String = apiBase(raw) + "/v1/"
    }

    suspend fun listInstalled(host: String): List<OllamaModel> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("${apiBase(host)}/api/tags").get().build()
        quick.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "http_${resp.code}" }
            val body = resp.body?.string().orEmpty()
            val parsed = json.decodeFromString(OTagsResponse.serializer(), body)
            parsed.models
                .filter { it.name.isNotBlank() }
                .sortedBy { it.name }
                .map { m ->
                    OllamaModel(
                        name = m.name,
                        sizeBytes = m.size,
                        family = m.details?.family,
                        parameterSize = m.details?.parameter_size,
                        quantization = m.details?.quantization_level,
                    )
                }
        }
    }

    suspend fun delete(host: String, model: String): Unit = withContext(Dispatchers.IO) {
        val body = json.encodeToString(OPullRequest.serializer(), OPullRequest(model))
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("${apiBase(host)}/api/delete").post(body).build()
        quick.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "http_${resp.code}" }
        }
    }

    /**
     * Streams a pull. [onProgress] receives (totalBytes, completedBytes, statusLine).
     * Cancelling the calling coroutine (or [pause]) aborts the transfer;
     * Ollama resumes from partial layers on the next pull.
     */
    suspend fun pull(
        host: String,
        model: String,
        onProgress: (Long, Long, String) -> Unit = { _, _, _ -> },
    ): String = withContext(Dispatchers.IO) {
        val body = json.encodeToString(OPullRequest.serializer(), OPullRequest(model))
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("${apiBase(host)}/api/pull").post(body).build()
        val call = streaming.newCall(req)
        synchronized(activeCalls) { activeCalls[model] = call }
        try {
            call.execute().use { resp ->
                check(resp.isSuccessful) { "http_${resp.code}" }
                val source = resp.body?.source() ?: throw IOException("empty_body")
                var lastPct = -1
                while (true) {
                    coroutineContext.ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank()) continue
                    val ev = runCatching { json.decodeFromString(OPullEvent.serializer(), line) }
                        .getOrNull() ?: continue
                    ev.error?.let { throw IllegalStateException(it) }
                    val total = ev.total ?: 0L
                    val done = ev.completed ?: 0L
                    if (total > 0) {
                        val pct = ((done * 100) / total).toInt().coerceIn(0, 100)
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress(total, done, ev.status.orEmpty())
                        }
                    } else {
                        onProgress(0, 0, ev.status.orEmpty())
                    }
                    if (ev.status == "success") return@withContext "success"
                }
                "done"
            }
        } catch (e: IOException) {
            // User pressed pause: okhttp aborts with "Canceled" — resume later works.
            if (e.message?.contains("cancel", ignoreCase = true) == true) return@withContext "paused"
            if (!coroutineContext.isActive) throw kotlinx.coroutines.CancellationException("paused", e)
            throw e
        } finally {
            synchronized(activeCalls) { activeCalls.remove(model) }
        }
    }

    /** Aborts an active pull; the next pull resumes from the same point. */
    fun pause(model: String) {
        synchronized(activeCalls) { activeCalls[model]?.cancel() }
    }
}
