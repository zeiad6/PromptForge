package com.promptforge.data

import android.content.Context
import com.promptforge.core.SavedPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Local-first prompt library persisted as a single JSON file
 * (atomic writes, in-memory StateFlow mirror).
 */
class PromptRepository(private val context: Context, private val json: Json) {

    private val mutex = Mutex()
    private val _prompts = MutableStateFlow<List<SavedPrompt>>(emptyList())
    val prompts: StateFlow<List<SavedPrompt>> = _prompts.asStateFlow()

    private fun storeFile(): File = File(context.filesDir, "prompts.json")

    suspend fun load() = withContext(Dispatchers.IO) {
        val file = storeFile()
        if (!file.exists()) return@withContext
        runCatching {
            val list = json.decodeFromString(ListSerializer(SavedPrompt.serializer()), file.readText())
            _prompts.value = list
        }
    }

    suspend fun seedIfEmpty() {
        mutex.withLock {
            if (_prompts.value.isNotEmpty()) return
            val now = System.currentTimeMillis()
            val starters = listOf(
                starter(
                    title = "وكيل الكتابة الاحترافي",
                    body = """# وكيل الكتابة الاحترافي

## الدور / Role
أنت كاتب محتوى عربي محترف بخبرة ١٥ عاماً في الصناعة والصحافة.

## المهمة / Task
اكتب لي المحتوى الذي أطلبه لاحقاً بجودة نشر فعلية.

## قواعد الإخراج
- اكتب بنبرة احترافية رصينة.
- استخدم تنسيق Markdown (عناوين، قوائم، إبراز).
- وازن بين الإيجاز والتغطية الكافية.

## طريقة العمل
- فكّر خطوة بخطوة داخلياً قبل صياغة الإجابة النهائية، ثم قدّم الناتج النهائي فقط.
- إن نقصت معلومة جوهرية ولم تستطع السؤال، نبّه إلى افتراضاتك بوضوح.""",
                    score = 78,
                ),
                starter(
                    title = "مراجع الأكواد الصارم",
                    body = """# مراجع الأكواد الصارم

## Role
You are a Staff Engineer specialized in code review: security, performance, readability and maintainability.

## Task
Review the code I paste next and flag bugs, security issues and perf problems.

## Output rules
- Order findings by severity (critical / medium / improvement).
- Provide a concrete fix snippet for each finding.
- End with the top 3 actions by priority.""",
                    score = 84,
                ),
                starter(
                    title = "مخطط الرحلات",
                    body = """# مخطط الرحلات

## الدور (Role)
مخطط رحلات سياحي خبير يعرف كيف يوازن بين المتعة والراحة والميزانية.

## المهمة (Task)
صمّم لي برنامج رحلة إلى: {{الوجهة}} لمدة {{عدد الأيام}} أيام.

## السياق (Context)
عدد المسافرين: {{العدد}}. الميزانية اليومية: {{الميزانية}}. الاهتمامات: {{الاهتمامات}}.

## قواعد الإخراج
- نظّم البرنامج في جدول يومي: الصباح، الظهيرة، المساء.
- اذكر تقدير تكلفة كل نشاط وبدائل أرخص.
- رتّب المعالم جغرافياً لتقليل التنقل.""",
                    score = 92,
                ),
            )
            _prompts.value = starters
            persistLocked()
        }
    }

    private fun starter(title: String, body: String, score: Int): SavedPrompt {
        val now = System.currentTimeMillis()
        return SavedPrompt(
            id = newId(), title = title, body = body,
            frameworkId = "balanced", tags = emptyList(),
            score = score, favorite = title.startsWith("مخطط"),
            createdAt = now, updatedAt = now,
        )
    }

    suspend fun upsert(prompt: SavedPrompt) = mutex.withLock {
        val list = _prompts.value.toMutableList()
        val idx = list.indexOfFirst { it.id == prompt.id }
        if (idx >= 0) list[idx] = prompt else list.add(0, prompt)
        _prompts.value = list
        persistLocked()
    }

    suspend fun delete(id: String) = mutex.withLock {
        _prompts.value = _prompts.value.filterNot { it.id == id }
        persistLocked()
    }

    suspend fun toggleFavorite(id: String) = mutex.withLock {
        _prompts.value = _prompts.value.map {
            if (it.id == id) it.copy(favorite = !it.favorite) else it
        }
        persistLocked()
    }

    suspend fun incrementUsage(id: String) = mutex.withLock {
        _prompts.value = _prompts.value.map {
            if (it.id == id) it.copy(usedCount = it.usedCount + 1) else it
        }
        persistLocked()
    }

    suspend fun clearAll() = mutex.withLock {
        _prompts.value = emptyList()
        persistLocked()
    }

    fun exportJson(): String =
        json.encodeToString(ListSerializer(SavedPrompt.serializer()), _prompts.value)

    private suspend fun persistLocked() = withContext(Dispatchers.IO) {
        runCatching {
            val file = storeFile()
            val tmp = File(file.parentFile, "prompts.json.tmp")
            tmp.writeText(json.encodeToString(ListSerializer(SavedPrompt.serializer()), _prompts.value))
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
