package moe.n4tsu.dextop.linux

import android.view.Surface
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/** JNI bridge (libdextop_linux.so) copying framebuffer rectangles into a Surface. */
internal object LinuxFrameBridge {
    init {
        val library = System.getProperty(LinuxServiceHost.LIBRARY_PROPERTY)
        if (library != null) System.load(library) else System.loadLibrary("dextop_linux")
    }

    @JvmStatic external fun nativeAttach(surface: Surface, width: Int, height: Int): Long
    @JvmStatic external fun nativeDetach(handle: Long)
    @JvmStatic external fun nativeBlit(
        handle: Long, buffer: ByteBuffer, offset: Int, stride: Int,
        fbWidth: Int, fbHeight: Int, x: Int, y: Int, w: Int, h: Int,
    ): Boolean

    /** Changes the Surface's buffer size (the X window was resized). */
    @JvmStatic external fun nativeResize(handle: Long, width: Int, height: Int): Boolean

    /** Like [nativeBlitPixels], from native memory at [address] (an MIT-SHM mapping). */
    @JvmStatic external fun nativeBlitAddress(
        handle: Long, address: Long, length: Long, stride: Int, x: Int, y: Int, w: Int, h: Int,
    ): Boolean

    /** Copies a w x h block of BGRX [pixels] (from [offset], rows [stride] bytes apart) to (x, y). */
    @JvmStatic external fun nativeBlitPixels(
        handle: Long, pixels: ByteArray, offset: Int, stride: Int, x: Int, y: Int, w: Int, h: Int,
    ): Boolean
}

/**
 * Xvfb's `-fbdir` screen file (XWD format), mapped read-only. Xvfb writes
 * pixels straight into this file, so the mapping always shows the live screen.
 */
internal class XvfbFramebuffer private constructor(
    val buffer: MappedByteBuffer,
    val width: Int,
    val height: Int,
    val stride: Int,
    val pixelOffset: Int,
) {
    companion object {
        /** Maps [file] once Xvfb has written a valid 32-bit ZPixmap header; null until then. */
        fun open(file: File): XvfbFramebuffer? {
            if (!file.isFile || file.length() < 100) return null
            RandomAccessFile(file, "r").use { raf ->
                val map = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
                val header = map.duplicate().order(ByteOrder.BIG_ENDIAN)
                val headerSize = header.getInt(0)
                val format = header.getInt(8)
                val width = header.getInt(16)
                val height = header.getInt(20)
                val bitsPerPixel = header.getInt(44)
                val bytesPerLine = header.getInt(48)
                val colors = header.getInt(76)
                if (format != 2 || bitsPerPixel != 32 || width <= 0 || height <= 0) return null
                val offset = headerSize + colors * 12
                if (offset.toLong() + bytesPerLine.toLong() * height > raf.length()) return null
                return XvfbFramebuffer(map, width, height, bytesPerLine, offset)
            }
        }
    }
}
