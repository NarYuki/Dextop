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

/** Receives only the Binder created by Dextop's own shell process. */
class EmbeddedPrivilegeProvider : ContentProvider() {
    private val lifecycleBinder = Binder()

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val shellCaller = Binder.getCallingUid() == Process.SHELL_UID
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
        if (binder.isBinderAlive) DistributionPrivilegeRuntime.accept(binder)
        return Bundle().apply {
            putBoolean("accepted", binder.isBinderAlive)
            putBinder(EmbeddedPrivilegeProtocol.providerLifecycleBinder, lifecycleBinder)
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
