package moe.n4tsu.dextop.linux

import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * MIT-MAGIC-COOKIE-1 for the X servers Dextop starts. They listen on abstract sockets, which any
 * process of the same SELinux context can reach (with full privilege: every shell-user process,
 * other privileged apps included); with -ac anyone reaching one could read the screen and type.
 * Each container gets a cookie of its own; Linux programs find it through XAUTHORITY (and
 * ~/.Xauthority for programs that clear their environment), and Dextop's own connections send it.
 */
internal object LinuxXAuth {
    /** Where Linux programs and the X servers read the cookie. */
    const val GUEST = "/var/lib/dextop/Xauthority"
    private const val NAME = "MIT-MAGIC-COOKIE-1"
    private const val FAMILY_WILD = 0xffff

    private val displays = ConcurrentHashMap<Int, ByteArray>()

    private fun cookieFile(containerId: String) = File(LinuxPaths.container(containerId), "runtime/x-cookie")

    /** The cookie of [containerId], made once and kept in the container's runtime directory. */
    @Synchronized
    fun cookie(containerId: String): ByteArray {
        val file = cookieFile(containerId)
        file.takeIf { it.isFile && it.length() == 16L }?.readBytes()?.let { return it }
        val cookie = ByteArray(16).also(SecureRandom()::nextBytes)
        file.parentFile?.mkdirs()
        val staged = File(file.parentFile, ".x-cookie.staged")
        staged.writeBytes(cookie)
        restrict(staged)
        check(staged.renameTo(file)) { moe.n4tsu.dextop.localText("uiCannotStoreTheXCookie") }
        return cookie
    }

    /** An Xauthority file holding [cookie] for every display ("wild" family, any display number). */
    fun authority(cookie: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        fun field(bytes: ByteArray) { write(bytes.size shr 8); write(bytes.size and 0xff); write(bytes) }
        write(FAMILY_WILD shr 8); write(FAMILY_WILD and 0xff)
        field(ByteArray(0))          // address: any
        field(ByteArray(0))          // display number: any
        field(NAME.toByteArray())
        field(cookie)
    }.toByteArray()

    /**
     * Writes [containerId]'s Xauthority where Linux finds it and registers [display] for Dextop's
     * own connections. Called before the X server of [display] starts.
     */
    @Synchronized
    fun prepare(containerId: String, display: Int) {
        val cookie = cookie(containerId)
        val data = authority(cookie)
        val root = LinuxPaths.rootfs(containerId)
        write(File(root, GUEST.removePrefix("/")), data)
        // Programs that start with a cleared environment look in their home.
        for (home in setOf("/root", LinuxUser.home(root))) {
            val file = File(root, home.removePrefix("/") + "/.Xauthority")
            write(file, mergeAuthority(if (file.exists()) file.readBytes() else ByteArray(0), cookie))
        }
        displays[display] = cookie
    }

    /** Replace our wildcard record while preserving user-managed host/display records. */
    internal fun mergeAuthority(previous: ByteArray, cookie: ByteArray): ByteArray {
        val kept = ByteArrayOutputStream()
        var offset = 0
        fun number(): Int {
            require(offset + 2 <= previous.size) { moe.n4tsu.dextop.localText("uiTruncatedXauthority") }
            return ((previous[offset++].toInt() and 255) shl 8) or (previous[offset++].toInt() and 255)
        }
        fun field(): ByteArray {
            val size = number()
            require(size <= previous.size - offset) { moe.n4tsu.dextop.localText("uiTruncatedXauthorityField") }
            return previous.copyOfRange(offset, offset + size).also { offset += size }
        }
        while (offset < previous.size) {
            val start = offset
            val family = number()
            val address = field(); val display = field(); val name = field(); field()
            val ours = family == FAMILY_WILD && address.isEmpty() && display.isEmpty() &&
                name.contentEquals(NAME.toByteArray())
            if (!ours) kept.write(previous, start, offset - start)
        }
        return authority(cookie) + kept.toByteArray()
    }

    /** The cookie Dextop sends to [display], or null for a server it did not start (no auth). */
    fun forDisplay(display: Int): ByteArray? = displays[display]

    fun forget(display: Int) { displays.remove(display) }

    private fun write(file: File, data: ByteArray) {
        file.parentFile?.mkdirs()
        if (file.isFile && file.readBytes().contentEquals(data)) return
        val staged = File(file.parentFile, ".${file.name}.staged")
        staged.writeBytes(data)
        restrict(staged)
        if (!staged.renameTo(file)) {
            staged.delete()
            error(moe.n4tsu.dextop.localText("uiCannotStoreXauthority"))
        }
    }

    private fun restrict(file: File) = runCatching { android.system.Os.chmod(file.path, "600".toInt(8)) }
}
