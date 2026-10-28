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
    private const val APK_CHECK_MS = 5_000L
    private var fontsInitialized = false

    @JvmStatic fun main(arguments: Array<String>) {
        require(arguments.size == 3) { "Expected package, APK and native library directory" }
        val (packageName, apk, nativeDirectory) = arguments
        System.setProperty(LIBRARY_PROPERTY, File(nativeDirectory, "libdextop_linux.so").absolutePath)
        Looper.prepareMainLooper()
        HiddenApiBypass.addHiddenApiExemptions("Landroid/app/", "Landroid/content/", "Landroid/os/",
            "Landroid/graphics/", "Landroid/text/", "Lcom/android/internal/graphics/fonts/")
        initializeFonts()
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
        // An APK update deletes the file this daemon was started from; its code is then stale.
        lateinit var watchApk: () -> Unit
        watchApk = {
            if (!File(apk).exists()) service.destroy() else handler.postDelayed(watchApk, APK_CHECK_MS)
        }
        handler.postDelayed(watchApk, APK_CHECK_MS)
        Looper.loop()
    }

    /** app_process does not receive bindApplication's font map. SVG renderers still
     * need initialized Typefaces even for icons containing no text. Use the font
     * service's current configuration, including vendor and updated font families. */
    @android.annotation.SuppressLint("BlockedPrivateApi") // Shell daemon, including Stellar UserService.
    @Synchronized
    internal fun initializeFonts() {
        if (fontsInitialized) return
        HiddenApiBypass.addHiddenApiExemptions("Landroid/os/", "Landroid/graphics/",
            "Landroid/text/", "Lcom/android/internal/graphics/fonts/")
        runCatching {
            val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
                .invoke(null, "font")
            val manager = Class.forName("com.android.internal.graphics.fonts.IFontManager\$Stub")
                .getMethod("asInterface", android.os.IBinder::class.java).invoke(null, binder)
            val config = manager.javaClass.getMethod("getFontConfig").invoke(manager)
            val configClass = Class.forName("android.text.FontConfig")
            val systemFonts = Class.forName("android.graphics.fonts.SystemFonts")
            val fallback = systemFonts.getMethod("buildSystemFallback", configClass).invoke(null, config)
            val fonts = systemFonts.getMethod("buildSystemTypefaces", configClass, Map::class.java)
                .invoke(null, config, fallback)
            android.graphics.Typeface::class.java.getDeclaredMethod("setSystemFontMap", Map::class.java)
                .invoke(null, fonts)
        }.recoverCatching {
            android.graphics.Typeface::class.java.getDeclaredMethod("loadPreinstalledSystemFontMap").invoke(null)
        }.onSuccess { fontsInitialized = true }
            .onFailure { android.util.Log.w("DextopLinuxHost", "Cannot initialize SVG icon fonts", it) }
    }

    fun startCommand(packageName: String, apk: String, nativeDirectory: String): String =
        "CLASSPATH=${quote(apk)} nohup /system/bin/setsid /system/bin/app_process /system/bin " +
            "--nice-name=${quote("$packageName:dextop_linux_embedded")} " +
            "moe.n4tsu.dextop.linux.LinuxServiceHost ${quote(packageName)} ${quote(apk)} ${quote(nativeDirectory)} " +
            ">/data/local/tmp/dextop-linux-embedded.log 2>&1 </dev/null &"

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
