package com.promptforge.core

import kotlinx.serialization.Serializable

/**
 * A single labeled field inside a prompt framework.
 */
@Serializable
data class PromptSection(
    val key: String,
    val arLabel: String,
    val enLabel: String,
    val arHint: String,
    val enHint: String,
    val icon: String = "target",
)

/**
 * Prompt engineering frameworks. Inspired by the best-known structures
 * (CO-STAR, RISEN, CRISPE, RTF, APE, RACE) plus a balanced default.
 */
enum class Framework(
    val id: String,
    val ar: String,
    val en: String,
    val mono: String,
    val descAr: String,
    val descEn: String,
    val sections: List<PromptSection>,
) {
    BALANCED(
        "balanced", "متوازن", "Balanced", "PB",
        "هيكل متوازن يناسب معظم المهام والوكلاء",
        "A balanced structure that fits most tasks and agents",
        listOf(
            PromptSection("role", "الدور (Role)", "Role", "من يكون الوكيل؟ خبير تسويق، محلل بيانات، كاتب محتوى…", "Who is the agent? A marketing expert, data analyst, writer…", "user"),
            PromptSection("task", "المهمة (Task)", "Task", "ما المطلوب بالضبط؟ صُغ مهمة واضحة وقابلة للتنفيذ", "What exactly is required? State a clear, actionable task", "target"),
            PromptSection("context", "السياق (Context)", "Context", "خلفية، بيانات، جمهور، أو أي معلومات تخص الموقف", "Background, data, audience, or any situational info", "globe"),
            PromptSection("constraints", "القيود والقواعد", "Constraints", "ما يجب فعله وتجنبه: الطول، الأسلوب، المصادر، الممنوعات…", "What to do and avoid: length, style, sources, taboos…", "shield"),
            PromptSection("audience", "الجمهور المستهدف", "Audience", "لمن سيُوجّه الناتج؟ مبتدئ، مدير تنفيذي، عميل…", "Who is the output for? Beginner, executive, customer…", "user"),
        ),
    ),
    COSTAR(
        "costar", "كو-ستار", "CO-STAR", "C★",
        "إطار سياقي شامل من مسابقة GPT-4 Singapore — ممتاز للمحتوى",
        "Context-heavy framework from the GPT-4 Singapore prompt contest",
        listOf(
            PromptSection("context", "السياق (Context)", "Context", "الخلفية الكاملة للمهمة والموقف", "Full background of the task and situation", "globe"),
            PromptSection("objective", "الهدف (Objective)", "Objective", "ما الذي تريد تحقيقه من هذا البرومبت؟", "What do you want to achieve with this prompt?", "target"),
            PromptSection("style", "الأسلوب (Style)", "Style", "أسلوب الكتابة المطلوب: تقني، صحفي، تسويقي…", "Required writing style: technical, journalistic, marketing…", "pencil"),
            PromptSection("tone", "النبرة (Tone)", "Tone", "الموقف العاطفي: واثق، متعاطف، حماسي…", "Emotional attitude: confident, empathetic, excited…", "flame"),
            PromptSection("audience", "الجمهور (Audience)", "Audience", "من سيقرأ الناتج؟", "Who will read the output?", "user"),
            PromptSection("response", "صيغة الإجابة (Response)", "Response", "الشكل المطلوب: جدول، نقاط، JSON…", "Required shape: table, bullets, JSON…", "layers"),
        ),
    ),
    RISEN(
        "risen", "رايزن", "RISEN", "R↑",
        "ممتاز للتعليمات المنظمة بخطوات وهدف نهائي واضح",
        "Great for structured step-by-step instructions with a clear end goal",
        listOf(
            PromptSection("role", "الدور (Role)", "Role", "شخصية الوكيل وخبرته", "The agent persona and expertise", "user"),
            PromptSection("instructions", "التعليمات (Instructions)", "Instructions", "التعليمات التفصيلية للمهمة", "Detailed task instructions", "target"),
            PromptSection("steps", "الخطوات (Steps)", "Steps", "خطوات واضحة يجب أن يتبعها الوكيل بالترتيب", "Clear steps the agent must follow in order", "layers"),
            PromptSection("goal", "الهدف النهائي (End goal)", "End goal", "ما الناتج النهائي المثالي؟", "What is the ideal final result?", "check"),
            PromptSection("constraints", "القيود (Narrowing)", "Constraints", "حدود الطول والأسلوب والممنوعات", "Length, style and limitation boundaries", "shield"),
        ),
    ),
    CRISPE(
        "crispe", "كريسب", "CRISPE", "C!",
        "يمنح النموذج شخصية واضحة ومساحة للاستكشاف — جيد للعصف الذهني",
        "Gives the model a clear persona and room to explore — good for brainstorming",
        listOf(
            PromptSection("capacity", "الدور والخبرة (Capacity)", "Capacity/Role", "ماذا يعمل الوكيل وما خبرته؟", "What the agent does and its expertise", "user"),
            PromptSection("insight", "الخلفية (Insight)", "Insight", "الخلفية والسياق الذي يحتاجه الوكيل", "Background insight the agent needs", "globe"),
            PromptSection("statement", "العبارة المطلوبة (Statement)", "Statement", "ما الذي تطلب منه فعله بالضبط؟", "What exactly do you ask it to do?", "target"),
            PromptSection("personality", "الشخصية (Personality)", "Personality", "شخصية الرد: عفوية، رسمية، محفزة…", "Reply personality: casual, formal, motivational…", "flame"),
            PromptSection("experiment", "البدائل (Experiment)", "Experiment", "اطلب عدة بدائل وطرق مختلفة للإجابة", "Ask for several alternatives and approaches", "sparkles"),
        ),
    ),
    RTF(
        "rtf", "آر تي إف", "RTF", "R/T",
        "الأبسط والأسرع: دور + مهمة + صيغة — للبرومبتات اليومية السريعة",
        "Simplest and fastest: Role + Task + Format — for quick daily prompts",
        listOf(
            PromptSection("role", "الدور (Role)", "Role", "من يكون الوكيل؟", "Who is the agent?", "user"),
            PromptSection("task", "المهمة (Task)", "Task", "ما المطلوب؟", "What is required?", "target"),
            PromptSection("format", "الصيغة (Format)", "Format", "شكل الناتج المطلوب", "Shape of the required output", "layers"),
        ),
    ),
    APE(
        "ape", "آب", "APE", "A/P",
        "فلسفة أقل، تنفيذ أكثر: فعل + غرض + توقع",
        "Less philosophy, more execution: Action + Purpose + Expectation",
        listOf(
            PromptSection("action", "الفعل (Action)", "Action", "ما الفعل المطلوب تحديداً؟", "What exact action is required?", "zap"),
            PromptSection("purpose", "الغرض (Purpose)", "Purpose", "لماذا ننفذها؟ ما الغاية؟", "Why are we doing it? What is the purpose?", "target"),
            PromptSection("expectation", "التوقع (Expectation)", "Expectation", "ماذا تتوقع أن يحصل عليه بالضبط؟", "What exactly do you expect to get?", "check"),
        ),
    ),
    RACE(
        "race", "ريس", "RACE", "R+C",
        "مناسب لوكلاء المهام: دور + فعل + سياق + توقع",
        "Fits task agents: Role + Action + Context + Expectation",
        listOf(
            PromptSection("role", "الدور (Role)", "Role", "شخصية الوكيل", "The agent persona", "user"),
            PromptSection("action", "الفعل (Action)", "Action", "المهمة المطلوبة", "The required action", "zap"),
            PromptSection("context", "السياق (Context)", "Context", "كل ما يخص الموقف", "Everything about the situation", "globe"),
            PromptSection("expectation", "التوقع (Expectation)", "Expectation", "شكل الناتج المتوقع", "The expected output shape", "check"),
        ),
    );

    companion object {
        fun byId(id: String): Framework = entries.firstOrNull { it.id == id } ?: BALANCED
    }
}

/** Tone of voice requested from the model. */
enum class Tone { professional, friendly, persuasive, academic, humorous, direct, empathetic, technical, storytelling }

/** Required output format. */
enum class OutputFormat { markdown, plain, json, table, steps, bullets, code, email }

/** Language the assembled prompt is written in. */
enum class PromptLang { ar, en, both }

/** How detailed the answer should be. */
enum class DetailLevel { concise, balanced, detailed, exhaustive }

/**
 * Output settings attached to a prompt.
 */
@Serializable
data class PromptConfig(
    val tone: Tone = Tone.professional,
    val format: OutputFormat = OutputFormat.markdown,
    val lang: PromptLang = PromptLang.ar,
    val detail: DetailLevel = DetailLevel.balanced,
    val chainOfThought: Boolean = true,
    val askClarifications: Boolean = false,
    val fewShot: Boolean = false,
    /** No-limits mode: full-compliance directives appended to the prompt. */
    val unrestricted: Boolean = false,
    /** Research & sources mode: citation and verification directives. */
    val research: Boolean = false,
    /** Creativity boost: professional creative-writing directives + divergent thinking. */
    val creative: Boolean = false,
)

/**
 * A user-defined section — unlimited custom sections per prompt.
 */
@Serializable
data class CustomSection(
    val title: String = "",
    val content: String = "",
)

/**
 * The editable draft behind the Builder screen.
 */
@Serializable
data class PromptDraft(
    val id: String? = null,
    val title: String = "",
    val frameworkId: String = Framework.BALANCED.id,
    val fields: Map<String, String> = emptyMap(),
    val config: PromptConfig = PromptConfig(),
    val tags: List<String> = emptyList(),
    val customSections: List<CustomSection> = emptyList(),
) {
    val framework: Framework get() = Framework.byId(frameworkId)
}

/**
 * A prompt persisted in the local library.
 */
@Serializable
data class SavedPrompt(
    val id: String,
    val title: String,
    val body: String,
    val frameworkId: String,
    val tags: List<String> = emptyList(),
    val score: Int = 0,
    val favorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val usedCount: Int = 0,
)
