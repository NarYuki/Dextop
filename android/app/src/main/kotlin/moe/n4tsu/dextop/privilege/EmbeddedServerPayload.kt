package moe.n4tsu.dextop.privilege

import android.content.Context
import android.os.Build

/** Owns Dextop's bundled server launcher contract. */
internal object EmbeddedServerPayload {
    fun startCommand(context: Context): String {
        val apk = context.applicationInfo.sourceDir
        val abi = Build.SUPPORTED_ABIS.firstOrNull {
            it in setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        } ?: "arm64-v8a"
        val nativeDirectory = "/data/local/tmp/dextop-input-$abi"
        val nativeLibrary = "$nativeDirectory/libdextop_input.so"
        val archiveEntry = "lib/$abi/libdextop_input.so"
        // app_process does not inherit the app's native-library namespace. Extract
        // Dextop's own JNI binary into a shell-readable private directory before
        // launching the bundled process, then pass its exact path to the server.
        return "mkdir -p ${shellQuote(nativeDirectory)} && " +
            "/system/bin/unzip -p ${shellQuote(apk)} ${shellQuote(archiveEntry)} > ${shellQuote(nativeLibrary)} && " +
            "CLASSPATH=${shellQuote(apk)} " +
            "nohup /system/bin/setsid /system/bin/app_process /system/bin " +
            "moe.n4tsu.dextop.privilege.EmbeddedPrivilegeServer ${shellQuote(context.packageName)} " +
            "${shellQuote(nativeLibrary)} " +
            ">/data/local/tmp/dextop-embedded.log 2>&1 </dev/null &"
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
