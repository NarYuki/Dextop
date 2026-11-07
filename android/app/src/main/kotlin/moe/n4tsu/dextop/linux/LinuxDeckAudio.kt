package moe.n4tsu.dextop.linux

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** App-UID Pulse/AAudio endpoint inherited only by the Deck process tree. */
internal object LinuxDeckAudio {
    private const val TAG = "DextopDeckAudio"
    const val STATE = "dextop-deck-audio.conf"
    private var process: Process? = null
    private var port = 0
    private var auth: ByteArray? = null
    @Synchronized fun cookie(): ByteArray = checkNotNull(auth).copyOf()
    private var generation = 0L
    private val restartTimes = ArrayDeque<Long>()
    private val owners = mutableSetOf<String>()

    @Synchronized
    fun ensure(context: Context, containerId: String): Int {
        if (process?.isAlive == true) { owners += containerId; return port }
        process = null
        val dir = File(context.filesDir, "deck-audio").apply { mkdirs() }
        val runtime = File(dir, "runtime").apply { mkdirs() }
        val pidFile = File(runtime, "pid")
        // A surviving child from an app-process restart must not keep its AAudio stream open.
        pidFile.takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull()?.let { oldPid ->
            val executable = runCatching { File("/proc/$oldPid/cmdline").readText().substringBefore('\u0000') }.getOrNull()
            if (executable?.startsWith("/data/app/") == true &&
                executable.contains(context.packageName) && executable.endsWith("/libpulseaudio.so")) {
                android.os.Process.killProcess(oldPid)
            }
        }
        val modules = File(dir, "modules").apply { mkdirs() }
        for (name in context.assets.list("deck-audio/modules").orEmpty()) {
            val staged = File(modules, "$name.staged")
            context.assets.open("deck-audio/modules/$name").use { input -> staged.outputStream().use { input.copyTo(it) } }
            check(staged.renameTo(File(modules, name))) { "Cannot stage Deck audio module $name" }
        }
        val selected = port.takeIf { it > 0 } ?: ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        val cookie = File(dir, "cookie")
        val secret = auth ?: ByteArray(256).also { java.security.SecureRandom().nextBytes(it) }
        cookie.writeBytes(secret)
        android.system.Os.chmod(cookie.path, 0x180)
        val config = File(dir, "default.pa").apply {
            writeText("""
                load-module module-native-protocol-tcp listen=127.0.0.1 port=$selected auth-anonymous=0 auth-cookie-enabled=1 auth-cookie=${cookie.path}
                load-module module-directaudio-native-sink sink_name=DirectAudio performance_mode=1 adaptive=1 decay=0 limiter=1 watchdog=1 volume=1.0
                set-default-sink DirectAudio
            """.trimIndent() + "\n")
        }
        val libraries = context.applicationInfo.nativeLibraryDir
        val child = ProcessBuilder("$libraries/libpulseaudio.so", "--system=false", "--disable-shm=true",
            "--fail=true", "-n", "--file=$config", "--daemonize=false", "--use-pid-file=true",
            "--exit-idle-time=-1", "--dl-search-path=$modules", "--log-level=info", "--log-target=stderr")
            .directory(dir).redirectErrorStream(true).apply {
                environment()["LD_LIBRARY_PATH"] = "$libraries:$modules"
                environment()["HOME"] = dir.path
                environment()["TMPDIR"] = dir.path
                environment()["PULSE_RUNTIME_PATH"] = runtime.path
            }.start()
        child.outputStream.close()
        val ready = CountDownLatch(1)
        val started = AtomicBoolean()
        Thread({
            runCatching {
                child.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                    if ("Daemon startup complete." in line) { started.set(true); ready.countDown() }
                    if (line.startsWith("E:") || "Final latency" in line) Log.i(TAG, line)
                } }
            }
            ready.countDown()
        }, "DextopDeckAudioLog").apply { isDaemon = true; start() }
        if (!ready.await(3, TimeUnit.SECONDS) || !started.get() || !child.isAlive) {
            child.destroy()
            if (!child.waitFor(500, TimeUnit.MILLISECONDS)) child.destroyForcibly()
            error("Deck DirectAudio did not become ready")
        }
        process = child
        auth = secret
        port = selected
        owners += containerId
        val token = ++generation
        Thread({
            child.waitFor()
            var delay = 250L
            var attempt = 0
            while (true) {
                Thread.sleep(delay)
                synchronized(this) {
                    if (generation != token || owners.isEmpty()) return@Thread
                    val now = SystemClock.uptimeMillis()
                    while (restartTimes.peekFirst()?.let { it < now - 30_000 } == true) restartTimes.removeFirst()
                    if (restartTimes.size >= 3) {
                        delay = (checkNotNull(restartTimes.peekFirst()) + 30_000 - now).coerceAtLeast(250L)
                        Log.w(TAG, "DirectAudio recovery cooling down for ${delay}ms")
                        return@synchronized
                    }
                    restartTimes.addLast(now)
                    attempt++
                    try {
                        ensure(context.applicationContext, owners.first())
                        Log.i(TAG, "DirectAudio recovered port=$selected attempt=$attempt")
                        return@Thread
                    } catch (error: Exception) {
                        Log.w(TAG, "DirectAudio recovery failed attempt=$attempt", error)
                        delay = (delay * 2).coerceAtMost(30_000L)
                    }
                }
            }
        }, "DextopDeckAudioWatch").apply { isDaemon = true; start() }
        Log.i(TAG, "DirectAudio ready port=$selected at=${SystemClock.uptimeMillis()}")
        return selected
    }

    @Synchronized
    fun release(containerId: String) {
        owners -= containerId
        if (owners.isNotEmpty()) return
        generation++
        restartTimes.clear()
        process?.let { child ->
            child.destroy()
            if (!child.waitFor(500, TimeUnit.MILLISECONDS)) child.destroyForcibly()
        }
        process = null
        port = 0
        auth = null
    }
}
