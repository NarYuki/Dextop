package moe.n4tsu.dextop.privilege

import android.content.Context

/** Owns Dextop's bundled server launcher contract. */
internal object EmbeddedServerPayload {
    /** The launcher and the key to write to its stdin: the key never appears in a command line. */
    fun start(context: Context): Pair<String, String> {
        val apk = context.applicationInfo.sourceDir
        // Package-manager extracted libraries are immutable to the shell user.
        val nativeLibrary = context.applicationInfo.nativeLibraryDir + "/libdextop_input.so"
        val key = PublisherKeys.issue(context, PublisherKeys.RUNTIME)
        return command(apk, context.packageName, nativeLibrary) to key
    }

    internal fun command(apk: String, packageName: String, nativeLibrary: String): String {
        return "IFS= read -r key; echo \"\$key\" | CLASSPATH=${shellQuote(apk)} " +
            "nohup /system/bin/setsid /system/bin/app_process /system/bin " +
            "moe.n4tsu.dextop.privilege.EmbeddedPrivilegeServer ${shellQuote(packageName)} " +
            "${shellQuote(nativeLibrary)} > /data/local/tmp/dextop-embedded.log 2>&1 & " +
            "echo DEXTOP_RUNTIME_PID=$!"
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
