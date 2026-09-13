package com.promptforge.ui.screens

import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.promptforge.AppContainer
import com.promptforge.R
import com.promptforge.ui.components.GhostButton
import com.promptforge.ui.components.GlassCard
import com.promptforge.ui.components.LoadingDots
import com.promptforge.ui.components.OptionPill
import com.promptforge.ui.components.SectionHeader
import com.promptforge.ui.nav.LocalAppContainer
import com.promptforge.ui.theme.Palette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class BuilderUi(
    val name: String = "",
    val desc: String = "",
    val target: String = "web", // web | android | desktop
    val busy: Boolean = false,
    val html: String? = null,
    val code: String? = null,
    val builtFile: File? = null,
    val apps: List<File> = emptyList(),
    val message: String? = null,
)

/**
 * AI App Builder: describe an app → the on-device/cloud model generates a
 * complete single-file HTML app (instantly previewable & testable in an
 * embedded WebView) or Android Compose / desktop-ready code for export.
 */
class AppBuilderVm(private val c: AppContainer) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _ui = MutableStateFlow(BuilderUi())
    val ui: StateFlow<BuilderUi> = _ui.asStateFlow()

    init {
        refreshApps()
    }

    fun refreshApps() {
        _ui.value = _ui.value.copy(apps = listApps())
    }

    private fun listApps(): List<File> =
        c.builtAppsDir.listFiles { f -> f.name.endsWith(".html") }
            ?.sortedByDescending { it.lastModified() }.orEmpty()

    fun setName(v: String) { _ui.value = _ui.value.copy(name = v) }
    fun setDesc(v: String) { _ui.value = _ui.value.copy(desc = v) }
    fun setTarget(v: String) { _ui.value = _ui.value.copy(target = v) }
    fun clearResult() { _ui.value = _ui.value.copy(html = null, code = null, builtFile = null) }

    fun openApp(f: File) {
        _ui.value = _ui.value.copy(html = runCatching { f.readText() }.getOrNull(), builtFile = f, code = null)
    }

    fun deleteApp(f: File) {
        runCatching { f.delete() }
        refreshApps()
    }

    fun currentHtml(): String? = _ui.value.html
    fun currentCode(): String? = _ui.value.code

    fun exportTo(uri: android.net.Uri) {
        scope.launch {
            val content = _ui.value.html ?: _ui.value.code ?: return@launch
            runCatching {
                withContext(Dispatchers.IO) {
                    c.appContext.contentResolver.openOutputStream(uri)?.use { o ->
                        o.write(content.toByteArray())
                    }
                }
            }
        }
    }

    private var job: kotlinx.coroutines.Job? = null

    /** User pressed stop — cancel AI work without blocking anything. */
    fun cancelWork() {
        job?.cancel()
        job = null
        runCatching { c.deviceEngine.abort() }
        _ui.value = _ui.value.copy(busy = false)
    }

    fun build() {
        if (_ui.value.busy) return
        val desc = _ui.value.desc.trim()
        if (desc.isBlank()) return
        val target = _ui.value.target
        val ar = com.promptforge.data.looksArabicText(desc)
        val system = when (target) {
            "android" ->
                if (ar) "أنت مهندس أندرويد خبير. أنشئ تطبيق أندرويد كاملاً في ملف واحد MainActivity.kt باستخدام Jetpack Compose (Material 3) مع كل الوظائف المطلوبة وعملية فعلياً، ونص عربي/إنجليزي حسب الطلب. أعد الكود فقط داخل كتلة ```kt واحدة، ثم سطران عن متطلبات البناء."
                else "You are an expert Android engineer. Create the complete app in ONE MainActivity.kt file using Jetpack Compose (Material 3), fully functional as requested. Output only the code in one ```kt block, then two lines about build requirements."
            else ->
                if (ar) "أنت مهندس ويب خبير. أنشئ تطبيق ويب كاملاً في ملف HTML واحد مكتفٍ ذاتياً: كل CSS وJavaScript مضمّن (لا أي موارد خارجية أو CDN)، واجهة عصرية متجاوبة أنيقة، تدعم العربية RTL والإنجليزية، وكل الوظائف المطلوبة تعمل فعلياً بلا أخطاء. أعد كود HTML فقط داخل كتلة ```html واحدة."
                else "You are an expert web engineer. Create a complete web app in ONE self-contained HTML file: all CSS/JS inline (absolutely no external resources or CDNs), a polished responsive modern UI, RTL-Arabic & English support, and every requested feature genuinely working without errors. Output only the HTML in one ```html block."
        }
        val user = (_ui.value.name.trim() + "\n\n" + desc).trim()
        _ui.value = _ui.value.copy(busy = true, html = null, code = null, builtFile = null, message = null)
        job = scope.launch {
            try {
                val raw = c.smartChat(system, user)
                val fence = Regex("(?s)```[a-zA-Z]*\\s*\\n(.*?)```").find(raw)
                val code = (fence?.groupValues?.get(1) ?: raw).trim()
                withContext(Dispatchers.Main) {
                    if ((target == "web" || target == "desktop") &&
                        (code.contains("<html", true) || code.contains("<!DOCTYPE", true))
                    ) {
                        val slug = (_ui.value.name.filter { it.isLetterOrDigit() }
                            .take(24).ifEmpty { "app" }) + "_" +
                            (System.currentTimeMillis() / 1000).toString(36) + ".html"
                        val f = File(c.builtAppsDir, slug)
                        runCatching { f.writeText(code) }
                        _ui.value = _ui.value.copy(busy = false, html = code, builtFile = f)
                        refreshApps()
                    } else {
                        _ui.value = _ui.value.copy(busy = false, code = code)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _ui.value = _ui.value.copy(busy = false, message = e.message?.take(140) ?: "failed")
                }
            }
        }
    }
}

@Composable
fun AppBuilderScreen(onNavigate: (String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: AppBuilderVm = viewModel(key = "appbuilder") { AppBuilderVm(container) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    val htmlExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html")
    ) { uri -> uri?.let { vm.exportTo(it) } }
    val codeExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { vm.exportTo(it) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
        Spacer(Modifier.height(18.dp))
        Column(Modifier.padding(horizontal = 20.dp)) {
            SectionHeader(stringResource(R.string.ab_title), painterResource(R.drawable.ic_nav_builder))
            Text(
                stringResource(R.string.ab_desc),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Sub,
            )
        }
        Spacer(Modifier.height(12.dp))

        // ── Composer ──
        Column(Modifier.padding(horizontal = 20.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = vm::setName,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.ab_name), color = Palette.Faint, style = MaterialTheme.typography.bodyMedium) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.desc,
                    onValueChange = vm::setDesc,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.ab_what), color = Palette.Faint, style = MaterialTheme.typography.bodyMedium) },
                    minLines = 3,
                    maxLines = 8,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionPill(
                        stringResource(R.string.ab_web), state.target == "web", Modifier.weight(1f),
                    ) { vm.setTarget("web") }
                    OptionPill(
                        stringResource(R.string.ab_android), state.target == "android", Modifier.weight(1f),
                    ) { vm.setTarget("android") }
                    OptionPill(
                        stringResource(R.string.ab_desktop), state.target == "desktop", Modifier.weight(1f),
                    ) { vm.setTarget("desktop") }
                }
                Spacer(Modifier.height(12.dp))
                com.promptforge.ui.components.BrandButton(
                    text = if (state.busy) stringResource(R.string.ab_building) else stringResource(R.string.ab_build),
                    icon = painterResource(R.drawable.ic_sparkles),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    loading = state.busy,
                ) { vm.build() }
                if (state.busy) {
                    Spacer(Modifier.height(8.dp))
                    GhostButton(
                        text = stringResource(R.string.msg_stop),
                        icon = painterResource(R.drawable.ic_stop),
                        tint = Palette.Red,
                        modifier = Modifier.fillMaxWidth(),
                    ) { vm.cancelWork() }
                }
                state.message?.let { m ->
                    Spacer(Modifier.height(6.dp))
                    Text(m, style = MaterialTheme.typography.labelSmall, color = Palette.Red)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // ── Result: live preview (web/desktop) ──
        val html = state.html
        if (html != null) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                SectionHeader(stringResource(R.string.ab_preview), painterResource(R.drawable.ic_nav_playground))
                GlassCard(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(430.dp).clip(RoundedCornerShape(14.dp))) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                }
                            },
                            update = { wv ->
                                wv.loadDataWithBaseURL("https://local.promptforge/", html, "text/html", "utf-8", null)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton(
                            text = stringResource(R.string.ab_refresh),
                            icon = painterResource(R.drawable.ic_refresh),
                            tint = Palette.Cyan,
                            modifier = Modifier.weight(1f),
                        ) { vm.openApp(state.builtFile ?: return@GhostButton) }
                        GhostButton(
                            text = stringResource(R.string.docs_export),
                            icon = painterResource(R.drawable.ic_download),
                            tint = Palette.Mint,
                            modifier = Modifier.weight(1f),
                        ) { htmlExport.launch((state.builtFile?.name ?: "app") + ".html") }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ── Result: code view (android / fallback) ──
        val code = state.code
        if (code != null) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                SectionHeader(stringResource(R.string.ab_code), painterResource(R.drawable.ic_copy))
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        code.take(6000),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = Palette.Ink,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton(
                            text = stringResource(R.string.ab_copy),
                            icon = painterResource(R.drawable.ic_copy),
                            tint = Palette.Cyan,
                            modifier = Modifier.weight(1f),
                        ) {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(code))
                            android.widget.Toast.makeText(
                                context, context.getString(R.string.copied),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                        GhostButton(
                            text = stringResource(R.string.docs_export),
                            icon = painterResource(R.drawable.ic_download),
                            tint = Palette.Mint,
                            modifier = Modifier.weight(1f),
                        ) { codeExport.launch("MainActivity.kt") }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ── My built apps ──
        if (state.apps.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                SectionHeader(stringResource(R.string.ab_apps), painterResource(R.drawable.ic_layers))
                GlassCard(Modifier.fillMaxWidth()) {
                    state.apps.take(12).forEach { f ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).clickable { vm.openApp(f) }) {
                                Text(
                                    f.name.removeSuffix(".html"),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Palette.Ink,
                                    maxLines = 1,
                                )
                                Text(
                                    java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.US)
                                        .format(java.util.Date(f.lastModified())),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Palette.Faint,
                                )
                            }
                            Text(
                                stringResource(R.string.ab_open),
                                style = MaterialTheme.typography.labelMedium,
                                color = Palette.Cyan,
                                modifier = Modifier
                                    .clickable { vm.openApp(f) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                            androidx.compose.material3.Icon(
                                painterResource(R.drawable.ic_trash),
                                contentDescription = stringResource(R.string.delete),
                                tint = Palette.Red,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { vm.deleteApp(f) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Spacer(Modifier.height(40.dp))
    }
}
