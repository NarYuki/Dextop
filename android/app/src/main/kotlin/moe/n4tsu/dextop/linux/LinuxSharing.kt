package moe.n4tsu.dextop.linux

import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Explicit grants belong to one container. Old global Android grants are never inherited. */
internal object LinuxSharing {
    val kinds = setOf("wholeStorage", "camera", "microphone")
    fun normalize(value: JSONObject) = JSONObject().apply {
        kinds.forEach { put(it, value.optBoolean(it, false)) }
    }
    fun read(id: String): JSONObject = read(LinuxPaths.container(id))
    fun read(directory: File): JSONObject {
        return runCatching { normalize(JSONObject(File(directory, "sharing.json").readText())) }
            .getOrElse { normalize(JSONObject()) }
    }
    fun write(id: String, value: JSONObject) = write(LinuxPaths.container(id), value)
    @Synchronized fun write(directory: File, value: JSONObject) {
        val target = File(directory, "sharing.json")
        val staged = File.createTempFile("sharing-", ".tmp", directory)
        try {
            staged.outputStream().use { output ->
                output.write(normalize(value).toString().toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { staged.delete() }
    }
    fun allowed(id: String, kind: String) = read(id).optBoolean(kind, false)
    fun remote(id: String): JSONObject = runCatching {
        JSONObject(LinuxContainerClient.service()?.containerSharing(id) ?: "{}")
    }.getOrElse { JSONObject() }

    fun wholeStorageEnabled(context: android.content.Context, policy: JSONObject): Boolean =
        policy.optBoolean("wholeStorage") &&
            (!LinuxExecutionMode.android(context) || android.os.Environment.isExternalStorageManager())
}
