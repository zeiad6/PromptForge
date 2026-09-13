package com.promptforge.core

/**
 * Heuristic prompt-quality analyzer.
 * Scores a draft 0–100 and returns actionable, localized tips.
 */
object PromptScorer {

    enum class Severity { info, warn, critical }

    data class Tip(val key: String, val severity: Severity)
    data class Result(val score: Int, val tips: List<Tip>)

    private val vagueWords = listOf(
        "شيء ما", "شيء جميل", "كويس", "حلو", "خلابة", "أي شيء", "نحوه",
        "nice", "something", "stuff", "good stuff", "etc", "whatever", "cool",
    )

    fun score(draft: PromptDraft): Result {
        val tips = mutableListOf<Tip>()
        var score = 22

        val filled = draft.framework.sections.count { !draft.fields[it.key].isNullOrBlank() }
        val total = draft.framework.sections.size
        val has = { key: String -> !draft.fields[key].isNullOrBlank() }

        // Title (+8)
        if (draft.title.isNotBlank()) score += 8 else tips += Tip("tip_title_missing", Severity.info)

        // Role (+14)
        val roleKey = draft.framework.sections.firstOrNull {
            it.key in setOf("role", "capacity", "style")
        }?.key
        if (roleKey != null && has(roleKey)) score += 14
        else if (roleKey != null) tips += Tip("tip_role_missing", Severity.warn)

        // Task (+16)
        val taskKey = draft.framework.sections.firstOrNull {
            it.key in setOf("task", "objective", "instructions", "statement", "action")
        }?.key
        if (taskKey != null && has(taskKey)) score += 16
        else tips += Tip("tip_task_missing", Severity.critical)

        // Context (+12)
        val ctxKey = draft.framework.sections.firstOrNull {
            it.key in setOf("context", "insight")
        }?.key
        if (ctxKey != null && has(ctxKey)) score += 12
        else if (ctxKey != null) tips += Tip("tip_context_missing", Severity.warn)

        // Constraints (+10)
        val conKey = draft.framework.sections.firstOrNull {
            it.key in setOf("constraints", "response", "format", "expectation", "goal")
        }?.key
        if (conKey != null && has(conKey)) score += 10
        else tips += Tip("tip_constraints_missing", Severity.warn)

        // Audience (+6)
        if (has("audience")) score += 6
        else tips += Tip("tip_audience_missing", Severity.info)

        // Coverage bonus (up to +8): fraction of sections filled
        if (total > 0) score += ((filled.toFloat() / total) * 8f).toInt()

        // Length sweet spot (no-limits mode never penalizes length)
        val bodyLen = draft.fields.values.sumOf { it.trim().length } +
            draft.customSections.sumOf { it.content.length }
        when {
            bodyLen < 80 -> { score -= 8; tips += Tip("tip_too_short", Severity.warn) }
            bodyLen > 2400 && !draft.config.unrestricted -> { score -= 6; tips += Tip("tip_too_long", Severity.info) }
            else -> score += 4
        }

        // Custom sections reward (+2 each, capped +6)
        score += (draft.customSections.count { it.content.isNotBlank() } * 2).coerceAtMost(6)

        // No-limits completeness directives
        if (draft.config.unrestricted) score += 4

        // Research & sources directives
        if (draft.config.research) score += 6
        // Creativity boost (+5)
        if (draft.config.creative) score += 5

        // Specificity: digits / measurable criteria (+6)
        val hasNumbers = draft.fields.values.any { it.any { ch -> ch.isDigit() } }
        if (hasNumbers) score += 6 else tips += Tip("tip_add_numbers", Severity.info)

        // Vague words penalty (-10, dedup)
        val joined = draft.fields.values.joinToString(" ").lowercase()
        val vagueHit = vagueWords.any { joined.contains(it) }
        if (vagueHit) { score -= 10; tips += Tip("tip_vague_words", Severity.warn) }

        // Advanced options (+5 / +3)
        if (draft.config.chainOfThought) score += 5
        if (draft.config.askClarifications) score += 3

        val final = score.coerceIn(0, 100)

        // Praise when great
        val orderedTips = if (final >= 85 && tips.isEmpty()) listOf(Tip("tip_excellent", Severity.info)) else
            tips.sortedByDescending { it.severity.ordinal }

        return Result(final, orderedTips)
    }
}
