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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.promptforge.AppContainer
import com.promptforge.R
import com.promptforge.core.PromptAssembler
import com.promptforge.core.PromptDraft
import com.promptforge.data.ChatMsg
import com.promptforge.data.LlmErrors
import com.promptforge.data.Provider
import com.promptforge.ui.components.BrandButton
import com.promptforge.ui.components.GlassCard
import com.promptforge.ui.components.LoadingDots
import com.promptforge.ui.components.SectionHeader
import com.promptforge.ui.nav.LocalAppContainer
import com.promptforge.ui.nav.Routes
import com.promptforge.ui.theme.Palette
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlaygroundUiState(
    val messages: List<ChatMsg> = emptyList(),
    val input: String = "",
    val busy: Boolean = false,
    val waiting: Boolean = false,
    val error: String? = null,
    val systemPrompt: String? = null,
    val systemTitle: String? = null,
    val systemPromptId: String? = null,
    val provider: Provider = Provider.GEMINI,
    val model: String = "",
    val models: List<String> = emptyList(),
    val hasKey: Boolean = false,
    val temperature: Float = 0.7f,
    val refreshingModels: Boolean = false,
)

class PlaygroundViewModel(private val c: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(PlaygroundUiState())
    val ui: StateFlow<PlaygroundUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            c.settings.settings.collectLatest { s ->
                runCatching {
                    _ui.update {
                        it.copy(
                            provider = s.provider,
                            model = s.modelFor(s.provider),
                            models = if (it.provider == s.provider && it.models.isNotEmpty()) it.models else s.provider.defaultModels,
                            hasKey = s.provider == Provider.LOCAL || s.provider == com.promptforge.data.Provider.DEVICE || s.apiKey(s.provider).isNotBlank(),
                            temperature = s.temperature,
                        )
                    }
                    if (s.provider == Provider.LOCAL) syncLocalModels()
                    else if (s.provider == com.promptforge.data.Provider.DEVICE) syncDeviceModels()
                }.onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.e("PromptForge", "settings collector failed", e)
                }
            }
        }
        viewModelScope.launch {
            c.playgroundHolder.collectLatest { setup ->
                if (setup != null) {
                    c.playgroundHolder.value = null
                    _ui.update {
                        it.copy(
                            systemPrompt = setup.systemPrompt,
                            systemTitle = setup.title,
                            systemPromptId = setup.promptId,
                            messages = emptyList(),
                            error = null,
                        )
                    }
                }
            }
        }
    }

    fun setProvider(p: Provider) {
        viewModelScope.launch { c.settings.setProvider(p) }
        if (p == Provider.LOCAL) syncLocalModels()
        if (p == com.promptforge.data.Provider.DEVICE) syncDeviceModels()
    }

    /** Mirrors imported on-device models into the picker. */
    fun syncDeviceModels() {
        if (_ui.value.provider != com.promptforge.data.Provider.DEVICE) return
        val names = c.deviceRegistry.all().map { it.name }
        if (names.isNotEmpty()) {
            _ui.update { st ->
                st.copy(models = names, model = if (st.model in names) st.model else names.first(), hasKey = true)
            }
        }
    }

    /**
     * Pulls the ACTUAL installed models from the local Ollama server and
     * activates the first one — so what the user downloaded is what runs.
     */
    fun syncLocalModels() {
        if (_ui.value.provider != Provider.LOCAL) return
        viewModelScope.launch {
            val s = c.settings.settings.first()
            val live = c.llm.listModels(Provider.LOCAL, null, s.localBaseUrl)
            if (live.isNotEmpty()) {
                _ui.update { st ->
                    val next = if (st.model in live) st.model else live.first()
                    st.copy(models = live, model = next, hasKey = true)
                }
            }
        }
    }

    fun setInput(v: String) = _ui.update { it.copy(input = v) }

    fun setModel(m: String) {
        _ui.update { it.copy(model = m) }
        viewModelScope.launch { c.settings.setModel(_ui.value.provider, m) }
    }

    fun setTemperature(v: Float) {
        _ui.update { it.copy(temperature = v) }
        viewModelScope.launch { c.settings.setTemperature(v) }
    }

    fun refreshModels() {
        viewModelScope.launch {
            _ui.update { it.copy(refreshingModels = true) }
            val s = c.settings.settings.first()
            val models = c.llm.listModels(
                s.provider,
                s.apiKey(s.provider).takeIf { it.isNotBlank() },
                if (s.provider == Provider.LOCAL) s.localBaseUrl else null,
            )
            _ui.update { it.copy(refreshingModels = false, models = models) }
        }
    }

    fun clearSystem() = _ui.update { it.copy(systemPrompt = null, systemTitle = null, systemPromptId = null) }

    fun clearChat() = _ui.update { it.copy(messages = emptyList(), error = null) }

    fun send() {
        val s = _ui.value
        val text = s.input.trim()
        if (text.isBlank() || s.busy) return
        _ui.update { it.copy(input = "", messages = it.messages + ChatMsg("user", text), error = null) }
        viewModelScope.launch { runConversation() }
    }

    /** Drops the last assistant reply and asks the model again. */
    fun regenerate() {
        if (_ui.value.busy) return
        val msgs = _ui.value.messages
        if (msgs.isEmpty()) return
        val trimmed = if (msgs.lastOrNull()?.role == "assistant") msgs.dropLast(1) else msgs
        if (trimmed.none { it.role == "user" }) return
        _ui.update { it.copy(messages = trimmed, error = null) }
        viewModelScope.launch { runConversation() }
    }

    /** Puts a past user message back into the editor, truncating history from there. */
    fun editMessageAt(index: Int) {
        if (_ui.value.busy) return
        val msgs = _ui.value.messages
        if (index !in msgs.indices || msgs[index].role != "user") return
        _ui.update { it.copy(messages = msgs.take(index), input = msgs[index].text, error = null) }
    }

    private suspend fun runConversation() {
        _ui.update { it.copy(busy = true, waiting = false, error = null) }
        var rateRetries = 0
        var retriedModel = false
        var switchedProvider = false
        while (true) {
            // Re-read settings every attempt so provider fallback picks up cleanly
            val settings = c.settings.settings.first()
            val key = settings.apiKey(settings.provider)
            if (key.isBlank() && settings.provider != Provider.LOCAL &&
                settings.provider != com.promptforge.data.Provider.DEVICE
            ) {
                _ui.update { it.copy(busy = false, waiting = false, error = LlmErrors.KEY) }
                break
            }
            // ── On-device: stream locally, no network at all ──
            if (settings.provider == com.promptforge.data.Provider.DEVICE) {
                val registry = c.deviceRegistry.all()
                val dm = registry.firstOrNull { it.name == _ui.value.model } ?: registry.firstOrNull()
                if (dm == null) {
                    _ui.update { it.copy(busy = false, waiting = false, error = "no_device_model") }
                    break
                }
                _ui.update { it.copy(model = dm.name, models = registry.map { m -> m.name }) }
                com.promptforge.DownloadService.startInference(
                    c.appContext, c.appContext.getString(com.promptforge.R.string.service_inference),
                )
                try {
                    val prompt = buildChatPrompt(_ui.value.systemPrompt, _ui.value.messages.map { it.role to it.text })
                    c.deviceEngine.chat(dm.path, prompt, _ui.value.temperature, 1024) { partial ->
                        _ui.update { st ->
                            val msgs = st.messages.toMutableList()
                            if (msgs.isNotEmpty() && msgs.last().role == "assistant") {
                                msgs[msgs.lastIndex] = ChatMsg("assistant", partial)
                            } else msgs.add(ChatMsg("assistant", partial))
                            st.copy(messages = msgs)
                        }
                    }
                    _ui.update { it.copy(busy = false, waiting = false) }
                    break
                } catch (e: Exception) {
                    val kind = if (e.message == "empty_response") LlmErrors.EMPTY else LlmErrors.OTHER
                    _ui.update { it.copy(busy = false, waiting = false, error = kind) }
                    break
                } finally {
                    com.promptforge.DownloadService.stopInference(c.appContext)
                }
            }
            try {
                // Local provider: guarantee the selected model actually exists on the server
                var modelForCall = _ui.value.model
                if (settings.provider == Provider.LOCAL &&
                    (modelForCall.isBlank() || !_ui.value.models.contains(modelForCall))
                ) {
                    val live = c.llm.listModels(Provider.LOCAL, null, settings.localBaseUrl)
                    if (live.isNotEmpty()) {
                        modelForCall = if (modelForCall in live) modelForCall else live.first()
                        _ui.update { it.copy(models = live, model = modelForCall) }
                    }
                }
                val history = _ui.value.messages.map { it.role to it.text }
                val reply = c.llm.chat(
                    provider = settings.provider,
                    apiKey = key,
                    model = modelForCall,
                    system = _ui.value.systemPrompt,
                    turns = history,
                    temperature = _ui.value.temperature.toDouble(),
                    maxTokens = 3072,
                    baseUrlOverride = if (settings.provider == Provider.LOCAL) settings.localBaseUrl else null,
                )
                _ui.value.systemPromptId?.let { id -> c.repository.incrementUsage(id) }
                _ui.update { it.copy(busy = false, waiting = false, messages = it.messages + ChatMsg("assistant", reply.trim())) }
                break
            } catch (e: Exception) {
                // Read the body once, then classify
                val code = (e as? retrofit2.HttpException)?.code()
                val body = LlmErrors.bodyOf(e)
                val kind = LlmErrors.kindOf(code, body, e)
                when {
                    // 429 / quota: honor the provider's own retryDelay, then our backoff — up to 2 tries
                    kind == LlmErrors.RATE && rateRetries < 2 -> {
                        rateRetries++
                        val seconds = LlmErrors.retrySecondsOf(body) ?: (7L * rateRetries)
                        _ui.update { it.copy(waiting = true) }
                        delay(seconds * 1000)
                        _ui.update { it.copy(waiting = false) }
                    }
                    // Quota still exhausted: hop to another connected provider's key automatically
                    kind == LlmErrors.RATE && !switchedProvider -> {
                        val alt = Provider.entries.firstOrNull {
                            it != settings.provider && it != Provider.LOCAL && settings.apiKey(it).isNotBlank()
                        }
                        if (alt != null) {
                            switchedProvider = true
                            c.settings.setProvider(alt)
                            val altModel = settings.modelFor(alt)
                            _ui.update { it.copy(provider = alt, model = altModel, hasKey = true, waiting = true) }
                            delay(1200)
                            _ui.update { it.copy(waiting = false) }
                        } else {
                            _ui.update { it.copy(busy = false, waiting = false, error = kind) }
                            break
                        }
                    }
                    // dead/overloaded model: refresh the live list and switch once
                    (kind == LlmErrors.MODEL || kind == LlmErrors.BAD) && !retriedModel -> {
                        retriedModel = true
                        val live = c.llm.listModels(settings.provider, key, if (settings.provider == Provider.LOCAL) settings.localBaseUrl else null)
                        val next = live.firstOrNull { it != _ui.value.model }
                        if (live.isNotEmpty() && next != null) {
                            _ui.update { it.copy(model = next, models = live) }
                            c.settings.setModel(settings.provider, next)
                        } else {
                            _ui.update { it.copy(busy = false, waiting = false, error = kind) }
                            break
                        }
                    }
                    else -> {
                        _ui.update { it.copy(busy = false, waiting = false, error = kind) }
                        break
                    }
                }
            }
        }
    }
}

@Composable
fun PlaygroundScreen(onNavigate: (String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: PlaygroundViewModel = viewModel(factory = container.vmFactory)
    val state by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val copiedMsg = stringResource(R.string.copied)
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.busy) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem((state.messages.size - 1).coerceAtLeast(0))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.playground_title),
                style = MaterialTheme.typography.displaySmall,
                color = Palette.Ink,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Palette.fill5)
                    .clickable { vm.clearChat() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_refresh), null, tint = Palette.Sub, modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.height(12.dp))

        // ── Provider & model row ──
        var providerMenu by remember { mutableStateOf(false) }
        var modelMenu by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Palette.Primary.copy(alpha = 0.14f))
                        .clickable { providerMenu = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(state.provider.mono, style = MaterialTheme.typography.labelLarge, color = Palette.Primary)
                    Spacer(Modifier.width(6.dp))
                    Icon(painterResource(R.drawable.ic_layers), null, tint = Palette.Primary, modifier = Modifier.size(13.dp))
                }
                DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }, containerColor = Palette.SurfaceHi) {
                    Provider.entries.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.labelAr, color = Palette.Ink) },
                            onClick = {
                                providerMenu = false
                                vm.setProvider(p)
                            },
                        )
                    }
                }
            }
            Box {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Palette.fill6)
                        .clickable { modelMenu = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.model,
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.Ink,
                        maxLines = 1,
                        modifier = Modifier.width(150.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    if (state.refreshingModels) LoadingDots() else Icon(
                        painterResource(R.drawable.ic_external), null,
                        tint = Palette.Faint, modifier = Modifier.size(13.dp),
                    )
                }
                DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }, containerColor = Palette.SurfaceHi) {
                    state.models.take(300).forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m, color = if (m == state.model) Palette.Cyan else Palette.Ink, style = MaterialTheme.typography.bodySmall) },
                            onClick = { modelMenu = false; vm.setModel(m) },
                        )
                    }
                }
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Palette.fill6)
                    .clickable { vm.refreshModels() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.refresh_models), style = MaterialTheme.typography.labelMedium, color = Palette.Sub)
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── System prompt card ──
        GlassCard(Modifier.fillMaxWidth(), contentPadding = 13.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_brain), null, tint = Palette.Pink, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.system_prompt), style = MaterialTheme.typography.labelMedium, color = Palette.Pink)
                    Text(
                        state.systemTitle ?: state.systemPrompt?.take(90)?.plus("…") ?: stringResource(R.string.system_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Sub,
                        maxLines = 2,
                    )
                }
                if (state.systemPrompt != null) {
                    Icon(
                        painterResource(R.drawable.ic_close), null,
                        tint = Palette.Faint,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { vm.clearSystem() },
                    )
                }
            }
        }

        // ── Key missing banner ──
        if (!state.hasKey) {
            Spacer(Modifier.height(10.dp))
            GlassCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_key), null, tint = Palette.Amber, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.key_missing_title), style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
                        Text(stringResource(R.string.key_missing_body), style = MaterialTheme.typography.bodySmall, color = Palette.Sub)
                    }
                }
                Spacer(Modifier.height(10.dp))
                BrandButton(
                    text = stringResource(R.string.get_free_key),
                    icon = painterResource(R.drawable.ic_external),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.provider.keyUrl)))
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Temperature ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_sliders), null, tint = Palette.Faint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.temperature), style = MaterialTheme.typography.labelMedium, color = Palette.Sub)
            Spacer(Modifier.width(10.dp))
            Slider(
                value = state.temperature,
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
                String.format(java.util.Locale.US, "%.1f", state.temperature),
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Ink,
                modifier = Modifier.width(34.dp),
                textAlign = TextAlign.End,
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── Messages ──
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.messages.isEmpty() && !state.busy) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(painterResource(R.drawable.ic_nav_playground), null, tint = Palette.Faint, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(stringResource(R.string.send_hint), style = MaterialTheme.typography.bodyMedium, color = Palette.Faint)
                        }
                    }
                }
            }
            itemsIndexed(state.messages) { idx, msg ->
                MessageBubble(
                    msg = msg,
                    isLast = idx == state.messages.lastIndex,
                    onCopy = {
                        clipboard.setText(AnnotatedString(msg.text))
                        Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                    },
                    onEdit = { vm.editMessageAt(idx) },
                    onRegenerate = { vm.regenerate() },
                )
            }
            if (state.busy) {
                item {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(Palette.fill5)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.waiting) {
                            Icon(painterResource(R.drawable.ic_clock), null, tint = Palette.Amber, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.waiting_retry), style = MaterialTheme.typography.bodySmall, color = Palette.Amber)
                        } else {
                            LoadingDots()
                        }
                    }
                }
            }
        }

        state.error?.let { err ->
            Spacer(Modifier.height(8.dp))
            Text(
                when (err) {
                    "no_device_model" -> stringResource(R.string.device_pick_first)
                    LlmErrors.RATE -> stringResource(R.string.err_rate)
                    LlmErrors.REGION -> stringResource(R.string.err_region)
                    LlmErrors.KEY, "no_key" -> stringResource(R.string.no_api_key)
                    LlmErrors.MODEL -> stringResource(R.string.err_model)
                    LlmErrors.BAD -> stringResource(R.string.err_bad_request)
                    LlmErrors.NETWORK -> stringResource(R.string.err_network)
                    LlmErrors.EMPTY -> stringResource(R.string.err_empty)
                    else -> stringResource(R.string.err_other)
                },
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Red,
            )
        }

        // ── Input ──
        Spacer(Modifier.height(10.dp))
        var inputExpanded by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = state.input,
                onValueChange = vm::setInput,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.send_hint), color = Palette.Faint, style = MaterialTheme.typography.bodyMedium) },
                minLines = if (inputExpanded) 6 else 1,
                maxLines = if (inputExpanded) 14 else 4,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.Primary,
                    unfocusedBorderColor = Palette.hair1,
                    focusedContainerColor = Palette.fill4,
                    unfocusedContainerColor = Palette.fill3,
                    cursorColor = Palette.Cyan,
                ),
            )
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Palette.fill5)
                    .border(1.dp, Palette.glassBorder, RoundedCornerShape(15.dp))
                    .clickable { inputExpanded = !inputExpanded }
                    .padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_expand),
                    contentDescription = stringResource(R.string.expand_input),
                    tint = if (inputExpanded) Palette.Cyan else Palette.Sub,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box(
                Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (state.input.isNotBlank() && !state.busy) Brush.linearGradient(Palette.brandColors) else Brush.linearGradient(listOf(Palette.fill7, Palette.fill7)))
                    .clickable(enabled = state.input.isNotBlank() && !state.busy) { vm.send() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_send), stringResource(R.string.send_hint),
                    tint = if (state.input.isNotBlank() && !state.busy) Color.White else Palette.Faint,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun MessageBubble(
    msg: ChatMsg,
    isLast: Boolean,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
) {
    val isUser = msg.role == "user"
    val shape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (isUser) 18.dp else 6.dp,
        bottomEnd = if (isUser) 6.dp else 18.dp,
    )
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Box(
            Modifier
                .widthIn(max = 310.dp)
                .clip(shape)
                .then(
                    if (isUser) Modifier.border(1.dp, Brush.linearGradient(Palette.brandColors), shape)
                    else Modifier.background(Palette.fill6)
                )
                .then(if (isUser) Modifier.background(Palette.fill3) else Modifier)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                msg.text,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Ink,
            )
        }
        Row(
            Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BubbleAction(R.drawable.ic_copy, stringResource(R.string.msg_copy), onCopy)
            if (isUser) BubbleAction(R.drawable.ic_pencil, stringResource(R.string.msg_edit), onEdit)
            if (!isUser && isLast) BubbleAction(R.drawable.ic_refresh, stringResource(R.string.msg_regenerate), onRegenerate)
        }
    }
}

@Composable
private fun BubbleAction(icon: Int, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = Palette.Faint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Faint)
    }
}

private fun buildChatPrompt(system: String?, turns: List<Pair<String, String>>): String = buildString {
    if (!system.isNullOrBlank()) append(system.trim()).append("\n\n")
    turns.forEach { (role, text) ->
        if (role == "user") append("User: ").append(text).append("\n")
        else append("Assistant: ").append(text).append("\n")
    }
    append("Assistant:")
}
