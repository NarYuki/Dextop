package moe.n4tsu.dextop.privilege

import android.os.Binder
import android.os.IBinder
import android.os.Parcel

object DistributionPrivilegeRuntime {
    @Volatile private var runtime: IBinder? = null
    @Volatile var enabled: Boolean = true
    @Volatile var onRuntimeDied: (() -> Unit)? = null

    val available: Boolean get() = enabled && runtime?.isBinderAlive == true

    internal fun accept(binder: IBinder) {
        runtime = binder
        runCatching {
            binder.linkToDeath({
                if (runtime === binder) {
                    runtime = null
                    onRuntimeDied?.invoke()
                }
            }, 0)
        }
    }

    fun serviceBinder(name: String): IBinder? {
        if (!enabled) return null
        val remote = runtime?.takeIf { it.isBinderAlive } ?: return null
        return object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                val request = Parcel.obtain()
                return try {
                    request.writeInterfaceToken(EmbeddedPrivilegeProtocol.descriptor)
                    request.writeString(name)
                    request.writeInt(code)
                    request.writeInt(flags)
                    request.appendFrom(data, 0, data.dataSize())
                    remote.transact(EmbeddedPrivilegeProtocol.transactService, request, reply, flags)
                } finally {
                    request.recycle()
                }
            }
        }
    }

    /**
     * Returns a Binder proxy for Dextop's bundled privileged input service.
     * Unlike a framework service, this endpoint is owned by the embedded shell
     * process and therefore must not be routed through Shizuku's UserService API.
     */
    fun inputServiceBinder(): IBinder? {
        if (!enabled) return null
        val remote = runtime?.takeIf { it.isBinderAlive } ?: return null
        return object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                val request = Parcel.obtain()
                return try {
                    request.writeInterfaceToken(EmbeddedPrivilegeProtocol.descriptor)
                    request.writeInt(code)
                    request.writeInt(flags)
                    request.appendFrom(data, 0, data.dataSize())
                    remote.transact(EmbeddedPrivilegeProtocol.transactInputService, request, reply, flags)
                } finally {
                    request.recycle()
                }
            }
        }
    }

    fun execute(arguments: Array<out String>): RuntimeCommandResult? {
        if (!enabled) return null
        val remote = runtime?.takeIf { it.isBinderAlive } ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(EmbeddedPrivilegeProtocol.descriptor)
            data.writeStringArray(Array(arguments.size) { arguments[it] })
            check(remote.transact(EmbeddedPrivilegeProtocol.executeCommand, data, reply, 0))
            reply.readException()
            RuntimeCommandResult(reply.readInt(), reply.readString().orEmpty(), reply.readString().orEmpty())
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
