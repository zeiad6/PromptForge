package com.promptforge.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.promptforge.R
import com.promptforge.core.DetailLevel
import com.promptforge.core.Framework
import com.promptforge.core.OutputFormat
import com.promptforge.core.PromptLang
import com.promptforge.core.Tone
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun Tone.label(): String = stringResource(
    when (this) {
        Tone.professional -> R.string.tone_professional
        Tone.friendly -> R.string.tone_friendly
        Tone.persuasive -> R.string.tone_persuasive
        Tone.academic -> R.string.tone_academic
        Tone.humorous -> R.string.tone_humorous
        Tone.direct -> R.string.tone_direct
        Tone.empathetic -> R.string.tone_empathetic
        Tone.technical -> R.string.tone_technical
        Tone.storytelling -> R.string.tone_storytelling
    }
)

@Composable
fun OutputFormat.label(): String = stringResource(
    when (this) {
        OutputFormat.markdown -> R.string.format_markdown
        OutputFormat.plain -> R.string.format_plain
        OutputFormat.json -> R.string.format_json
        OutputFormat.table -> R.string.format_table
        OutputFormat.steps -> R.string.format_steps
        OutputFormat.bullets -> R.string.format_bullets
        OutputFormat.code -> R.string.format_code
        OutputFormat.email -> R.string.format_email
    }
)

@Composable
fun PromptLang.label(): String = stringResource(
    when (this) {
        PromptLang.ar -> R.string.lang_arabic
        PromptLang.en -> R.string.lang_english
        PromptLang.both -> R.string.lang_both
    }
)

@Composable
fun DetailLevel.label(): String = stringResource(
    when (this) {
        DetailLevel.concise -> R.string.detail_concise
        DetailLevel.balanced -> R.string.detail_balanced
        DetailLevel.detailed -> R.string.detail_detailed
        DetailLevel.exhaustive -> R.string.detail_exhaustive
    }
)

@Composable
fun Framework.label(): String {
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current !=
        androidx.compose.ui.unit.LayoutDirection.Ltr
    return if (rtl) ar else en
}

@Composable
fun Framework.desc(): String {
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current !=
        androidx.compose.ui.unit.LayoutDirection.Ltr
    return if (rtl) descAr else descEn
}

@Composable
fun com.promptforge.core.PromptSection.label(): String {
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current !=
        androidx.compose.ui.unit.LayoutDirection.Ltr
    return if (rtl) arLabel else enLabel
}

@Composable
fun com.promptforge.core.PromptSection.hint(): String {
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current !=
        androidx.compose.ui.unit.LayoutDirection.Ltr
    return if (rtl) arHint else enHint
}

fun formatDate(ts: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(ts))

fun greetingKey(hour: Int): Int = when (hour) {
    in 5..11 -> R.string.greeting_morning
    in 12..16 -> R.string.greeting_afternoon
    in 17..21 -> R.string.greeting_evening
    else -> R.string.greeting_night
}

/** Maps a framework-section key to its icon drawable. */
fun sectionIcon(key: String): Int = when (key) {
    "role", "capacity", "audience" -> R.drawable.ic_user
    "task", "objective", "instructions", "statement" -> R.drawable.ic_target
    "context", "insight" -> R.drawable.ic_globe
    "constraints" -> R.drawable.ic_shield
    "style" -> R.drawable.ic_pencil
    "tone", "personality" -> R.drawable.ic_flame
    "response", "format", "steps", "goal", "expectation" -> R.drawable.ic_layers
    "experiment" -> R.drawable.ic_sparkles
    "action", "purpose" -> R.drawable.ic_zap
    else -> R.drawable.ic_target
}

/** Maps a template category to its icon drawable. */
fun categoryIcon(category: String): Int = when (category) {
    "writing" -> R.drawable.ic_pen
    "dev" -> R.drawable.ic_code
    "business" -> R.drawable.ic_briefcase
    "edu" -> R.drawable.ic_graduation
    "marketing" -> R.drawable.ic_megaphone
    "agents" -> R.drawable.ic_bot
    "analysis" -> R.drawable.ic_trending
    "media" -> R.drawable.ic_clapperboard
    else -> R.drawable.ic_target
}
