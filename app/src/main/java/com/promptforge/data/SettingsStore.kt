package com.promptforge.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "forge_settings")

data class AppSettings(
    val provider: Provider = Provider.GEMINI,
    val keys: Map<Provider, String> = emptyMap(),
    val models: Map<Provider, String> = emptyMap(),
    val temperature: Float = 0.7f,
    val onboarded: Boolean = false,
    val theme: String = "system", // system | dark | light
    val localBaseUrl: String = "http://localhost:11434/v1/",
) {
    fun apiKey(p: Provider): String = keys[p].orEmpty()

    /** Never throws: providers without a default list (e.g. DEVICE) return "". */
    fun modelFor(p: Provider): String = models[p] ?: p.defaultModels.firstOrNull().orEmpty()
}

/**
 * DataStore-backed settings: provider choice, API keys per provider,
 * model choice per provider, creativity and onboarding flag.
 */
class SettingsStore(private val context: Context) {

    private object K {
        val provider = stringPreferencesKey("provider")
        val temperature = floatPreferencesKey("temperature")
        val onboarded = booleanPreferencesKey("onboarded")
        val theme = stringPreferencesKey("theme")
        val localBase = stringPreferencesKey("local_base_url")
        fun key(p: Provider) = stringPreferencesKey("key_${p.key}")
        fun model(p: Provider) = stringPreferencesKey("model_${p.key}")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            provider = p[K.provider]?.let(Provider::byKey) ?: Provider.GEMINI,
            keys = Provider.entries.mapNotNull { prov ->
                p[K.key(prov)]?.takeIf { it.isNotBlank() }?.let { prov to it }
            }.toMap(),
            models = Provider.entries.mapNotNull { prov ->
                p[K.model(prov)]?.takeIf { it.isNotBlank() }?.let { prov to it }
            }.toMap(),
            temperature = p[K.temperature] ?: 0.7f,
            onboarded = p[K.onboarded] ?: false,
            theme = p[K.theme] ?: "system",
            localBaseUrl = p[K.localBase] ?: "http://localhost:11434/v1/",
        )
    }

    suspend fun setTheme(value: String) = context.settingsDataStore.edit { it[K.theme] = value }
    suspend fun setLocalBaseUrl(value: String) = context.settingsDataStore.edit { it[K.localBase] = OllamaManager.normalizeV1(value) }
    suspend fun setProvider(value: Provider) = context.settingsDataStore.edit { it[K.provider] = value.key }
    suspend fun setApiKey(p: Provider, value: String) = context.settingsDataStore.edit { it[K.key(p)] = value.trim() }
    suspend fun setModel(p: Provider, value: String) = context.settingsDataStore.edit { it[K.model(p)] = value }
    suspend fun setTemperature(value: Float) = context.settingsDataStore.edit { it[K.temperature] = value }
    suspend fun setOnboarded(value: Boolean) = context.settingsDataStore.edit { it[K.onboarded] = value }

    suspend fun clearAll() = context.settingsDataStore.edit { it.clear() }
}
