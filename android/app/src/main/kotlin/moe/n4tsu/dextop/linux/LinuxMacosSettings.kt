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
                    "Enter the absolute .app path inside Linux"
                }
                "--register --bundle ${quote(value)}"
            }
            "unregister" -> {
                require(value.matches(Regex("[a-f0-9]{24}"))) { "Invalid macOS application registration" }
                "--unregister $value"
            }
            else -> error("Unknown macOS settings operation")
        }
        return "python3 $LAUNCHER $arguments"
    }

    fun execute(service: ILinuxContainerService, id: String, operation: String, value: String = ""): String {
        val result = JSONObject(service.runCommand(id, command(operation, value), 30))
        val output = result.optString("output")
        check(result.optInt("exitCode", -1) == 0) {
            output.takeLast(4000).ifBlank { "macOS settings failed; restart the Linux session and check Python 3 is installed" }
        }
        return if (operation == "unregister") "{}" else {
            // PRoot may also print diagnostics on its merged stderr stream.
            val json = output.lineSequence().lastOrNull { it.trimStart().startsWith('{') }
                ?: error("macOS settings did not return application metadata")
            JSONObject(json).toString()
        }
    }
}
