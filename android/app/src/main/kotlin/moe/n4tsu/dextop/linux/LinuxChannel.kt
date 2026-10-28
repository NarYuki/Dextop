package moe.n4tsu.dextop.linux

import android.content.Context
import android.os.Build
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import moe.n4tsu.dextop.plasma.WindowManagerSelection
import org.json.JSONArray
import org.json.JSONObject

/**
 * MethodChannel 'app.freedextop/linux' for the Linux page. Calls go to
 * [LinuxContainerClient]; service events are forwarded to Flutter as
 * `progress`, `log`, `changed` and `connection`.
 */
internal class LinuxChannel(private val context: Context, messenger: BinaryMessenger) : LinuxContainerClient.Listener {
    private val channel = MethodChannel(messenger, "app.freedextop/linux")
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    init {
        LinuxStoreJobs.attach(context)
        LinuxContainerClient.addListener(this)
        channel.setMethodCallHandler { call, result ->
            fun <T> remote(action: (ILinuxContainerService) -> T, map: (T) -> Any? = { it }) =
                LinuxContainerClient.call(context, action, { result.success(map(it)) }) { error ->
                    result.error("linux", error.message ?: error.javaClass.simpleName, null)
                }
            when (call.method) {
                "status" -> result.success(mapOf(
                    "supportedDevice" to Build.SUPPORTED_ABIS.contains("arm64-v8a"),
                    "privilege" to LinuxContainerClient.privilegeAvailable(),
                    "connected" to LinuxContainerClient.connected,
                    "plasmaV2" to (WindowManagerSelection.current(context) == WindowManagerSelection.DEXTOP_PLASMA_V2),
                ))
                "connect" -> {
                    LinuxContainerClient.connect(context)
                    result.success(null)
                }
                "openDesktop" -> result.success(
                    moe.n4tsu.dextop.MirrorService.openLinuxDesktop(
                        call.argument<String>("id")!!, call.argument<String>("name").orEmpty(),
                    ),
                )
                "runtimeLogs" -> remote({ it.runtimeLogs(call.argument<String>("id")!!) })
                "resetSession" -> remote({ it.resetSession(call.argument<String>("id")!!, call.argument<Boolean>("force") == true) })
                "catalog" -> remote({ it.catalog() }) { jsonList(it) }
                "storeSources" -> result.success(LinuxStoreSources.read(context))
                "storeSourcePrepareJobs" -> result.success(LinuxStoreJobs.poll(call.argument<String>("id")!!, preparations = true))
                "setStoreSource" -> runCatching {
                    LinuxStoreSources.write(context, call.argument<String>("source")!!, call.argument<Boolean>("enabled") == true)
                }.onSuccess { result.success(it) }.onFailure { result.error("store", it.message, null) }
                "storeSourcePrepare", "storeSourceRun" -> runCatching {
                    LinuxStoreJobs.startSource(context, call.argument<String>("id")!!,
                        call.argument<String>("source")!!,
                        if (call.method == "storeSourcePrepare") "prepare" else call.argument<String>("action")!!,
                        call.argument<String>("package").orEmpty())
                }.onSuccess { result.success(it) }.onFailure { result.error("store", it.message, null) }
                "storeSourceCatalog" -> remote({ service ->
                    val source = call.argument<String>("source")!!
                    LinuxStoreSources.requireEnabled(context, source)
                    require(source in setOf("deb-get", "flathub"))
                    val id = call.argument<String>("id")!!
                    LinuxStoreSources.requireApt(service, id)
                    val offset = (call.argument<Int>("offset") ?: 0).coerceIn(0, 100000)
                    LinuxStoreSources.result(service.runCommand(id,
                        LinuxStoreSources.helper() + "dextop-store catalog '$source' $offset 80", 600))
                }) { jsonMap(it) }
                "storeSourceStatus" -> remote({ service ->
                    val id = call.argument<String>("id")!!
                    LinuxStoreSources.requireApt(service, id)
                    LinuxStoreSources.result(service.runCommand(id,
                        "if command -v python3 >/dev/null 2>&1; then " + LinuxStoreSources.helper() +
                            "dextop-store status ${if (call.argument<Boolean>("refresh") == true) "refresh" else ""}; else printf '%s\\n' '{\"installed\":[],\"upgradable\":[]}'; fi", 120))
                }) { jsonMap(it) }
                "settings" -> remote({ it.linuxSettings() }) { jsonMap(it) }
                "setSettings" -> remote({
                    it.setLinuxSettings(JSONObject(call.argument<Map<String, Any?>>("settings") ?: emptyMap<String, Any?>()).toString())
                }) { jsonMap(it) }
                "list" -> remote({ it.listContainers() }) { json ->
                    jsonList(json).also { list ->
                        if (list.any { (it as? Map<*, *>)?.let { c -> c["runtimeState"] == "running" || c["appsRunning"] == true } == true })
                            LinuxSessionService.start(context)
                    }
                }
                "install" -> remote({
                    it.installWithOptions(call.argument<String>("name").orEmpty(), call.argument<String>("distro") ?: "debian",
                        JSONObject(call.argument<Map<String, Any?>>("options") ?: emptyMap<String, Any?>()).toString())
                })
                "setup" -> remote({ it.setup(call.argument<String>("id")!!,
                    JSONObject(call.argument<Map<String, Any?>>("options") ?: emptyMap<String, Any?>()).toString()) })
                "cancel" -> remote({ it.cancelInstall(call.argument<String>("id")!!) })
                "remove" -> remote({ it.remove(call.argument<String>("id")!!) })
                "repair" -> remote({ it.repair(call.argument<String>("id")!!) })
                "run" -> remote({
                    it.runCommand(call.argument<String>("id")!!, call.argument<String>("command")!!,
                        call.argument<Int>("timeout") ?: 120)
                }) { jsonMap(it) }
                "apps" -> remote({
                    it.listApps(call.argument<String>("id")!!, java.util.Locale.getDefault().toLanguageTag())
                }) { jsonList(it) }
                "launchApp" -> {
                    val icon = call.argument<String>("icon")?.takeIf { it.isNotEmpty() }?.let { encoded ->
                        runCatching {
                            val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }.getOrNull()
                    }
                    LinuxAndroidWindows.launch(context, call.argument<String>("id")!!,
                        call.argument<String>("desktop")!!, call.argument<String>("label"), icon)
                    result.success(null)
                }
                "sessions" -> result.success(LinuxAndroidWindows.sessions().map {
                    mapOf("container" to it.container, "desktop" to it.desktopId, "xid" to it.xid, "title" to it.title)
                })
                "sessionCapture" -> LinuxAndroidWindows.capture(call.argument<String>("id")!!, call.argument<Int>("xid")!!) { bitmap ->
                    if (bitmap == null) { result.success(null); return@capture }
                    Thread {
                        val png = java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
                        main.post { result.success(png.toByteArray()) }
                    }.start()
                }
                "focusSession" -> result.success(LinuxAndroidWindows.focus(call.argument<String>("id")!!, call.argument<Int>("xid")!!))
                "closeApp" -> {
                    LinuxAndroidWindows.closeApp(call.argument<String>("id")!!, call.argument<String>("desktop")!!)
                    result.success(null)
                }
                // The package that installed a desktop entry, so the app list can uninstall it.
                "appPackage" -> {
                    val desktop = call.argument<String>("desktop")!!.replace(Regex("[^A-Za-z0-9._+-]"), "")
                    remote({
                        it.runCommand(call.argument<String>("id")!!,
                            "dpkg -S /usr/share/applications/$desktop 2>/dev/null | head -n 1 | cut -d: -f1", 30)
                    }) { json -> org.json.JSONObject(json).optString("output").trim().substringBefore(',').ifEmpty { null } }
                }
                "createShortcut" -> {
                    val id = call.argument<String>("id")!!
                    val desktop = call.argument<String>("desktop")!!
                    val label = call.argument<String>("label").orEmpty()
                    val icon = call.argument<String>("icon")?.takeIf { it.isNotEmpty() }
                        ?.let { runCatching { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }.getOrNull() }
                    Thread {
                        val outcome = runCatching {
                            LinuxShortcutInstaller.install(context, id, desktop, label, icon, silent = call.argument<Boolean>("silent") == true)
                        }
                        main.post {
                            outcome.onSuccess { result.success(it) }
                                .onFailure { result.error("shortcut", it.message ?: it.javaClass.simpleName, null) }
                        }
                    }.start()
                }
                "launcherInstall" -> Thread {
                    val available = LinuxShortcutInstaller.silentAvailable()
                    main.post { result.success(mapOf("mode" to LinuxShortcutInstaller.mode(context), "silentAvailable" to available)) }
                }.start()
                "setLauncherInstall" -> {
                    LinuxShortcutInstaller.setMode(context, call.argument<String>("mode") ?: "ask")
                    result.success(null)
                }
                "openTerminal" -> {
                    LinuxTerminalActivity.open(context, call.argument<String>("id")!!, call.argument<String>("name"))
                    result.success(null)
                }
                "linuxTheme" -> result.success(LinuxTheme.mode(context))
                "setLinuxTheme" -> {
                    LinuxTheme.setMode(context, call.argument<String>("mode") ?: "auto")
                    result.success(null)
                }
                "clipboardSync" -> result.success(LinuxClipboard.enabled(context))
                "setClipboardSync" -> {
                    LinuxClipboard.setEnabled(context, call.argument<Boolean>("enabled") != false)
                    result.success(null)
                }
                "removeShortcut" -> {
                    LinuxShortcutInstaller.uninstall(context, call.argument<String>("id")!!, call.argument<String>("desktop")!!)
                    result.success(null)
                }
                "shortcuts" -> Thread {
                    val keys = LinuxShortcutInstaller.installedKeys(context)
                    main.post { result.success(keys) }
                }.start()
                "storePackages" -> remote({
                    LinuxStoreSources.requireApt(it, call.argument<String>("id")!!)
                    it.runCommand(call.argument<String>("id")!!,
                        "dpkg-query -W -f='\${db:Status-Abbrev} \${Package}\\n' 2>/dev/null | awk '\$1==\"ii\"{print \$2}'; " +
                            "echo '--upgradable--'; apt list --upgradable 2>/dev/null | grep -v '^Listing'", 120)
                }) { json ->
                    val output = org.json.JSONObject(json).optString("output")
                    val (installed, upgradable) = output.split("--upgradable--").let { it[0] to it.getOrElse(1) { "" } }
                    // "name/suite 2.0-1 arm64 [upgradable from: 1.0-1]"
                    val updates = upgradable.lines().map(String::trim).filter { it.contains('/') }.map { line ->
                        val name = line.substringBefore('/')
                        val fields = line.substringAfter(' ').split(' ')
                        mapOf("name" to name, "to" to fields.firstOrNull().orEmpty(),
                            "from" to line.substringAfter("upgradable from: ", "").removeSuffix("]"))
                    }
                    mapOf("installed" to installed.lines().map(String::trim).filter(String::isNotEmpty),
                        "upgradable" to updates.map { it["name"] }, "updates" to updates)
                }
                "storeJobMetadata" -> {
                    LinuxStoreJobs.metadata(call.argument<String>("id")!!,
                        call.argument<List<Map<String, Any?>>>("apps").orEmpty())
                    result.success(null)
                }
                "storeRun" -> runCatching {
                    LinuxStoreSources.requireEnabled(context, "apt")
                    LinuxStoreJobs.start(call.argument<String>("id")!!,
                        call.argument<String>("action") ?: "install", call.argument<List<String>>("packages").orEmpty(),
                        repair = call.argument<Boolean>("repair") == true, before = {
                            LinuxStoreSources.requireEnabled(context, "apt")
                            LinuxStoreSources.requireApt(checkNotNull(LinuxContainerClient.service()), call.argument<String>("id")!!)
                        })
                }.onSuccess { result.success(it) }.onFailure { result.error("store", it.message, null) }
                // What keeps apt from working, for the Store to offer a repair first.
                "aptDiagnose" -> remote({ it.runCommand(call.argument<String>("id")!!, LinuxStoreJobs.DIAGNOSE, 120) }) { json ->
                    org.json.JSONObject(json).optString("output").lines().mapNotNull { line ->
                        val kind = line.substringBefore('|', "")
                        if (kind !in setOf("interrupted", "broken", "locked")) null
                        else mapOf("kind" to kind, "detail" to line.substringAfter('|').trim())
                    }
                }
                "storeJobs" -> result.success(LinuxStoreJobs.poll(call.argument<String>("id")!!, consume = call.argument<Boolean>("peek") != true))
                "storeCancel" -> result.success(LinuxStoreJobs.cancel(call.argument<String>("id")!!, call.argument<String>("name")!!))
                // Every apt package matching a search, beside the AppStream applications.
                "aptSearch" -> {
                    val query = call.argument<String>("query").orEmpty().trim().take(80)
                    if (query.isEmpty()) result.success(emptyList<Any>()) else remote({
                        it.runCommand(call.argument<String>("id")!!,
                            "apt-cache search -- '" + query.replace("'", "'\\''") + "' 2>/dev/null | head -n 80", 30)
                    }) { json ->
                        org.json.JSONObject(json).optString("output").lines().mapNotNull { line ->
                            val name = line.substringBefore(" - ", "").trim()
                            name.takeIf { it.isNotEmpty() && ' ' !in it }?.let { mapOf("name" to it, "summary" to line.substringAfter(" - ").trim()) }
                        }
                    }
                }
                // Windows applications (winget catalogue, installed with Proton).
                "winSearch" -> Thread {
                    val outcome = runCatching { WindowsCatalog.search(context, call.argument<String>("query").orEmpty()).map { it.toMap() } }
                    main.post {
                        outcome.onSuccess { result.success(it) }
                            .onFailure { result.error("windows", it.message ?: it.javaClass.simpleName, null) }
                    }
                }.start()
                "winDetails" -> Thread {
                    val outcome = runCatching { WindowsCatalog.details(context, call.argument<String>("package")!!) }
                    main.post {
                        outcome.onSuccess { result.success(it) }
                            .onFailure { result.error("windows", it.message ?: it.javaClass.simpleName, null) }
                    }
                }.start()
                "winInstalled" -> remote({
                    it.runCommand(call.argument<String>("id")!!, "/usr/local/bin/dextop-windows list 2>/dev/null || echo '[]'", 30)
                }) { json ->
                    val output = org.json.JSONObject(json).optString("output").trim().lines().lastOrNull().orEmpty()
                    runCatching { org.json.JSONArray(output) }.getOrNull()?.let { array ->
                        (0 until array.length()).map { array.getJSONObject(it).optString("id") }
                    } ?: emptyList<String>()
                }
                "winRun" -> result.success(LinuxStoreJobs.startWindows(call.argument<String>("id")!!,
                    call.argument<String>("action") ?: "install", call.argument<String>("package")!!, call.argument<String>("spec")))
                else -> result.notImplemented()
            }
        }
    }

    fun dispose() {
        LinuxContainerClient.removeListener(this)
        channel.setMethodCallHandler(null)
    }

    override fun onProgress(containerId: String, phase: String, percent: Int, message: String) =
        channel.invokeMethod("progress", mapOf("id" to containerId, "phase" to phase, "percent" to percent, "message" to message))

    override fun onLog(containerId: String, line: String) =
        channel.invokeMethod("log", mapOf("id" to containerId, "line" to line))

    override fun onContainersChanged() = channel.invokeMethod("changed", null)

    override fun onConnectionChanged(connected: Boolean) = channel.invokeMethod("connection", connected)

    private fun jsonMap(json: String): Map<String, Any?> = toMap(JSONObject(json))

    private fun jsonList(json: String): List<Any?> = JSONArray(json).let { array ->
        (0 until array.length()).map { index -> array.get(index).let { if (it is JSONObject) toMap(it) else it } }
    }

    private fun toMap(json: JSONObject): Map<String, Any?> =
        json.keys().asSequence().associateWith { key -> toValue(json.get(key)) }

    private fun toValue(value: Any?): Any? = when (value) {
        JSONObject.NULL -> null
        is JSONObject -> toMap(value)
        is JSONArray -> (0 until value.length()).map { toValue(value.get(it)) }
        else -> value
    }
}
