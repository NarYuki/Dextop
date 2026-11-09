package moe.n4tsu.dextop.linux

import java.io.File
import java.util.concurrent.TimeUnit

/** Filesystem layout under the shell-owned /data/local/tmp. */
internal object LinuxPaths {
    @Volatile private var androidBase: File? = null
    val base get() = androidBase ?: File("/data/local/tmp/dextop/linux")
    val containers get() = File(base, "containers")
    /** PROOT_TMP_DIR; must be writable and executable by the shell user. */
    val prootTmp get() = File(base, ".tmp")

    @Synchronized fun initializeAndroid(context: android.content.Context) {
        check(android.system.Os.getuid() !in setOf(0, 2000))
        val selected = File(context.noBackupFilesDir, "linux-android")
        check(androidBase == null || androidBase == selected)
        androidBase = selected
    }

    fun container(id: String): File {
        require(id.matches(Regex("c[a-zA-Z0-9]+"))) { moe.n4tsu.dextop.localText("uiInvalidContainerId") }
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
        /** Distribution packages also install games outside /usr/bin. Do not add
         * every /opt application directory: vendor desktop entries/wrappers own those. */
        const val GUEST_PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/local/games:/usr/games:/root/.local/bin:/opt/bin"
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
    /** Linux runs as this app (the Android execution mode), not as the shell user. */
    @Volatile var androidMode = false

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

    private val uid by lazy { android.system.Os.getuid() }

    private fun sharedMemory(id: String): String = File(LinuxPaths.container(id), "shm").apply {
        mkdirs()
        setWritable(true, false)
    }.absolutePath

    private fun binds(id: String, fakeRoot: Boolean): List<String> = buildList {
        val runtime = File(LinuxPaths.container(id), "runtime")
        add("/dev"); add("/proc"); add("/sys")
        add("/dev/urandom:/dev/random")
        add("/proc/self/fd:/dev/fd")
        // /dev already supplies stdin/stdout/stderr symlinks to /proc/self/fd/N.
        // Binding individual descriptors canonicalizes pipes/sockets at startup,
        // fails with "can't sanitize binding", and cannot follow later redirects.
        // Android has no /dev/shm; POSIX shared memory (Firefox, wlroots) needs one.
        add("${sharedMemory(id)}:/dev/shm")
        // The Android mode cannot read /dev/input; Dextop's controller placeholders take its place.
        if (androidMode) add("${File(runtime, "input").apply { mkdirs() }.absolutePath}:/dev/input")
        // /proc files Android denies to apps ("/proc is not mounted" from procps and others): stand-ins
        // generated at each launch are bound over them, as proot-distro does, so even programs that
        // bypass libc see them.
        if (androidMode) addAll(LinuxProcStandIns.binds(File(runtime, "proc")))
        // System information Android denies or lacks (network counters, battery, device modelâ€¦),
        // rebuilt for every program that reads it (LinuxSysStandIns).
        addAll(runCatching { LinuxSysStandIns.binds() }.getOrDefault(emptyList()))
        // Android's shared storage and mounted USB volumes; later mounts appear under it too. The
        // Android mode sees the folders picked in Linux settings (LinuxFolders) instead.
        val wholeStorage = LinuxSharing.allowed(id, "wholeStorage") &&
            (!androidMode || android.os.Environment.isExternalStorageManager())
        if (wholeStorage && File("/storage").isDirectory) {
            add("/storage:/media/storage")
            // Retain existing shell-mode paths, gated by the same explicit container grant.
            if (!androidMode) add("/storage:/media/android")
        } else if (androidMode) {
            add("${File(LinuxPaths.base, "folders/$id").apply { mkdirs() }.absolutePath}:${LinuxFolders.GUEST}")
        }
        File(runtime, "empty").apply { mkdirs() }.let {
            add("${it.absolutePath}:/sys/fs/selinux")
            // Android exposes PCI entries whose attributes the shell cannot read.
            // libpci treats the denied vendor read as fatal and calls exit(1), taking
            // Electron's browser and GPU processes down. KGSL GPUs are not PCI devices.
            add("${it.absolutePath}:/sys/bus/pci/devices")
        }
        add("$nativeLibraryDir:$GUEST_LIBRARY_DIR")
        // Security keys relayed as /dev/hidrawN, as udev finds them (LinuxHidSysfs).
        runCatching { LinuxHidSysfs.binds(id) }.getOrNull()?.let(::addAll)
        // USB storage relayed as /dev/sdX: /sys/block, /sys/dev/block and /proc/partitions (LinuxDiskSysfs).
        runCatching { LinuxDiskSysfs.binds(id) }.getOrNull()?.let(::addAll)
        // Keep installed distro packages intact. Only this daemon's process
        // loads the loopback policy; Steam and user servers keep normal binds.
        val faked = File(LinuxPaths.rootfs(id), "usr/bin/faked-tcp")
        if (faked.isFile) {
            check(File(nativeLibraryDir, "libdextop_faked.so").isFile) { moe.n4tsu.dextop.localText("uiMissingFakerootLoopbackPolicy") }
            val wrapper = File(runtime, "faked-loopback")
            val script = "#!/bin/sh\nexec env LD_PRELOAD=\"$GUEST_LIBRARY_DIR/libdextop_faked.so\${LD_PRELOAD:+:\$LD_PRELOAD}\" /run/dextop-faked-original \"\$@\"\n"
            if (!wrapper.isFile || wrapper.readText() != script) {
                val staged = File.createTempFile("faked-", ".staged", runtime)
                try {
                    staged.writeText(script)
                    check(staged.setExecutable(true, false) && staged.renameTo(wrapper)) { moe.n4tsu.dextop.localText("uiCannotStageFakerootPolicy") }
                } finally { staged.delete() }
            }
            add("${faked.absolutePath}:/run/dextop-faked-original")
            add("${wrapper.absolutePath}:/usr/bin/faked-tcp")
        }
        // /proc/*/maps contains Android host paths. Apps such as VLC derive plugin
        // directories from those paths, so expose the same library/data trees at
        // their mapped names too. Bind subtrees, never the whole root recursively.
        for (name in listOf("usr", "opt", "root", "home")) {
            val tree = File(LinuxPaths.rootfs(id), name)
            if (tree.isDirectory) add("${tree.absolutePath}:${tree.absolutePath}")
        }
        if (!fakeRoot) accounts(id, runtime)?.let { (passwd, group) -> add("$passwd:/etc/passwd"); add("$group:/etc/group") }
        addAll(fakeProc(runtime))
        addAll(gpuNode(runtime))
    }

    /** Match the session account to the kernel uid for D-Bus EXTERNAL and file ownership. */
    private fun accounts(id: String, runtime: File): Pair<String, String>? = runCatching {
        val rootfs = LinuxPaths.rootfs(id)
        val name = LinuxUser.name(rootfs) ?: "root"
        val home = LinuxUser.home(rootfs)
        val accountUid = if (uid == 0 && name != "root") 1000 else uid
        val etc = File(rootfs, "etc")
        fun copy(file: String, replacement: String) = File(runtime, file).apply {
            writeText(File(etc, file).readLines().joinToString("\n", postfix = "\n") {
                if (it.startsWith("$name:")) replacement else it
            })
        }.absolutePath
        copy("passwd", "$name:x:$accountUid:$accountUid:$name:$home:/bin/bash") to copy("group", "$name:x:$accountUid:")
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
            "max_user_watches" to ("/proc/sys/fs/inotify/max_user_watches" to "65536\n"),
            "max_user_instances" to ("/proc/sys/fs/inotify/max_user_instances" to "128\n"),
            "max_queued_events" to ("/proc/sys/fs/inotify/max_queued_events" to "16384\n"),
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
     * (`-i`), named as the configured account through a bound passwd, so every process and the
     * kernel agree on the uid (D-Bus EXTERNAL, file owners); [fakeRoot]
     * (`-0`) is for package management, which needs root semantics.
     */
    fun guest(id: String, script: String, fakeRoot: Boolean = false,
              extraBinds: List<String> = emptyList(), extraEnvironment: List<String> = emptyList()): ProcessBuilder {
        LinuxSettings.customResolver()?.let { LinuxNetworkLink.stageResolver(LinuxPaths.rootfs(id), it) }
        LinuxSessionCompatibility.stageNpm(LinuxPaths.rootfs(id))
        val sessionBinds = binds(id, fakeRoot) + extraBinds
        val root = LinuxPaths.rootfs(id)
        val account = if (fakeRoot) "root" else LinuxUser.name(root) ?: "root"
        val home = if (fakeRoot) "/root" else LinuxUser.home(root)
        val accountUid = if (uid == 0 && account != "root") 1000 else uid
        // Package management requires fake ownership/permissions; retain PRoot's full translation there.
        val fastPath = if (!fakeRoot && "PROOT_FP_OFF=1" !in extraEnvironment && extraBinds.isEmpty() && File(root, LinuxProotFastPath.LIBRARY.removePrefix("/")).isFile &&
            System.getenv("PROOT_NO_SECCOMP") == null) LinuxProotFastPath.create(root, sessionBinds) else null
        return environment(ProcessBuilder(
            proot.absolutePath,
            // Secure stores reject link-emulated temporary files and can retry forever.
            // Keep emulation for installers; ordinary applications need real file semantics.
            "--kill-on-exit", *(if (fakeRoot) arrayOf("--link2symlink") else emptyArray()),
            *(if (fakeRoot) arrayOf("-0") else arrayOf("-i", "$accountUid:$accountUid")),
            "-r", LinuxPaths.rootfs(id).absolutePath,
            "-w", home,
            *sessionBinds.flatMap { listOf("-b", it) }.toTypedArray(),
            "/usr/bin/env", "-i",
            "HOME=$home", "USER=$account", "LOGNAME=$account",
            "TERM=xterm-256color", "LANG=C.UTF-8",
            // Android's time zone (images default to UTC); sites compare it with the network location.
            *timeZone(id)?.let { arrayOf("TZ=$it") }.orEmpty(),
            *(if (fakeRoot) arrayOf("DEXTOP_HARDLINK_COPY=1") else emptyArray()),
            "TMPDIR=/tmp", "PATH=${GUEST_PATH.replace("/root/.local/bin", "$home/.local/bin")}",
            // Path unix sockets fall back to abstract ones (the shell user may not create them).
            // DD's bind/connect hooks must run before the socket shim: its udev
            // stand-in sockets need bind() emulation before the real syscall.
            *(if (shim.isFile) arrayOf("LD_PRELOAD=" +
                // Devices from Android (USB, serial, controllers) in both modes; /proc stand-ins where denied.
                "$GUEST_LIBRARY_DIR/libdextop_androidproc.so:" +
                (if (File(LinuxPaths.rootfs(id), LinuxSessionCompatibility.GUEST_PRELOAD.removePrefix("/")).isFile)
                    "${LinuxSessionCompatibility.GUEST_PRELOAD}:" else "") +
                "$GUEST_LIBRARY_DIR/$SOCKET_SHIM", "DEXTOP_SOCKET_NS=${socketNamespace(id)}") else emptyArray()),
            // No accessibility bus runs in the session.
            "NO_AT_BRIDGE=1",
            // The X servers Dextop starts require their cookie (LinuxXAuth).
            "XAUTHORITY=${LinuxXAuth.GUEST}",
            "DEXTOP_EVDEV_UDEV=1",
            // libudev must accept Dextop's file-backed sysfs device entries.
            "SYSTEMD_DEVICE_VERIFY_SYSFS=0",
            *fastPath?.guestEnvironment?.toTypedArray().orEmpty(),
            *guestEnvironment(id).toTypedArray(),
            *extraEnvironment.toTypedArray(),
            "/bin/sh", "-c", script,
        )).apply {
            environment().remove("PROOT_FASTPATH")
            if (fastPath != null) environment()["PROOT_FASTPATH"] = fastPath.key
        }
    }

    data class Result(val exitCode: Int, val output: String)

    /** Runs [builder] to completion, streaming merged output lines to [onLine]. */
    fun run(builder: ProcessBuilder, timeoutSeconds: Long = 0, onProcess: ((Process) -> Unit)? = null, input: String? = null, inputBytes: ByteArray? = null, onLine: (String) -> Unit = {}): Result {
        val process = try { builder.redirectErrorStream(true).start() }
            catch (error: Throwable) { inputBytes?.fill(0); throw error }
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
        // Drain output before writing input: a child may write a full pipe before
        // it reads stdin. Input must also be covered by cancellation and timeout.
        val writeFailure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val writer = Thread {
            runCatching {
                val bytes = inputBytes ?: input?.toByteArray(Charsets.UTF_8)
                try { process.outputStream.use { stream -> if (bytes != null) stream.write(bytes) } }
                finally { bytes?.fill(0) }
            }.onFailure { writeFailure.set(it) }
        }.apply { isDaemon = true }
        try {
            onProcess?.invoke(process)
            writer.start()
            val finished = if (timeoutSeconds > 0) process.waitFor(timeoutSeconds, TimeUnit.SECONDS) else {
                process.waitFor(); true
            }
            if (!finished) LinuxRuntime.terminate(process)
            writer.join(2_000)
            reader.join(2_000)
            if (finished) writeFailure.get()?.let { throw it }
            return Result(if (finished) process.exitValue() else -1, synchronized(output) { output.toString() })
        } finally {
            inputBytes?.fill(0)
            // onProcess, stdin, callbacks or waitFor can throw (notably when the
            // service is shutting down). None may leave the guest running.
            if (process.isAlive) runCatching { LinuxRuntime.terminate(process) }
                .onFailure { process.destroyForcibly() }
        }
    }
}
