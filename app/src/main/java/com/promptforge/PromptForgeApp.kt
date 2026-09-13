package com.promptforge

import android.app.Application
import com.promptforge.data.ForgeVmFactory
import com.promptforge.data.DeviceLlmEngine
import com.promptforge.data.DeviceModelDownloader
import com.promptforge.data.DeviceModelRegistry
import com.promptforge.data.LlmClient
import com.promptforge.data.McpManager
import com.promptforge.data.Skills
import com.promptforge.data.looksArabicText
import com.promptforge.data.OllamaManager
import com.promptforge.data.PlaygroundSetup
import com.promptforge.data.PromptRepository
import com.promptforge.data.AppSettings
import com.promptforge.data.Provider
import com.promptforge.data.SettingsStore
import com.promptforge.core.PromptDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Manual DI container — no framework, zero codegen, instant startup.
 */
class AppContainer(app: Application) {

    val appContext: android.content.Context = app.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false
        prettyPrint = true
    }

    val settings = SettingsStore(app)
    val repository = PromptRepository(app, json)
    val llm = LlmClient(debugLogging = false, json = json)
    val ollama = OllamaManager(json)
    val deviceRegistry = DeviceModelRegistry(appContext, json)
    val deviceEngine = DeviceLlmEngine(appContext)
    val deviceDownloader = DeviceModelDownloader(deviceRegistry)

    /** Draft hand-off (templates → builder, home → builder). */
    val draftHolder = MutableStateFlow<PromptDraft?>(null)

    /** Prompt hand-off (anywhere → playground). */
    val playgroundHolder = MutableStateFlow<PlaygroundSetup?>(null)

    val vmFactory = ForgeVmFactory(this)
    val mcp = McpManager(appContext, json)
    val builtAppsDir: java.io.File = java.io.File(appContext.filesDir, "built_apps").apply { mkdirs() }

    init {
        runCatching { com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(appContext) }
        appScope.launch {
            repository.load()
            repository.seedIfEmpty()
        }
    }

    /**
     * One entry point for single-shot generations (enhance / research):
     * routes to the cloud provider or the on-device engine transparently.
     */
    suspend fun chatSmart(
        settings: AppSettings,
        system: String,
        user: String,
        temperature: Double,
        maxTokens: Int = 2048,
    ): String {
        if (settings.provider == Provider.DEVICE) {
            val registry = deviceRegistry.all()
            val wanted = settings.modelFor(Provider.DEVICE)
            val model = registry.firstOrNull { it.name == wanted || it.displayName() == wanted }
                ?: registry.firstOrNull()
                ?: throw IllegalArgumentException("no_device_model")
            val prompt = buildString {
                if (system.isNotBlank()) append(system).append("\n\n")
                append(user)
            }
            DownloadService.startInference(
                appContext,
                appContext.getString(com.promptforge.R.string.service_inference),
            )
            try {
                return deviceEngine.chat(model, prompt, temperature.toFloat())
            } finally {
                DownloadService.stopInference(appContext)
            }
        }
        return llm.chat(
            provider = settings.provider,
            apiKey = settings.apiKey(settings.provider),
            model = settings.modelFor(settings.provider),
            system = system,
            turns = listOf("user" to user),
            temperature = temperature,
            maxTokens = maxTokens,
        )
    }

    /** Device-first one-shot generation used by App Builder & Documents. */
    suspend fun smartChat(system: String, user: String, maxTokens: Int = 4096): String {
        val s = settings.settings.first()
        val dm = deviceRegistry.all().firstOrNull()
        return if (dm != null) {
            val prompt = buildString {
                if (system.isNotBlank()) append(system.trim()).append("\n\n")
                append(user)
            }
            DownloadService.startInference(
                appContext, appContext.getString(com.promptforge.R.string.service_inference),
            )
            try {
                deviceEngine.chat(dm, prompt, 0.6f)
            } finally {
                DownloadService.stopInference(appContext)
            }
        } else {
            chatSmart(s, system, user, 0.6, maxTokens)
        }
    }

    /** Smart Tools: auto-applies the matching skill + relevant MCP tool to a
     *  user message, even when the user did not ask. Never throws. */
    suspend fun smartContextFor(currentSystem: String?, userText: String): String {
        val s = runCatching { settings.settings.first() }.getOrNull()
        if (s != null && !s.smartTools) return currentSystem.orEmpty()
        val sb = StringBuilder(currentSystem.orEmpty())
        Skills.match(userText)?.let { sk ->
            if (sb.isNotBlank()) sb.append("\n\n")
            sb.append("[").append(if (looksArabicText(userText)) sk.ar else sk.en).append("] ")
            sb.append(Skills.render(sk, userText))
        }
        val toolOut = runCatching { mcp.autoUse(userText) }.getOrNull()
        if (!toolOut.isNullOrBlank()) {
            if (sb.isNotBlank()) sb.append("\n\n")
            sb.append("[MCP]\n").append(toolOut.take(3000))
        }
        return sb.toString()
    }
}

class PromptForgeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        installCrashCapture()
        container = AppContainer(this)
    }

    /**
     * Crash-proofing: persist any uncaught stack trace so it can be
     * inspected (and copied) from Settings → About, instead of vanishing.
     */
    private fun installCrashCapture() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                java.io.File(filesDir, "crash_log.txt").writeText(
                    "time=" + java.util.Date() + "\nthread=" + t.name + "\n\n" +
                        android.util.Log.getStackTraceString(e).take(12000)
                )
            }
            prev?.uncaughtException(t, e)
        }
    }
}
