package moe.n4tsu.dextop.linux

import org.json.JSONObject

/**
 * Measures, on this device, whether the GPU path draws Linux applications faster than the
 * processor, and recommends a renderer from that.
 *
 * In a container, on an X display of its own, the same OpenGL program is drawn twice: once with
 * the GPU path this device has ([LinuxGraphics.candidate]: Turnip with Zink, or VirGL) and once
 * with llvmpipe. The GPU path must start, report its own renderer (not a software fallback),
 * keep drawing for the whole run, and be at least [MIN_SPEEDUP] times as fast. What is timed is
 * the whole path an application window takes, presentation included, at a desktop window size.
 *
 * The recommendation applies only while the user has not picked a renderer ([LinuxSettings]).
 */
internal object LinuxGpuProbe {
    /** Bumped when the test changes, so a device tested by an older one is tested again. */
    const val VERSION = 1
    /** The GPU path has to be at least this much faster than llvmpipe to be recommended. */
    const val MIN_SPEEDUP = 1.2
    private const val MARK = "DEXTOP_PROBE"
    private const val WIDTH = 1280
    private const val HEIGHT = 720
    /** glxgears reports every 5 s; 13 s gives two reports, the second one warmed up. */
    private const val RUN_SECONDS = 13

    /**
     * The guest script: [gpu] and [software] are the environments of the two runs; every variable
     * named in [unset] (those of any renderer) is cleared first, so neither run inherits the other's.
     */
    fun script(display: Int, gpu: List<String>, software: List<String>, unset: Collection<String>): String {
        fun env(list: List<String>) = list.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }
        val clear = (unset + listOf("LIBGL_ALWAYS_SOFTWARE", "GALLIUM_DRIVER", "VK_ICD_FILENAMES"))
            .filter { it.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) }.distinct().joinToString(" ") { "-u $it" }
        return """
            for tool in Xvfb glxinfo glxgears timeout; do
                command -v ${'$'}tool >/dev/null 2>&1 || { echo "$MARK missing ${'$'}tool"; exit 0; }
            done
            D=$display
            rm -f /tmp/.X${'$'}D-lock /tmp/.X11-unix/X${'$'}D
            mkdir -p /tmp/.X11-unix && chmod 1777 /tmp/.X11-unix
            XROOT=${'$'}(command -v fakeroot-tcp || command -v fakeroot)
            ${LinuxCatalog.X_SERVER_ENV} ${'$'}XROOT Xvfb :${'$'}D -nolock -screen 0 ${WIDTH}x${HEIGHT}x24 -nolisten tcp -ac >/tmp/dextop-gpu-probe.log 2>&1 &
            XPID=${'$'}!
            trap 'kill ${'$'}XPID 2>/dev/null' EXIT HUP INT TERM
            i=0
            while [ ! -e /tmp/.X11-unix/X${'$'}D ] && [ ${'$'}i -lt 100 ]; do sleep 0.1; i=${'$'}((i + 1)); done
            kill -0 ${'$'}XPID 2>/dev/null || { echo "$MARK xvfb-failed"; exit 0; }
            export DISPLAY=:${'$'}D
            run() {
                label=${'$'}1; shift
                info=${'$'}(env $clear "${'$'}@" timeout -k 2 15 glxinfo -B 2>&1)
                echo "$MARK ${'$'}{label}_info ${'$'}?"
                printf '%s\n' "${'$'}info" | sed -n "s/^OpenGL renderer string: /$MARK ${'$'}{label}_renderer /p"
                env $clear "${'$'}@" \
                    timeout -k 2 $RUN_SECONDS glxgears -geometry ${WIDTH}x$HEIGHT >/tmp/dextop-gpu-probe-${'$'}label.log 2>&1
                echo "$MARK ${'$'}{label}_exit ${'$'}?"
                sed -n "s/.* = \([0-9.]*\) FPS.*/$MARK ${'$'}{label}_fps \1/p" /tmp/dextop-gpu-probe-${'$'}label.log
            }
            run gpu ${env(gpu)}
            run cpu ${env(software)}
            echo "$MARK done"
        """.trimIndent() + "\n"
    }

    /** The result of [output] for [candidate], with the recommended renderer. */
    fun parse(candidate: LinuxGpuMode, output: String): JSONObject {
        val values = HashMap<String, MutableList<String>>()
        output.lineSequence().filter { it.startsWith("$MARK ") }.forEach { line ->
            val rest = line.removePrefix("$MARK ")
            values.getOrPut(rest.substringBefore(' ')) { ArrayList() } += rest.substringAfter(' ', "")
        }
        val result = JSONObject().put("version", VERSION).put("at", System.currentTimeMillis())
            .put("candidate", candidate.id).put("minSpeedup", MIN_SPEEDUP)
        values["missing"]?.firstOrNull()?.let {
            return result.put("state", "unavailable").put("error", "$it is not installed; run container setup with graphics again")
        }
        if ("xvfb-failed" in values) return result.put("state", "unavailable").put("error", "The test display did not start")
        val gpuFps = values["gpu_fps"]?.lastOrNull()?.toDoubleOrNull()
        val cpuFps = values["cpu_fps"]?.lastOrNull()?.toDoubleOrNull()
        val renderer = values["gpu_renderer"]?.firstOrNull().orEmpty()
        result.put("renderer", renderer).put("cpuRenderer", values["cpu_renderer"]?.firstOrNull().orEmpty())
        gpuFps?.let { result.put("gpuFps", it) }
        cpuFps?.let { result.put("cpuFps", it) }
        // timeout ends glxgears with 124; anything else means it stopped on its own (a crash).
        val gpuExit = values["gpu_exit"]?.firstOrNull()?.toIntOrNull()
        val software = Regex("llvmpipe|softpipe|swrast", RegexOption.IGNORE_CASE).containsMatchIn(renderer)
        val stable = values["gpu_info"]?.firstOrNull() == "0" && renderer.isNotEmpty() && !software &&
            gpuExit == 124 && (values["gpu_fps"]?.size ?: 0) >= 2
        if (!stable) return result.put("state", "failed").put("recommended", LinuxGpuMode.SOFTWARE.id).put("error", when {
            renderer.isEmpty() -> "The GPU path did not start"
            software -> "The GPU path fell back to software ($renderer)"
            gpuExit != null && gpuExit != 124 -> "The test program stopped on the GPU (exit $gpuExit)"
            else -> "The GPU path did not keep drawing"
        })
        if (cpuFps == null || cpuFps <= 0.0) return result.put("state", "passed").put("recommended", candidate.id)
        val speedup = gpuFps!! / cpuFps
        result.put("speedup", speedup)
        return if (speedup >= MIN_SPEEDUP) result.put("state", "passed").put("recommended", candidate.id)
            else result.put("state", "slow").put("recommended", LinuxGpuMode.SOFTWARE.id)
    }
}

/**
 * The user's own per-program renderer choices: `~/.config/dextop/gpu-apps` in the home of the
 * container's account (/root without one), one per line,
 * `software NAME` or `gpu NAME`, where NAME is the program (its executable's name) or its
 * desktop entry (with or without `.desktop`). `#` starts a comment. It wins over Dextop's own
 * automatic choices; a renderer picked in an application's menu still applies.
 */
internal object LinuxGpuApps {
    const val PATH = ".config/dextop/gpu-apps"
    private val WHITESPACE = Regex("[ \t]+")

    /** "software", "gpu", or null when the file says nothing about this program. */
    fun choice(root: java.io.File, desktopId: String, command: String): String? {
        // Applications run as the container's account, in its home.
        val file = java.io.File(root, LinuxUser.home(root).removePrefix("/") + "/" + PATH)
        if (!file.isFile || file.length() > 64 * 1024) return null
        val names = setOfNotNull(desktopId, desktopId.removeSuffix(".desktop"), program(command))
        var found: String? = null
        runCatching { file.readLines() }.getOrDefault(emptyList()).forEach { raw ->
            val line = raw.substringBefore('#').trim()
            val parts = line.split(WHITESPACE, limit = 2)
            if (parts.size == 2 && parts[0] in setOf("software", "gpu") && parts[1] in names) found = parts[0]
        }
        return found
    }

    /** The executable's name in a launch command, past `env` and its assignments and options. */
    fun program(command: String): String? {
        val words = command.trim().split(WHITESPACE).map { it.trim('"', '\'') }.filter { it.isNotEmpty() }
        var index = 0
        if (words.getOrNull(index)?.substringAfterLast('/') == "env") {
            index++
            while (index < words.size) {
                val word = words[index]
                when {
                    word == "-u" -> index += 2
                    word.startsWith("-") -> index++
                    '=' in word -> index++
                    else -> break
                }
            }
        }
        while (index < words.size && '=' in words[index] && !words[index].startsWith("/")) index++
        return words.getOrNull(index)?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }
    }
}
