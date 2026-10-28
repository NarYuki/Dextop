package moe.n4tsu.dextop.linux

import java.io.File

/** Setup embeds binary resources and can exceed Linux's per-argument size limit. */
internal object LinuxSetupScript {
    fun <T> run(root: File, script: String, execute: (String) -> T): T {
        val directory = File(root, "tmp").apply { mkdirs() }
        val file = File.createTempFile("dextop-setup-", ".sh", directory)
        try {
            file.writeText(LinuxScript.text(script))
            // The random basename is generated here, never taken from user input.
            return execute("exec /bin/sh /tmp/${file.name}")
        } finally {
            file.delete()
        }
    }
}
