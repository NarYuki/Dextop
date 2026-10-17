package moe.n4tsu.dextop.linux

import java.io.File
import java.util.concurrent.TimeUnit

/** Filesystem layout under the shell-owned /data/local/tmp. */
internal object LinuxPaths {
    val base = File("/data/local/tmp/dextop/linux")
    val containers = File(base, "containers")
    /** PROOT_TMP_DIR; must be writable and executable by the shell user. */
    val prootTmp = File(base, ".tmp")

    fun container(id: String): File {
        require(id.matches(Regex("c[a-zA-Z0-9]+"))) { "Invalid container id" }
        return File(containers, id)
    }
    fun rootfs(id: String) = File(container(id), "rootfs")
    fun config(id: String) = File(container(id), "config.json")
    fun log(id: String) = File(container(id), "install.log")
}

/** Builds proot command lines from the APK's nativeLibraryDir. */
internal class LinuxProot(private val nativeLibraryDir: String) {
    private val proot = File(nativeLibraryDir, "libproot.so")
    private val loader = File(nativeLibraryDir, "libproot_loader.so")

    fun available(): Boolean = proot.canExecute() && loader.canRead()

    private fun environment(builder: ProcessBuilder): ProcessBuilder = builder.apply {
        LinuxPaths.prootTmp.mkdirs()
        environment().apply {
            put("PROOT_LOADER", loader.absolutePath)
            put("PROOT_TMP_DIR", LinuxPaths.prootTmp.absolutePath)
        }
    }

    /** Runs [arguments] on the host filesystem under proot (fake root, hard links as symlinks). */
    fun host(vararg arguments: String): ProcessBuilder = environment(
        ProcessBuilder(proot.absolutePath, "--kill-on-exit", "--link2symlink", "-0", "-r", "/", "-w", "/", *arguments)
    )

    /** Runs a /bin/sh script as root inside the container [id]. */
    fun guest(id: String, script: String): ProcessBuilder = environment(
        ProcessBuilder(
            proot.absolutePath,
            "--kill-on-exit", "--link2symlink", "-0",
            "-r", LinuxPaths.rootfs(id).absolutePath,
            "-w", "/root",
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "/usr/bin/env", "-i",
            "HOME=/root", "USER=root", "LOGNAME=root", "TERM=xterm-256color", "LANG=C.UTF-8",
            "TMPDIR=/tmp", "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "/bin/sh", "-c", script,
        )
    )

    data class Result(val exitCode: Int, val output: String)

    /** Runs [builder] to completion, streaming merged output lines to [onLine]. */
    fun run(builder: ProcessBuilder, timeoutSeconds: Long = 0, onProcess: ((Process) -> Unit)? = null, onLine: (String) -> Unit = {}): Result {
        val process = builder.redirectErrorStream(true).start()
        onProcess?.invoke(process)
        process.outputStream.close()
        val output = StringBuilder()
        val reader = Thread {
            process.inputStream.bufferedReader().forEachLine { line ->
                synchronized(output) { if (output.length < 64_000) output.appendLine(line) }
                onLine(line)
            }
        }.apply { isDaemon = true; start() }
        val finished = if (timeoutSeconds > 0) process.waitFor(timeoutSeconds, TimeUnit.SECONDS) else {
            process.waitFor(); true
        }
        if (!finished) process.destroyForcibly()
        reader.join(2_000)
        return Result(if (finished) process.exitValue() else -1, synchronized(output) { output.toString() })
    }
}
