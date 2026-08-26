package moe.n4tsu.dextop.privilege

import android.content.AttributionSource
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import moe.n4tsu.dextop.input.PrivilegedInputService
import java.io.File
import kotlin.system.exitProcess
import org.lsposed.hiddenapibypass.HiddenApiBypass

/** Minimal bundled ADB process. It exposes no package-management UI or third-party API. */
object EmbeddedPrivilegeServer {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val packageName = arguments.firstOrNull() ?: error("Missing package name")
        arguments.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { nativeInputLibrary ->
            System.setProperty("moe.n4tsu.dextop.embedded_input_library", nativeInputLibrary)
        }
        Looper.prepareMainLooper()
        HiddenApiBypass.addHiddenApiExemptions("Landroid/app/", "Landroid/content/", "Landroid/os/")
        val server = RuntimeBinder()
        val response = publishBinder("$packageName.embedded_privilege", server)
        check(response?.getBoolean("accepted") == true) { "Dextop rejected the embedded runtime" }
        val lifecycle = response?.getBinder(EmbeddedPrivilegeProtocol.providerLifecycleBinder)
            ?: error("Dextop did not return its lifecycle Binder")
        lifecycle.linkToDeath({ exitProcess(0) }, 0)
        Looper.loop()
    }

    private fun publishBinder(authority: String, binder: Binder): Bundle? {
        val activityManager = Class.forName("android.app.ActivityManager")
            .getDeclaredMethod("getService")
            .invoke(null)
        val acquire = activityManager.javaClass.interfaces
            .flatMap { it.methods.asIterable() }
            .first { it.name == "getContentProviderExternal" }
        val acquired = acquire.invoke(activityManager, authority, 0, null, authority)
            ?: error("Provider unavailable: $authority")
        val provider = runCatching {
            acquired.javaClass.getDeclaredField("provider").apply { isAccessible = true }.get(acquired)
        }.getOrElse { acquired }
        val extras = Bundle().apply { putBinder(EmbeddedPrivilegeProtocol.providerBinder, binder) }
        val call = Class.forName("android.content.IContentProvider").methods
            .filter { it.name == "call" }
            .maxBy { it.parameterTypes.size }
        var stringIndex = 0
        val callArguments = call.parameterTypes.map { type ->
            when {
                type == AttributionSource::class.java -> AttributionSource.Builder(android.os.Process.myUid())
                    .setPackageName("com.android.shell")
                    .build()
                type == String::class.java -> when (stringIndex++) {
                    0 -> authority
                    1 -> EmbeddedPrivilegeProtocol.providerMethod
                    else -> null
                }
                type == Bundle::class.java -> extras
                type == Int::class.javaPrimitiveType -> 0
                else -> null
            }
        }.toTypedArray()
        return call.invoke(provider, *callArguments) as? Bundle
    }

    private fun systemService(name: String): android.os.IBinder? =
        Class.forName("android.os.ServiceManager")
            .getDeclaredMethod("getService", String::class.java)
            .invoke(null, name) as? android.os.IBinder

    private class RuntimeBinder : Binder() {
        private val inputService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { PrivilegedInputService() }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            data.enforceInterface(EmbeddedPrivilegeProtocol.descriptor)
            return when (code) {
                EmbeddedPrivilegeProtocol.transactService -> {
                    val serviceName = data.readString() ?: error("Missing service name")
                    val remoteCode = data.readInt()
                    val remoteFlags = data.readInt()
                    val target = systemService(serviceName) ?: error("Service unavailable: $serviceName")
                    val nested = Parcel.obtain()
                    try {
                        nested.appendFrom(data, data.dataPosition(), data.dataAvail())
                        nested.setDataPosition(0)
                        target.transact(remoteCode, nested, reply, remoteFlags)
                    } finally {
                        nested.recycle()
                    }
                }
                EmbeddedPrivilegeProtocol.executeCommand -> {
                    val command = data.createStringArray() ?: emptyArray()
                    val result = runCommand(command)
                    reply?.writeNoException()
                    reply?.writeInt(result.exitCode)
                    reply?.writeString(result.output)
                    reply?.writeString(result.error)
                    true
                }
                EmbeddedPrivilegeProtocol.transactInputService -> {
                    val remoteCode = data.readInt()
                    val remoteFlags = data.readInt()
                    val nested = Parcel.obtain()
                    try {
                        nested.appendFrom(data, data.dataPosition(), data.dataAvail())
                        nested.setDataPosition(0)
                        inputService.asBinder().transact(remoteCode, nested, reply, remoteFlags)
                    } finally {
                        nested.recycle()
                    }
                }
                else -> super.onTransact(code, data, reply, flags)
            }
        }

        private fun runCommand(command: Array<String>): RuntimeCommandResult {
            if (command.isEmpty()) return RuntimeCommandResult(-1, "", "Empty command")
            return runCatching {
                val process = ProcessBuilder(*command).directory(File("/data/local/tmp")).start()
                val stdout = process.inputStream.bufferedReader().use { it.readText() }
                val stderr = process.errorStream.bufferedReader().use { it.readText() }
                RuntimeCommandResult(process.waitFor(), stdout.trim(), stderr.trim())
            }.getOrElse { RuntimeCommandResult(-1, "", it.message.orEmpty()) }
        }
    }
}
