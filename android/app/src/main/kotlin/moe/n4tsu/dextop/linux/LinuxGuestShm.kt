package moe.n4tsu.dextop.linux

import android.system.Os
import android.util.Log
import java.io.File

/**
 * MIT-SHM for Linux programs: Xlib and the toolkits hand their pixels to the X server through
 * System V shared memory when the server can attach it, instead of writing every frame into the
 * X socket. Android has no System V IPC; the session preload (third_party/droiddeck-preload,
 * sysv.c) provides it with files in the container's /dev/shm/wnsysv, whose inode is the segment id.
 *
 * Each application display tests that path once it is up, before any program starts: a segment is
 * made the way the preload makes one and the X server is asked to attach it, as XShmAttach does.
 * If the server refuses, [OFF_FILE] tells the preload to report System V shared memory as
 * unavailable, so programs use the socket from the start rather than failing on an attach.
 * `/etc/dextop/no-shm` in the container turns the path off for good.
 */
internal object LinuxGuestShm {
    private const val TAG = "DextopLinuxShm"
    /** In the preload's object directory; the preload checks for it (and /etc/dextop/no-shm) at shmget. */
    const val OFF_FILE = ".dextop-shm-off"
    const val USER_OFF = "etc/dextop/no-shm"

    private fun directory(containerId: String) = File(LinuxPaths.container(containerId), "shm/wnsysv")

    /** Tests the path on [x]; true when programs may use it. */
    fun selfTest(containerId: String, x: X11Client, display: Int): Boolean {
        val dir = directory(containerId)
        val off = File(dir, OFF_FILE)
        if (File(LinuxPaths.rootfs(containerId), USER_OFF).isFile) {
            Log.i(TAG, "[$display] MIT-SHM for Linux programs is off (/$USER_OFF)")
            return false
        }
        val passed = runCatching {
            check(x.shmOpcode >= 0) { "the X server has no MIT-SHM" }
            dir.mkdirs()
            dir.setWritable(true, false); dir.setReadable(true, false); dir.setExecutable(true, false)
            val segmentFile = File(dir, "shm.dextop-test.${android.os.Process.myPid()}")
            try {
                segmentFile.delete()
                java.io.RandomAccessFile(segmentFile, "rw").use { it.setLength(4096) }
                segmentFile.setReadable(true, false); segmentFile.setWritable(true, false)
                val shmid = (Os.stat(segmentFile.absolutePath).st_ino and 0x7fffffffL).toInt()
                val segment = x.allocateId()
                val attached = x.shmAttachSysV(segment, shmid)
                if (attached) x.shmDetach(segment)
                check(attached) { "the X server refused a System V segment" }
            } finally {
                segmentFile.delete()
            }
        }.onFailure { Log.w(TAG, "[$display] MIT-SHM self-test failed: ${it.message}; Linux programs use the X socket") }
            .isSuccess
        runCatching {
            if (passed) off.delete() else { dir.mkdirs(); off.writeText("self-test failed\n"); off.setReadable(true, false) }
        }
        if (passed) Log.i(TAG, "[$display] MIT-SHM for Linux programs: self-test passed")
        return passed
    }
}
