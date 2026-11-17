package moe.n4tsu.dextop.linux

import java.io.File
import java.security.MessageDigest

internal enum class LinuxFastMode(val id: String, val library: String, val resource: String) {
    PROTECTED("protected", "/usr/local/lib/libblfastpath.so", "libblfastpath-aarch64.bin"),
    UNPROTECTED("unprotected", "/usr/local/lib/libblfastpath-unprotected.so", "libblfastpath-unprotected-aarch64.bin");
    companion object {
        fun from(value: String?) = entries.firstOrNull { it.id == value } ?: PROTECTED
    }
}

/** The host seccomp trampoline and guest path translator must agree on the same mounts. */
internal data class LinuxProotFastPath(val key: String, val root: String, val binds: List<String>, val landlockPaths: List<String>, val mode: LinuxFastMode = LinuxFastMode.PROTECTED) {
    val guestEnvironment get() = listOf("PROOT_FP_ROOT=$root", "PROOT_FP_BINDS=${binds.joinToString("|")}", "PROOT_FP_KEY=$key")
    companion object {
        const val LIBRARY = "/usr/local/lib/libblfastpath.so"
        fun stage(root: File, mode: LinuxFastMode = LinuxFastMode.PROTECTED): Boolean = runCatching {
            val bytes = checkNotNull(LinuxProotFastPath::class.java.getResourceAsStream(
                "/moe/n4tsu/dextop/linux/gaming/${mode.resource}")).use { it.readBytes() }
            val target = File(root, mode.library.removePrefix("/"))
            target.parentFile?.mkdirs()
            if (!target.isFile || !target.readBytes().contentEquals(bytes)) {
                val pending = File.createTempFile(".landlock-", ".staged", target.parentFile)
                try {
                    pending.writeBytes(bytes)
                    check(pending.setReadable(true, false))
                    java.nio.file.Files.move(pending.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                } finally { pending.delete() }
            }
            true
        }.getOrDefault(false)

        fun create(root: File, binds: List<String>, nativeLibraryDir: String, mode: LinuxFastMode = LinuxFastMode.PROTECTED): LinuxProotFastPath? {
            if (binds.size > 256 || binds.any { '|' in it || '\n' in it || '\u0000' in it } ||
                root.path.any { it == '|' || it == '\n' || it == '\u0000' }) return null
            // No ambiguous PRoot bind suffixes or relative sources. Fail closed.
            if (binds.any { spec ->
                val parts = spec.split(':')
                parts.size > 2 || (mode == LinuxFastMode.PROTECTED && parts.last() == "/") || parts.any { path ->
                    !path.startsWith("/") || path.endsWith("!") ||
                        (path != "/" && path.split('/').drop(1).any { it.isEmpty() || it == "." || it == ".." })
                }
            }) return null
            val paths = if (mode == LinuxFastMode.UNPROTECTED) emptyList() else runCatching {
                (listOf(root.path, nativeLibraryDir) + binds.map { it.substringBefore(':') })
                    .map { File(it).canonicalPath }.distinct()
            }.getOrNull() ?: return null
            if (paths.any { it == "/" || '\n' in it || !File(it).exists() }) return null
            val digest = MessageDigest.getInstance("SHA-256").digest((root.path + "\n" + binds.joinToString("\n")).toByteArray())
            return LinuxProotFastPath(digest.take(12).joinToString("") { "%02x".format(it) }, root.path, binds.toList(), paths, mode)
        }
    }
}
