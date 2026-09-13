package com.promptforge.core

/**
 * Assembles a structured, professional prompt from a [PromptDraft].
 * Supports Arabic, English or bilingual section headings.
 */
object PromptAssembler {

    fun assemble(draft: PromptDraft): String {
        val lang = draft.config.lang
        val sb = StringBuilder()

        val title = draft.title.trim()
        if (title.isNotEmpty()) sb.append("# $title\n\n")

        // 1) Framework sections
        for (section in draft.framework.sections) {
            val value = draft.fields[section.key]?.trim().orEmpty()
            if (value.isEmpty()) continue
            sb.append(header(lang, "## ", section))
            sb.append(value).append("\n\n")
        }

        // 1.5) Custom sections (unlimited)
        for (cs in draft.customSections) {
            if (cs.title.isBlank() && cs.content.isBlank()) continue
            val title = cs.title.trim().ifBlank {
                if (lang == PromptLang.en) "Additional section" else "قسم إضافي"
            }
            sb.append("## ").append(title).append("\n")
            val content = cs.content.trim()
            if (content.isNotEmpty()) sb.append(content).append("\n")
            sb.append("\n")
        }

        // 2) No-limits completeness directives
        if (draft.config.unrestricted) {
            sb.append(header(lang, "## ", "توجيهات التنفيذ", "Execution directives"))
            val lines = if (lang == PromptLang.en) listOf(
                "Carry out every request above in full — no omissions, no summarizing, no shortening.",
                "For long outputs, deliver the maximum possible now; when asked to continue, resume exactly where you stopped.",
                "No meta-commentary — go straight to executing what is requested.",
            ) else listOf(
                "نفّذ كل ما هو مطلوب أعلاه كاملاً: دون حذف أي نقطة، دون تلخيص، دون اختصار.",
                "إن كان الناتج طويلاً، أعطِ الحد الأقصى الممكن في الرد الواحد، وعند طلب الاستمرار أكمل من نفس النقطة التي توقفت عنها تماماً.",
                "تجنّب الكلام التعريفى والحواشي؛ اذهب مباشرة إلى تنفيذ المطلوب.",
            )
            lines.forEach { sb.append("- ").append(it).append("\n") }
            sb.append("\n")
        }

        // 2.5) Research & sources directives
        if (draft.config.research) {
            sb.append(header(lang, "## ", "البحث والمصادر", "Research & sources"))
            val lines = if (lang == PromptLang.en) listOf(
                "Before answering, research the topic and rely on the most current, verifiable information available to you.",
                "Cite every substantive claim: [n] Source name — URL — publish date.",
                "Prefer primary and official sources; note each source's publish date.",
                "Clearly label every statement: ✅ verified fact / 🟡 inference / ⚠️ assumption.",
                "End with a numbered references list and a confidence note (high/medium/low) per source.",
                "Never fabricate sources, links or dates — if you cannot verify something, say so explicitly.",
            ) else listOf(
                "قبل الإجابة، ابحث في الموضوع واعتمد على أحدث المعلومات القابلة للتحقق المتاحة لديك.",
                "وثّق كل معلومة جوهرية بمصدرها: [رقم] اسم المصدر — الرابط — تاريخ النشر.",
                "فضّل المصادر الأولية والرسمية، واذكر تاريخ نشر كل مصدر.",
                "ميّز بوضوح كل عبارة: ✅ حقيقة موثقة / 🟡 استنتاج / ⚠️ فرضية.",
                "اختم بقائمة مراجع مرقمة مع ملاحظة ثقة (عالية/متوسطة/منخفضة) لكل مصدر.",
                "لا تختلق مصادر أو روابط أو تواريخ أبداً — إن لم تستطع التحقق فقل ذلك صراحة.",
            )
            lines.forEach { sb.append("- ").append(it).append("\n") }
            sb.append("\n")
        }

        // 2.6) Creativity boost — professional creative-craft directives
        if (draft.config.creative) {
            sb.append(header(lang, "## ", "تعزيز الإبداع", "Creativity boost"))
            val lines = if (lang == PromptLang.en) listOf(
                "Bring genuine originality: open with the least obvious angle, never the first idea that comes to mind.",
                "Use vivid, concrete sensory imagery and bold yet precise metaphors; avoid clichés and stock phrases entirely.",
                "Vary sentence rhythm and structure — mix short punches with long flowing lines; never open two sentences the same way.",
                "Prefer strong active verbs; cut filler adverbs and hollow intensifiers.",
                "Add one memorable twist, insight or emotional beat the reader will not see coming.",
                "Stay faithful to the requested task, facts and format — creativity must elevate the output, never distort it.",
            ) else listOf(
                "أظهر أصالة حقيقية: ابدأ بأقل الزوايا وضوحاً، وليس بأول فكرة تخطر للعقل.",
                "استخدم صوراً حسية حية ومحددة، وتشبيهات جريئة لكن دقيقة؛ وتجنّب الكليشيهات والعبارات المستهلكة تماماً.",
                "نوّع إيقاع الجُمل وبنيتها — اخلط الجمل القصيرة الحادة بالطويلة المنسابة، ولا تفتح جملتين بالطريقة نفسها.",
                "فضّل الأفعال القوية النشطة، واحذف الظروف الحشوية والمكثّفات الفارغة.",
                "أضف لمسة واحدة لا تُنسى: انعطافة أو رؤية أو إحساس لا يتوقعه القارئ.",
                "ابقَ وفياً للمطلوب من مهمة وحقائق وصيغة — الإبداع يرفع النتيجة ولا يشوّهها.",
            )
            lines.forEach { sb.append("- ").append(it).append("\n") }
            sb.append("\n")
        }

        // 3) Few-shot placeholder block
        if (draft.config.fewShot) {
            sb.append(header(lang, "## ", "أمثلة (Few-shot)", "Examples (few-shot)"))
            sb.append(
                when (lang) {
                    PromptLang.ar -> "- مثال 1: <المدخل> → <المخرج المتوقع>\n- مثال 2: <المدخل> → <المخرج المتوقع>\n"
                    PromptLang.en -> "- Example 1: <input> → <expected output>\n- Example 2: <input> → <expected output>\n"
                    PromptLang.both -> "- مثال 1 / Example 1: <input> → <expected output>\n"
                }
            ).append("\n")
        }

        // 3) Output rules
        val rules = outputRules(draft.config)
        if (rules.isNotEmpty()) {
            sb.append(header(lang, "## ", "قواعد الإخراج", "Output rules"))
            rules.forEach { sb.append("- ").append(it).append("\n") }
            sb.append("\n")
        }

        // 4) Working method
        val method = methodRules(draft.config)
        if (method.isNotEmpty()) {
            sb.append(header(lang, "## ", "طريقة العمل", "Working method"))
            method.forEach { sb.append("- ").append(it).append("\n") }
            sb.append("\n")
        }

        return sb.toString().trim()
    }

    data class Parsed(
        val fields: Map<String, String>,
        val customSections: List<CustomSection>,
    )

    /**
     * Reverse-parse an assembled prompt back into framework fields and
     * custom sections, enabling full round-trip editing of saved prompts.
     */
    fun parse(text: String, framework: Framework): Parsed {
        val skipHeadings = setOf(
            "أمثلة (Few-shot)", "قواعد الإخراج", "طريقة العمل", "توجيهات التنفيذ",
            "البحث والمصادر", "Research & sources",
            "Examples (few-shot)", "Output rules", "Working method", "Execution directives",
        )
        val fields = mutableMapOf<String, String>()
        val customs = mutableListOf<CustomSection>()
        var section: PromptSection? = null
        var customTitle: String? = null
        val buffer = StringBuilder()

        fun flush() {
            val value = buffer.toString().trim()
            buffer.clear()
            val s = section
            val ct = customTitle
            when {
                s != null && value.isNotEmpty() -> fields[s.key] = value
                ct != null && value.isNotEmpty() -> customs += CustomSection(ct, value)
            }
        }

        text.lines().forEach { raw ->
            val line = raw.trimEnd()
            val heading = when {
                line.startsWith("## ") -> line.removePrefix("## ").trim()
                line.startsWith("# ") -> "" // document title — skip
                else -> null
            }
            if (heading != null) {
                flush()
                val arPart = heading.substringBefore(" / ").trim()
                val match = framework.sections.firstOrNull {
                    it.arLabel == heading || it.enLabel == heading ||
                        it.arLabel == arPart || it.enLabel == arPart
                }
                when {
                    match != null && heading !in skipHeadings && arPart !in skipHeadings -> {
                        section = match; customTitle = null
                    }
                    heading in skipHeadings || arPart in skipHeadings -> {
                        section = null; customTitle = null
                    }
                    else -> {
                        section = null; customTitle = heading
                    }
                }
            } else if ((section != null || customTitle != null) && line.isNotBlank()) {
                buffer.appendLine(line)
            }
        }
        flush()
        return Parsed(fields, customs)
    }

    private fun header(lang: PromptLang, prefix: String, section: PromptSection): String =
        when (lang) {
            PromptLang.ar -> "$prefix${section.arLabel}\n"
            PromptLang.en -> "$prefix${section.enLabel}\n"
            PromptLang.both -> "$prefix${section.arLabel} / ${section.enLabel}\n"
        }

    private fun header(lang: PromptLang, prefix: String, ar: String, en: String): String =
        when (lang) {
            PromptLang.ar -> "$prefix$ar\n"
            PromptLang.en -> "$prefix$en\n"
            PromptLang.both -> "$prefix$ar / $en\n"
        }

    private fun outputRules(c: PromptConfig): List<String> {
        val out = mutableListOf<String>()
        when (c.lang) {
            PromptLang.ar -> {
                out += when (c.tone) {
                    Tone.professional -> "اكتب بنبرة احترافية رصينة."
                    Tone.friendly -> "اكتب بنبرة ودّية قريبة."
                    Tone.persuasive -> "اكتب بنبرة إقناعية مدروسة."
                    Tone.academic -> "اكتب بنبرة أكاديمية موثقة."
                    Tone.humorous -> "اكتب بنبرة خفيفة ظريفة دون تكلّف."
                    Tone.direct -> "اذهب مباشرة إلى صلب الموضوع دون مقدمات."
                    Tone.empathetic -> "اكتب بنبرة متعاطفة ومتفهمة."
                    Tone.technical -> "اكتب بنبرة تقنية دقيقة المصطلحات."
                    Tone.storytelling -> "اسرد المحتوى بأسلوب قصصي جذاب."
                }
                out += when (c.format) {
                    OutputFormat.markdown -> "استخدم تنسيق Markdown (عناوين، قوائم، إبراز)."
                    OutputFormat.plain -> "أخرج نصاً عادياً بفقرات واضحة دون تنسيق خاص."
                    OutputFormat.json -> "أخرج JSON صالحاً فقط دون أي نص إضافي."
                    OutputFormat.table -> "نظّم الناتج في جدول واضح الأعمدة."
                    OutputFormat.steps -> "رقّم الخطوات بالترتيب 1، 2، 3…"
                    OutputFormat.bullets -> "استخدم نقاطاً موجزة لكل فكرة."
                    OutputFormat.code -> "قدّم الكود أولاً في كتلة كود ثم شرحاً موجزاً."
                    OutputFormat.email -> "صِغ الناتج كرسالة بريد إلكتروني بعنوان وتحية وخاتمة."
                }
                out += when (c.detail) {
                    DetailLevel.concise -> "كن مقتضباً: لا تتجاوز اللازم، وركّز على الجوهر."
                    DetailLevel.balanced -> "وازن بين الإيجاز والتغطية الكافية."
                    DetailLevel.detailed -> "فصّل الإجابة بشرح وافٍ وأمثلة عند الحاجة."
                    DetailLevel.exhaustive -> "غطِّ الموضوع تغطية شاملة بكل جوانبه وحالاته."
                }
            }
            PromptLang.en -> {
                out += when (c.tone) {
                    Tone.professional -> "Write in a professional, polished tone."
                    Tone.friendly -> "Write in a warm, friendly tone."
                    Tone.persuasive -> "Write in a persuasive, well-argued tone."
                    Tone.academic -> "Write in an academic, well-cited tone."
                    Tone.humorous -> "Write with light, natural humor."
                    Tone.direct -> "Get straight to the point with no preamble."
                    Tone.empathetic -> "Write with an empathetic, understanding tone."
                    Tone.technical -> "Write with precise technical terminology."
                    Tone.storytelling -> "Tell it as an engaging story."
                }
                out += when (c.format) {
                    OutputFormat.markdown -> "Use Markdown formatting (headings, lists, emphasis)."
                    OutputFormat.plain -> "Output plain paragraphs with no special formatting."
                    OutputFormat.json -> "Output valid JSON only, no extra prose."
                    OutputFormat.table -> "Organize the output into a clear table."
                    OutputFormat.steps -> "Number the steps in order: 1, 2, 3…"
                    OutputFormat.bullets -> "Use concise bullets, one idea per bullet."
                    OutputFormat.code -> "Provide the code first in a code block, then a brief explanation."
                    OutputFormat.email -> "Format as an email with subject, greeting and sign-off."
                }
                out += when (c.detail) {
                    DetailLevel.concise -> "Be concise: never pad, keep to the essence."
                    DetailLevel.balanced -> "Balance brevity with sufficient coverage."
                    DetailLevel.detailed -> "Answer in detail with thorough explanations and examples."
                    DetailLevel.exhaustive -> "Cover the topic exhaustively, all angles and cases."
                }
            }
            PromptLang.both -> {
                out += "اكتب بنبرة ${toneAr(c.tone)} / Write in a ${c.tone.name} tone."
                out += "التزم بصيغة ${formatAr(c.format)} / Follow the ${c.format.name} format."
                out += "المستوى: ${detailAr(c.detail)} / Detail level: ${c.detail.name}."
            }
        }
        return out
    }

    private fun methodRules(c: PromptConfig): List<String> {
        val out = mutableListOf<String>()
        val ar = c.lang != PromptLang.en
        if (c.chainOfThought) {
            out += if (ar) "فكّر خطوة بخطوة داخلياً قبل صياغة الإجابة النهائية، ثم قدّم الناتج النهائي فقط."
            else "Reason step by step internally before writing the final answer, then output only the final result."
        }
        if (c.askClarifications) {
            out += if (ar) "إن كان أي جزء من المهمة غامضاً، اطرح أسئلة توضيحية قصيرة أولاً قبل البدء."
            else "If any part of the task is ambiguous, ask brief clarifying questions first."
        }
        out += if (ar) "إن نقصت معلومة جوهرية ولم تستطع السؤال، نبّه إلى افتراضاتك بوضوح."
        else "If essential information is missing and you cannot ask, state your assumptions clearly."
        return out
    }

    private fun toneAr(t: Tone) = when (t) {
        Tone.professional -> "احترافية"
        Tone.friendly -> "ودّية"
        Tone.persuasive -> "إقناعية"
        Tone.academic -> "أكاديمية"
        Tone.humorous -> "طريفة"
        Tone.direct -> "مباشرة"
        Tone.empathetic -> "متعاطفة"
        Tone.technical -> "تقنية"
        Tone.storytelling -> "قصصية"
    }

    private fun formatAr(f: OutputFormat) = when (f) {
        OutputFormat.markdown -> "Markdown"
        OutputFormat.plain -> "نص عادي"
        OutputFormat.json -> "JSON"
        OutputFormat.table -> "جدول"
        OutputFormat.steps -> "خطوات مرقمة"
        OutputFormat.bullets -> "نقاط"
        OutputFormat.code -> "كود + شرح"
        OutputFormat.email -> "بريد إلكتروني"
    }

    private fun detailAr(d: DetailLevel) = when (d) {
        DetailLevel.concise -> "موجز"
        DetailLevel.balanced -> "متوازن"
        DetailLevel.detailed -> "مفصّل"
        DetailLevel.exhaustive -> "شامل جداً"
    }
}
