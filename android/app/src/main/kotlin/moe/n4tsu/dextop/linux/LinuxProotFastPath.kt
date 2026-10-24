package moe.n4tsu.dextop.linux

import java.io.File
import java.security.MessageDigest

/** The host seccomp trampoline and guest path translator must agree on the same mounts. */
internal data class LinuxProotFastPath(val key: String, val root: String, val binds: List<String>) {
    val guestEnvironment get() = listOf("PROOT_FP_ROOT=$root", "PROOT_FP_BINDS=${binds.joinToString("|")}", "PROOT_FP_KEY=$key")
    companion object {
        const val LIBRARY = "/usr/local/lib/libblfastpath.so"
        fun create(root: File, binds: List<String>): LinuxProotFastPath? {
            if (binds.size > 256 || binds.any { '|' in it || '\n' in it || '\u0000' in it } ||
                root.path.any { it == '|' || it == '\n' || it == '\u0000' }) return null
            val digest = MessageDigest.getInstance("SHA-256").digest((root.path + "\n" + binds.joinToString("\n")).toByteArray())
            return LinuxProotFastPath(digest.take(12).joinToString("") { "%02x".format(it) }, root.path, binds.toList())
        }
    }
}
