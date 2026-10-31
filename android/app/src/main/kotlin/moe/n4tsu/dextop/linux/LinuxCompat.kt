package moe.n4tsu.dextop.linux

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

/**
 * Linux settings → Compatibility and permissions: the Android limits and grants that decide
 * what Linux can do, above all in the Android execution mode, and the ways to change them.
 */
internal object LinuxCompat {
    private const val PERMISSION_REQUEST = 0x4c43

    fun status(context: Context): Map<String, Any> = mapOf(
        "androidMode" to LinuxExecutionMode.android(context),
        "phantomLimited" to LinuxPhantomProcesses.limited(context),
        "phantomFixable" to (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED || LinuxContainerClient.privilegeAvailable(context)),
        "camera" to granted(context, Manifest.permission.CAMERA),
        "microphone" to granted(context, Manifest.permission.RECORD_AUDIO),
        // The whole shared storage may be shown too, besides the picked folders.
        "wholeStorageAvailable" to true,
        "wholeStorage" to android.os.Environment.isExternalStorageManager(),
    )

    private fun granted(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Turns off Android's child process limit; true when it is off afterwards. Without
     * WRITE_SECURE_SETTINGS the privileged service sets it as the shell would; with neither,
     * developer options open on the switch the user can turn on.
     */
    fun liftPhantomLimit(context: Context): Boolean {
        if (!LinuxPhantomProcesses.limited(context)) return true
        if (granted(context, Manifest.permission.WRITE_SECURE_SETTINGS) &&
            runCatching { Settings.Global.putString(context.contentResolver, "settings_enable_monitor_phantom_procs", "false") }
                .getOrDefault(false) && !LinuxPhantomProcesses.limited(context)) return true
        if (LinuxContainerClient.privilegeAvailable(context)) {
            runCatching {
                moe.n4tsu.dextop.PrivilegedAccess("DextopLinuxCompat").execute(
                    "/system/bin/settings", "put", "global", "settings_enable_monitor_phantom_procs", "false")
            }
            if (!LinuxPhantomProcesses.limited(context)) return true
        }
        open(context, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        return false
    }

    private fun permissionOf(kind: String) = when (kind) {
        "camera" -> Manifest.permission.CAMERA
        "microphone" -> Manifest.permission.RECORD_AUDIO
        else -> throw IllegalArgumentException("Unknown permission")
    }

    /** With privilege the grant needs no dialog, as for USB devices; false when Android must ask. Off the main thread. */
    fun grantSilently(context: Context, kind: String): Boolean {
        val permission = permissionOf(kind)
        if (granted(context, permission)) return true
        if (LinuxExecutionMode.android(context) || !LinuxContainerClient.privilegeAvailable(context)) return false
        runCatching {
            moe.n4tsu.dextop.PrivilegedAccess("DextopLinuxCompat").execute("/system/bin/pm", "grant", context.packageName, permission)
        }
        return granted(context, permission)
    }

    /** Asks for the camera or microphone; a permission refused for good opens the app's settings instead. */
    fun request(context: Context, kind: String) {
        val permission = permissionOf(kind)
        if (granted(context, permission)) return
        val activity = context as? Activity
        if (activity != null && !alreadyRefused(activity, permission)) {
            activity.requestPermissions(arrayOf(permission), PERMISSION_REQUEST)
            refusalAsked += permission
        } else {
            open(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }
    }

    private val refusalAsked = HashSet<String>()

    /** After one refusal Android may not show the prompt again; the app settings page is the way then. */
    private fun alreadyRefused(activity: Activity, permission: String) =
        permission in refusalAsked && !activity.shouldShowRequestPermissionRationale(permission)

    /** "All files access": Linux in the Android mode then sees the whole storage at /media/storage. */
    fun openStorageAccess(context: Context) = open(context,
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))

    private fun open(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
