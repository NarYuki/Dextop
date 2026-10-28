package moe.n4tsu.dextop.linux

import java.io.File
import android.os.ParcelFileDescriptor
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap

/**
 * Sound output of Linux containers.
 *
 * Each container gets a PulseAudio daemon (installed on first use) whose
 * default sink is a null sink. Its monitor is streamed as raw 48 kHz stereo
 * PCM over an anonymous descriptor handed to the app through Binder. Dextop's
 * app process plays it (LinuxAudioPlayer): the shell user may not create
 * an AudioTrack.
 * Applications reach PulseAudio at unix:/tmp/dextop-pulse/native (an
 * abstract socket through the socket shim).
 */
internal class LinuxAudio(
    private val proot: () -> LinuxProot,
    private val log: (String, String) -> Unit,
    /** The set of playing containers changed; the app reads [generation] from the container list. */
    private val onChanged: () -> Unit,
) {
    companion object {
        const val PULSE_SERVER = "unix:/tmp/dextop-pulse/native"
    }

    private class Output(val process: Process, val generation: Int)
    private val sequence = AtomicInteger(SecureRandom().nextInt(Int.MAX_VALUE / 2) + 1)

    private val outputs = ConcurrentHashMap<String, Output>()
    private val installing = ConcurrentHashMap.newKeySet<String>()

    fun running(containerId: String) = outputs[containerId]?.process?.isAlive == true

    fun generation(containerId: String) = outputs[containerId]?.takeIf { it.process.isAlive }?.generation ?: 0

    private fun cookie(containerId: String): File = File(LinuxPaths.rootfs(containerId), "root/.config/pulse/dextop-cookie")
    fun environment(containerId: String): List<String> = listOf(
        "PULSE_SERVER=$PULSE_SERVER", "PULSE_COOKIE=/root/.config/pulse/dextop-cookie")

    @Synchronized fun openStream(containerId: String): ParcelFileDescriptor {
        check(running(containerId)) { "Container audio is not running" }
        val builder = proot().guest(containerId,
            "exec parec --raw --device=dextop.monitor --format=s16le --rate=48000 --channels=2 " +
                "--latency-msec=20 --process-time-msec=5 --client-name='Dextop audio output'")
        val pid = IntArray(1)
        val descriptor = LinuxPty.nativeSpawnPipe(builder.command().toTypedArray(),
            builder.environment().map { "${it.key}=${it.value}" }.toTypedArray(), pid)
        check(descriptor >= 0) { "Cannot open audio pipe: ${-descriptor}" }
        Thread({ LinuxPty.nativeWaitFor(pid[0]) }, "DextopAudioWait").apply { isDaemon = true; start() }
        return ParcelFileDescriptor.adoptFd(descriptor)
    }

    /** Starts sound for [containerId] in the background, installing PulseAudio first when it is missing. */
    fun ensure(containerId: String, family: LinuxPackageFamily?, install: Boolean = true) {
        if (running(containerId) || !installing.add(containerId)) return
        Thread({
            try {
                if (!installed(containerId) && install) install(containerId, family ?: LinuxPackageFamily.APT)
                if (installed(containerId)) start(containerId)
            } catch (error: Throwable) {
                log(containerId, "Linux audio unavailable: ${error.message}")
            } finally {
                installing.remove(containerId)
            }
        }, "DextopLinuxAudio-$containerId").apply { isDaemon = true; start() }
    }

    private fun installed(containerId: String) = listOf("pulseaudio", "parec").all {
        File(LinuxPaths.rootfs(containerId), "usr/bin/$it").exists()
    }

    private fun install(containerId: String, family: LinuxPackageFamily) {
        log(containerId, "Installing PulseAudio for Linux sound output")
        val script = when (family) {
            LinuxPackageFamily.APT -> "export DEBIAN_FRONTEND=noninteractive\n" +
                "apt-get install -y --no-install-recommends pulseaudio pulseaudio-utils || " +
                "{ apt-get update && apt-get install -y --no-install-recommends pulseaudio pulseaudio-utils; }\n"
            LinuxPackageFamily.PACMAN -> "pacman -S --noconfirm --needed pulseaudio\n"
        }
        val runner = proot()
        val result = runner.run(runner.guest(containerId, script, fakeRoot = true), 1_800) { line -> log(containerId, line) }
        if (result.exitCode != 0) log(containerId, "PulseAudio installation failed (exit ${result.exitCode})")
    }

    @Synchronized
    private fun start(containerId: String) {
        if (running(containerId)) return
        stop(containerId)
        val auth = cookie(containerId)
        auth.parentFile?.mkdirs()
        if (!auth.isFile || auth.length() != 256L) auth.writeBytes(ByteArray(256).also { SecureRandom().nextBytes(it) })
        android.system.Os.chmod(auth.path, 0x180) // 0600
        val script = """
            set -e
            mkdir -p /tmp/dextop-pulse
            rm -rf /tmp/dextop-pulse/native /root/.config/pulse/*-runtime
            exec pulseaudio --daemonize=no --exit-idle-time=-1 --disallow-exit -n --use-pid-file=no \
                --log-target=stderr --log-level=warning \
                --load="module-null-sink sink_name=dextop rate=48000 channels=2 sink_properties=device.description=Dextop" \
                --load="module-native-protocol-unix socket=/tmp/dextop-pulse/native auth-anonymous=0 auth-cookie-enabled=1 auth-cookie=/root/.config/pulse/dextop-cookie" \
                --load="module-always-sink" >/tmp/dextop-pulse.log 2>&1
        """.trimIndent() + "\n"
        val process = proot().guest(containerId, script).redirectErrorStream(true).start()
        process.outputStream.close()
        Thread { runCatching { process.inputStream.copyTo(java.io.OutputStream.nullOutputStream()) } }
            .apply { isDaemon = true; start() }
        val output = Output(process, sequence.incrementAndGet())
        outputs[containerId] = output
        Thread({
            process.waitFor()
            if (outputs.remove(containerId, output)) onChanged()
        }, "DextopAudioServerWait").apply { isDaemon = true; start() }
        log(containerId, "Linux sound output started (authenticated PulseAudio, Binder PCM descriptor)")
        onChanged()
    }

    fun stop(containerId: String) {
        outputs.remove(containerId)?.process?.let(LinuxRuntime::terminate)
    }

    fun stopAll() = outputs.keys.toList().forEach(::stop)
}
