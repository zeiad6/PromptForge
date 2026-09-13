package com.promptforge.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import android.app.Activity
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.promptforge.AppContainer
import com.promptforge.BuildConfig
import com.promptforge.R
import android.provider.OpenableColumns
import com.promptforge.data.AppSettings
import com.promptforge.data.DeviceDownloadSpec
import com.promptforge.data.DeviceModel
import com.promptforge.data.DeviceModelDownloader
import com.promptforge.data.DlUi
import com.promptforge.data.Provider
import com.promptforge.ui.components.BrandButton
import com.promptforge.ui.components.GhostButton
import com.promptforge.ui.components.GlassCard
import com.promptforge.ui.components.LoadingDots
import com.promptforge.ui.components.OptionPill
import com.promptforge.ui.components.SectionHeader
import com.promptforge.ui.nav.LocalAppContainer
import com.promptforge.util.LangPrefs
import com.promptforge.ui.theme.Palette
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val modelsCache: Map<Provider, List<String>> = emptyMap(),
    val refreshing: Provider? = null,
    val deviceModels: List<DeviceModel> = emptyList(),
    val downloads: Map<String, DlUi> = emptyMap(),
)

class SettingsViewModel(private val c: AppContainer) : ViewModel() {

    private val modelsCache = MutableStateFlow<Map<Provider, List<String>>>(emptyMap())
    private val refreshing = MutableStateFlow<Provider?>(null)

    private val deviceModelsFlow = MutableStateFlow<List<DeviceModel>>(emptyList())

    val state: StateFlow<SettingsUiState> = kotlinx.coroutines.flow.combine(
        c.settings.settings, modelsCache, refreshing, deviceModelsFlow, c.deviceDownloader.progress,
    ) { s, cache, busy, deviceModels, downloads ->
        SettingsUiState(s, cache, busy, deviceModels, downloads)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    init { refreshDeviceModels() }

    /** Live on-device model list — read off the main thread, pushed via state. */
    fun refreshDeviceModels() = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        deviceModelsFlow.value = runCatching { c.deviceRegistry.all() }.getOrElse { emptyList() }
    }

    fun selectProvider(p: Provider) = viewModelScope.launch { c.settings.setProvider(p) }

    fun saveKey(p: Provider, key: String, onDone: () -> Unit = {}) = viewModelScope.launch {
        c.settings.setApiKey(p, key.trim())
        onDone()
    }

    fun setTheme(v: String) = viewModelScope.launch { c.settings.setTheme(v) }

    fun verifyKey(p: Provider, key: String, cb: (String) -> Unit) = viewModelScope.launch {
        cb(c.llm.validateKey(p, key))
    }

    fun setModel(p: Provider, m: String) = viewModelScope.launch { c.settings.setModel(p, m) }

    fun setLocalBaseUrl(v: String) = viewModelScope.launch { c.settings.setLocalBaseUrl(v) }

    fun testLocal(url: String, cb: (Int?) -> Unit) = viewModelScope.launch {
        val models = c.llm.listModels(Provider.LOCAL, null, url.trim())
        if (models.isNotEmpty()) modelsCache.value = modelsCache.value + (Provider.LOCAL to models)
        cb(if (models.isEmpty()) null else models.size)
    }

    fun setTemperature(v: Float) = viewModelScope.launch { c.settings.setTemperature(v) }

    fun refreshModels(p: Provider, key: String) = viewModelScope.launch {
        refreshing.value = p
        try {
            val localBase = c.settings.settings.first().localBaseUrl
            val models = c.llm.listModels(
                p, key.takeIf { it.isNotBlank() },
                if (p == Provider.LOCAL) localBase else null,
            )
            modelsCache.value = modelsCache.value + (p to models)
        } finally {
            refreshing.value = null
        }
    }

    fun importDeviceModel(uri: Uri, displayName: String?, cb: (Boolean) -> Unit) = viewModelScope.launch {
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                c.deviceRegistry.import(c.appContext, uri, displayName ?: uri.lastPathSegment ?: "model.task")
            }
            refreshDeviceModels()
            cb(true)
        } catch (e: Exception) {
            cb(false)
        }
    }

    fun deleteDeviceModel(name: String) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        runCatching { c.deviceRegistry.remove(name) }
        refreshDeviceModels()
    }

    /** One-tap in-app download (no browser); foreground service keeps it alive in background. */
    fun downloadDeviceModel(spec: DeviceDownloadSpec) {
        com.promptforge.DownloadService.startDownload(
            c.appContext, c.appContext.getString(R.string.service_download),
        )
        return c.deviceDownloader.download(viewModelScope, spec) { ok, name ->
        if (ok && name != null) {
            viewModelScope.launch { c.settings.setModel(Provider.DEVICE, name) }
            refreshDeviceModels()
        }
            Toast.makeText(
                c.appContext,
                c.appContext.getString(if (ok) R.string.device_dl_done else R.string.device_dl_fail),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun pauseDeviceDownload(id: String) = c.deviceDownloader.pause(id)

    /** Last uncaught stack trace (if any) — for the About card crash viewer. */
    fun crashLog(cb: (String?) -> Unit) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val f = java.io.File(c.appContext.filesDir, "crash_log.txt")
        cb(runCatching { if (f.exists()) f.readText() else null }.getOrNull())
    }

    fun resetAll(onDone: () -> Unit = {}) = viewModelScope.launch {
        c.repository.clearAll()
        c.settings.clearAll()
        onDone()
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit = {}) {
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel(factory = container.vmFactory)
    val state by vm.state.collectAsStateWithLifecycle()
    // FIX: real Android context for toasts & browser intents (was silently exiting the screen before)
    val appContext = LocalContext.current
    val keyVerifiedMsg = stringResource(R.string.key_verified)
    val keyInvalidMsg = stringResource(R.string.key_invalid)
    val keyUnverifiedMsg = stringResource(R.string.key_unverified)
    val clipboard = LocalClipboardManager.current
    val keySavedMsg = stringResource(R.string.key_saved)
    val keyRemovedMsg = stringResource(R.string.key_removed)

    val selected = state.settings.provider
    val models = state.modelsCache[selected] ?: selected.defaultModels
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // Per-provider drafts: switching tabs never loses what you typed.
    var drafts by remember { mutableStateOf(Provider.entries.associateWith { "" }) }

    // Status-bar notifications (Android 13+): requested on first download.
    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) runCatching { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    // On-device model picker — hoisted to the root so the launcher is
    // registered for the whole lifetime of the screen (crash-proof).
    val devicePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.importDeviceModel(uri, null) { ok ->
                Toast.makeText(
                    appContext,
                    appContext.getString(if (ok) R.string.device_imported else R.string.device_import_fail),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    LaunchedEffect(state.settings.keys) {
        drafts = drafts.mapValues { (p, d) ->
            if (d.isBlank()) state.settings.apiKey(p) else d
        }
    }

    var showKey by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    fun commitCurrentKey(onDone: () -> Unit = {}) {
        val key = drafts[selected].orEmpty().trim()
        if (key != state.settings.apiKey(selected)) vm.saveKey(selected, key, onDone) else onDone()
    }

    fun switchProvider(target: Provider) {
        if (target == selected) return
        commitCurrentKey()
        vm.selectProvider(target)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 40.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        // ── Header with back ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Palette.fill5)
                    .border(1.dp, Palette.glassBorder, RoundedCornerShape(13.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_back),
                    contentDescription = stringResource(R.string.back),
                    tint = Palette.Ink,
                    modifier = Modifier
                        .size(19.dp)
                        .scale(scaleX = if (isRtl) -1f else 1f, scaleY = 1f),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.displaySmall,
                color = Palette.Ink,
            )
        }
        Spacer(Modifier.height(14.dp))

        // ── Providers ──
        SectionHeader(stringResource(R.string.provider_section), painterResource(R.drawable.ic_key))
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.provider_hint), style = MaterialTheme.typography.bodySmall, color = Palette.Sub)
        Spacer(Modifier.height(10.dp))

        Provider.entries.forEach { p ->
            val isSelected = p == selected
            val hasKey = p == Provider.LOCAL || state.settings.apiKey(p).isNotBlank()
            val shape = RoundedCornerShape(18.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(
                        if (isSelected) Brush.linearGradient(listOf(Palette.Primary.copy(alpha = 0.16f), Palette.Cyan.copy(alpha = 0.10f)))
                        else Brush.linearGradient(listOf(Palette.fill5, Palette.fill5))
                    )
                    .border(
                        1.dp,
                        if (isSelected) Brush.linearGradient(Palette.brandColors) else Palette.glassBorder,
                        shape,
                    )
                    .clickable { switchProvider(p) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Palette.brand else Brush.linearGradient(listOf(Palette.fill7, Palette.fill7))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(p.mono, style = MaterialTheme.typography.labelLarge, color = Color.White, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.labelAr, style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
                    Text(
                        p.freeNoteAr,
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Sub,
                        maxLines = 1,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Icon(
                        painterResource(if (hasKey) R.drawable.ic_check else R.drawable.ic_key),
                        contentDescription = null,
                        tint = if (hasKey) Palette.Mint else Palette.Faint,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        stringResource(if (hasKey) R.string.connected else R.string.not_connected),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasKey) Palette.Mint else Palette.Faint,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))

        if (selected == Provider.LOCAL) {
            LocalModelsCard(
                baseUrl = state.settings.localBaseUrl,
                models = state.modelsCache[Provider.LOCAL],
                onSaveHost = { vm.setLocalBaseUrl(it) },
                onTest = { url, cb -> vm.testLocal(url, cb) },
                onManage = { onNavigate(com.promptforge.ui.nav.Routes.LOCALMODELS) },
            )
        } else if (selected == Provider.DEVICE) {
            DeviceModelsCard(
                models = state.deviceModels,
                downloads = state.downloads,
                onPick = { devicePicker.launch(arrayOf("*/*")) },
                onImport = { uri, name, cb -> vm.importDeviceModel(uri, name, cb) },
                onDelete = { vm.deleteDeviceModel(it) },
                onDownload = {
                    ensureNotifPermission()
                    vm.downloadDeviceModel(it)
                },
                onPause = { vm.pauseDeviceDownload(it) },
            )
        } else {
        // ── API key manager (bound to the selected provider) ──
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader(
                stringResource(R.string.api_key_for, selected.labelAr),
                painterResource(R.drawable.ic_key),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = drafts[selected].orEmpty(),
                onValueChange = { v -> drafts = drafts + (selected to v) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.api_key_hint), color = Palette.Faint, style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation('●'),
                trailingIcon = {
                    Text(
                        stringResource(if (showKey) R.string.hide else R.string.show),
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.Cyan,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showKey = !showKey }
                            .padding(6.dp),
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.Primary,
                    unfocusedBorderColor = Palette.hair1,
                    focusedContainerColor = Palette.fill4,
                    unfocusedContainerColor = Palette.fill3,
                    cursorColor = Palette.Cyan,
                ),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrandButton(
                    text = stringResource(R.string.save),
                    icon = painterResource(R.drawable.ic_save),
                    modifier = Modifier.weight(1f).height(46.dp),
                    enabled = drafts[selected].orEmpty().trim().isNotBlank(),
                ) {
                    commitCurrentKey {
                        Toast.makeText(appContext, keySavedMsg, Toast.LENGTH_SHORT).show()
                        val k = drafts[selected].orEmpty().trim()
                        vm.verifyKey(selected, k) { result ->
                            val msg = when (result) {
                                "ok" -> keyVerifiedMsg
                                "invalid" -> keyInvalidMsg
                                else -> keyUnverifiedMsg
                            }
                            Toast.makeText(appContext, msg, Toast.LENGTH_LONG).show()
                        }
                        vm.refreshModels(selected, k)
                    }
                }
                GhostButton(
                    text = stringResource(R.string.paste),
                    modifier = Modifier.weight(1f),
                    icon = painterResource(R.drawable.ic_paste),
                    tint = Palette.Cyan,
                ) {
                    val text = clipboard.getText()?.text?.trim().orEmpty()
                    if (text.isNotBlank()) {
                        drafts = drafts + (selected to text)
                        vm.saveKey(selected, text) {
                            Toast.makeText(appContext, keySavedMsg, Toast.LENGTH_SHORT).show()
                            vm.verifyKey(selected, text) { result ->
                                val msg = when (result) {
                                    "ok" -> keyVerifiedMsg
                                    "invalid" -> keyInvalidMsg
                                    else -> keyUnverifiedMsg
                                }
                                Toast.makeText(appContext, msg, Toast.LENGTH_LONG).show()
                            }
                            vm.refreshModels(selected, text)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (state.settings.apiKey(selected).isNotBlank()) {
                GhostButton(
                    text = stringResource(R.string.remove_key),
                    modifier = Modifier.fillMaxWidth(),
                    icon = painterResource(R.drawable.ic_trash),
                    tint = Palette.Red,
                ) {
                    drafts = drafts + (selected to "")
                    vm.saveKey(selected, "") {
                        Toast.makeText(appContext, keyRemovedMsg, Toast.LENGTH_SHORT).show()
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            GhostButton(
                text = stringResource(R.string.get_free_key),
                modifier = Modifier.fillMaxWidth(),
                icon = painterResource(R.drawable.ic_external),
                tint = Palette.Cyan,
            ) {
                runCatching { appContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(selected.keyUrl))) }
            }
        }
        } // end: non-local key manager

        Spacer(Modifier.height(14.dp))

        // ── Model ──
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader(stringResource(R.string.model), painterResource(R.drawable.ic_bot))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.fill6)
                            .clickable { modelMenu = true }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                    ) {
                        Text(
                            state.settings.modelFor(selected),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = Palette.Ink,
                        )
                    }
                    DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }, containerColor = Palette.SurfaceHi) {
                        models.take(300).forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m, style = MaterialTheme.typography.bodySmall, color = if (m == state.settings.modelFor(selected)) Palette.Cyan else Palette.Ink) },
                                onClick = { modelMenu = false; vm.setModel(selected, m) },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Palette.fill6)
                        .clickable { vm.refreshModels(selected, state.settings.apiKey(selected)) }
                        .padding(10.dp),
                ) {
                    if (state.refreshing == selected) LoadingDots() else
                        Icon(painterResource(R.drawable.ic_refresh), null, tint = Palette.Sub, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.models_count, models.size) + " " +
                    stringResource(if (state.modelsCache.containsKey(selected)) R.string.models_live else R.string.models_default),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Faint,
            )
        }

        Spacer(Modifier.height(14.dp))

        // ── Creativity (temperature) ──
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.temperature_label), style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
            Spacer(Modifier.height(8.dp))
            val t = state.settings.temperature
            val hintRes = when {
                t < 0.45f -> R.string.temp_hint_precise
                t < 0.9f -> R.string.temp_hint_balanced
                t < 1.2f -> R.string.temp_hint_creative
                else -> R.string.temp_hint_wild
            }
            Text(stringResource(hintRes), style = MaterialTheme.typography.labelSmall, color = Palette.Sub)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    stringResource(R.string.temp_precise) to 0.2f,
                    stringResource(R.string.temp_balanced) to 0.7f,
                    stringResource(R.string.temp_creative) to 1.1f,
                    stringResource(R.string.temp_wild) to 1.4f,
                ).forEach { (label, v) ->
                    OptionPill(
                        text = label,
                        selected = kotlin.math.abs(t - v) < 0.26f,
                        modifier = Modifier.weight(1f),
                    ) { vm.setTemperature(v) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = state.settings.temperature,
                    onValueChange = { vm.setTemperature((it * 10).toInt() / 10f) },
                    valueRange = 0f..1.5f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Palette.Cyan,
                        activeTrackColor = Palette.Primary,
                        inactiveTrackColor = Palette.hair1,
                    ),
                )
                Text(
                    String.format(java.util.Locale.US, "%.1f", state.settings.temperature),
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.Ink,
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.End,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── Appearance & language ──
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader(stringResource(R.string.appearance_section), painterResource(R.drawable.ic_sun))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val themes = listOf("system", "dark", "light")
                themes.forEach { t ->
                    val label = when (t) {
                        "dark" -> stringResource(R.string.theme_dark)
                        "light" -> stringResource(R.string.theme_light)
                        else -> stringResource(R.string.theme_system)
                    }
                    OptionPill(label, state.settings.theme == t, Modifier.weight(1f)) { vm.setTheme(t) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.lang_title), style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val current = LangPrefs.get(appContext)
                val langs = listOf(LangPrefs.SYSTEM, LangPrefs.ARABIC, LangPrefs.ENGLISH)
                langs.forEach { l ->
                    val label = when (l) {
                        LangPrefs.ARABIC -> stringResource(R.string.lang_name_ar)
                        LangPrefs.ENGLISH -> stringResource(R.string.lang_name_en)
                        else -> stringResource(R.string.lang_system)
                    }
                    OptionPill(label, current == l, Modifier.weight(1f)) {
                        if (current != l) {
                            LangPrefs.set(appContext, l)
                            (appContext as? Activity)?.recreate()
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.lang_restart_hint), style = MaterialTheme.typography.labelSmall, color = Palette.Faint)
        }

        Spacer(Modifier.height(14.dp))

        // ── Developer ──
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader(stringResource(R.string.dev_title), painterResource(R.drawable.ic_user))
            Text(stringResource(R.string.dev_credit), style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.dev_phone), style = MaterialTheme.typography.bodySmall, color = Palette.Sub)
            Text(stringResource(R.string.dev_email), style = MaterialTheme.typography.bodySmall, color = Palette.Sub)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(
                    text = stringResource(R.string.dev_call),
                    modifier = Modifier.weight(1f),
                    icon = painterResource(R.drawable.ic_phone),
                    tint = Palette.Mint,
                ) {
                    runCatching { appContext.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+967784908515"))) }
                }
                GhostButton(
                    text = stringResource(R.string.dev_email_action),
                    modifier = Modifier.weight(1f),
                    icon = painterResource(R.drawable.ic_mail),
                    tint = Palette.Cyan,
                ) {
                    runCatching {
                        appContext.startActivity(
                            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:z30432981@gmail.com"))
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── About ──
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader(stringResource(R.string.about_title), painterResource(R.drawable.ic_sparkles))
            Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyMedium, color = Palette.Sub)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelMedium, color = Palette.Faint)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.privacy_note), style = MaterialTheme.typography.labelMedium, color = Palette.Mint)
            Spacer(Modifier.height(10.dp))
            CreditLine(stringResource(R.string.credits_docs))
            CreditLine(stringResource(R.string.credits_icons))
            CreditLine(stringResource(R.string.credits_font))
            var crashText by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) { vm.crashLog { crashText = it } }
            crashText?.let { txt ->
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.crash_title), style = MaterialTheme.typography.labelMedium, color = Palette.Red)
                Spacer(Modifier.height(4.dp))
                Text(
                    txt.take(600),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Palette.Sub,
                    maxLines = 6,
                )
                Spacer(Modifier.height(6.dp))
                GhostButton(
                    text = stringResource(R.string.crash_copy),
                    icon = painterResource(R.drawable.ic_copy),
                    tint = Palette.Red,
                ) {
                    clipboard.setText(AnnotatedString(txt))
                    Toast.makeText(appContext, appContext.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                }
            }

            Spacer(Modifier.height(12.dp))
            GhostButton(
                text = stringResource(R.string.reset_data),
                modifier = Modifier.fillMaxWidth(),
                icon = painterResource(R.drawable.ic_trash),
                tint = Palette.Red,
                onClick = { confirmReset = true },
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Palette.Surface,
            titleContentColor = Palette.Ink,
            textContentColor = Palette.Sub,
            title = { Text(stringResource(R.string.reset_data)) },
            text = { Text(stringResource(R.string.reset_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    drafts = Provider.entries.associateWith { "" }
                    vm.resetAll()
                }) { Text(stringResource(R.string.ok), color = Palette.Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel), color = Palette.Sub) }
            },
        )
    }
}

@Composable
private fun CreditLine(text: String) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(Palette.Cyan))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Palette.Faint)
    }
}

@Composable
private fun LocalModelsCard(
    baseUrl: String,
    models: List<String>?,
    onSaveHost: (String) -> Unit,
    onTest: (String, (Int?) -> Unit) -> Unit,
    onManage: () -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current
    var host by remember(baseUrl) { mutableStateOf(baseUrl) }
    var testing by remember { mutableStateOf(false) }
    // -1 = idle, null = failed, n = model count
    var result by remember { mutableStateOf<Int?>(-1) }

    GlassCard(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.local_section), painterResource(R.drawable.ic_bot))
        Text(stringResource(R.string.local_hint), style = MaterialTheme.typography.labelSmall, color = Palette.Sub)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.local_host_hint), color = Palette.Faint, style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(color = Palette.Ink, fontFamily = FontFamily.Monospace),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Palette.Cyan,
                unfocusedBorderColor = Palette.hair1,
                focusedContainerColor = Palette.fill4,
                unfocusedContainerColor = Palette.fill3,
                cursorColor = Palette.Cyan,
            ),
        )
        Spacer(Modifier.height(8.dp))
        BrandButton(
            text = if (testing) stringResource(R.string.local_testing) else stringResource(R.string.local_test),
            icon = painterResource(R.drawable.ic_zap),
            modifier = Modifier.fillMaxWidth().height(44.dp),
            loading = testing,
        ) {
            result = -1
            onSaveHost(host)
            onTest(host) { count ->
                testing = false
                result = count
            }
        }
        Spacer(Modifier.height(8.dp))
        GhostButton(
            text = stringResource(R.string.local_manage),
            modifier = Modifier.fillMaxWidth(),
            icon = painterResource(R.drawable.ic_download),
            tint = Palette.Mint,
            onClick = onManage,
        )
        when {
            result != -1 && result != null -> {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.local_ok, result!!),
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.Mint,
                )
                if (!models.isNullOrEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        models.take(6).joinToString("  •  "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Faint,
                        maxLines = 2,
                    )
                }
            }
            result == null -> {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.local_fail), style = MaterialTheme.typography.labelSmall, color = Palette.Red)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.local_recommended), style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
        Spacer(Modifier.height(6.dp))
        listOf("llama3.1:8b", "qwen3:8b", "gemma3:4b", "deepseek-r1:8b", "mistral:7b", "gpt-oss:20b").forEach { m ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "ollama pull $m",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Palette.codeText,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painterResource(R.drawable.ic_copy),
                    contentDescription = stringResource(R.string.copy_command),
                    tint = Palette.Cyan,
                    modifier = Modifier
                        .size(15.dp)
                        .clickable { clipboard.setText(AnnotatedString("ollama pull $m")) },
                )
            }
        }
    }
}

@Composable
private fun DeviceModelsCard(
    models: List<DeviceModel>,
    downloads: Map<String, DlUi>,
    onPick: () -> Unit,
    onImport: (Uri, String?, (Boolean) -> Unit) -> Unit,
    onDelete: (String) -> Unit,
    onDownload: (DeviceDownloadSpec) -> Unit,
    onPause: (String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = LocalClipboardManager.current

    GlassCard(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.device_section), painterResource(R.drawable.ic_bot))
        Text(stringResource(R.string.device_import_hint), style = MaterialTheme.typography.labelSmall, color = Palette.Sub)
        Spacer(Modifier.height(8.dp))
        BrandButton(
            text = stringResource(R.string.device_import),
            icon = painterResource(R.drawable.ic_plus),
            modifier = Modifier.fillMaxWidth().height(44.dp),
        ) { onPick() }

        Spacer(Modifier.height(12.dp))
        if (models.isEmpty()) {
            Text(stringResource(R.string.device_none), style = MaterialTheme.typography.bodySmall, color = Palette.Faint)
        } else {
            models.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_check), null, tint = Palette.Mint, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.titleSmall, color = Palette.Ink, maxLines = 1)
                        Text(
                            String.format(java.util.Locale.US, "%.2f GB", m.sizeBytes / 1_000_000_000.0),
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.Cyan,
                        )
                    }
                    Icon(
                        painterResource(R.drawable.ic_trash),
                        contentDescription = stringResource(R.string.delete),
                        tint = Palette.Red,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onDelete(m.name) },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.device_catalog_title), style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
        Text(stringResource(R.string.device_dl_bg), style = MaterialTheme.typography.labelSmall, color = Palette.Faint)
        Spacer(Modifier.height(6.dp))

        val catNames = listOf(
            stringResource(R.string.device_cat_smollm),
            stringResource(R.string.device_cat_olmo),
            stringResource(R.string.device_cat_qwen),
        )
        DeviceModelDownloader.CATALOG.forEachIndexed { idx, spec ->
            val dl = downloads[spec.id]
            val installed = models.any { it.name == spec.fileName.substringBeforeLast('.') }
            val actionRes = when {
                dl?.paused == true -> R.string.device_resume
                dl?.failed == true -> R.string.device_retry
                else -> R.string.device_download
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(catNames[idx], style = MaterialTheme.typography.labelLarge, color = Palette.Ink)
                    Text(
                        String.format(java.util.Locale.US, "%.2f GB", spec.sizeBytes / 1_000_000_000.0),
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Faint,
                    )
                    if (dl != null && dl.percent in 1..99 && !installed) {
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .fillMaxWidth(0.6f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Palette.fill6)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(dl.percent / 100f)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (dl.failed) Palette.Red else Palette.Cyan)
                            )
                        }
                    }
                }
                when {
                    installed -> Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = Palette.Mint,
                        modifier = Modifier.size(20.dp),
                    )
                    dl?.running == true -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${dl.percent}%", style = MaterialTheme.typography.labelMedium, color = Palette.Cyan)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.device_pause),
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.Red,
                            modifier = Modifier.clickable { onPause(spec.id) },
                        )
                    }
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        if (dl != null && dl.percent in 1..99) {
                            Text("${dl.percent}%", style = MaterialTheme.typography.labelSmall, color = Palette.Faint)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            stringResource(actionRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.Cyan,
                            modifier = Modifier.clickable { onDownload(spec) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.device_rec), style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
        Text(stringResource(R.string.device_gated_hint), style = MaterialTheme.typography.labelSmall, color = Palette.Faint)
        Spacer(Modifier.height(4.dp))
        listOf(
            "Gemma 3 1B — 0.6 GB" to "https://huggingface.co/litert-community/Gemma3-1B-IT",
            "Gemma 3 4B — 2.7 GB" to "https://huggingface.co/litert-community/Gemma3-4B-IT",
        ).forEach { (label, url) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Ink, modifier = Modifier.weight(1f))
                Icon(
                    painterResource(R.drawable.ic_external),
                    contentDescription = url,
                    tint = Palette.Cyan,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable {
                            clipboard.setText(AnnotatedString(url))
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        },
                )
            }
        }
    }
}
