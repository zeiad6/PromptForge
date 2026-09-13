package com.promptforge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.promptforge.AppContainer
import com.promptforge.R
import com.promptforge.data.McpServer
import com.promptforge.data.Skills
import com.promptforge.ui.components.BrandButton
import com.promptforge.ui.components.GlassCard
import com.promptforge.ui.components.LoadingDots
import com.promptforge.ui.components.SectionHeader
import com.promptforge.ui.nav.LocalAppContainer
import com.promptforge.ui.theme.Palette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ExtensionsUi(
    val smartTools: Boolean = true,
    val servers: List<McpServer> = emptyList(),
    val newName: String = "",
    val newUrl: String = "",
    val busyAdd: Boolean = false,
    val message: String? = null,
)

/** Extensions hub: skills, MCP tool servers & the Smart-Tools auto-use switch. */
class ExtensionsVm(private val c: AppContainer) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _ui = MutableStateFlow(ExtensionsUi())
    val ui: StateFlow<ExtensionsUi> = _ui.asStateFlow()

    init {
        scope.launch {
            val smart = runCatching { c.settings.settings.first().smartTools }.getOrDefault(true)
            _ui.value = _ui.value.copy(smartTools = smart, servers = c.mcp.all())
        }
    }

    fun setSmart(v: Boolean) {
        _ui.value = _ui.value.copy(smartTools = v)
        scope.launch { runCatching { c.settings.setSmartTools(v) } }
    }

    fun setNewName(v: String) { _ui.value = _ui.value.copy(newName = v) }
    fun setNewUrl(v: String) { _ui.value = _ui.value.copy(newUrl = v) }

    fun addServer() {
        if (_ui.value.busyAdd) return
        val name = _ui.value.newName.trim()
        val url = _ui.value.newUrl.trim()
        if (name.isEmpty() || !url.startsWith("http")) return
        _ui.value = _ui.value.copy(busyAdd = true, message = null)
        scope.launch {
            try {
                c.mcp.addOrUpdate(name, url)
                _ui.value = _ui.value.copy(
                    busyAdd = false, newName = "", newUrl = "",
                    servers = c.mcp.all(),
                    message = null,
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(busyAdd = false, message = e.message?.take(140) ?: "failed")
            }
        }
    }

    fun removeServer(s: McpServer) {
        c.mcp.remove(s.id)
        _ui.value = _ui.value.copy(servers = c.mcp.all())
    }
}

@Composable
fun ExtensionsScreen(onNavigate: (String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: ExtensionsVm = viewModel(key = "extensions") { ExtensionsVm(container) }
    val state by vm.ui.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
        Spacer(Modifier.height(18.dp))
        Column(Modifier.padding(horizontal = 20.dp)) {
            SectionHeader(stringResource(R.string.ext_title), painterResource(R.drawable.ic_layers))
            Text(
                stringResource(R.string.ext_desc),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Sub,
            )
        }
        Spacer(Modifier.height(12.dp))

        // ── Smart Tools ──
        Column(Modifier.padding(horizontal = 20.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.ext_smart),
                            style = MaterialTheme.typography.titleSmall,
                            color = Palette.Ink,
                        )
                        Text(
                            stringResource(R.string.ext_smart_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.Sub,
                        )
                    }
                    Box(
                        Modifier
                            .width(44.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (state.smartTools) Palette.Mint.copy(alpha = 0.85f) else Palette.fill5
                            )
                            .clickable { vm.setSmart(!state.smartTools) }
                            .padding(4.dp),
                        contentAlignment = if (state.smartTools) Alignment.CenterEnd else Alignment.CenterStart,
                    ) {
                        androidx.compose.foundation.layout.Box(
                            Modifier
                                .padding(horizontal = 2.dp)
                                .size(16.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Palette.Bg),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // ── Skills ──
        Column(Modifier.padding(horizontal = 20.dp)) {
            SectionHeader(stringResource(R.string.ext_skills), painterResource(R.drawable.ic_sparkles))
            GlassCard(Modifier.fillMaxWidth()) {
                val uiAr = androidx.compose.ui.platform.LocalConfiguration.current
                    .locales[0].language == "ar"
                Skills.ALL.forEach { sk ->
                    val ar = uiAr
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("✦", style = MaterialTheme.typography.labelMedium, color = Palette.Amber)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                if (ar) sk.ar else sk.en,
                                style = MaterialTheme.typography.titleSmall,
                                color = Palette.Ink,
                            )
                            Text(
                                sk.triggers.take(4).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = Palette.Faint,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.ext_skills_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Sub,
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // ── MCP servers ──
        Column(Modifier.padding(horizontal = 20.dp)) {
            SectionHeader(stringResource(R.string.ext_mcp), painterResource(R.drawable.ic_key))
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.ext_mcp_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Sub,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.newName,
                    onValueChange = vm::setNewName,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.ext_name), color = Palette.Faint, style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.newUrl,
                    onValueChange = vm::setNewUrl,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.ext_url), color = Palette.Faint, style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(10.dp))
                BrandButton(
                    text = if (state.busyAdd) stringResource(R.string.ext_connecting) else stringResource(R.string.ext_add),
                    icon = painterResource(R.drawable.ic_plus),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    loading = state.busyAdd,
                ) { vm.addServer() }
                state.message?.let { m ->
                    Spacer(Modifier.height(6.dp))
                    Text(m, style = MaterialTheme.typography.labelSmall, color = Palette.Red)
                }

                if (state.servers.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    state.servers.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    s.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Palette.Ink,
                                )
                                Text(
                                    s.url,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Palette.Faint,
                                    maxLines = 1,
                                )
                                if (s.lastError.isNotBlank()) {
                                    Text(
                                        s.lastError,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Palette.Red,
                                        maxLines = 2,
                                    )
                                }
                            }
                            val n = container.mcp.toolCount(s)
                            Text(
                                if (n > 0) "✓ $n" else "…",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (n > 0) Palette.Mint else Palette.Amber,
                                modifier = Modifier.padding(horizontal = 10.dp),
                            )
                            Icon(
                                painterResource(R.drawable.ic_trash),
                                contentDescription = stringResource(R.string.delete),
                                tint = Palette.Red,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { vm.removeServer(s) },
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.ext_none),
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Faint,
                    )
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}
