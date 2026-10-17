package moe.n4tsu.dextop.linux

import android.os.Handler
import android.os.Looper
import androidx.annotation.Keep
import moe.n4tsu.dextop.privilege.EmbeddedPrivilegeServer
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File

/** Linux's private shell daemon, launched by Dextop's built-in access runtime. */
@Keep
object LinuxServiceHost {
    const val PUBLISH_METHOD = "publishLinuxService"
    const val LIBRARY_PROPERTY = "moe.n4tsu.dextop.linux_library"

    @JvmStatic fun main(arguments: Array<String>) {
        require(arguments.size == 3) { "Expected package, APK and native library directory" }
        val (packageName, apk, nativeDirectory) = arguments
        System.setProperty(LIBRARY_PROPERTY, File(nativeDirectory, "libdextop_linux.so").absolutePath)
        Looper.prepareMainLooper()
        HiddenApiBypass.addHiddenApiExemptions("Landroid/app/", "Landroid/content/", "Landroid/os/")
        val service = LinuxContainerService()
        val handler = Handler(Looper.getMainLooper())
        lateinit var publish: () -> Unit
        publish = {
            val response = runCatching {
                EmbeddedPrivilegeServer.publishBinder("$packageName.embedded_privilege", service, PUBLISH_METHOD, apk)
            }.getOrNull()
            if (response?.getBoolean("outdated") == true) {
                // Retire only this daemon after an APK update, preserving the main access runtime.
                service.destroy()
            } else {
                val lifecycle = response?.takeIf { it.getBoolean("accepted") }?.getBinder("providerLifecycleBinder")
                if (lifecycle == null) handler.postDelayed(publish, 1_500)
                else runCatching { lifecycle.linkToDeath({ handler.postDelayed(publish, 500) }, 0) }
                    .onFailure { handler.postDelayed(publish, 500) }
            }
        }
        publish()
        Looper.loop()
    }

    fun startCommand(packageName: String, apk: String, nativeDirectory: String): String =
        "CLASSPATH=${quote(apk)} nohup /system/bin/setsid /system/bin/app_process /system/bin " +
            "--nice-name=${quote("$packageName:dextop_linux_embedded")} " +
            "moe.n4tsu.dextop.linux.LinuxServiceHost ${quote(packageName)} ${quote(apk)} ${quote(nativeDirectory)} " +
            ">/data/local/tmp/dextop-linux-embedded.log 2>&1 </dev/null &"

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
