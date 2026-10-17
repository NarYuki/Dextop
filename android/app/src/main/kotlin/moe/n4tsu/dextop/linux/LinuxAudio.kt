package moe.n4tsu.dextop.linux

import java.io.File
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap

/**
 * Sound output of Linux containers.
 *
 * Each container gets a PulseAudio daemon (installed on first use) whose
 * default sink is a null sink. Its monitor is streamed as raw 48 kHz stereo
 * PCM over a loopback TCP socket (module-simple-protocol-tcp). Dextop's
 * app process plays it (LinuxAudioPlayer): the shell user may not create
 * an AudioTrack.
 * Applications reach PulseAudio at unix:/tmp/dextop-pulse/native (an
 * abstract socket through the socket shim).
 */
internal class LinuxAudio(
    private val proot: () -> LinuxProot,
    private val log: (String, String) -> Unit,
    /** The set of playing containers changed; the app reads [port] from the container list. */
    private val onChanged: () -> Unit,
) {
    companion object {
        const val PULSE_SERVER = "unix:/tmp/dextop-pulse/native"
    }

    private class Output(val process: Process, val port: Int)

    private val outputs = ConcurrentHashMap<String, Output>()
    private val installing = ConcurrentHashMap.newKeySet<String>()

    fun running(containerId: String) = outputs[containerId]?.process?.isAlive == true

    /** Loopback TCP port streaming the container's sound as 48 kHz stereo s16le, or 0. */
    fun port(containerId: String) = outputs[containerId]?.takeIf { it.process.isAlive }?.port ?: 0

    /** NAME=value entries for container processes while its sound server runs. */
    fun environment(containerId: String): List<String> =
        if (running(containerId)) listOf("PULSE_SERVER=$PULSE_SERVER") else emptyList()

    /** Starts sound for [containerId] in the background, installing PulseAudio first when it is missing. */
    fun ensure(containerId: String, family: LinuxPackageFamily?) {
        if (running(containerId) || !installing.add(containerId)) return
        Thread({
            try {
                if (!installed(containerId)) install(containerId, family ?: LinuxPackageFamily.APT)
                if (installed(containerId)) start(containerId)
            } catch (error: Throwable) {
                log(containerId, "Linux audio unavailable: ${error.message}")
            } finally {
                installing.remove(containerId)
            }
        }, "DextopLinuxAudio-$containerId").apply { isDaemon = true; start() }
    }

    private fun installed(containerId: String) = File(LinuxPaths.rootfs(containerId), "usr/bin/pulseaudio").exists()

    private fun install(containerId: String, family: LinuxPackageFamily) {
        log(containerId, "Installing PulseAudio for Linux sound output")
        val script = when (family) {
            LinuxPackageFamily.APT -> "export DEBIAN_FRONTEND=noninteractive\n" +
                "apt-get install -y --no-install-recommends pulseaudio pulseaudio-utils || " +
                "{ apt-get update && apt-get install -y --no-install-recommends pulseaudio pulseaudio-utils; }\n"
            LinuxPackageFamily.PACMAN -> "pacman -S --noconfirm --needed pulseaudio\n"
        }
        val runner = proot()
        val result = runner.run(runner.guest(containerId, script), 1_800) { line -> log(containerId, line) }
        if (result.exitCode != 0) log(containerId, "PulseAudio installation failed (exit ${result.exitCode})")
    }

    private fun freePort(): Int = ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { it.localPort }

    @Synchronized
    private fun start(containerId: String) {
        if (running(containerId)) return
        stop(containerId)
        val port = freePort()
        val script = """
            mkdir -p /tmp/dextop-pulse
            rm -rf /tmp/dextop-pulse/native /root/.config/pulse/*-runtime
            exec pulseaudio --daemonize=no --exit-idle-time=-1 --disallow-exit -n --use-pid-file=no \
                --log-target=stderr --log-level=warning \
                --load="module-null-sink sink_name=dextop rate=48000 channels=2 sink_properties=device.description=Dextop" \
                --load="module-native-protocol-unix socket=/tmp/dextop-pulse/native auth-anonymous=1" \
                --load="module-simple-protocol-tcp listen=127.0.0.1 port=$port source=dextop.monitor record=true playback=false format=s16le rate=48000 channels=2" \
                --load="module-always-sink" >/tmp/dextop-pulse.log 2>&1
        """.trimIndent() + "\n"
        val process = proot().guest(containerId, script).redirectErrorStream(true).start()
        process.outputStream.close()
        Thread { runCatching { process.inputStream.copyTo(java.io.OutputStream.nullOutputStream()) } }
            .apply { isDaemon = true; start() }
        outputs[containerId] = Output(process, port)
        log(containerId, "Linux sound output started (PulseAudio, port $port)")
        onChanged()
    }

    fun stop(containerId: String) {
        outputs.remove(containerId)?.process?.destroyForcibly()
    }

    fun stopAll() = outputs.keys.toList().forEach(::stop)
}
