package moe.n4tsu.dextop.linux

import org.json.JSONObject

/** Settings operations inspect/register files; they never start a Darwin process. */
internal object LinuxMacosSettings {
    private const val LAUNCHER = "/usr/local/lib/dextop/macos/launch_app.py"

    internal fun command(operation: String, value: String = ""): String {
        fun quote(text: String) = "'" + text.replace("'", "'\\''") + "'"
        val arguments = when (operation) {
            "status" -> "--status"
            "register" -> {
                require(value.startsWith('/') && value.length <= 4096 && value.none { it == '\u0000' || it == '\n' || it == '\r' }) {
                    moe.n4tsu.dextop.localText("uiEnterTheAbsoluteAppPathInsideLinux")
                }
                "--register --bundle ${quote(value)}"
            }
            "unregister" -> {
                require(value.matches(Regex("[a-f0-9]{24}"))) { moe.n4tsu.dextop.localText("uiInvalidMacOSApplicationRegistration") }
                "--unregister $value"
            }
            else -> error(moe.n4tsu.dextop.localText("uiUnknownMacOSSettingsOperation"))
        }
        val command = "python3 $LAUNCHER $arguments"
        return if (operation == "status") "if command -v python3 >/dev/null 2>&1; then $command; else " +
            "printf '%s\\n' '{\"pythonAvailable\":false,\"componentsPresent\":false,\"guiPresent\":false,\"applications\":[]}'; fi" else command
    }

    fun execute(service: ILinuxContainerService, id: String, operation: String, value: String = ""): String {
        val result = JSONObject(service.runCommand(id, command(operation, value), 30))
        val output = result.optString("output")
        check(result.optInt("exitCode", -1) == 0) {
            output.takeLast(4000).ifBlank { moe.n4tsu.dextop.localText("uiMacOSSettingsFailedRestartTheLinuxSessionAndCheckPython3IsInstalled") }
        }
        return if (operation == "unregister") "{}" else {
            // PRoot may also print diagnostics on its merged stderr stream.
            val json = output.lineSequence().lastOrNull { it.trimStart().startsWith('{') }
                ?: error(moe.n4tsu.dextop.localText("uiMacOSSettingsDidNotReturnApplicationMetadata"))
            JSONObject(json).toString()
        }
    }
}
