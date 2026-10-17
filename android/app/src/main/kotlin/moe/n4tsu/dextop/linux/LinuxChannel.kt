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

    init {
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
                "list" -> remote({ it.listContainers() }) { jsonList(it) }
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
