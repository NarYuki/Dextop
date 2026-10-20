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
internal class LinuxProot(val nativeLibraryDir: String) {
    companion object {
        /** Where the APK's native libraries appear inside containers. */
        const val GUEST_LIBRARY_DIR = "/opt/dextop/lib"
        private const val SOCKET_SHIM = "libdextop_unixsock.so"
        private const val KGSL_DEVICE = "/dev/kgsl-3d0"

        /** Abstract socket namespace of a container (see linux_unixsock.c). */
        fun socketNamespace(id: String) = "dextop-$id"
    }

    private val proot = File(nativeLibraryDir, "libproot.so")
    private val loader = File(nativeLibraryDir, "libproot_loader.so")
    private val shim = File(nativeLibraryDir, SOCKET_SHIM)

    /** Extra NAME=value entries for container [id]'s processes (the renderer settings). */
    @Volatile var guestEnvironment: (String) -> List<String> = { emptyList() }

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

    private fun timeZone(id: String): String? = java.util.TimeZone.getDefault().id.takeIf { zone ->
        zone.matches(Regex("[A-Za-z0-9_+\\-]+(/[A-Za-z0-9_+\\-]+)*")) &&
            File(LinuxPaths.rootfs(id), "usr/share/zoneinfo/$zone").isFile
    }

    private val uid = android.system.Os.getuid()

    private fun sharedMemory(id: String): String = File(LinuxPaths.container(id), "shm").apply {
        mkdirs()
        setWritable(true, false)
    }.absolutePath

    private fun binds(id: String, fakeRoot: Boolean): List<String> = buildList {
        val runtime = File(LinuxPaths.container(id), "runtime")
        add("/dev"); add("/proc"); add("/sys")
        add("/dev/urandom:/dev/random")
        add("/proc/self/fd:/dev/fd")
        add("/proc/self/fd/0:/dev/stdin"); add("/proc/self/fd/1:/dev/stdout"); add("/proc/self/fd/2:/dev/stderr")
        // Android has no /dev/shm; POSIX shared memory (Firefox, wlroots) needs one.
        add("${sharedMemory(id)}:/dev/shm")
        File(runtime, "empty").apply { mkdirs() }.let { add("${it.absolutePath}:/sys/fs/selinux") }
        add("$nativeLibraryDir:$GUEST_LIBRARY_DIR")
        if (!fakeRoot) accounts(id, runtime)?.let { (passwd, group) -> add("$passwd:/etc/passwd"); add("$group:/etc/group") }
        addAll(fakeProc(runtime))
        addAll(gpuNode(runtime))
    }

    /** The image's accounts with root renamed to the real uid (DroidDeck's writeAccounts), for sessions only. */
    private fun accounts(id: String, runtime: File): Pair<String, String>? = runCatching {
        val etc = File(LinuxPaths.rootfs(id), "etc")
        fun copy(name: String, root: String) = File(runtime, name).apply {
            writeText(File(etc, name).readLines().joinToString("\n", postfix = "\n") { if (it.startsWith("root:")) root else it })
        }.absolutePath
        copy("passwd", "root:x:$uid:$uid:root:/root:/bin/bash") to copy("group", "root:x:$uid:")
    }.getOrNull()

    /** Stand-ins for /proc files the shell user may not read (DroidDeck's fake proc). */
    private fun fakeProc(runtime: File): List<String> {
        val dir = File(runtime, "proc").apply { mkdirs() }
        val files = mapOf(
            "pci_devices" to ("/proc/bus/pci/devices" to ""),
            "cap_last_cap" to ("/proc/sys/kernel/cap_last_cap" to "40\n"),
            "overflowuid" to ("/proc/sys/kernel/overflowuid" to "65534\n"),
            "overflowgid" to ("/proc/sys/kernel/overflowgid" to "65534\n"),
            "file-max" to ("/proc/sys/fs/file-max" to "9223372036854775807\n"),
        )
        return files.mapNotNull { (name, target) ->
            val (path, content) = target
            if (File(path).canRead()) return@mapNotNull null
            val fake = File(dir, name)
            runCatching { if (!fake.isFile) fake.writeText(content) }
            if (fake.isFile) "${fake.absolutePath}:$path" else null
        }
    }

    /** /dev/kgsl-3d0 presented as a DRM render node with its sysfs identity (DroidDeck's bindGpuNode). */
    private fun gpuNode(runtime: File): List<String> = runCatching {
        val st = android.system.Os.stat(KGSL_DEVICE)
        val dev = st.st_rdev
        val major = ((dev shr 8) and 0xfff) or ((dev shr 32) and 0xfffL.inv())
        val minor = (dev and 0xff) or ((dev shr 12) and 0xffL.inv())
        val node = "renderD$minor"
        val base = File(runtime, "drm")
        val dri = File(base, "dri")
        val device = File(base, "sys/$major:$minor/device")
        val drm = File(device, "drm/$node")
        check((dri.isDirectory || dri.mkdirs()) && (drm.isDirectory || drm.mkdirs()))
        File(dri, node).createNewFile()
        File(drm, "dev").writeText("$major:$minor\n")
        File(device, "uevent").writeText("DRIVER=kgsl-3d0\nMODALIAS=platform:kgsl-3d0\n")
        for ((link, target) in listOf("subsystem" to "/sys/bus/platform", "driver" to "/sys/bus/platform/drivers/msm_drm")) {
            val file = File(device, link)
            if (!java.nio.file.Files.isSymbolicLink(file.toPath())) android.system.Os.symlink(target, file.path)
        }
        listOf("${File(base, "sys").absolutePath}:/sys/dev/char", "${dri.absolutePath}:/dev/dri", "$KGSL_DEVICE:/dev/dri/$node")
    }.getOrDefault(emptyList())

    /**
     * Runs a /bin/sh script inside the container [id], laid out like
     * DroidDeck's LinuxRuntime. Sessions run with the shell user's real ids
     * (`-i`), named root through a bound passwd, so every process and the
     * kernel agree on the uid (D-Bus EXTERNAL, file owners); [fakeRoot]
     * (`-0`) is for package management, which needs root semantics.
     */
    fun guest(id: String, script: String, fakeRoot: Boolean = false): ProcessBuilder = environment(
        ProcessBuilder(
            proot.absolutePath,
            // link2symlink stays for sessions too: Xvfb locks its display with link(), which Android denies.
            "--kill-on-exit", "--link2symlink",
            *(if (fakeRoot) arrayOf("-0") else arrayOf("-i", "$uid:$uid")),
            "-r", LinuxPaths.rootfs(id).absolutePath,
            "-w", "/root",
            *binds(id, fakeRoot).flatMap { listOf("-b", it) }.toTypedArray(),
            "/usr/bin/env", "-i",
            "HOME=/root", "USER=root", "LOGNAME=root", "TERM=xterm-256color", "LANG=C.UTF-8",
            // Android's time zone (images default to UTC); sites compare it with the network location.
            *timeZone(id)?.let { arrayOf("TZ=$it") }.orEmpty(),
            "TMPDIR=/tmp", "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            // Path unix sockets fall back to abstract ones (the shell user may not create them).
            *(if (shim.isFile) arrayOf("LD_PRELOAD=$GUEST_LIBRARY_DIR/$SOCKET_SHIM", "DEXTOP_SOCKET_NS=${socketNamespace(id)}") else emptyArray()),
            // No accessibility bus runs in the session.
            "NO_AT_BRIDGE=1",
            *guestEnvironment(id).toTypedArray(),
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
            // A cancelled or timed-out process interrupts this read; that must not crash the service.
            runCatching {
                process.inputStream.bufferedReader().forEachLine { line ->
                    synchronized(output) { if (output.length < 64_000) output.appendLine(line) }
                    onLine(line)
                }
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
