package moe.n4tsu.dextop.linux

import android.content.Context
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

/** App-owned real FIFO inodes, shared with Linux through Binder descriptors. */
internal object LinuxFifoBridge {
    private val workers = Executors.newCachedThreadPool { task ->
        Thread(task, "DextopLinuxFifoOpen").apply { isDaemon = true }
    }
    private val slots = Semaphore(256)
    private var endpoint: ILinuxFifoBridge? = null

    @Synchronized fun endpoint(context: Context): ILinuxFifoBridge {
        endpoint?.let { return it }
        val directory = File(context.filesDir, "linux-fifos").apply { mkdirs() }
        check(directory.isDirectory) { "Cannot create Linux FIFO directory" }
        return object : ILinuxFifoBridge.Stub() {
            override fun request(operation: Int, flags: Int, id: String?, result: ILinuxFifoResult?) {
                if (result == null) return
                if (Binder.getCallingUid() !in setOf(Process.myUid(), 2000, 0)) {
                    result.complete(OsConstants.EACCES, null)
                    return
                }
                if (id == null || !id.matches(Regex("[a-f0-9]{32}"))) {
                    result.complete(OsConstants.EINVAL, null)
                    return
                }
                if (!slots.tryAcquire()) {
                    result.complete(OsConstants.EMFILE, null)
                    return
                }
                workers.execute {
                    var descriptor: ParcelFileDescriptor? = null
                    var error = 0
                    try {
                        val target = File(directory, id)
                        when (operation) {
                            1 -> {
                                Os.mkfifo(target.path, flags and 0x1ff)
                                Os.chmod(target.path, flags and 0x1ff)
                            }
                            2 -> {
                                val allowed = OsConstants.O_ACCMODE or OsConstants.O_NONBLOCK or
                                    OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW or 0x200000
                                if (flags and allowed.inv() != 0) throw ErrnoException("FIFO flags", OsConstants.EINVAL)
                                if (!OsConstants.S_ISFIFO(Os.lstat(target.path).st_mode))
                                    throw ErrnoException("FIFO type", OsConstants.EINVAL)
                                val fd = Os.open(target.path, flags or OsConstants.O_NOFOLLOW, 0)
                                try { descriptor = ParcelFileDescriptor.dup(fd) } finally { Os.close(fd) }
                            }
                            3 -> Os.remove(target.path)
                            // Empty anonymous pipe for checking descriptor
                            // transport separately from private-file access.
                            4 -> {
                                val pipe = ParcelFileDescriptor.createPipe()
                                pipe[1].close()
                                descriptor = pipe[0]
                            }
                            else -> error = OsConstants.EINVAL
                        }
                    } catch (failure: ErrnoException) {
                        error = failure.errno
                    } catch (failure: Exception) {
                        error = OsConstants.EIO
                    }
                    try { result.complete(error, descriptor) }
                    catch (failure: Exception) {
                        Log.w("DextopLinuxFifo", "FIFO descriptor delivery failed", failure)
                        // Do not leave the shell relay waiting indefinitely if
                        // Binder rejects a descriptor or its recipient dies.
                        runCatching { result.complete(OsConstants.EIO, null) }
                    }
                    finally { descriptor?.close(); slots.release() }
                }
            }
        }.also { endpoint = it }
    }
}
