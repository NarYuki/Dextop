package moe.n4tsu.dextop.linux

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** A measurement is separate from config.json so a scan cannot overwrite concurrent settings. */
internal object LinuxContainerSizeSnapshot {
    private fun file(directory: File) = File(directory, "last-size-bytes")

    fun read(directory: File, fallback: Long): Long = runCatching {
        file(directory).readText().trim().toLong().takeIf { it >= 0 } ?: fallback
    }.getOrDefault(fallback)

    fun write(directory: File, bytes: Long) {
        require(bytes >= 0)
        val target = file(directory)
        val temp = File(directory, "last-size-bytes.tmp")
        temp.outputStream().use { stream ->
            stream.write(bytes.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
