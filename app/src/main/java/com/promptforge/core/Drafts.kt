package com.promptforge.core

/**
 * Helpers to move between saved prompts and editable drafts.
 */
object Drafts {

    fun fromSaved(p: SavedPrompt): PromptDraft {
        val parsed = PromptAssembler.parse(p.body, Framework.byId(p.frameworkId))
        return PromptDraft(
            id = p.id,
            title = p.title,
            frameworkId = p.frameworkId,
            fields = parsed.fields,
            config = PromptConfig(),
            tags = p.tags,
            customSections = parsed.customSections,
        )
    }

    fun fromTemplate(t: TemplateItem): PromptDraft = PromptDraft(
        id = null,
        title = t.titleAr,
        frameworkId = t.frameworkId,
        fields = t.fields,
        config = PromptConfig(),
        tags = t.tags,
    )
}
