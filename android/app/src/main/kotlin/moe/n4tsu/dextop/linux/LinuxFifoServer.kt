package moe.n4tsu.dextop.linux

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.OsConstants
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

/** Shell-owned socket facing glibc clients; app FIFO descriptors arrive via Binder. */
internal class LinuxFifoServer(@Volatile var endpoint: ILinuxFifoBridge) {
    val namespace = "dextop-fifo-" + UUID.randomUUID().toString().replace("-", "")
    private val listener = LocalServerSocket(namespace)
    private val slots = Semaphore(256)
    private val workers = Executors.newCachedThreadPool { task ->
        Thread(task, "DextopLinuxFifoRelay").apply { isDaemon = true }
    }
    init {
        Thread({
            while (true) {
                val socket = try { listener.accept() } catch (failure: Exception) { break }
                val allowed = runCatching { socket.peerCredentials.uid in setOf(Process.myUid(), 2000, 0) }.getOrDefault(false)
                if (!allowed || !slots.tryAcquire()) {
                    runCatching { DataOutputStream(socket.outputStream).apply {
                        writeInt(if (allowed) OsConstants.EMFILE else OsConstants.EACCES); flush()
                    } }
                    socket.close()
                    continue
                }
                workers.execute {
                    try { socket.use { relay(it) } }
                    catch (failure: Exception) { Log.w("DextopLinuxFifo", "FIFO relay failed: ${failure.javaClass.simpleName}") }
                    finally { slots.release() }
                }
            }
        }, "DextopLinuxFifoAccept").apply { isDaemon = true; start() }
        Log.i("DextopLinuxFifo", "FIFO endpoint ready: $namespace (Binder relay)")
    }
    private fun relay(socket: LocalSocket) {
        socket.soTimeout = 10_000
        val input = DataInputStream(socket.inputStream)
        val output = DataOutputStream(socket.outputStream)
        if (input.readInt() != 0x44465031) { output.writeInt(OsConstants.EINVAL); return }
        val operation = input.readInt()
        val flags = input.readInt()
        val id = ByteArray(32).also(input::readFully).toString(Charsets.US_ASCII)
        if (!id.matches(Regex("[a-f0-9]{32}"))) { output.writeInt(OsConstants.EINVAL); return }
        val completed = CountDownLatch(1)
        endpoint.request(operation, flags, id, object : ILinuxFifoResult.Stub() {
            override fun complete(error: Int, descriptor: ParcelFileDescriptor?) {
                try {
                    if (error == 0 && operation == 2 && descriptor == null) {
                        output.writeStatus(OsConstants.EIO)
                    } else {
                        if (descriptor != null) socket.setFileDescriptorsForSend(arrayOf(descriptor.fileDescriptor))
                        output.writeStatus(error)
                    }
                    output.flush()
                } finally {
                    socket.setFileDescriptorsForSend(null)
                    descriptor?.close()
                    completed.countDown()
                }
            }
        })
        completed.await()
    }
    private fun DataOutputStream.writeStatus(value: Int) {
        // Android's writeInt can issue separate one-byte writes. A descriptor
        // must travel with the complete header in one SCM_RIGHTS message.
        write(byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(),
            (value ushr 8).toByte(), value.toByte()))
    }
    fun close() { listener.close(); workers.shutdownNow() }
}
