package com.promptforge.ui.screens

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.promptforge.AppContainer
import com.promptforge.DownloadService
import com.promptforge.R
import com.promptforge.data.DeviceModel
import com.promptforge.data.LlmErrors
import com.promptforge.ui.components.GhostButton
import com.promptforge.ui.components.GlassCard
import com.promptforge.ui.components.LoadingDots
import com.promptforge.ui.components.SectionHeader
import com.promptforge.ui.nav.LocalAppContainer
import com.promptforge.ui.nav.Routes
import com.promptforge.ui.theme.Palette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** One installed app in the scanner list. */
data class ScannedApp(
    val pkg: String,
    val label: String,
    val version: String,
    val isSystem: Boolean,
    val dangerous: Int,
    val totalPerms: Int,
    val sizeMb: Long,
    val icon: Bitmap?,
)

/** Privacy-sensitive permissions the report pays special attention to. */
private val SENSITIVE_PERMS = mapOf(
    "android.permission.CAMERA" to ("الكاميرا" to "Camera"),
    "android.permission.RECORD_AUDIO" to ("تسجيل الصوت" to "Microphone"),
    "android.permission.ACCESS_FINE_LOCATION" to ("الموقع الدقيق" to "Precise location"),
    "android.permission.ACCESS_COARSE_LOCATION" to ("الموقع التقريبي" to "Approximate location"),
    "android.permission.ACCESS_BACKGROUND_LOCATION" to ("الموقع في الخلفية" to "Background location"),
    "android.permission.READ_CONTACTS" to ("قراءة جهات الاتصال" to "Read contacts"),
    "android.permission.WRITE_CONTACTS" to ("تعديل جهات الاتصال" to "Write contacts"),
    "android.permission.READ_CALL_LOG" to ("سجل المكالمات" to "Call log"),
    "android.permission.READ_PHONE_STATE" to ("حالة الهاتف والمعرّفات" to "Phone state & IDs"),
    "android.permission.CALL_PHONE" to ("إجراء المكالمات" to "Direct calls"),
    "android.permission.READ_SMS" to ("قراءة الرسائل" to "Read SMS"),
    "android.permission.SEND_SMS" to ("إرسال الرسائل" to "Send SMS"),
    "android.permission.RECEIVE_SMS" to ("استقبال الرسائل" to "Receive SMS"),
    "android.permission.READ_MEDIA_IMAGES" to ("قراءة الصور" to "Read images"),
    "android.permission.READ_MEDIA_VIDEO" to ("قراءة الفيديو" to "Read videos"),
    "android.permission.READ_EXTERNAL_STORAGE" to ("قراءة التخزين" to "Read storage"),
    "android.permission.WRITE_EXTERNAL_STORAGE" to ("تعديل التخزين" to "Write storage"),
    "android.permission.BODY_SENSORS" to ("مستشعرات الجسم" to "Body sensors"),
    "android.permission.SYSTEM_ALERT_WINDOW" to ("الرسم فوق التطبيقات" to "Draw over apps"),
    "android.permission.RECEIVE_BOOT_COMPLETED" to ("الإقلاع التلقائي" to "Auto-start on boot"),
    "android.permission.QUERY_ALL_PACKAGES" to ("رؤية كل التطبيقات" to "See all apps"),
)

data class ScannerUiState(
    val apps: List<ScannedApp> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
    val selected: ScannedApp? = null,
    val report: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val errorDetail: String? = null,
    val engineName: String = "",
)

/**
 * On-device app auditor: picks any installed app, extracts a raw-facts
 * dossier (permissions, versions, sources — zero speculation), then runs a
 * deep AI analysis with the phone's own model first (fully private),
 * cloud fallback.
 */
class AppScannerVm(private val c: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(ScannerUiState())
    val ui: StateFlow<ScannerUiState> = _ui.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val isArabic: Boolean
        get() = c.appContext.resources.configuration.locales[0].language == "ar"

    init {
        refresh()
    }

    fun refresh() {
        _ui.value = _ui.value.copy(loading = true)
        scope.launch {
            val apps = runCatching { loadApps(c.appContext) }.getOrElse { emptyList() }
            _ui.value = _ui.value.copy(apps = apps, loading = false)
        }
    }

    fun setQuery(q: String) {
        _ui.value = _ui.value.copy(query = q)
    }

    /** Loads launchable installed apps with a privacy-focused facts summary. */
    private fun loadApps(context: Context): List<ScannedApp> {
        val pm = context.packageManager
        return pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            .mapNotNull { pi ->
                val app = pi.applicationInfo ?: return@mapNotNull null
                if (pm.getLaunchIntentForPackage(pi.packageName) == null) return@mapNotNull null
                val perms = pi.requestedPermissions?.toList().orEmpty()
                val label = runCatching { pm.getApplicationLabel(app).toString() }
                    .getOrDefault(pi.packageName)
                val icon = runCatching {
                    val d = pm.getApplicationIcon(pi.packageName)
                    val s = 96
                    Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888).also { b ->
                        val canvas = Canvas(b)
                        d.setBounds(0, 0, s, s)
                        d.draw(canvas)
                    }
                }.getOrNull()
                ScannedApp(
                    pkg = pi.packageName,
                    label = label,
                    version = pi.versionName ?: "?",
                    isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    dangerous = perms.count { it in SENSITIVE_PERMS },
                    totalPerms = perms.size,
                    sizeMb = runCatching {
                        java.io.File(app.sourceDir ?: "").length() / (1024 * 1024)
                    }.getOrDefault(0),
                    icon = icon,
                )
            }
            .sortedWith(compareByDescending<ScannedApp> { it.dangerous }.thenBy { it.label.lowercase() })
    }

    fun visibleApps(): List<ScannedApp> {
        val s = _ui.value
        val q = s.query.trim().lowercase()
        return if (q.isEmpty()) s.apps
        else s.apps.filter { it.label.lowercase().contains(q) || it.pkg.contains(q) }
    }

    fun selectApp(app: ScannedApp) {
        if (_ui.value.busy) return
        _ui.value = _ui.value.copy(selected = app, report = "", error = null, errorDetail = null)
        analyze()
    }

    fun clearSelection() {
        if (!_ui.value.busy) {
            _ui.value = _ui.value.copy(selected = null, report = "", error = null, errorDetail = null)
        }
    }

    /** Builds a raw-facts dossier for the selected app (bilingual). */
    private fun buildDossier(app: ScannedApp): String = buildString {
        val pm = c.appContext.packageManager
        val ar = isArabic
        val dateFmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        runCatching {
            val pi = pm.getPackageInfo(app.pkg, PackageManager.GET_PERMISSIONS)
            val installer = runCatching {
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    pm.getInstallSourceInfo(app.pkg).installingPackageName ?: "unknown"
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstallerPackageName(app.pkg) ?: "unknown"
                }
            }.getOrDefault("unknown")
            fun sdkName(v: Int) = when {
                v >= 36 -> "Android 16"; v >= 35 -> "Android 15"; v >= 34 -> "Android 14"
                v >= 33 -> "Android 13"; v >= 32 -> "Android 12L"; v >= 31 -> "Android 12"
                v >= 30 -> "Android 11"; v >= 29 -> "Android 10"; v >= 28 -> "Android 9"
                else -> "API $v"
            }
            if (ar) {
                appendLine("## بيانات التطبيق (حقائق مستخرجة)")
                appendLine("- الاسم: ${app.label}")
                appendLine("- الحزمة: ${app.pkg}")
                appendLine("- الإصدار: ${app.version}")
                appendLine("- تطبيق نظام: ${if (app.isSystem) "نعم" else "لا"}")
                appendLine("- مصدر التثبيت: $installer")
                appendLine("- تاريخ التثبيت: ${dateFmt.format(Date(pi.firstInstallTime))}")
                appendLine("- آخر تحديث: ${dateFmt.format(Date(pi.lastUpdateTime))}")
                appendLine("- حجم APK: ${app.sizeMb} MB")
                appendLine("- الحد الأدنى: ${sdkName(pi.applicationInfo?.minSdkVersion ?: 0)} | يستهدف: ${sdkName(pi.applicationInfo?.targetSdkVersion ?: 0)}")
                appendLine("- الأذونات (${app.totalPerms}):")
                pi.requestedPermissions?.toList()?.forEach { p ->
                    val sensitive = SENSITIVE_PERMS[p]
                    val name = sensitive?.let { if (ar) it.first else it.second }
                        ?: p.substringAfterLast('.')
                    appendLine("  - $name${if (sensitive != null) " ⚠️" else ""}")
                }
            } else {
                appendLine("## App facts (extracted data)")
                appendLine("- Name: ${app.label}")
                appendLine("- Package: ${app.pkg}")
                appendLine("- Version: ${app.version}")
                appendLine("- System app: ${if (app.isSystem) "yes" else "no"}")
                appendLine("- Install source: $installer")
                appendLine("- Installed: ${dateFmt.format(Date(pi.firstInstallTime))}")
                appendLine("- Last update: ${dateFmt.format(Date(pi.lastUpdateTime))}")
                appendLine("- APK size: ${app.sizeMb} MB")
                appendLine("- Min: ${sdkName(pi.applicationInfo?.minSdkVersion ?: 0)} | Target: ${sdkName(pi.applicationInfo?.targetSdkVersion ?: 0)}")
                appendLine("- Permissions (${app.totalPerms}):")
                pi.requestedPermissions?.toList()?.forEach { p ->
                    val sensitive = SENSITIVE_PERMS[p]
                    val name = sensitive?.let { if (ar) it.first else it.second }
                        ?: p.substringAfterLast('.')
                    appendLine("  - $name${if (sensitive != null) " ⚠️" else ""}")
                }
            }
        }.onFailure { appendLine("facts_unavailable: ${it.message}") }
    }

    fun analysisSystemPrompt(ar: Boolean): String =
        if (ar) {
            "أنت مدقق أمان وخصوصية تطبيقات أندرويد محترف. ستعطيك بيانات دقيقة مستخرجة من تطبيق مثبت. اكتب تقريراً شاملاً بالعربية بهذه الأقسام بالضبط:\n" +
                "## ملخص تنفيذي\nجملتان عن وظيفة التطبيق ومستوى الثقة العام.\n" +
                "## تحليل الأذونات\nلكل إذن حساس: لماذا يطلبه التطبيق عادة؟ وهل هناك نمط مقلق؟ لا تختلق أذونات غير موجودة في القائمة.\n" +
                "## مؤشرات الخصوصية والمخاطر\nإشارات إيجابية (استهداف أندرويد حديث، مصدر موثوق، أذونات قليلة) وسلبيات إن وُجدت، مع أساس كل حكم.\n" +
                "## درجة الثقة\nدرجة من 0 إلى 100 مع سطر تبرير. النطاق: 85-100 ممتاز، 65-84 جيد مع ملاحظات، 40-64 حذر، أقل من 40 خطر مرتفع.\n" +
                "## توصيات\n3-5 خطوات عملية للمستخدم (إعدادات، بدائل، تقييد أذونات).\n" +
                "قواعد صارمة: لا تختلق معلومات غير موجودة في البيانات؛ إن عرفت التطبيق فاذكر معرفتك كسياق عام فقط. كن دقيقاً ومحايداً."
        } else {
            "You are a professional Android security & privacy auditor. You will receive exact facts extracted from an installed app. Write a comprehensive report in English with exactly these sections:\n" +
                "## Executive summary\nTwo sentences about what the app does and overall trust.\n" +
                "## Permission analysis\nFor each sensitive permission: why apps normally request it, and whether the pattern is concerning. Never invent permissions absent from the list.\n" +
                "## Privacy indicators & risks\nPositive signals (modern target SDK, trusted source, few permissions) and negatives if any, each with its basis.\n" +
                "## Trust score\nA score from 0 to 100 with one-line justification. Bands: 85-100 excellent, 65-84 good with notes, 40-64 cautious, below 40 high risk.\n" +
                "## Recommendations\n3-5 practical user actions (settings, alternatives, permission limits).\n" +
                "Strict rules: never invent facts not present in the data; if you recognise the app, present that knowledge as general context only. Be precise and neutral."
        }

    /**
     * Deep analysis — on-device model first (fully private), cloud fallback.
     * Streams live; runs under the foreground service so it survives
     * backgrounding.
     */
    fun analyze() {
        if (_ui.value.busy) return
        if (_ui.value.selected == null) return
        val ar = isArabic
        val system = analysisSystemPrompt(ar)
        val dossier = buildDossier(_ui.value.selected!!)
        val user = buildString { appendLine(dossier); appendLine() }

        _ui.value = _ui.value.copy(busy = true, report = "", error = null, errorDetail = null)
        DownloadService.startInference(
            c.appContext, c.appContext.getString(R.string.service_inference),
        )
        scope.launch {
            try {
                val dm: DeviceModel? = c.deviceRegistry.all().firstOrNull()
                val engineLabel: (String) -> String =
                    { if (ar) "على الجهاز: $it" else "On-device: $it" }
                if (dm != null) {
                    val prompt = buildString {
                        appendLine(system)
                        appendLine()
                        append(user)
                    }
                    val final = c.deviceEngine.chat(dm, prompt, 0.4f) { partial ->
                        _ui.value = _ui.value.copy(report = partial, engineName = engineLabel(dm.displayName()))
                    }
                    _ui.value = _ui.value.copy(
                        report = final, busy = false,
                        engineName = engineLabel(dm.displayName()),
                    )
                } else {
                    // No device model — fall back to the active cloud provider.
                    val s = c.settings.settings.first()
                    val reply = c.chatSmart(s, system, user, 0.4, 2048)
                    _ui.value = _ui.value.copy(
                        report = reply.trim(), busy = false,
                        engineName = if (ar) "سحابي" else "Cloud",
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    _ui.value = _ui.value.copy(busy = false)
                } else {
                    val kind = when (e.message) {
                        "no_device_model" -> "no_device_model"
                        "empty_response" -> LlmErrors.EMPTY
                        "prompt_too_long" -> "prompt_too_long"
                        else -> LlmErrors.OTHER
                    }
                    _ui.value = _ui.value.copy(busy = false, error = kind, errorDetail = e.message?.take(160))
                }
            } finally {
                DownloadService.stopInference(c.appContext)
            }
        }
    }
}

@Composable
fun AppScannerScreen(onNavigate: (String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: AppScannerVm = viewModel(key = "appscanner") { AppScannerVm(container) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(18.dp))
        Column(Modifier.padding(horizontal = 20.dp)) {
            SectionHeader(stringResource(R.string.scanner_title), painterResource(R.drawable.ic_target))
            Text(
                stringResource(R.string.scanner_desc),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Sub,
            )
        }
        Spacer(Modifier.height(12.dp))

        val selected = state.selected
        if (selected != null) {
            // ── Analysis view ──
            Column(Modifier.padding(horizontal = 20.dp)) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(selected.icon, 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(selected.label, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                            Text(
                                selected.pkg,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = Palette.Faint,
                                maxLines = 1,
                            )
                        }
                        Text(
                            stringResource(R.string.scanner_back),
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.Cyan,
                            modifier = Modifier.clickable { vm.clearSelection() },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    if (state.busy && state.report.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LoadingDots()
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.scanner_analyzing),
                                style = MaterialTheme.typography.labelMedium,
                                color = Palette.Sub,
                            )
                        }
                    }
                    if (state.report.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.busy) LoadingDots()
                            Spacer(Modifier.width(8.dp))
                            Text(
                                state.engineName.ifEmpty { stringResource(R.string.scanner_engine) },
                                style = MaterialTheme.typography.labelSmall,
                                color = Palette.Mint,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.report + if (state.busy) " ▌" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.Ink,
                        )
                        if (!state.busy) {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GhostButton(
                                    text = stringResource(R.string.copy),
                                    icon = painterResource(R.drawable.ic_copy),
                                    tint = Palette.Cyan,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(state.report))
                                    android.widget.Toast.makeText(
                                        context, context.getString(R.string.copied),
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                GhostButton(
                                    text = stringResource(R.string.scanner_rerun),
                                    icon = painterResource(R.drawable.ic_refresh),
                                    tint = Palette.Mint,
                                    modifier = Modifier.weight(1f),
                                ) { vm.analyze() }
                            }
                        }
                    }
                    state.error?.let { err ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (err) {
                                "no_device_model" -> stringResource(R.string.device_pick_first)
                                "prompt_too_long" -> stringResource(R.string.err_prompt_long)
                                LlmErrors.EMPTY -> stringResource(R.string.err_empty)
                                LlmErrors.OTHER -> stringResource(R.string.err_other)
                                else -> stringResource(R.string.err_other)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.Red,
                        )
                    }
                }
            }
        } else {
            // ── Installed-apps list ──
            Column(Modifier.padding(horizontal = 20.dp)) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.scanner_device_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.Sub,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.scanner_settings),
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.Cyan,
                            modifier = Modifier.clickable { onNavigate(Routes.SETTINGS) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 20.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            stringResource(R.string.scanner_search),
                            color = Palette.Faint,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            if (state.loading) {
                Row(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { LoadingDots() }
            } else {
                val apps = vm.visibleApps().take(400)
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    items(apps, key = { it.pkg }) { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { vm.selectApp(app) }
                                .padding(horizontal = 6.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.icon, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    app.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Palette.Ink,
                                    maxLines = 1,
                                )
                                Text(
                                    app.pkg + " · " + app.version + " · " + app.sizeMb + " MB",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = Palette.Faint,
                                    maxLines = 1,
                                )
                            }
                            if (app.dangerous > 0) {
                                Text(
                                    "⚠️ ${app.dangerous}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Palette.Amber,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun AppIcon(icon: Bitmap?, size: androidx.compose.ui.unit.Dp) {
    if (icon != null) {
        Image(
            bitmap = icon.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp)),
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(Palette.fill5),
            contentAlignment = Alignment.Center,
        ) {
            Text("?", style = MaterialTheme.typography.labelMedium, color = Palette.Faint)
        }
    }
}
