package com.promptforge.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

@Serializable
data class DeviceModel(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    /** Optional user-defined display name (rename without touching the file). */
    val label: String? = null,
    /** Output budget — user-tunable in the Model Editor (no hard limits). */
    val maxTokens: Int = 2048,
    /** Sampling diversity (top-K). */
    val topK: Int = 40,
    /** auto | gpu | cpu */
    val backend: String = "auto",
) {
    fun displayName(): String = label?.takeIf { it.isNotBlank() } ?: name
}

/**
 * Registry of on-device model files (imported via the file picker or
 * downloaded in-app). Persisted as JSON in app-private storage.
 * Every read is crash-proof: missing or corrupt state simply reads as empty.
 */
class DeviceModelRegistry(context: Context, private val json: Json) {

    private val dir = File(context.filesDir, "device_models").apply { mkdirs() }
    private val file = File(context.filesDir, "device_models.json")
    private val codec = ListSerializer(DeviceModel.serializer())

    fun all(): List<DeviceModel> = runCatching {
        if (!file.exists()) emptyList()
        else json.decodeFromString(codec, file.readText())
            .filter { File(it.path).exists() }
    }.getOrElse { emptyList() }

    fun find(name: String): DeviceModel? = all().firstOrNull { it.name == name }

    /** Storage dir for model files (used by the in-app downloader). */
    fun storageDir(): File = dir

    /** Final destination for a catalog file, and its in-progress .part file. */
    fun fileFor(fileName: String): File = File(dir, fileName)
    fun partialFor(fileName: String): File = File(dir, "$fileName.part")

    /** Adopts a fully downloaded file that already lives in our storage dir. */
    fun adopt(final: File, displayName: String): DeviceModel {
        val model = DeviceModel(
            name = displayName.substringBeforeLast('.'),
            path = final.absolutePath,
            sizeBytes = final.length(),
        )
        save(all().filter { it.name != model.name } + model)
        return model
    }

    /** Copies the picked content URI into app storage and registers it. */
    fun import(context: Context, uri: android.net.Uri, displayName: String): DeviceModel {
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifEmpty { "model.task" }
        var dest = File(dir, safeName)
        if (dest.exists()) {
            dest = File(dir, safeName.substringBeforeLast('.') + "_" + System.currentTimeMillis() +
                "." + safeName.substringAfterLast('.', "task"))
        }
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("cannot_open_uri")
        input.use { i -> dest.outputStream().use { i.copyTo(it) } }
        return adopt(dest, dest.name)
    }

    /** Persists user edits (rename / maxTokens / topK / backend). */
    fun update(model: DeviceModel) {
        save(all().filter { it.name != model.name } + model)
    }

    fun remove(name: String) {
        all().firstOrNull { it.name == name }?.let { File(it.path).delete() }
        save(all().filter { it.name != name })
    }

    private fun save(list: List<DeviceModel>) {
        runCatching { file.writeText(json.encodeToString(codec, list)) }
    }
}
