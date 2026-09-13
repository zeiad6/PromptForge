package com.promptforge.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/** Arabic-script dominance heuristic (shared). */
internal fun looksArabicText(t: String): Boolean {
    val letters = t.count { it.isLetter() }
    if (letters < 4) return false
    val ar = t.count {
        it.code in 0x0600..0x06FF || it.code in 0x0750..0x077F || it.code in 0xFB50..0xFEFF
    }
    return ar * 100 >= 30 * letters
}

/** A built-in skill: auto-applied when the user's message matches its triggers. */
data class Skill(
    val id: String,
    val ar: String,
    val en: String,
    val triggers: List<String>,
    val instrAr: String,
    val instrEn: String,
)

object Skills {
    val ALL = listOf(
        Skill(
            "translate", "مهارة الترجمة", "Translation",
            listOf("ترجم", "الترجمة", "translate", "translation"),
            "ترجم النص التالي بدقة وكامل معناه، محافِظاً على التنسيق والأسلوب والمصطلحات، مع تحديد لغة المصدر والهدف تلقائياً. أعد الترجمة فقط دون شروح.\nالنص:\n{q}",
            "Translate the following text faithfully — preserve formatting, tone and terminology; auto-detect source/target languages. Output the translation only.\nText:\n{q}",
        ),
        Skill(
            "summarize", "مهارة التلخيص", "Summarization",
            listOf("لخص", "ملخص", "تلخيص", "summarize", "summary", "tl;dr"),
            "لخّص ما يلي في نقاط واضحة: 3–7 نقاط للجوهر، ثم سطر «الخلاصة:» بجملة واحدة. حافظ على الأرقام والأسماء كما هي.\nالنص:\n{q}",
            "Summarize the following in 3–7 crisp bullet points, then a one-line \"Bottom line:\". Keep numbers and names exact.\nText:\n{q}",
        ),
        Skill(
            "code", "مهارة البرمجة", "Coding",
            listOf("كود", "برمج", "برنامج", "دالة", "سكربت", "code", "script", "function", "bug", "sql", "regex"),
            "اكتب كوداً كاملاً وصحيحاً وقابلاً للتشغيل، بأسلوب نظيف وتعليقات مختصرة بلغة المستخدم. اشرح أي قرار غير بديهي في سطر واحد، واذكر كيفية التشغيل والاختبار.\nالمطلوب:\n{q}",
            "Write complete, correct, runnable code — clean style with brief comments in the user's language. One-line notes for non-obvious decisions, plus how to run & test.\nTask:\n{q}",
        ),
        Skill(
            "math", "مهارة الحساب", "Math solving",
            listOf("احسب", "معادلة", "رياض", "نسبة", "calculate", "equation", "solve", "math"),
            "حل المسألة خطوة خطوة: فكّك المطلوب، نفّذ كل خطوة مع الاستنتاج، تحقّق من صحة الحل بطريقة ثانية إن أمكن، ثم اكتب «الجواب النهائي:» بوضوح.\nالمسألة:\n{q}",
            "Solve step by step: decompose, execute each step with its conclusion, verify with a second method when possible, then state \"Final answer:\" clearly.\nProblem:\n{q}",
        ),
        Skill(
            "email", "مهارة المراسلات", "Email & letters",
            listOf("ايميل", "إيميل", "بريد", "رسالة", "email", "letter", "message to"),
            "اكتب رسالة/بريداً احترافياً جاهزاً للإرسال: سطر موضوع واضح، افتتاحية مهذبة، جسم منظم، وخاتمة بأدب. اختر النبرة حسب السياق.\nالمطلوب:\n{q}",
            "Draft a professional, ready-to-send email: clear subject line, polite opening, organized body, courteous closing. Match tone to context.\nBrief:\n{q}",
        ),
        Skill(
            "explain", "مهارة الشرح", "Explaining",
            listOf("اشرح", "وضح", "ما هو", "ما هي", "كيف", "explain", "what is", "how does", "how to"),
            "اشرح بوضوح تام: تعريف مبسط أولاً، ثم الفكرة بمثال من الحياة، ثم تفاصيل أدق، مع تشبيه مناسب. كيّف العمق حسب مستوى السؤال.\nالموضوع:\n{q}",
            "Explain clearly: simple definition first, then the idea with a real-life example, then finer details, with a fitting analogy. Match depth to the question.\nTopic:\n{q}",
        ),
        Skill(
            "cv", "مهارة السيرة الذاتية", "CV & resumes",
            listOf("سيرة ذاتية", "سيره ذاتيه", "cv", "resume"),
            "أنشئ سيرة ذاتية كاملة منظمة: معلومات، ملخص مهني، خبرات (إنجازات بأرقام)، تعليم، مهارات، وشهادات — بصيغة جاهزة للنسخ.\nالمعطيات:\n{q}",
            "Create a complete, well-structured CV: info, professional summary, experience (quantified achievements), education, skills, certifications — copy-ready.\nInput:\n{q}",
        ),
        Skill(
            "research", "مهارة البحث", "Research",
            listOf("بحث", "مصادر", "دراسة", "research", "sources", "deep dive"),
            "أعد بحثاً منظماً: مقدمة، محاور رئيسية، تحليل، ثم «مصادر ومراجع». ميّز: ✅ موثّق / 🟡 استنتاج / ⚠️ فرضية، ولا تختلق مصادر أبداً.\nالموضوع:\n{q}",
            "Produce an organized research brief: intro, key sections, analysis, then \"Sources\". Label ✅ verified / 🟡 inference / ⚠️ assumption; never fabricate sources.\nTopic:\n{q}",
        ),
    )

    /** First skill whose trigger appears in the message. */
    fun match(userText: String): Skill? {
        val l = userText.lowercase()
        return ALL.firstOrNull { sk -> sk.triggers.any { l.contains(it) } }
    }

    /** Renders the skill instruction in the user's language. */
    fun render(sk: Skill, userText: String): String =
        if (looksArabicText(userText)) sk.instrAr.replace("{q}", userText.take(4000))
        else sk.instrEn.replace("{q}", userText.take(4000))
}

@Serializable
data class McpServer(
    val id: String,
    val name: String,
    val url: String,
    /** "name :: description" lines for the discovered tools. */
    val tools: String = "",
    val lastError: String = "",
)

/**
 * Minimal MCP (Model Context Protocol) client over the Streamable-HTTP
 * transport: JSON-RPC 2.0 `initialize` → `tools/list` → `tools/call`,
 * with `Mcp-Session-Id` session handling. Works with any remote MCP endpoint.
 */
class McpManager(private val context: Context, private val json: Json) {

    private val file = File(context.filesDir, "mcp_servers.json")
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    fun all(): List<McpServer> = runCatching {
        if (!file.exists()) emptyList()
        else json.decodeFromString(ListSerializer(McpServer.serializer()), file.readText())
    }.getOrElse { emptyList() }

    private fun save(list: List<McpServer>) {
        runCatching { file.writeText(json.encodeToString(ListSerializer(McpServer.serializer()), list)) }
    }

    fun toolCount(s: McpServer): Int = s.tools.lines().count { it.contains(" :: ") }

    /** Connects (initialize + tools/list) and persists the server. */
    suspend fun addOrUpdate(name: String, url: String): McpServer = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        // 1) initialize
        val initBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":" +
            "{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{}," +
            "\"clientInfo\":{\"name\":\"PromptForge\",\"version\":\"2.1\"}}}"
        val (initResp, session, err1) = rpc(trimmedUrl, initBody, null, 1)
        if (initResp == null) throw java.io.IOException(err1 ?: "initialize_failed")
        // 2) initialized notification (no response expected)
        runCatching {
            rpc(trimmedUrl, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}", session, 0)
        }
        // 3) tools/list
        val (listResp, _, err3) = rpc(
            trimmedUrl,
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}",
            session, 2,
        )
        if (listResp == null) throw java.io.IOException(err3 ?: "tools_list_failed")
        val toolsText = runCatching {
            val tools = listResp["result"]?.jsonObject?.get("tools")?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())
            tools.mapNotNull { t ->
                val o = t.jsonObject
                val n = o["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val d = o["description"]?.jsonPrimitive?.content ?: ""
                "$n :: ${d.take(160)}"
            }.joinToString("\n")
        }.getOrDefault("")
        val server = McpServer(
            id = trimmedUrl.hashCode().toString(36) + "_" + name.hashCode().toString(36),
            name = name.trim().ifEmpty { "MCP" },
            url = trimmedUrl,
            tools = toolsText,
            lastError = "",
        )
        save(all().filter { it.url != trimmedUrl } + server)
        server
    }

    fun remove(id: String) = save(all().filter { it.id != id })

    /** Calls a tool on [server] with a single `query` argument. */
    suspend fun callTool(server: McpServer, tool: String, query: String): String =
        withContext(Dispatchers.IO) {
            val (_, session, e0) = rpc(
                server.url,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":" +
                    "{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{}," +
                    "\"clientInfo\":{\"name\":\"PromptForge\",\"version\":\"2.1\"}}}",
                null, 1,
            )
            if (session == null && e0 != null && server.url.startsWith("http://")) {
                // some servers are stateless; continue anyway
            }
            val safeQ = query.take(500)
                .replace("\\", "\\\\").replace("\"", "'")
                .replace("\n", " ").replace("\r", " ")
            val body = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":" +
                "{\"name\":\"" + tool.replace("\"", "") + "\",\"arguments\":{\"query\":\"" + safeQ + "\"}}}"
            val (resp, _, err) = rpc(server.url, body, session, 3)
            if (resp == null) throw java.io.IOException(err ?: "call_failed")
            val result = resp["result"]?.jsonObject
                ?: throw java.io.IOException(resp["error"]?.toString() ?: "no_result")
            val isError = result["isError"]?.jsonPrimitive?.content == "true"
            val text = runCatching {
                result["content"]?.jsonArray?.mapNotNull { c ->
                    c.jsonObject["text"]?.jsonPrimitive?.content
                }?.joinToString("\n")
            }.getOrNull().orEmpty()
            if (isError) throw java.io.IOException(text.ifEmpty { "tool_error" })
            text
        }

    /**
     * Smart auto-use: routes the user's message to the most relevant MCP tool
     * (keyword routing across all configured servers) and returns its output,
     * or null when nothing fits. Never throws.
     */
    suspend fun autoUse(query: String): String? {
        val q = query.lowercase()
        val want: List<String> = when {
            listOf("طقس", "weather", "درجة الحرارة").any { q.contains(it) } ->
                listOf("weather", "طقس", "forecast")
            listOf("ابحث", "بحث", "search", "أخبار", "اخبار", "news", "web", "جلبدid").any { q.contains(it) } ->
                listOf("search", "web", "fetch", "news", "http", "browse")
            listOf("احسب", "calculate", "calc", "حسب").any { q.contains(it) } ->
                listOf("calc", "math", "eval", "compute")
            listOf("وقت", "الساعة", "time", "date", "اليوم").any { q.contains(it) } ->
                listOf("time", "clock", "date", "calendar")
            else -> emptyList()
        }
        if (want.isEmpty()) return null
        for (s in all()) {
            val tool = s.tools.lines()
                .map { it.split(" :: ")[0].trim() to (it.substringAfter(" :: ", "").lowercase()) }
                .firstOrNull { (n, d) ->
                    val nd = (n + " " + d).lowercase()
                    want.any { nd.contains(it) }
                }?.first ?: continue
            return runCatching { callTool(s, tool, query) }.getOrNull() ?: continue
        }
        return null
    }

    /** One JSON-RPC POST; handles both plain-JSON and SSE responses. */
    private fun rpc(
        url: String,
        body: String,
        session: String?,
        expectId: Int,
    ): Triple<JsonObject?, String?, String?> = runCatching {
        val b = body.toRequestBody("application/json".toMediaType())
        val rb = Request.Builder().url(url).post(b)
            .header("Accept", "application/json, text/event-stream")
        if (!session.isNullOrBlank()) rb.header("Mcp-Session-Id", session)
        http.newCall(rb.build()).execute().use { resp ->
            if (!resp.isSuccessful) return Triple(null, session, "HTTP ${resp.code}")
            val newSession = resp.header("Mcp-Session-Id") ?: session
            val raw = resp.body?.string().orEmpty()
            val ct = resp.header("Content-Type").orEmpty()
            val payload: String = if (ct.contains("event-stream") || raw.contains("\ndata:")) {
                raw.lines().filter { it.startsWith("data:") }
                    .map { it.removePrefix("data:").trim() }
                    .firstOrNull { it.contains("\"result\"") || it.contains("\"id\":$expectId") || it.contains("\"id\": $expectId") }
                    ?: ""
            } else raw
            if (payload.isEmpty()) return Triple(null, newSession, "empty_response")
            val obj = json.parseToJsonElement(payload).jsonObject
            if (obj.containsKey("error")) {
                return Triple(null, newSession, obj["error"].toString().take(200))
            }
            Triple(obj, newSession, null as String?)
        }
    }.getOrElse { Triple(null, session, it.message ?: "network_error") }
}
