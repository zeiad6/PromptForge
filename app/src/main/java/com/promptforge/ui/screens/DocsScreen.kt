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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

data class DocsUi(
    val title: String = "",
    val text: String = "",
    val info: String = "",
    val tab: String = "read", // read | edit | convert
    val summary: String = "",
    val busySummary: Boolean = false,
    val message: String? = null,
)

/**
 * Documents studio: open PDF / DOCX / TXT / MD / HTML from the phone, read
 * comfortably, edit & save, summarize with the on-device/cloud AI, and
 * convert Markdown to a live-rendered HTML preview — all offline-capable.
 */
class DocsVm(private val c: AppContainer) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _ui = MutableStateFlow(DocsUi())
    val ui: StateFlow<DocsUi> = _ui.asStateFlow()

    fun setTab(v: String) { _ui.value = _ui.value.copy(tab = v) }
    fun setText(v: String) { _ui.value = _ui.value.copy(text = v) }

    /** Extracts text from the picked document (pdf/docx/txt/md/html). */
    fun open(uri: android.net.Uri) {
        scope.launch {
            val ctx = c.appContext
            val res = withContext(Dispatchers.IO) {
                val name = runCatching {
                    ctx.contentResolver.query(uri, null, null, null, null)?.use { cr ->
                        val idx = cr.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0 && cr.moveToFirst()) cr.getString(idx) else null
                    }
                }.getOrNull() ?: uri.lastPathSegment ?: "document"
                val ext = name.substringAfterLast('.', "").lowercase()
                val text = runCatching {
                    when (ext) {
                        "pdf" -> {
                            val input = ctx.contentResolver.openInputStream(uri)
                                ?: throw IllegalStateException("open")
                            input.use { ins ->
                                com.tom_roush.pdfbox.pdmodel.PDDocument.load(ins).use { doc ->
                                    com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc)
                                }
                            }
                        }
                        "docx" -> {
                            val input = ctx.contentResolver.openInputStream(uri)!!
                            input.use { ins ->
                                java.util.zip.ZipInputStream(ins).use { zip ->
                                    var e = zip.nextEntry
                                    var xml = ""
                                    while (e != null) {
                                        if (e.name == "word/document.xml") {
                                            xml = zip.readBytes().toString(Charsets.UTF_8)
                                            break
                                        }
                                        e = zip.nextEntry
                                    }
                                    xml.replace(Regex("</w:p>"), "\n")
                                        .replace(Regex("<[^>]+>"), "")
                                        .replace("&amp;", "&").replace("&lt;", "<")
                                        .replace("&gt;", ">").replace("&quot;", "\"")
                                        .replace("&apos;", "'")
                                }
                            }
                        }
                        "html", "htm" -> {
                            val raw = ctx.contentResolver.openInputStream(uri)!!
                                .bufferedReader().use { it.readText() }
                            raw.replace(Regex("(?s)<(script|style)[^>]*>.*?</\\1>"), "")
                                .replace(Regex("<br\\s*/?>"), "\n")
                                .replace(Regex("</(p|div|h[1-6]|li)>"), "\n")
                                .replace(Regex("<[^>]+>"), "")
                                .replace("&nbsp;", " ").replace("&amp;", "&")
                                .replace("&lt;", "<").replace("&gt;", ">")
                        }
                        else -> ctx.contentResolver.openInputStream(uri)!!
                            .bufferedReader().use { it.readText() }
                    }
                }.getOrElse { return@withContext name to null }
                name to text
            }
            val (name, text) = res
            _ui.value = if (text == null) {
                _ui.value.copy(message = c.appContext.getString(R.string.docs_fail))
            } else {
                val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
                _ui.value.copy(
                    title = name, text = text, summary = "", message = null,
                    info = c.appContext.getString(R.string.docs_info, words / 100 + 1, words),
                    tab = "read",
                )
            }
        }
    }

    /** AI summary — on-device model first, cloud fallback. */
    fun summarize() {
        if (_ui.value.busySummary) return
        val text = _ui.value.text
        if (text.isBlank()) return
        val ar = com.promptforge.data.looksArabicText(text.take(2000))
        val system = if (ar)
            "أنت خبير تلخيص. لخّص المستند التالي بدقة: 5–8 نقاط جوهرية، ثم «الخلاصة:» بسطر واحد، ثم أهم 3 أرقام أو معلومات إن وُجدت."
        else
            "You are an expert summarizer. Summarize the document precisely: 5–8 key points, then \"Bottom line:\" one sentence, then top-3 numbers/facts if present."
        _ui.value = _ui.value.copy(busySummary = true, summary = "")
        scope.launch {
            try {
                val out = c.smartChat(system, text.take(12_000))
                _ui.value = _ui.value.copy(busySummary = false, summary = out.trim())
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(
                    busySummary = false,
                    message = e.message?.take(140) ?: "failed",
                )
            }
        }
    }

    fun exportTextTo(uri: android.net.Uri) {
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    c.appContext.contentResolver.openOutputStream(uri)?.use {
                        it.write(_ui.value.text.toByteArray())
                    }
                }
            }
        }
    }

    fun exportHtmlTo(uri: android.net.Uri) {
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    c.appContext.contentResolver.openOutputStream(uri)?.use {
                        it.write(mdToHtml(_ui.value.text).toByteArray())
                    }
                }
            }
        }
    }
}

/** Tiny Markdown → HTML renderer (headings, bold/italic/code, lists). */
internal fun mdToHtml(md: String): String {
    fun inline(s: String): String = s
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
        .replace(Regex("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)"), "<i>$1</i>")
        .replace(Regex("`([^`]+)`"), "<code>$1</code>")
    val body = StringBuilder()
    var inCode = false
    md.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .lines().forEach { raw ->
            val l = raw.trimEnd()
            when {
                l.startsWith("```") -> {
                    body.append(if (inCode) "</code></pre>" else "<pre><code>")
                    inCode = !inCode
                }
                inCode -> body.append(l).append('\n')
                l.startsWith("### ") -> body.append("<h3>").append(inline(l.substring(4))).append("</h3>")
                l.startsWith("## ") -> body.append("<h2>").append(inline(l.substring(3))).append("</h2>")
                l.startsWith("# ") -> body.append("<h1>").append(inline(l.substring(2))).append("</h1>")
                l.startsWith("- ") -> body.append("• ").append(inline(l.substring(2))).append("<br/>")
                l.isEmpty() -> body.append("<br/>")
                else -> body.append(inline(l)).append("<br/>")
            }
        }
    if (inCode) body.append("</code></pre>")
    return """<!DOCTYPE html><html dir="auto"><head><meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<style>
body{background:#0A0F1E;color:#E7ECF5;font-family:sans-serif;line-height:1.65;padding:18px}
h1,h2,h3{color:#8B5CF6} code{background:#121A30;color:#22D3EE;padding:2px 5px;border-radius:5px}
pre{background:#121A30;padding:10px;border-radius:10px;overflow-x:auto}
pre code{background:none;color:#9AE6F5}
</style></head><body>""" + body + "</body></html>"
}

@Composable
fun DocsScreen(onNavigate: (String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: DocsVm = viewModel(key = "docs") { DocsVm(container) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.open(it) }
    }
    val saveText = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { vm.exportTextTo(it) } }
    val saveHtml = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html")
    ) { uri -> uri?.let { vm.exportHtmlTo(it) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
        Spacer(Modifier.height(18.dp))
        Column(Modifier.padding(horizontal = 20.dp)) {
            SectionHeader(stringResource(R.string.docs_title), painterResource(R.drawable.ic_library))
            Text(
                stringResource(R.string.docs_desc),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Sub,
            )
        }
        Spacer(Modifier.height(12.dp))

        if (state.text.isEmpty()) {
            // ── Open gate ──
            Column(Modifier.padding(horizontal = 20.dp)) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.docs_gate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Ink,
                    )
                    Spacer(Modifier.height(12.dp))
                    com.promptforge.ui.components.BrandButton(
                        text = stringResource(R.string.docs_pick),
                        icon = painterResource(R.drawable.ic_plus),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                    ) { pick.launch(arrayOf("*/*")) }
                    state.message?.let { m ->
                        Spacer(Modifier.height(6.dp))
                        Text(m, style = MaterialTheme.typography.labelSmall, color = Palette.Red)
                    }
                }
            }
        } else {
            // ── Tabs ──
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionPill(stringResource(R.string.docs_read), state.tab == "read", Modifier.weight(1f)) { vm.setTab("read") }
                    OptionPill(stringResource(R.string.docs_edit), state.tab == "edit", Modifier.weight(1f)) { vm.setTab("edit") }
                    OptionPill(stringResource(R.string.docs_sum), state.tab == "sum", Modifier.weight(1f)) { vm.setTab("sum") }
                    OptionPill(stringResource(R.string.docs_conv), state.tab == "conv", Modifier.weight(1f)) { vm.setTab("conv") }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    state.title + " · " + state.info,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Palette.Faint,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(10.dp))

            when (state.tab) {
                "edit" -> Column(Modifier.padding(horizontal = 20.dp)) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        var t by remember(state.text) { mutableStateOf(state.text) }
                        OutlinedTextField(
                            value = t,
                            onValueChange = { t = it; vm.setText(it) },
                            modifier = Modifier.fillMaxWidth().height(340.dp),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GhostButton(
                                text = stringResource(R.string.docs_save),
                                icon = painterResource(R.drawable.ic_download),
                                tint = Palette.Mint,
                                modifier = Modifier.weight(1f),
                            ) { saveText.launch(state.title.ifBlank { "document.txt" }) }
                        }
                    }
                }
                "sum" -> Column(Modifier.padding(horizontal = 20.dp)) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        if (state.summary.isEmpty() && !state.busySummary) {
                            Text(
                                stringResource(R.string.docs_sum_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = Palette.Sub,
                            )
                            Spacer(Modifier.height(10.dp))
                            com.promptforge.ui.components.BrandButton(
                                text = stringResource(R.string.docs_sum_now),
                                icon = painterResource(R.drawable.ic_sparkles),
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                            ) { vm.summarize() }
                        } else {
                            if (state.busySummary) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LoadingDots()
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        stringResource(R.string.docs_summarizing),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Palette.Sub,
                                    )
                                }
                            }
                            if (state.summary.isNotEmpty()) {
                                Text(
                                    state.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Palette.Ink,
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    GhostButton(
                                        text = stringResource(R.string.copy),
                                        icon = painterResource(R.drawable.ic_copy),
                                        tint = Palette.Cyan,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(state.summary))
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
                                    ) { vm.summarize() }
                                }
                            }
                        }
                    }
                }
                "conv" -> Column(Modifier.padding(horizontal = 20.dp)) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(14.dp))) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply { settings.javaScriptEnabled = false }
                                },
                                update = { wv ->
                                    wv.loadDataWithBaseURL(
                                        "https://local.promptforge/",
                                        mdToHtml(state.text), "text/html", "utf-8", null,
                                    )
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GhostButton(
                                text = stringResource(R.string.docs_export_html),
                                icon = painterResource(R.drawable.ic_download),
                                tint = Palette.Mint,
                                modifier = Modifier.weight(1f),
                            ) {
                                saveHtml.launch(state.title.substringBeforeLast('.') + ".html")
                            }
                        }
                    }
                }
                else -> Column(Modifier.padding(horizontal = 20.dp)) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text(
                            state.text.take(20_000),
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.Ink,
                        )
                    }
                }
            }
            state.message?.let { m ->
                Spacer(Modifier.height(6.dp))
                Text(
                    m,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Red,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}
