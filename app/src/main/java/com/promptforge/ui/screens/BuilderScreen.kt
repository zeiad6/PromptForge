@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.promptforge.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.promptforge.AppContainer
import com.promptforge.R
import com.promptforge.core.CustomSection
import com.promptforge.core.DetailLevel
import com.promptforge.core.Framework
import com.promptforge.core.OutputFormat
import com.promptforge.core.PromptAssembler
import com.promptforge.core.PromptConfig
import com.promptforge.core.PromptDraft
import com.promptforge.core.PromptLang
import com.promptforge.core.PromptScorer
import com.promptforge.core.PromptScorer.Severity
import com.promptforge.core.SavedPrompt
import com.promptforge.core.Tone
import com.promptforge.data.LlmErrors
import com.promptforge.data.PromptRepository
import com.promptforge.data.PlaygroundSetup
import com.promptforge.ui.components.BrandButton
import com.promptforge.ui.components.GhostButton
import com.promptforge.ui.components.GlassCard
import com.promptforge.ui.components.LoadingDots
import com.promptforge.ui.components.LabeledArea
import com.promptforge.ui.components.OptionPill
import com.promptforge.ui.components.ScoreRing
import com.promptforge.ui.components.SectionHeader
import com.promptforge.ui.components.TipRow
import com.promptforge.ui.nav.LocalAppContainer
import com.promptforge.ui.nav.Routes
import com.promptforge.ui.theme.Palette
import com.promptforge.util.desc
import com.promptforge.util.hint
import com.promptforge.util.label
import com.promptforge.util.sectionIcon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── State & ViewModel ───────────────────────────────────────────────

data class BuilderUiState(
    val draft: PromptDraft = PromptDraft(),
    val assembled: String = "",
    val score: Int = 0,
    val tips: List<PromptScorer.Tip> = emptyList(),
    val enhancing: Boolean = false,
    val enhanced: String? = null,
    val enhanceError: String? = null,
    val researchBusy: Boolean = false,
)

class BuilderViewModel(private val c: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(BuilderUiState(assembled = PromptAssembler.assemble(PromptDraft())))
    val ui: StateFlow<BuilderUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            c.draftHolder.collect { d ->
                if (d != null) {
                    apply(d)
                    c.draftHolder.value = null
                }
            }
        }
    }

    private fun apply(d: PromptDraft) {
        val assembled = PromptAssembler.assemble(d)
        val result = PromptScorer.score(d)
        _ui.update { it.copy(draft = d, assembled = assembled, score = result.score, tips = result.tips) }
    }

    fun setTitle(v: String) = apply(_ui.value.draft.copy(title = v))
    fun setFramework(f: Framework) = apply(_ui.value.draft.copy(frameworkId = f.id))
    fun setField(key: String, v: String) = apply(_ui.value.draft.copy(fields = _ui.value.draft.fields + (key to v)))
    fun setConfig(transform: (PromptConfig) -> PromptConfig) = apply(_ui.value.draft.copy(config = transform(_ui.value.draft.config)))

    fun addCustomSection() = apply(_ui.value.draft.copy(customSections = _ui.value.draft.customSections + CustomSection()))

    fun updateCustomSection(index: Int, title: String, content: String) {
        val list = _ui.value.draft.customSections.toMutableList()
        if (index in list.indices) {
            list[index] = CustomSection(title, content)
            apply(_ui.value.draft.copy(customSections = list))
        }
    }

    fun removeCustomSection(index: Int) {
        val list = _ui.value.draft.customSections.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            apply(_ui.value.draft.copy(customSections = list))
        }
    }

    fun save(onSaved: () -> Unit) {
        val s = _ui.value
        val now = System.currentTimeMillis()
        val existing = s.draft.id?.let { id -> c.repository.prompts.value.firstOrNull { it.id == id } }
        val prompt = SavedPrompt(
            id = existing?.id ?: PromptRepository.newId(),
            title = s.draft.title.ifBlank { "" },
            body = s.assembled,
            frameworkId = s.draft.frameworkId,
            tags = s.draft.tags,
            score = s.score,
            favorite = existing?.favorite ?: false,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            usedCount = existing?.usedCount ?: 0,
        )
        viewModelScope.launch {
            c.repository.upsert(prompt)
            _ui.update { it.copy(draft = it.draft.copy(id = prompt.id)) }
            onSaved()
        }
    }

    fun enhance() {
        if (_ui.value.enhancing) return
        viewModelScope.launch {
            _ui.update { it.copy(enhancing = true, enhanceError = null, enhanced = null) }
            val settings = c.settings.settings.first()
            val key = settings.apiKey(settings.provider)
            if (key.isBlank() && settings.provider != com.promptforge.data.Provider.DEVICE) {
                _ui.update { it.copy(enhancing = false, enhanceError = "no_key") }
                return@launch
            }
            try {
                val s = _ui.value
                val arabic = s.draft.config.lang != PromptLang.en
                val system = if (arabic) ENHANCE_SYSTEM_AR else ENHANCE_SYSTEM_EN
                // Feed the model the analyzer's weak points so it fixes them precisely
                val weaknesses = s.tips.mapNotNull { WEAK_POINTS[it.key] }
                val userPayload = buildString {
                    append(s.assembled)
                    if (weaknesses.isNotEmpty()) {
                        append("\n\n---\n")
                        append(if (arabic) "نقاط الضعف المكتشفة (أصلحها كلها): " else "Detected weak points (fix all of them): ")
                        append(weaknesses.joinToString(" | "))
                    }
                }
                val improved = c.chatSmart(settings, system, userPayload, 0.55, 4096)
                _ui.update { it.copy(enhancing = false, enhanced = improved.trim()) }
            } catch (e: Exception) {
                _ui.update { it.copy(enhancing = false, enhanceError = LlmErrors.kind(e)) }
            }
        }
    }

    /**
     * Runs an AI research pass over the prompt topic and appends the result
     * as a "sources" custom section, so the executing agent starts grounded.
     */
    fun researchEnrich() {
        if (_ui.value.researchBusy || _ui.value.enhancing) return
        viewModelScope.launch {
            _ui.update { it.copy(researchBusy = true, enhanceError = null) }
            val settings = c.settings.settings.first()
            val key = settings.apiKey(settings.provider)
            if (key.isBlank() && settings.provider != com.promptforge.data.Provider.DEVICE) {
                _ui.update { it.copy(researchBusy = false, enhanceError = LlmErrors.KEY) }
                return@launch
            }
            try {
                val s = _ui.value
                val arabic = s.draft.config.lang != PromptLang.en
                val improved = c.chatSmart(settings, if (arabic) RESEARCH_SYSTEM_AR else RESEARCH_SYSTEM_EN, s.assembled, 0.4, 2048)
                val newSection = CustomSection(
                    title = if (arabic) "مصادر ومراجع (Research)" else "Sources & references (Research)",
                    content = improved.trim(),
                )
                apply(s.draft.copy(customSections = s.draft.customSections + newSection))
                _ui.update { it.copy(researchBusy = false) }
            } catch (e: Exception) {
                _ui.update { it.copy(researchBusy = false, enhanceError = LlmErrors.kind(e)) }
            }
        }
    }

    fun saveEnhanced(onSaved: () -> Unit) {
        val enhanced = _ui.value.enhanced ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            c.repository.upsert(
                SavedPrompt(
                    id = PromptRepository.newId(),
                    title = _ui.value.draft.title.ifBlank { "" } + " ✦",
                    body = enhanced,
                    frameworkId = _ui.value.draft.frameworkId,
                    score = 90,
                    createdAt = now, updatedAt = now,
                )
            )
            onSaved()
        }
    }

    fun tryInLab() {
        val s = _ui.value
        c.playgroundHolder.value = PlaygroundSetup(
            systemPrompt = s.enhanced ?: s.assembled,
            title = s.draft.title.ifBlank { null },
        )
    }

    fun dismissEnhanced() = _ui.update { it.copy(enhanced = null, enhanceError = null) }

    private val WEAK_POINTS = mapOf(
        "tip_role_missing" to "define a clear expert role/persona for the agent",
        "tip_task_missing" to "state the main task as one sharp actionable sentence",
        "tip_context_missing" to "add richer context and background",
        "tip_constraints_missing" to "add explicit output constraints and rules",
        "tip_audience_missing" to "define the target audience",
        "tip_too_short" to "enrich with concrete specifics",
        "tip_too_long" to "trim redundancy, keep it tight",
        "tip_vague_words" to "replace vague wording with precise terms",
        "tip_add_numbers" to "add measurable numeric criteria",
    )

    private companion object {
        val ENHANCE_SYSTEM_AR = """أنت مهندس برومبتات عالمي المستوى (Prompt Engineer).
مهمتك: إعادة صياغة البرومبت الذي سيرسل لك المستخدم ليصبح برومبتاً احترافياً مثالياً لوكلاء الذكاء الاصطناعي، مع:
1) الحفاظ على لغة البرومبت الأصلية ونطانه.
2) الحفاظ على كل معلوماته ونيته، وإضافة الوضوح والهيكلة والأمثلة عند الحاجة.
3) إضافة معايير إخراج محددة وقابلة للقياس إن لم تكن موجودة.
4) استخدام عناوين Markdown واضحة.
أخرج البرومبت المحسّن فقط، دون أي شرح أو مقدمات."""

        val RESEARCH_SYSTEM_AR = """أنت باحث محترف. مهمتك: إعداد حزمة بحثية مركزية لبرومبت سيُنفَّذ لاحقاً، ليبدأ الوكيل المنفذ من أساس موثوق:
1) 5-8 حقائق جوهرية موثوقة حول موضوع البرومبت مع أرقامها.
2) أهم المصادر لكل حقيقة: اسم المصدر + الرابط + تاريخ النشر، وما تعذر التحقق منه اكتب بجانبه: [يحتاج تحقق].
3) أحدث التطورات ذات الصلة بالموضوع.
4) التناقضات والمزاعم الشائعة التي يجب الحذر منها.
5) ملاحظة ثقة لكل نقطة (عالية/متوسطة/منخفضة).
أخرج النتيجة كقائمة Markdown موجزة جاهزة للدمج داخل برومبت، دون مقدمات أو كلام زائد."""

        val RESEARCH_SYSTEM_EN = """You are a professional researcher. Prepare a focused research pack for a prompt that will run later, so the executing agent starts grounded:
1) 5-8 core, trustworthy facts about the prompt's topic, with numbers.
2) Key sources per fact: source name + URL + publish date; mark anything unverifiable as [needs verification].
3) Latest relevant developments.
4) Common contradictions and claims to watch out for.
5) A confidence note per point (high/medium/low).
Output as a compact Markdown list ready to embed inside a prompt. No preamble."""

        val ENHANCE_SYSTEM_EN = """You are a world-class prompt engineer.
Rewrite the user's prompt below into a professional, optimized prompt for AI agents:
1) Keep the original language and intent intact.
2) Preserve all information while adding clarity, structure and examples where helpful.
3) Add measurable output criteria if missing.
4) Use clear Markdown headings.
Output only the improved prompt, no explanations."""
    }
}

// ── Screen ──────────────────────────────────────────────────────────

@Composable
fun BuilderScreen(onNavigate: (String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: BuilderViewModel = viewModel(factory = container.vmFactory)
    val state by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val copiedMsg = stringResource(R.string.copied)
    val savedMsg = stringResource(R.string.saved)
    val titleRequired = stringResource(R.string.title_required)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 140.dp),
    ) {
        Text(
            stringResource(R.string.builder_title),
            style = MaterialTheme.typography.displaySmall,
            color = Palette.Ink,
            modifier = Modifier.padding(vertical = 16.dp),
        )

        // ── Title ──
        LabeledArea(
            label = stringResource(R.string.field_title),
            value = state.draft.title,
            onValueChange = vm::setTitle,
            hint = stringResource(R.string.field_title_hint),
            minLines = 1,
        )

        Spacer(Modifier.height(18.dp))

        // ── Framework picker ──
        SectionHeader(stringResource(R.string.framework_label), painterResource(R.drawable.ic_layers))
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(Framework.entries.toList()) { fw ->
                FrameworkCard(
                    framework = fw,
                    selected = fw.id == state.draft.frameworkId,
                    onClick = { vm.setFramework(fw) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Dynamic fields ──
        state.draft.framework.sections.forEach { section ->
            LabeledArea(
                label = section.label(),
                value = state.draft.fields[section.key].orEmpty(),
                onValueChange = { vm.setField(section.key, it) },
                hint = section.hint(),
                minLines = section.minLinesOf(),
                maxLines = 40,
            )
            Spacer(Modifier.height(14.dp))
        }

        // ── Custom sections (unlimited) ──
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader(stringResource(R.string.custom_sections), painterResource(R.drawable.ic_layers))
            Spacer(Modifier.height(8.dp))
            state.draft.customSections.forEachIndexed { idx, cs ->
                OutlinedTextField(
                    value = cs.title,
                    onValueChange = { vm.updateCustomSection(idx, it, cs.content) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.section_title_hint), color = Palette.Faint, style = MaterialTheme.typography.bodyMedium) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.titleSmall.copy(color = Palette.Ink),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Palette.Cyan,
                        unfocusedBorderColor = Palette.hair1,
                        focusedContainerColor = Palette.fill4,
                        unfocusedContainerColor = Palette.fill3,
                        cursorColor = Palette.Cyan,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = cs.content,
                    onValueChange = { vm.updateCustomSection(idx, cs.title, it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.preview_empty), color = Palette.Faint, style = MaterialTheme.typography.bodyMedium) },
                    minLines = 3,
                    maxLines = 40,
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Ink),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Palette.Cyan,
                        unfocusedBorderColor = Palette.hair1,
                        focusedContainerColor = Palette.fill4,
                        unfocusedContainerColor = Palette.fill3,
                        cursorColor = Palette.Cyan,
                    ),
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Icon(
                        painterResource(R.drawable.ic_trash),
                        contentDescription = stringResource(R.string.remove_section),
                        tint = Palette.Red,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { vm.removeCustomSection(idx) },
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            GhostButton(
                text = stringResource(R.string.add_section),
                modifier = Modifier.fillMaxWidth(),
                icon = painterResource(R.drawable.ic_plus),
                tint = Palette.Cyan,
            ) { vm.addCustomSection() }
        }

        Spacer(Modifier.height(14.dp))

        // ── Output settings ──
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader(stringResource(R.string.output_settings), painterResource(R.drawable.ic_sliders))
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.tone_label), style = MaterialTheme.typography.titleSmall, color = Palette.Sub)
            Spacer(Modifier.height(7.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Tone.entries.forEach { tone ->
                    OptionPill(tone.label(), tone == state.draft.config.tone) {
                        vm.setConfig { it.copy(tone = tone) }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.format_label), style = MaterialTheme.typography.titleSmall, color = Palette.Sub)
            Spacer(Modifier.height(7.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutputFormat.entries.forEach { fmt ->
                    OptionPill(fmt.label(), fmt == state.draft.config.format) {
                        vm.setConfig { it.copy(format = fmt) }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PromptLang.entries.forEach { lang ->
                    OptionPill(lang.label(), lang == state.draft.config.lang, Modifier.weight(1f)) {
                        vm.setConfig { it.copy(lang = lang) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLevel.entries.forEach { level ->
                    OptionPill(level.label(), level == state.draft.config.detail, Modifier.weight(1f)) {
                        vm.setConfig { it.copy(detail = level) }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── Advanced options ──
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader(stringResource(R.string.adv_options), painterResource(R.drawable.ic_sparkles))
            Spacer(Modifier.height(6.dp))
            ToggleRow(
                title = stringResource(R.string.opt_chain),
                subtitle = stringResource(R.string.opt_chain_desc),
                checked = state.draft.config.chainOfThought,
            ) { v -> vm.setConfig { it.copy(chainOfThought = v) } }
            ToggleRow(
                title = stringResource(R.string.opt_clarify),
                subtitle = stringResource(R.string.opt_clarify_desc),
                checked = state.draft.config.askClarifications,
            ) { v -> vm.setConfig { it.copy(askClarifications = v) } }
            ToggleRow(
                title = stringResource(R.string.opt_fewshot),
                subtitle = stringResource(R.string.opt_fewshot_desc),
                checked = state.draft.config.fewShot,
            ) { v -> vm.setConfig { it.copy(fewShot = v) } }
            ToggleRow(
                title = stringResource(R.string.opt_unlimited),
                subtitle = stringResource(R.string.opt_unlimited_desc),
                checked = state.draft.config.unrestricted,
            ) { v -> vm.setConfig { it.copy(unrestricted = v) } }
            ToggleRow(
                title = stringResource(R.string.opt_research),
                subtitle = stringResource(R.string.opt_research_desc),
                checked = state.draft.config.research,
            ) { v -> vm.setConfig { it.copy(research = v) } }
            ToggleRow(
                title = stringResource(R.string.opt_creative),
                subtitle = stringResource(R.string.opt_creative_desc),
                checked = state.draft.config.creative,
            ) { v -> vm.setConfig { it.copy(creative = v) } }
            Spacer(Modifier.height(8.dp))
            GhostButton(
                text = if (state.researchBusy) stringResource(R.string.enhance_loading) else stringResource(R.string.btn_research),
                modifier = Modifier.fillMaxWidth(),
                icon = painterResource(R.drawable.ic_search),
                tint = Palette.Mint,
            ) { vm.researchEnrich() }
        }

        Spacer(Modifier.height(20.dp))

        // ── Live preview + score ──
        SectionHeader(
            stringResource(R.string.live_preview),
            painterResource(R.drawable.ic_target),
        )
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(state.score, ringSize = 58.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.quality_score), style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
                    Spacer(Modifier.height(3.dp))
                    state.tips.take(3).forEach { tip ->
                        val (colorRes, icon) = when (tip.severity) {
                            Severity.critical -> Palette.Red to R.drawable.ic_alert
                            Severity.warn -> Palette.Amber to R.drawable.ic_alert
                            Severity.info -> Palette.Cyan to R.drawable.ic_check
                        }
                        TipRow(stringResource(tipKey(tip.key)), colorRes, painterResource(icon))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.codeBg)
                    .border(1.dp, Palette.fill6, RoundedCornerShape(14.dp))
                    .padding(12.dp),
            ) {
                if (state.assembled.isBlank()) {
                    Text(
                        stringResource(R.string.preview_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Faint,
                    )
                } else {
                    SelectionContainer {
                        Text(
                            state.assembled,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = Palette.codeText,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // ── Actions ──
        BrandButton(
            text = stringResource(R.string.btn_save),
            icon = painterResource(R.drawable.ic_save),
            modifier = Modifier.fillMaxWidth(),
            enabled = state.assembled.isNotBlank(),
        ) {
            if (state.draft.title.isBlank()) {
                Toast.makeText(context, titleRequired, Toast.LENGTH_SHORT).show()
            } else {
                vm.save { Toast.makeText(context, savedMsg, Toast.LENGTH_SHORT).show() }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton(
                text = if (state.enhancing) stringResource(R.string.enhance_loading) else stringResource(R.string.btn_enhance),
                onClick = { vm.enhance() },
                modifier = Modifier.weight(1f),
                icon = painterResource(R.drawable.ic_sparkles),
                tint = Palette.Pink,
            )
            GhostButton(
                stringResource(R.string.btn_try),
                Modifier.weight(1f),
                painterResource(R.drawable.ic_zap),
                Palette.Cyan,
            ) {
                vm.tryInLab()
                onNavigate(Routes.PLAYGROUND)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton(
                stringResource(R.string.btn_copy),
                Modifier.weight(1f), painterResource(R.drawable.ic_copy),
            ) {
                clipboard.setText(AnnotatedString(state.assembled))
                Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
            }
            GhostButton(
                stringResource(R.string.btn_share),
                Modifier.weight(1f), painterResource(R.drawable.ic_share),
            ) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, state.assembled)
                }
                context.startActivity(Intent.createChooser(send, null))
            }
        }
    }

    // ── Enhanced result sheet ──
    if (state.enhanced != null || state.enhanceError != null) {
        ModalBottomSheet(
            onDismissRequest = { vm.dismissEnhanced() },
            containerColor = Palette.Surface,
        ) {
            Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 34.dp)) {
                val err = state.enhanceError
                if (err != null) {
                    Text(
                        when (err) {
                            LlmErrors.KEY, "no_key" -> stringResource(R.string.no_api_key)
                            LlmErrors.RATE -> stringResource(R.string.err_rate)
                            LlmErrors.NETWORK -> stringResource(R.string.err_network)
                            else -> stringResource(R.string.error_generic)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Palette.Ink,
                    )
                } else {
                    SectionHeader(stringResource(R.string.enhance_title), painterResource(R.drawable.ic_sparkles))
                    Spacer(Modifier.height(10.dp))
                    SelectionContainer {
                        Text(
                            state.enhanced.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = Palette.codeText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Palette.codeBg)
                                .padding(14.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton(
                            stringResource(R.string.copy),
                            Modifier.weight(1f), painterResource(R.drawable.ic_copy),
                        ) {
                            clipboard.setText(AnnotatedString(state.enhanced.orEmpty()))
                            Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                        }
                        GhostButton(
                            stringResource(R.string.save_to_library),
                            Modifier.weight(1f), painterResource(R.drawable.ic_save),
                        ) {
                            vm.saveEnhanced { Toast.makeText(context, savedMsg, Toast.LENGTH_SHORT).show() }
                            vm.dismissEnhanced()
                        }
                    }
                }
            }
        }
    }
}

private fun com.promptforge.core.PromptSection.minLinesOf(): Int = when (key) {
    "role", "style", "tone", "audience", "format", "expectation", "goal", "purpose" -> 2
    else -> 3
}

private fun tipKey(key: String): Int = when (key) {
    "tip_title_missing" -> R.string.tip_title_missing
    "tip_role_missing" -> R.string.tip_role_missing
    "tip_task_missing" -> R.string.tip_task_missing
    "tip_context_missing" -> R.string.tip_context_missing
    "tip_constraints_missing" -> R.string.tip_constraints_missing
    "tip_audience_missing" -> R.string.tip_audience_missing
    "tip_too_short" -> R.string.tip_too_short
    "tip_too_long" -> R.string.tip_too_long
    "tip_vague_words" -> R.string.tip_vague_words
    "tip_add_numbers" -> R.string.tip_add_numbers
    else -> R.string.tip_excellent
}

@Composable
private fun FrameworkCard(framework: Framework, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .width(150.dp)
            .clip(shape)
            .background(
                if (selected) Brush.linearGradient(listOf(Palette.Primary.copy(alpha = 0.20f), Palette.Cyan.copy(alpha = 0.14f)))
                else Brush.linearGradient(listOf(Palette.fill5, Palette.fill5))
            )
            .border(
                1.dp,
                if (selected) Brush.linearGradient(Palette.brandColors)
                else Brush.linearGradient(listOf(Palette.hair1, Palette.hair1)),
                shape,
            )
            .clickable { onClick() }
            .padding(13.dp),
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) Palette.brand else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(framework.mono, style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
        Spacer(Modifier.height(9.dp))
        Text(framework.label(), style = MaterialTheme.typography.titleSmall, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text(framework.desc(), style = MaterialTheme.typography.bodySmall, color = Palette.Sub, minLines = 2, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.Faint)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Palette.Primary,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = Palette.hair1,
                uncheckedThumbColor = Palette.Faint,
                uncheckedBorderColor = Palette.hair2,
            ),
        )
    }
}
