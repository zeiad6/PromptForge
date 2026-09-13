package com.promptforge.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/** Conversation turn used by the Playground. */
data class ChatMsg(val role: String, val text: String)

/** Data pushed from other screens into the Prompt Lab. */
data class PlaygroundSetup(
    val systemPrompt: String,
    val title: String?,
    val promptId: String? = null,
)
enum class Provider(
    val key: String,
    val labelAr: String,
    val labelEn: String,
    val baseUrl: String,
    val keyUrl: String,
    val mono: String,
    val defaultModels: List<String>,
    val freeNoteAr: String,
    val freeNoteEn: String,
) {
    GEMINI(
        "gemini", "Google Gemini", "Google Gemini",
        "https://generativelanguage.googleapis.com/",
        "https://aistudio.google.com/apikey",
        "G",
        listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash"),
        "أكثر من ١٠٠٠ طلب يومياً مجاناً — بدون بطاقة بنكية",
        "1,000+ free requests per day — no credit card",
    ),
    GROQ(
        "groq", "Groq", "Groq",
        "https://api.groq.com/openai/v1/",
        "https://console.groq.com/keys",
        "GQ",
        listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "openai/gpt-oss-120b",
            "openai/gpt-oss-20b",
            "qwen/qwen3-32b",
            "deepseek-r1-distill-llama-70b",
        ),
        "استدلال فائق السرعة — آلاف الطلبات يومياً مجاناً",
        "Blazing fast inference — thousands of free daily requests",
    ),
    OPENROUTER(
        "openrouter", "OpenRouter", "OpenRouter",
        "https://openrouter.ai/api/v1/",
        "https://openrouter.ai/keys",
        "OR",
        listOf(
            "google/gemma-4-31b-it:free",
            "google/gemma-4-26b-a4b-it:free",
            "nvidia/nemotron-3-super-120b-a12b:free",
            "nvidia/nemotron-3-ultra-550b-a55b:free",
            "inclusionai/ling-3.0-flash-vl:free",
            "thinkingmachines/inkling:free",
        ),
        "عشرات الموديلات المجانية (:free) — ٥٠ طلباً يومياً",
        "Dozens of :free models — 50 requests/day",
    ),
    MISTRAL(
        "mistral", "Mistral", "Mistral",
        "https://api.mistral.ai/v1/",
        "https://console.mistral.ai/api-keys",
        "MI",
        listOf("mistral-small-latest", "open-mistral-nemo-2407", "mistral-large-latest", "codestral-latest"),
        "باقة تجريبية مجانية سخية — بدون بطاقة",
        "Generous free experiment tier — no credit card",
    ),
    CEREBRAS(
        "cerebras", "سيربراس", "Cerebras",
        "https://api.cerebras.ai/v1/",
        "https://cloud.cerebras.ai",
        "CB",
        listOf("gpt-oss-120b", "llama3.1-8b", "llama-4-scout-17b-16e-instruct", "qwen-3-32b"),
        "مليون توكن يومياً مجاناً + أسرع استدلال عالمياً",
        "1M tokens/day free + the fastest inference on the planet",
    ),
    DEVICE(
        "device", "على الجهاز", "On-device",
        "internal://device",
        "https://huggingface.co/litert-community",
        "AI",
        emptyList(),
        "استدلال كامل داخل هاتفك: بلا حاسوب، بلا إنترنت، بلا مفاتيح",
        "Runs fully on your phone: no PC, no internet, no keys",
    ),
    LOCAL(
        "local", "محلي (Ollama)", "Local (Ollama)",
        "http://localhost:11434/v1/",
        "https://ollama.com/download",
        "OL",
        listOf("llama3.1:8b", "qwen3:8b", "gemma3:4b", "deepseek-r1:8b", "mistral:7b"),
        "موديلات على جهازك: بلا مفاتيح، بلا إنترنت، بلا حدود",
        "Models on your machine: no keys, no internet, no limits",
    );

    companion object {
        fun byKey(k: String): Provider = entries.firstOrNull { it.key == k } ?: GEMINI
    }
}

// ── OpenAI-compatible DTOs ─────────────────────────────────────────

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    val max_tokens: Int? = null,
)

@Serializable
data class ChatChoice(val message: ChatMessage? = null)

@Serializable
data class ChatCompletionResponse(val choices: List<ChatChoice> = emptyList()) {
    val firstText: String get() = choices.firstOrNull()?.message?.content.orEmpty()
}

@Serializable
data class ModelsResponse(val data: List<ModelDto> = emptyList())

@Serializable
data class ModelDto(val id: String = "", val name: String? = null)

@Serializable
data class ORKeyData(val label: String? = null, val is_free_tier: Boolean? = null)

@Serializable
data class ORKeyResponse(val data: ORKeyData? = null)

// ── Gemini DTOs ────────────────────────────────────────────────────

@Serializable
data class GPart(val text: String)

@Serializable
data class GContent(val role: String? = null, val parts: List<GPart> = emptyList())

@Serializable
data class GGenerationConfig(val temperature: Double? = null, val maxOutputTokens: Int? = null)

@Serializable
data class GGenerateRequest(
    val contents: List<GContent>,
    val systemInstruction: GContent? = null,
    val generationConfig: GGenerationConfig? = null,
)

@Serializable
data class GCandidate(val content: GContent? = null)

@Serializable
data class GGenerateResponse(val candidates: List<GCandidate> = emptyList()) {
    val firstText: String get() = candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text.orEmpty()
}

@Serializable
data class GModel(
    val name: String = "",
    val displayName: String? = null,
    val supportedGenerationMethods: List<String> = emptyList(),
)

@Serializable
data class GModelsResponse(val models: List<GModel> = emptyList())

// ── Retrofit services ──────────────────────────────────────────────

interface OpenAiCompatApi {
    @GET("models")
    suspend fun models(@Header("Authorization") auth: String?): ModelsResponse

    @POST("chat/completions")
    suspend fun chat(@Header("Authorization") auth: String, @Body body: ChatCompletionRequest): ChatCompletionResponse

    @POST("chat/completions")
    suspend fun chatWithHeaders(@HeaderMap headers: Map<String, String>, @Body body: ChatCompletionRequest): ChatCompletionResponse

    @GET("key")
    suspend fun keyInfo(@Header("Authorization") auth: String): ORKeyResponse
}

interface GeminiApi {
    @GET("v1beta/models")
    suspend fun models(
        @Query("key") key: String,
        @Query("pageSize") pageSize: Int = 200,
    ): GModelsResponse

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generate(
        @Path("model") model: String,
        @Query("key") key: String,
        @Body body: GGenerateRequest,
    ): GGenerateResponse
}

// ── Unified client ─────────────────────────────────────────────────

class LlmClient(debugLogging: Boolean, private val json: Json) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .apply {
            if (debugLogging) addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
        .build()

    private val contentType = "application/json".toMediaType()
    private val openAiApis = mutableMapOf<String, OpenAiCompatApi>()

    private fun retrofit(baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()

    private fun openAiApi(baseUrl: String): OpenAiCompatApi =
        synchronized(openAiApis) { openAiApis.getOrPut(baseUrl) { retrofit(baseUrl).create(OpenAiCompatApi::class.java) } }

    private val geminiApi: GeminiApi by lazy { retrofit(Provider.GEMINI.baseUrl).create(GeminiApi::class.java) }

    /**
     * Sends a chat request. [turns] is the conversation history as (role, content) pairs.
     * Returns the assistant text.
     */
    suspend fun chat(
        provider: Provider,
        apiKey: String,
        model: String,
        system: String?,
        turns: List<Pair<String, String>>,
        temperature: Double,
        maxTokens: Int = 2048,
        baseUrlOverride: String? = null,
    ): String {
        require(apiKey.isNotBlank()) { "missing_api_key" }
        return when (provider) {
            Provider.GEMINI -> {
                val contents = turns.map { (role, content) ->
                    GContent(role = if (role == "assistant") "model" else "user", parts = listOf(GPart(content)))
                }
                val request = GGenerateRequest(
                    contents = contents,
                    systemInstruction = system?.takeIf { it.isNotBlank() }?.let { GContent(role = "system", parts = listOf(GPart(it))) },
                    generationConfig = GGenerationConfig(temperature = temperature, maxOutputTokens = maxTokens),
                )
                val response = geminiApi.generate(model, apiKey, request)
                response.firstText.ifBlank { throw IllegalStateException("empty_response") }
            }
            else -> {
                val messages = buildList {
                    if (!system.isNullOrBlank()) add(ChatMessage("system", system))
                    turns.forEach { (role, content) -> add(ChatMessage(role, content)) }
                }
                val body = ChatCompletionRequest(model = model, messages = messages, temperature = temperature, max_tokens = maxTokens)
                val base = if (provider == Provider.LOCAL) OllamaManager.normalizeV1(baseUrlOverride ?: provider.baseUrl)
                    else (baseUrlOverride ?: provider.baseUrl)
                val response = if (provider == Provider.OPENROUTER) {
                    openAiApi(base).chatWithHeaders(
                        headers = mapOf(
                            "Authorization" to "Bearer $apiKey",
                            "HTTP-Referer" to "https://promptforge.app",
                            "X-Title" to "PromptForge",
                        ),
                        body = body,
                    )
                } else {
                    openAiApi(base).chat(if (provider == Provider.LOCAL) "Bearer local" else "Bearer $apiKey", body)
                }
                response.firstText.ifBlank { throw IllegalStateException("empty_response") }
            }
        }
    }

    /** Fetches the live model list; falls back to curated defaults on failure. */
    suspend fun listModels(provider: Provider, apiKey: String?, baseUrlOverride: String? = null): List<String> = runCatching {
        when (provider) {
            Provider.LOCAL -> openAiApi(OllamaManager.normalizeV1(baseUrlOverride ?: provider.baseUrl))
                .models("Bearer local").data.map { it.id }.filter { it.isNotBlank() }.sorted()
            Provider.GEMINI -> {
                require(!apiKey.isNullOrBlank()) { "missing_api_key" }
                geminiApi.models(apiKey).models
                    .filter {
                        it.supportedGenerationMethods.any { m -> m.equals("generateContent", true) } &&
                            !it.name.contains("embedding", true) &&
                            !it.name.contains("aqa", true) &&
                            !it.name.contains("imagen", true) &&
                            !it.name.contains("veo", true) &&
                            !it.name.contains("tts", true) &&
                            !it.name.contains("live", true)
                    }
                    .map { it.name.removePrefix("models/") }
                    .sorted()
            }
            Provider.GROQ -> openAiApi(provider.baseUrl).models(apiKey?.takeIf { it.isNotBlank() }?.let { "Bearer $it" })
                .data.map { it.id }.sorted()
            Provider.OPENROUTER -> openAiApi(provider.baseUrl).models(null)
                .data.filter { it.id.endsWith(":free") }.map { it.id }.sorted()
            Provider.DEVICE -> emptyList() // models live in the on-device registry
            else -> emptyList()
        }
    }.getOrElse { provider.defaultModels }

    suspend fun validateKey(provider: Provider, apiKey: String, baseUrlOverride: String? = null): String {
        if (provider == Provider.LOCAL) {
            return if (listModels(Provider.LOCAL, null, baseUrlOverride).isNotEmpty()) "ok" else "network"
        }
        if (apiKey.isBlank()) return "invalid"
        return try {
            when (provider) {
                Provider.GEMINI -> geminiApi.models(apiKey, pageSize = 1)
                Provider.GROQ -> openAiApi(provider.baseUrl).models("Bearer $apiKey")
                Provider.OPENROUTER -> openAiApi(provider.baseUrl).keyInfo("Bearer $apiKey")
                else -> openAiApi(provider.baseUrl).models("Bearer $apiKey")
            }
            "ok"
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 429) "ok" else "invalid"
        } catch (e: Exception) {
            "network"
        }
    }
}

object LlmErrors {
    const val RATE = "rate"
    const val KEY = "invalid_key"
    const val MODEL = "model"
    const val BAD = "bad_request"
    const val NETWORK = "network"
    const val EMPTY = "empty"
    const val REGION = "region"
    const val OTHER = "other"

    /** Single-pass classifier: the error body can only be read once. */
    fun kindOf(code: Int?, body: String, cause: Throwable): String = when {
        code == null -> when (cause) {
            is java.io.IOException -> NETWORK
            is IllegalStateException -> if (cause.message == "empty_response") EMPTY else OTHER
            is IllegalArgumentException -> if (cause.message == "missing_api_key") KEY else OTHER
            else -> OTHER
        }
        else -> when (code) {
            429, 402 -> RATE
            401, 403 -> KEY
            404 -> MODEL
            503, 529 -> MODEL
            408, 504 -> NETWORK
            400 -> when {
                body.contains("FAILED_PRECONDITION", true) || body.contains("User location", true) -> REGION
                body.contains("API key", true) || body.contains("API_KEY", true) -> KEY
                else -> BAD
            }
            else -> OTHER
        }
    }

    fun bodyOf(e: Throwable): String =
        (e as? retrofit2.HttpException)?.let { ex ->
            runCatching { ex.response()?.errorBody()?.string() }.getOrNull()
        }.orEmpty()

    /** Google puts "retryDelay":"18s" in 429 bodies - honor it. */
    fun retrySecondsOf(body: String): Long? {
        Regex("""retryDelay"\s*:\s*"(\d+(?:\.\d+)?)s""").find(body)?.let {
            return it.groupValues[1].toFloatOrNull()?.toLong()?.coerceIn(3, 45)
        }
        Regex("""retry after (\d+)""").find(body)?.let {
            return it.groupValues[1].toLongOrNull()?.coerceIn(3, 45)
        }
        return null
    }

    fun kind(e: Throwable): String = kindOf(
        (e as? retrofit2.HttpException)?.code(), bodyOf(e), e,
    )
}
