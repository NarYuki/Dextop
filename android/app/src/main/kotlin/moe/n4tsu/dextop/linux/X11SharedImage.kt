package moe.n4tsu.dextop.linux

import android.system.Os
import android.system.OsConstants
import java.io.FileDescriptor

/**
 * A memfd shared with the X server through MIT-SHM AttachFd. ShmGetImage
 * writes window pixels straight into it, and the blit reads them from the
 * mapping: no copy through the X socket.
 */
internal class X11SharedImage private constructor(
    private val x: X11Client,
    private val fd: FileDescriptor,
    val segment: Int,
    val address: Long,
    val size: Long,
) {
    companion object {
        fun create(x: X11Client, size: Long): X11SharedImage? {
            if (x.shmOpcode < 0) return null
            return runCatching {
                val fd = Os.memfd_create("dextop-x11-shm", 0)
                Os.ftruncate(fd, size)
                val address = Os.mmap(0, size, OsConstants.PROT_READ or OsConstants.PROT_WRITE, OsConstants.MAP_SHARED, fd, 0)
                val segment = x.allocateId()
                x.shmAttachFd(segment, fd)
                x.sync()
                X11SharedImage(x, fd, segment, address, size)
            }.getOrNull()
        }
    }

    fun close() {
        runCatching { x.shmDetach(segment) }
        runCatching { Os.munmap(address, size) }
        runCatching { Os.close(fd) }
    }
}
