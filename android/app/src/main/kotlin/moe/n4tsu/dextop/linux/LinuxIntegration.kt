package moe.n4tsu.dextop.linux

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/** Dextop's opt-in remote access, service supervisor, calendar and print runtime. */
internal class LinuxIntegration(
    private val proot: () -> LinuxProot,
    private val family: (String) -> LinuxPackageFamily,
    private val desktop: (String) -> Int,
    private val log: (String, String) -> Unit,
    private val changed: () -> Unit,
) {
    private val processes = ConcurrentHashMap<String, Process>()
    private val errors = ConcurrentHashMap<String, String>()
    private val vncPasswords = ConcurrentHashMap<String, String>()
    private val restored = ConcurrentHashMap.newKeySet<String>()
    private val lock = Any()
    private val jobName = Regex("[a-f0-9]{32}")
    private val unitName = Regex("[A-Za-z0-9_.@:-]+\\.(service|timer)")
    private val features = setOf("ssh", "vnc", "calendar", "printing", "services")

    private fun directory(id: String) = File(LinuxPaths.container(id), "integration").apply { mkdirs() }
    private fun config(id: String): JSONObject = File(directory(id), "settings.json").let {
        if (it.isFile) JSONObject(it.readText()) else JSONObject()
    }
    private fun save(id: String, value: JSONObject) {
        val dir = directory(id)
        val staged = File(dir, "settings.staged")
        staged.writeText(value.toString())
        Os.chmod(staged.path, 0x180)
        check(staged.renameTo(File(dir, "settings.json")))
    }
    private fun run(id: String, command: String, input: String? = null, timeout: Long = 120, root: Boolean = true): LinuxProot.Result {
        val runner = proot()
        return runner.run(runner.guest(id, command, fakeRoot = root,
            extraEnvironment = listOf("HOME=${LinuxUser.home(id)}", "DEXTOP_LINUX_USER=${LinuxUser.name(LinuxPaths.rootfs(id)) ?: "root"}")),
            timeout, input = input) { log(id, it) }
    }
    private fun checked(id: String, command: String, input: String? = null, timeout: Long = 120, root: Boolean = true): String {
        val result = run(id, command, input, timeout, root)
        check(result.exitCode == 0) { result.output.takeLast(4000).ifBlank { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiLinuxIntegrationCommandFailed0"), result.exitCode) } }
        return result.output
    }
    private fun helper(id: String, feature: String, value: JSONObject) = checked(id,
        "exec python3 /usr/local/libexec/dextop-integrate $feature", value.toString(), root = feature != "ssh")

    private fun prepare(id: String, feature: String) {
        val packages = when (feature) {
            "ssh" -> if (family(id) == LinuxPackageFamily.APT) "openssh-server" else "openssh"
            "vnc" -> "x11vnc"
            "printing" -> if (family(id) == LinuxPackageFamily.APT) "cups-daemon cups-client cups-server-common ghostscript qpdf" else "cups ghostscript qpdf"
            else -> if (family(id) == LinuxPackageFamily.APT) "python3" else "python"
        }
        val binaries = when (feature) {
            "ssh" -> listOf("usr/sbin/sshd")
            "vnc" -> listOf("usr/bin/x11vnc")
            "printing" -> listOf("usr/sbin/cupsd", "usr/sbin/lpadmin", "usr/bin/gs", "usr/bin/qpdf", "usr/share/cups/mime/mime.types")
            else -> listOf("usr/bin/python3")
        }
        if (binaries.any { !File(LinuxPaths.rootfs(id), it).exists() }) {
            val command = if (family(id) == LinuxPackageFamily.APT)
                "export DEBIAN_FRONTEND=noninteractive; apt-get update && apt-get install -y --no-install-recommends $packages"
            else "pacman -S --needed --noconfirm $packages"
            checked(id, command, timeout = 1800)
        }
        LinuxSessionCompatibility.stage(LinuxPaths.rootfs(id), proot().nativeLibraryDir, LinuxProot.socketNamespace(id))
        if (feature == "services") checked(id, "set -e; cp /usr/local/libexec/dextop-systemctl /usr/local/bin/systemctl; cp /usr/local/libexec/dextop-journalctl /usr/local/bin/journalctl; chmod 755 /usr/local/bin/systemctl /usr/local/bin/journalctl")
    }

    fun running(id: String): Boolean = processes.entries.any { it.key.startsWith("$id:") && it.value.isAlive }
    private fun alive(id: String, feature: String) = processes["$id:$feature"]?.isAlive == true

    /** Service management is part of the Linux runtime, not an opt-in integration. */
    fun ensureServices(id: String) = synchronized(lock) {
        if (!alive(id, "services")) {
            prepare(id, "services")
            startProcess(id, "services", "exec python3 /usr/local/libexec/dextop-systemd daemon", root = true)
            changed()
        }
    }

    /** Make systemctl ready before launch, then restore optional integrations. */
    fun restore(id: String) {
        runCatching { ensureServices(id) }.onFailure {
            errors["$id:services"] = it.message.orEmpty()
            log(id, "Service manager startup failed: ${it.message}")
            changed()
        }
        if (!restored.add(id)) return
        Thread({
            val settings = runCatching { config(id) }.getOrDefault(JSONObject())
            for (feature in listOf("ssh", "calendar", "printing")) {
                val value = settings.optJSONObject(feature) ?: continue
                if (value.optBoolean("enabled")) runCatching { action(id, JSONObject(value.toString()).put("op", "start").put("feature", feature).toString()) }
                    .onFailure { errors["$id:$feature"] = it.message.orEmpty(); changed() }
            }
        }, "DextopIntegrations-$id").apply { isDaemon = true; start() }
    }

    private fun stopProcess(id: String, feature: String) {
        val key = "$id:$feature"
        processes.remove(key)?.let { process ->
            if (feature == "services" && process.isAlive) runCatching {
                run(id, "python3 /usr/local/libexec/dextop-systemd ctl --json shutdown", timeout = 5)
                process.waitFor(12, java.util.concurrent.TimeUnit.SECONDS)
            }
            if (process.isAlive) LinuxRuntime.terminate(process)
        }
        vncPasswords.remove(key)
    }

    private fun startProcess(id: String, feature: String, command: String, port: Int? = null, root: Boolean = false, socket: String? = null) {
        stopProcess(id, feature)
        if (port != null) ServerSocket().use { it.reuseAddress = true; it.bind(InetSocketAddress("0.0.0.0", port)) }
        val key = "$id:$feature"
        val output = File(directory(id), "$feature.log").apply { writeText("") }
        val process = proot().guest(id, command, fakeRoot = root,
            extraEnvironment = listOf("HOME=${LinuxUser.home(id)}", "DEXTOP_LINUX_USER=${LinuxUser.name(LinuxPaths.rootfs(id)) ?: "root"}"))
            .redirectErrorStream(true).start()
        process.outputStream.close()
        processes[key] = process
        errors.remove(key)
        Thread({
            runCatching { process.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                output.appendText(line.take(4000)+"\n")
                if (output.length() > 128*1024) output.writeText(output.readText().takeLast(64000))
            } } }
            val code = process.waitFor()
            if (processes.remove(key, process)) {
                errors[key] = "Exited ($code): " + runCatching { output.readText().takeLast(2000) }.getOrDefault("")
                changed()
            }
        }, "DextopIntegration-$feature").apply { isDaemon = true; start() }
        try {
            val end = System.nanoTime() + 12_000_000_000L
            var ready = false
            while (System.nanoTime() < end && process.isAlive) {
                ready = if (socket != null) runCatching {
                    // Path sockets are abstract under the container's namespace (linux_unixsock.c).
                    android.net.LocalSocket().use { it.connect(android.net.LocalSocketAddress(LinuxProot.socketNamespace(id) + socket,
                        android.net.LocalSocketAddress.Namespace.ABSTRACT)) }
                    true
                }.getOrDefault(false)
                else if (port != null) runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 150) }; true }.getOrDefault(false)
                else File(LinuxPaths.rootfs(id), "var/lib/dextop/services/status.json").let { file -> file.isFile && System.currentTimeMillis()-file.lastModified() < 2000 }
                if (ready) break
                Thread.sleep(100)
            }
            check(ready && process.isAlive) { "${feature}: " + output.readText().takeLast(3000).ifBlank { moe.n4tsu.dextop.localText("uiStartupTimedOut") } }
        } catch (error: Throwable) {
            stopProcess(id, feature)
            throw error
        }
    }

    fun status(id: String): String {
        val settings = config(id)
        val result = JSONObject().put("username", LinuxUser.name(LinuxPaths.rootfs(id)) ?: "")
        val addresses = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .filterIsInstance<java.net.Inet4Address>().filterNot { it.isLoopbackAddress || it.isLinkLocalAddress }.map { it.hostAddress }
        }.getOrDefault(emptyList())
        result.put("addresses", JSONArray(addresses))
        for (feature in features) {
            val value = settings.optJSONObject(feature) ?: JSONObject()
            value.put("running", alive(id, feature)).put("error", errors["$id:$feature"].orEmpty())
            if (feature == "services") value.put("enabled", true)
            if (feature == "vnc") value.put("secret", vncPasswords["$id:$feature"] ?: value.optString("secret"))
            result.put(feature, value)
        }
        val unitStatus = File(LinuxPaths.rootfs(id), "var/lib/dextop/services/status.json")
        val units = if (alive(id, "services")) runCatching { JSONObject(unitStatus.readText()).optJSONArray("units") }.getOrNull() else null
        result.put("units", units ?: JSONArray()).put("printJobs", printJobs(id))
        return result.toString()
    }

    fun action(id: String, json: String): String {
        val request = JSONObject(json)
        if (request.optString("op") == "services-ready" ||
            (request.optString("op") == "start" && request.optString("feature") == "services")) {
            ensureServices(id)
            return status(id)
        }
        if (request.optString("op") == "save") {
            var restartRequest: JSONObject? = null
            val savedStatus = synchronized(lock) {
            val feature = request.getString("feature").also { require(it in features && it != "services") }
            val settings = config(id)
            val value = JSONObject(settings.optJSONObject(feature)?.toString() ?: "{}")
            val fields = when (feature) {
                "ssh" -> listOf("port", "lan", "password", "keys")
                "vnc" -> listOf("port", "lan", "secret")
                else -> listOf("port")
            }
            fields.filter(request::has).forEach { value.put(it, request.get(it)) }
            val defaultPort = mapOf("ssh" to 2222, "vnc" to 5900, "calendar" to 8585, "printing" to 8631).getValue(feature)
            require(value.optInt("port", defaultPort) in 1024..65535) { moe.n4tsu.dextop.localText("uiUseAPortFrom1024To65535") }
            if (feature == "vnc") {
                val password = value.optString("secret")
                require(password.isEmpty() || (password.length == 8 && password.all { it.code in 33..126 })) { moe.n4tsu.dextop.localText("uiVNCRequiresAn8CharacterASCIIPassword") }
            }
            if (feature == "ssh") {
                val keys = value.optString("keys")
                require(keys.toByteArray().size <= 65536) { moe.n4tsu.dextop.localText("uiAuthorizedKeysAreTooLarge") }
                require(value.optBoolean("password", true) || keys.isNotBlank()) { moe.n4tsu.dextop.localText("uiAddAnSSHPublicKeyBeforeDisablingPasswordAuthentication") }
                val keyPattern = Regex("(?:ssh-ed25519|ssh-rsa|ecdsa-sha2-nistp(?:256|384|521)|sk-ssh-ed25519@openssh.com) [A-Za-z0-9+/=]+(?: [^\\r\\n]*)?")
                require(keys.lineSequence().all { it.isBlank() || keyPattern.matches(it.trim()) }) { moe.n4tsu.dextop.localText("uiPasteOpenSSHPublicKeysWithoutCommandOptions") }
            }
            if (feature == "calendar" && value.optString("token").length < 32) value.put("token", random(32))
            if (alive(id, feature)) {
                restartRequest = JSONObject(value.toString()).put("op", "start").put("feature", feature)
                ""
            } else {
                settings.put(feature, value)
                save(id, settings)
                changed()
                status(id)
            }
            }
            return restartRequest?.let { action(id, it.toString()) } ?: savedStatus
        }
        if (request.optString("op") == "start") ensureServices(id)
        val vncDisplay = if (request.optString("op") == "start" && request.optString("feature") == "vnc") desktop(id) else null
        return synchronized(lock) {
        val operation = request.getString("op")
        if (operation == "calendar-data") {
            val settings = config(id).optJSONObject("calendar")
            check(settings?.optBoolean("enabled") == true) { moe.n4tsu.dextop.localText("uiCalendarSharingIsDisabled") }
            val ics = request.getString("ics")
            val events = request.getJSONArray("events").toString()
            require(ics.toByteArray().size <= 512000 && events.toByteArray().size <= 256000 && ics.startsWith("BEGIN:VCALENDAR\r\n"))
            val directory = "\"${'$'}HOME/.local/share/dextop/calendar\""
            checked(id, "set -e; mkdir -p $directory; umask 077; cat >$directory/.android.ics.pending; mv $directory/.android.ics.pending $directory/android.ics", ics, root = false)
            checked(id, "set -e; umask 077; cat >$directory/.events.json.pending; mv $directory/.events.json.pending $directory/events.json", events, root = false)
            return status(id)
        }
        if (operation == "print-delete") {
            val job = request.getString("job").also { require(jobName.matches(it)) }
            checked(id, "rm -f /var/spool/dextop-print/$job.pdf /var/spool/dextop-print/$job.json")
            changed()
            return status(id)
        }
        if (operation == "unit-save") {
            ensureServices(id)
            val name = request.getString("unit").also { require(unitName.matches(it)) }
            val content = request.getString("content").also { require(it.toByteArray().size <= 32768) }
            checked(id, "python3 /usr/local/libexec/dextop-systemd write-unit",
                JSONObject().put("name", name).put("content", content).put("user", request.optBoolean("user")).toString())
            return status(id)
        }
        if (operation == "unit") {
            ensureServices(id)
            val action = request.getString("action").also { require(it in setOf("start", "stop", "restart", "enable", "disable", "reload", "journal", "daemon-reload", "reset-failed")) }
            val name = request.optString("unit").also { require(action == "daemon-reload" || unitName.matches(it)) }
            val journal = if (action == "journal") "--journal -u $name -n 150" else "$action $name"
            val output = checked(id, "python3 /usr/local/libexec/dextop-systemd ctl --json ${if (request.optBoolean("user")) "--user" else ""} $journal", timeout = 60)
            val answer = JSONObject(output.lineSequence().last { it.trimStart().startsWith('{') })
            check(answer.optBoolean("ok")) { answer.optString("error", moe.n4tsu.dextop.localText("uiServiceOperationFailed")) }
            return if (action == "journal") answer.toString() else status(id)
        }
        val feature = request.getString("feature").also { require(it in features) }
        val settings = config(id)
        if (operation == "stop") {
            require(feature != "services") { moe.n4tsu.dextop.localText("uiServiceManagementIsPartOfTheLinuxRuntime") }
            stopProcess(id, feature)
            val value = settings.optJSONObject(feature) ?: JSONObject()
            value.put("enabled", false)
            settings.put(feature, value)
            save(id, settings)
            if (feature == "calendar") checked(id, "rm -f \"${'$'}HOME/.local/share/dextop/calendar/android.ics\" \"${'$'}HOME/.local/share/dextop/calendar/events.json\" \"${'$'}HOME/.local/share/applications/dextop-calendar.desktop\"", root = false)
            if (feature == "printing") checked(id, "rm -f /etc/dextop/printing.env")
            changed()
            return status(id)
        }
        require(operation == "start")
        if (feature == "ssh") check(LinuxUser.name(LinuxPaths.rootfs(id)) != null) { moe.n4tsu.dextop.localText("uiCreateALinuxEnvironmentWithAUserAccountFirst") }
        prepare(id, feature)
        val value = JSONObject()
        val defaults = mapOf("ssh" to 2222, "vnc" to 5900, "calendar" to 8585, "printing" to 8631)
        val port = defaults[feature]?.let { request.optInt("port", it).also { selected -> require(selected in 1024..65535) { moe.n4tsu.dextop.localText("uiUseAPortFrom1024To65535") } } }
        if (port != null) value.put("port", port)
        value.put("lan", request.optBoolean("lan", false))
        when (feature) {
            "ssh" -> {
                value.put("password", request.optBoolean("password", true)).put("keys", request.optString("keys"))
                helper(id, feature, value)
                startProcess(id, feature, "exec /usr/sbin/sshd -D -e -f /etc/dextop/ssh/sshd_config", port)
            }
            "vnc" -> {
                val secret = request.optString("secret").ifEmpty { random(6).take(8) }
                value.put("secret", secret)
                helper(id, feature, value)
                val display = checkNotNull(vncDisplay)
                startProcess(id, feature, "exec x11vnc -display :$display -rfbport $port -passwdfile /etc/dextop/vnc/password -forever -shared -noxdamage -noshm -no6 ${if (value.optBoolean("lan")) "-listen 0.0.0.0" else "-localhost"}", port)
                vncPasswords["$id:$feature"] = secret
            }
            "calendar" -> {
                val old = settings.optJSONObject(feature)
                value.put("token", old?.optString("token")?.takeIf { it.length >= 32 } ?: random(32))
                helper(id, feature, value)
                startProcess(id, feature, "exec python3 /usr/local/libexec/dextop-integrate calendar-server", port)
            }
            "printing" -> {
                helper(id, feature, value)
                // A unix socket of the container: a TCP port on 127.0.0.1 is reachable by every app on the device.
                startProcess(id, feature, "exec cupsd -f -c /etc/dextop/cups/cupsd.conf -s /etc/dextop/cups/cups-files.conf",
                    socket = "/run/dextop-cups/cups.sock")
                try { helper(id, "printer", value) } catch (error: Throwable) { stopProcess(id, feature); throw error }
            }
            "services" -> startProcess(id, feature, "exec python3 /usr/local/libexec/dextop-systemd daemon", root = true)
        }
        value.put("enabled", true)
        settings.put(feature, value)
        save(id, settings)
        restored += id
        changed()
        status(id)
        }
    }

    fun printJobs(id: String): JSONArray {
        val folder = File(LinuxPaths.rootfs(id), "var/spool/dextop-print")
        val result = JSONArray()
        folder.listFiles().orEmpty().filter { it.name.endsWith(".json") && jobName.matches(it.name.removeSuffix(".json")) }.take(32).forEach { file ->
            runCatching {
                val value = ParcelFileDescriptor.AutoCloseInputStream(openFile(id, "var/spool/dextop-print/${file.name}")).use { input ->
                    val bytes = ByteArray(32769)
                    var count = 0
                    while (count < bytes.size) {
                        val n = input.read(bytes, count, bytes.size-count)
                        if (n < 0) break
                        count += n
                    }
                    require(count <= 32768)
                    JSONObject(String(bytes, 0, count, Charsets.UTF_8))
                }
                result.put(JSONObject().put("id", file.name.removeSuffix(".json")).put("title", value.optString("title").take(256))
                    .put("copies", value.optInt("copies", 1).coerceIn(1, 99)).put("bytes", value.optLong("bytes")))
            }
        }
        return result
    }

    /** Anchored, no-symlink traversal: guest paths can never escape into shell-owned host files. */
    internal fun openFile(id: String, relative: String): ParcelFileDescriptor {
        val pieces = relative.split('/')
        require(pieces.all { it.isNotEmpty() && it != "." && it != ".." })
        val handles = ArrayList<ParcelFileDescriptor>()
        fun open(path: String, flags: Int): ParcelFileDescriptor {
            val descriptor = Os.open(path, flags, 0)
            return try { ParcelFileDescriptor.dup(descriptor).also(handles::add) } finally { Os.close(descriptor) }
        }
        try {
            var anchor = open(LinuxPaths.rootfs(id).path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW)
            require(OsConstants.S_ISDIR(Os.fstat(anchor.fileDescriptor).st_mode))
            pieces.forEachIndexed { index, piece ->
                val flags = OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW
                anchor = open("/proc/self/fd/${anchor.fd}/$piece", flags)
                if (index < pieces.lastIndex) require(OsConstants.S_ISDIR(Os.fstat(anchor.fileDescriptor).st_mode))
            }
            val stat = Os.fstat(anchor.fileDescriptor)
            require(OsConstants.S_ISREG(stat.st_mode) && stat.st_size in 1..64L*1024*1024) { moe.n4tsu.dextop.localText("uiInvalidPrintSpoolFile") }
            return ParcelFileDescriptor.dup(anchor.fileDescriptor)
        } finally { handles.asReversed().forEach { it.close() } }
    }

    fun openPrint(id: String, job: String): ParcelFileDescriptor {
        require(jobName.matches(job))
        return openFile(id, "var/spool/dextop-print/$job.pdf")
    }

    fun filterPrint(id: String, job: String, ranges: String): ParcelFileDescriptor {
        require(jobName.matches(job))
        val requested = JSONArray(ranges)
        require(requested.length() in 1..1000)
        var count = 0L
        val pages = (0 until requested.length()).joinToString(",") { index ->
            val range = requested.getJSONArray(index)
            val first = range.getInt(0)
            val last = range.getInt(1)
            require(first >= 0 && last in first..1000000)
            count += last.toLong()-first+1
            require(count <= 10000)
            "${first+1}-${last+1}"
        }
        val temporary = "var/spool/dextop-print/filtered-${java.util.UUID.randomUUID()}.pdf"
        try {
            val result = run(id, "qpdf --empty --pages /var/spool/dextop-print/$job.pdf $pages -- /$temporary", timeout = 90)
            check(result.exitCode == 0 || result.exitCode == 3) { result.output.takeLast(1000) }
            return openFile(id, temporary)
        } finally { runCatching { run(id, "rm -f /$temporary") } }
    }

    fun stop(id: String) = synchronized(lock) {
        features.forEach { stopProcess(id, it) }
        restored.remove(id)
    }
    fun stopAll() { processes.keys.map { it.substringBefore(':') }.toSet().forEach(::stop) }

    private fun random(bytes: Int): String = Base64.encodeToString(ByteArray(bytes).also(SecureRandom()::nextBytes), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
