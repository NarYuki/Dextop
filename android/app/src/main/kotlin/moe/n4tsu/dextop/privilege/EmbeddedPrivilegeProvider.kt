package moe.n4tsu.dextop.privilege

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Binder
import android.os.Process
import moe.n4tsu.dextop.BuildConfig
import moe.n4tsu.dextop.PrivilegedAccess
import moe.n4tsu.dextop.linux.LinuxServiceHost
import moe.n4tsu.dextop.linux.LinuxContainerClient

/** Receives only the Binder created by Dextop's own shell process. */
class EmbeddedPrivilegeProvider : ContentProvider() {
    private val lifecycleBinder = Binder()
    private val WHITESPACE = Regex("[ \t]+")

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val shellCaller = Binder.getCallingUid() == Process.SHELL_UID
        if (shellCaller && method == LinuxServiceHost.PUBLISH_METHOD && extras != null) {
            val currentContext = requireNotNull(context)
            val outdated = arg != currentContext.applicationInfo.sourceDir
            val binder = extras.getBinder(EmbeddedPrivilegeProtocol.providerBinder)
            // Every Linux program runs as the shell user with full privilege; only the daemon Dextop
            // started, unaltered, may become the Linux service that receives keyring passwords.
            val genuine = PublisherKeys.matches(currentContext, PublisherKeys.LINUX, extras.getString(PublisherKeys.EXTRA)) &&
                genuineDextopProcess(Binder.getCallingPid(), currentContext.applicationInfo.sourceDir)
            if (!genuine) android.util.Log.w("DextopPrivilege", "rejected a Linux service published by pid ${Binder.getCallingPid()}")
            val accepted = genuine && !outdated && binder?.isBinderAlive == true
            if (accepted) LinuxContainerClient.acceptEmbeddedService(currentContext, binder!!)
            return Bundle().apply {
                putBoolean("accepted", accepted)
                putBoolean("outdated", outdated)
                putBinder(EmbeddedPrivilegeProtocol.providerLifecycleBinder, lifecycleBinder)
            }
        }
        if (BuildConfig.DEBUG && shellCaller && method == "probeRuntime") {
            val command = DistributionPrivilegeRuntime.execute(arrayOf("id"))
            val rotation = runCatching {
                val window = PrivilegedAccess("DextopEmbeddedProbe")
                    .service("window", "android.view.IWindowManager")
                window.javaClass.getMethod("getDefaultDisplayRotation").invoke(window) as Int
            }
            return Bundle().apply {
                putBoolean("available", DistributionPrivilegeRuntime.available)
                putBoolean("storedPairing", DistributionPrivilegeBootstrap.hasStoredPairing(requireNotNull(context)))
                putInt("exitCode", command?.exitCode ?: -1)
                putString("output", command?.output.orEmpty())
                putString("error", command?.error.orEmpty())
                putInt("rotation", rotation.getOrDefault(-1))
                putString("binderError", rotation.exceptionOrNull()?.message.orEmpty())
            }
        }
        if (!shellCaller || method != EmbeddedPrivilegeProtocol.providerMethod || extras == null) return null
        val binder = extras.getBinder(EmbeddedPrivilegeProtocol.providerBinder) ?: return null
        val expectedPid = PublisherKeys.runtimePid(requireNotNull(context))
        // Publication can precede the ADB launch response. Retry until its PID has been pinned.
        if (expectedPid <= 0) return Bundle().apply { putBoolean("accepted", false) }
        if (Binder.getCallingPid() != expectedPid) {
            return Bundle().apply { putBoolean("accepted", false); putBoolean("retire", true) }
        }
        // Only the access runtime Dextop started (it carries the key Dextop gave it) is accepted.
        if (!PublisherKeys.matches(requireNotNull(context), PublisherKeys.RUNTIME, extras.getString(PublisherKeys.EXTRA))) {
            android.util.Log.w("DextopPrivilege", "rejected an access runtime published by pid ${Binder.getCallingPid()}")
            return Bundle().apply { putBoolean("accepted", false); putBoolean("retire", true) }
        }
        // Servers from an older APK, or extra servers started while one was still alive, retire
        // instead of retrying forever; otherwise every app restart leaves one more behind.
        val outdated = arg != requireNotNull(context).applicationInfo.sourceDir
        val retire = outdated || DistributionPrivilegeRuntime.servedByOther(binder)
        val accepted = !retire && binder.isBinderAlive
        if (accepted) {
            DistributionPrivilegeRuntime.accept(binder)
            EmbeddedPrivilegeKeeper.onRuntimeAccepted(requireNotNull(context))
            LinuxContainerClient.onPrivilegeChanged()
        }
        return Bundle().apply {
            putBoolean("accepted", accepted)
            putBoolean("retire", retire)
            putBinder(EmbeddedPrivilegeProtocol.providerLifecycleBinder, lifecycleBinder)
        }
    }

    /**
     * Whether [pid] runs Dextop's own code unaltered: started from [apk] (CLASSPATH), with no
     * preloaded library, and executing only code from the system and from Dextop's APK. Read
     * through the trusted access runtime, since an app cannot see the shell's processes.
     */
    private fun genuineDextopProcess(pid: Int, apk: String): Boolean = runCatching {
        if (pid <= 0) return false
        val result = DistributionPrivilegeRuntime.execute(arrayOf("/system/bin/sh", "-c",
            "tr '\\000' '\\n' < /proc/$pid/environ; echo '--- maps'; cat /proc/$pid/maps")) ?: return false
        if (result.exitCode != 0) return false
        val environment = result.output.substringBefore("--- maps").lines()
        val maps = result.output.substringAfter("--- maps", "").lines()
        val classpath = environment.firstOrNull { it.startsWith("CLASSPATH=") }?.removePrefix("CLASSPATH=")
        if (classpath != apk || environment.any { it.startsWith("LD_PRELOAD=") && it.length > "LD_PRELOAD=".length }) {
            android.util.Log.w("DextopPrivilege", "pid $pid is not Dextop's daemon: CLASSPATH=$classpath")
            return false
        }
        val appDirectory = apk.substringBeforeLast('/') + "/"
        val trusted = listOf("/system/", "/apex/", "/vendor/", "/product/", "/system_ext/", "/odm/", "/data/dalvik-cache/",
            "/data/misc/apexdata/", appDirectory)
        val foreign = maps.firstOrNull { line ->
            val fields = line.trim().split(WHITESPACE, limit = 6)
            val executable = fields.getOrNull(1)?.contains('x') == true
            val path = fields.getOrNull(5).orEmpty()
            executable && path.isNotEmpty() && !path.startsWith("[") && !path.startsWith("/memfd:") && trusted.none(path::startsWith)
        }
        if (foreign != null) android.util.Log.w("DextopPrivilege", "pid $pid executes code from outside Dextop and the system: $foreign")
        foreign == null
    }.getOrDefault(false)

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
