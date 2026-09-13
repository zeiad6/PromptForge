package com.promptforge.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.promptforge.AppContainer
import com.promptforge.R
import com.promptforge.data.LlmErrors
import com.promptforge.data.OllamaModel
import com.promptforge.ui.components.GhostButton
import com.promptforge.ui.components.GlassCard
import com.promptforge.ui.components.LoadingDots
import com.promptforge.ui.components.SectionHeader
import com.promptforge.ui.nav.LocalAppContainer
import com.promptforge.ui.theme.Palette
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** A catalog entry: recommended models with honest approximated sizes. */
private data class CatalogItem(
    val tag: String,
    val sizeLabel: String,
    val descAr: String,
    val badge: String,
)

private val CATALOG = listOf(
    CatalogItem("llama3.1:8b", "≈4.7GB", "الخيار العام المتوازن — محادثة وكتابة ومعظم المهام", "عام"),
    CatalogItem("qwen3:8b", "≈5.2GB", "استدلال قوي ودعم ممتاز للعربية", "استدلال"),
    CatalogItem("deepseek-r1:8b", "≈5.2GB", "تفكير عميق خطوة بخطوة للمسائل المعقدة", "استدلال"),
    CatalogItem("qwen2.5-coder:7b", "≈4.7GB", "متخصص برمجة وتصحيح أكواد", "برمجة"),
    CatalogItem("gemma3:4b", "≈3.3GB", "خفيف وسريع من جوجل — مثالي للأجهزة المتوسطة", "خفيف"),
    CatalogItem("qwen3:4b", "≈2.6GB", "أصغر موديلات Qwen3 — للأجهزة الضعيفة", "خفيف"),
    CatalogItem("mistral:7b", "≈4.1GB", "كلاسيكي سريع واقتصادي", "عام"),
    CatalogItem("phi4:14b", "≈9.1GB", "كفاءة مذهلة لحجمه من مايكروسوفت", "استدلال"),
    CatalogItem("gpt-oss:20b", "≈14GB", "موديل مفتوح من OpenAI مبني للوكلاء", "وكلاء"),
    CatalogItem("llama3.1:70b", "≈40GB", "الوحش الكامل — يحتاج حاسوباً قوياً", "متقدم"),
)

data class PullUi(
    val percent: Int? = null,
    val running: Boolean = false,
    val paused: Boolean = false,
    val done: Boolean = false,
    val failed: Boolean = false,
)

data class LocalModelsUiState(
    val host: String = "",
    val installed: List<OllamaModel> = emptyList(),
    val loading: Boolean = false,
    val connected: Boolean = false,
    val connectionFailed: Boolean = false,
    val pulls: Map<String, PullUi> = emptyMap(),
    val deleting: String? = null,
    val discovering: Boolean = false,
    val discoverTried: Boolean = false,
    val found: List<String> = emptyList(),
)

class LocalModelsViewModel(private val c: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(LocalModelsUiState())
    val ui: StateFlow<LocalModelsUiState> = _ui.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            val host = c.settings.settings.first().localBaseUrl
            _ui.update { it.copy(host = host) }
            connect()
        }
    }

    fun setHost(v: String) {
        _ui.update { it.copy(host = v) }
        viewModelScope.launch { c.settings.setLocalBaseUrl(v) }
    }

    /** Scans the LAN for a running Ollama server and connects to the first hit. */
    fun discover() {
        if (_ui.value.discovering) return
        viewModelScope.launch {
            _ui.update { it.copy(discovering = true, discoverTried = true, found = emptyList()) }
            val list = c.ollama.discover()
            _ui.update { it.copy(discovering = false, found = list) }
            if (list.isNotEmpty()) {
                setHost(list.first())
                connect()
            }
        }
    }

    fun connect() {
        val host = _ui.value.host
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, connectionFailed = false) }
            try {
                val models = c.ollama.listInstalled(host)
                _ui.update { it.copy(loading = false, connected = true, installed = models) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, connected = false, connectionFailed = true) }
            }
        }
    }

    fun download(tag: String) {
        if (jobs[tag]?.isActive == true) return
        _ui.update { it.copy(pulls = it.pulls + (tag to PullUi(percent = 0, running = true))) }
        jobs[tag] = viewModelScope.launch {
            try {
                c.ollama.pull(_ui.value.host, tag) { total, done, _ ->
                    val pct = if (total > 0) ((done * 100) / total).toInt() else 0
                    _ui.update { s ->
                        s.copy(pulls = s.pulls + (tag to (s.pulls[tag] ?: PullUi()).copy(percent = pct, running = true, paused = false)))
                    }
                }
                _ui.update { s -> s.copy(pulls = s.pulls + (tag to PullUi(percent = 100, done = true))) }
                // Activate immediately: the model is selectable in the Lab right away
                c.settings.setModel(com.promptforge.data.Provider.LOCAL, tag)
                connect()
            } catch (e: kotlinx.coroutines.CancellationException) {
                _ui.update { s ->
                    val cur = s.pulls[tag] ?: PullUi()
                    s.copy(pulls = s.pulls + (tag to cur.copy(running = false, paused = true)))
                }
            } catch (e: Exception) {
                _ui.update { s ->
                    val cur = s.pulls[tag] ?: PullUi()
                    s.copy(pulls = s.pulls + (tag to cur.copy(running = false, failed = true)))
                }
            }
        }
    }

    /** Pause = cancel the HTTP call; Ollama keeps partial blobs, resume re-pulls. */
    fun pause(tag: String) {
        c.ollama.pause(tag)
        jobs[tag]?.cancel()
    }

    fun delete(tag: String) {
        viewModelScope.launch {
            try {
                c.ollama.delete(_ui.value.host, tag)
                _ui.update { it.copy(deleting = null) }
                connect()
            } catch (e: Exception) {
                _ui.update { it.copy(deleting = null) }
            }
        }
    }

    fun askDelete(tag: String?) = _ui.update { it.copy(deleting = tag) }
}

@Composable
fun LocalModelsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: LocalModelsViewModel = viewModel(factory = container.vmFactory)
    val state by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val copiedMsg = stringResource(R.string.copied)
    val deletedMsg = stringResource(R.string.local_deleted)
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
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
                    modifier = Modifier.size(19.dp).scale(scaleX = if (isRtl) -1f else 1f, scaleY = 1f),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.local_mgmt_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Palette.Ink,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── Connection ──
            item {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                    OutlinedTextField(
                        value = state.host,
                        onValueChange = vm::setHost,
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
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.local_mgmt_hint), style = MaterialTheme.typography.labelSmall, color = Palette.Faint)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton(
                            text = stringResource(R.string.local_test),
                            modifier = Modifier.weight(1f),
                            icon = painterResource(R.drawable.ic_zap),
                            tint = Palette.Cyan,
                        ) { vm.connect() }
                        GhostButton(
                            text = stringResource(R.string.local_refresh_list),
                            modifier = Modifier.weight(1f),
                            icon = painterResource(R.drawable.ic_refresh),
                        ) { vm.connect() }
                    }
                    Spacer(Modifier.height(8.dp))
                    GhostButton(
                        text = stringResource(R.string.local_discover),
                        modifier = Modifier.fillMaxWidth(),
                        icon = painterResource(R.drawable.ic_globe),
                        tint = Palette.Mint,
                    ) { vm.discover() }
                    when {
                        state.discovering -> {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LoadingDots()
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.local_discovering), style = MaterialTheme.typography.labelSmall, color = Palette.Sub)
                            }
                        }
                        state.discoverTried && state.found.isEmpty() -> {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.local_discover_fail), style = MaterialTheme.typography.labelSmall, color = Palette.Amber)
                        }
                        state.found.size > 1 -> {
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(state.found) { hostUrl ->
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(Palette.Mint.copy(alpha = 0.13f))
                                            .clickable { setHostAction(vm, hostUrl) }
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                    ) {
                                        Text(hostUrl, style = MaterialTheme.typography.labelSmall, color = Palette.Mint, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                    when {
                        state.loading -> {
                            Spacer(Modifier.height(8.dp))
                            LoadingDots()
                        }
                        state.connected -> {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.local_ok, state.installed.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = Palette.Mint,
                            )
                        }
                        state.connectionFailed -> {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.local_fail), style = MaterialTheme.typography.labelSmall, color = Palette.Red)
                        }
                    }
                }
            }

            // ── Installed ──
            item {
                SectionHeader(
                    stringResource(R.string.local_installed),
                    painterResource(R.drawable.ic_bot),
                    trailing = {
                        Text(
                            stringResource(R.string.local_catalog),
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.Faint,
                        )
                    },
                )
            }
            if (state.installed.isEmpty() && state.connected) {
                item {
                    Text(
                        stringResource(R.string.local_none_installed),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Sub,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
            items(state.installed, key = { "inst_" + it.name }) { m ->
                val pull = state.pulls[m.name]
                GlassCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Palette.Mint.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.ic_bot), null, tint = Palette.Mint, modifier = Modifier.size(19.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = MaterialTheme.typography.titleSmall, color = Palette.Ink, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                formatGb(m.sizeBytes),
                                style = MaterialTheme.typography.labelMedium,
                                color = Palette.Cyan,
                            )
                            if (m.family != null) {
                                Text(
                                    stringResource(
                                        R.string.local_mdl_family,
                                        m.family ?: "?", m.parameterSize ?: "?", m.quantization ?: "?",
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Palette.Faint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Row {
                            // Update = re-pull (fetches newer digest if available)
                            Icon(
                                painterResource(R.drawable.ic_refresh),
                                contentDescription = stringResource(R.string.local_update),
                                tint = Palette.Cyan,
                                modifier = Modifier
                                    .size(19.dp)
                                    .clickable { vm.download(m.name) }
                                    .padding(3.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Icon(
                                painterResource(R.drawable.ic_trash),
                                contentDescription = stringResource(R.string.delete),
                                tint = Palette.Red,
                                modifier = Modifier
                                    .size(19.dp)
                                    .clickable { vm.askDelete(m.name) }
                                    .padding(3.dp),
                            )
                        }
                    }
                    if (pull != null && (pull.running || pull.paused || pull.failed)) {
                        Spacer(Modifier.height(8.dp))
                        PullStatus(pull)
                    }
                }
            }

            // ── Catalog ──
            item {
                Spacer(Modifier.height(6.dp))
                SectionHeader(stringResource(R.string.local_catalog), painterResource(R.drawable.ic_download))
            }
            items(CATALOG, key = { "cat_" + it.tag }) { item ->
                val pull = state.pulls[item.tag]
                GlassCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                    val p = pull
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Palette.Primary.copy(alpha = 0.14f))
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                        ) {
                            Text(item.badge, style = MaterialTheme.typography.labelSmall, color = Palette.Primary)
                        }
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.tag, style = MaterialTheme.typography.titleSmall, color = Palette.Ink, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.sizeLabel, style = MaterialTheme.typography.labelMedium, color = Palette.Cyan)
                                Spacer(Modifier.width(8.dp))
                                Text(item.descAr, style = MaterialTheme.typography.labelSmall, color = Palette.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        val installed = state.installed.any { it.name == item.tag }
                        when {
                            p?.running == true -> {
                                GhostButton(stringResource(R.string.local_pause), Modifier) { vm.pause(item.tag) }
                            }
                            p?.paused == true -> {
                                GhostButton(
                                    stringResource(R.string.local_resume), Modifier,
                                    painterResource(R.drawable.ic_zap), Palette.Mint,
                                ) { vm.download(item.tag) }
                            }
                            else -> {
                                GhostButton(
                                    text = if (installed) stringResource(R.string.local_update) else stringResource(R.string.local_download),
                                    modifier = Modifier,
                                    icon = painterResource(if (installed) R.drawable.ic_refresh else R.drawable.ic_download),
                                    tint = if (installed) Palette.Cyan else Palette.Primary,
                                ) { vm.download(item.tag) }
                            }
                        }
                    }
                    if (p != null && (p.running || p.paused || p.done || p.failed)) {
                        Spacer(Modifier.height(9.dp))
                        PullStatus(p)
                    }
                }
            }
            item { Spacer(Modifier.height(120.dp)) }
        }
    }

    state.deleting?.let { tag ->
        val sizeLabel = state.installed.firstOrNull { it.name == tag }?.let { formatGb(it.sizeBytes) } ?: ""
        AlertDialog(
            onDismissRequest = { vm.askDelete(null) },
            containerColor = Palette.Surface,
            titleContentColor = Palette.Ink,
            textContentColor = Palette.Sub,
            title = { Text(stringResource(R.string.local_confirm_delete)) },
            text = { Text(stringResource(R.string.local_confirm_delete_body, tag, sizeLabel)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(tag)
                    Toast.makeText(context, deletedMsg, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.delete), color = Palette.Red) }
            },
            dismissButton = {
                TextButton(onClick = { vm.askDelete(null) }) { Text(stringResource(R.string.cancel), color = Palette.Sub) }
            },
        )
    }
}

@Composable
private fun PullStatus(pull: PullUi) {
    Column {
        if (pull.running && pull.percent != null && pull.percent > 0) {
            val animated by animateFloatAsState(
                targetValue = pull.percent / 100f,
                animationSpec = tween(300),
                label = "pull",
            )
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                color = Palette.Primary,
                trackColor = Palette.hair1,
            )
            Spacer(Modifier.height(4.dp))
            Text("$pull.percent%", style = MaterialTheme.typography.labelSmall, color = Palette.Sub)
        } else if (pull.running) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LoadingDots()
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.local_testing), style = MaterialTheme.typography.labelSmall, color = Palette.Sub)
            }
        }
        when {
            pull.paused -> Text(stringResource(R.string.local_paused), style = MaterialTheme.typography.labelSmall, color = Palette.Amber)
            pull.done -> Text(stringResource(R.string.local_done), style = MaterialTheme.typography.labelSmall, color = Palette.Mint)
            pull.failed -> Text(stringResource(R.string.local_failed_dl), style = MaterialTheme.typography.labelSmall, color = Palette.Red)
        }
    }
}

private fun formatGb(bytes: Long): String =
    String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)

private fun setHostAction(vm: LocalModelsViewModel, hostUrl: String) {
    vm.setHost(hostUrl)
    vm.connect()
}
