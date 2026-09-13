package com.promptforge

import android.app.Application
import com.promptforge.data.ForgeVmFactory
import com.promptforge.data.DeviceLlmEngine
import com.promptforge.data.DeviceModelDownloader
import com.promptforge.data.DeviceModelRegistry
import com.promptforge.data.LlmClient
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

    init {
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
