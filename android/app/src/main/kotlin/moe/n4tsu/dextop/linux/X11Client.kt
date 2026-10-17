package moe.n4tsu.dextop.linux

import android.graphics.Rect
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A minimal X11 protocol client: just enough of the core protocol, XTEST,
 * DAMAGE and Composite for Dextop to drive an Xvfb display from outside
 * proot, and to act as the window manager of its application windows.
 *
 * It connects to the abstract socket "@/tmp/.X11-unix/X<display>", which
 * proot does not translate, so the shell-side service reaches the server
 * directly. Byte order is little-endian ('l').
 */
internal class X11Client private constructor(
    private val socket: LocalSocket,
    private val input: DataInputStream,
    private val output: OutputStream,
) {
    companion object {
        private const val TAG = "DextopX11"
        const val KEY_PRESS = 2
        const val KEY_RELEASE = 3
        const val BUTTON_PRESS = 4
        const val BUTTON_RELEASE = 5
        const val MOTION_NOTIFY = 6
        const val DESTROY_NOTIFY = 17
        const val UNMAP_NOTIFY = 18
        const val MAP_NOTIFY = 19
        const val MAP_REQUEST = 20
        const val CONFIGURE_NOTIFY = 22
        const val CONFIGURE_REQUEST = 23
        const val PROPERTY_NOTIFY = 28

        const val EVENT_STRUCTURE_NOTIFY = 1 shl 17
        const val EVENT_SUBSTRUCTURE_NOTIFY = 1 shl 19
        const val EVENT_SUBSTRUCTURE_REDIRECT = 1 shl 20
        const val EVENT_PROPERTY_CHANGE = 1 shl 22

        const val CONFIG_X = 1
        const val CONFIG_Y = 2
        const val CONFIG_WIDTH = 4
        const val CONFIG_HEIGHT = 8
        const val CONFIG_BORDER = 16
        const val CONFIG_SIBLING = 32
        const val CONFIG_STACK_MODE = 64

        const val ATOM_WINDOW = 33
        const val ATOM_WM_NAME = 39
        const val ATOM_WM_CLASS = 67
        const val ATOM_WM_TRANSIENT_FOR = 68

        /** Marks a request that failed with an X error instead of a reply. */
        private val ERROR_REPLY: ByteBuffer = ByteBuffer.allocate(0)

        fun connect(display: Int): X11Client {
            val socket = LocalSocket(LocalSocket.SOCKET_STREAM)
            socket.connect(LocalSocketAddress("/tmp/.X11-unix/X$display", LocalSocketAddress.Namespace.ABSTRACT))
            return X11Client(socket, DataInputStream(socket.inputStream.buffered(256 * 1024)), socket.outputStream).apply { setup() }
        }
    }

    /** Receives every event (32 bytes, little-endian) on the reader thread; it must not wait for replies. */
    var onEvent: ((code: Int, event: ByteBuffer) -> Unit)? = null
    var onClosed: (() -> Unit)? = null

    var root = 0; private set
    var screenWidth = 0; private set
    var screenHeight = 0; private set
    var minKeycode = 8; private set
    var maxKeycode = 255; private set
    var xtestOpcode = -1; private set
    var damageOpcode = -1; private set
    var damageEventBase = -1; private set
    var compositeOpcode = -1; private set
    var shmOpcode = -1; private set
    var xfixesOpcode = -1; private set
    var xfixesEventBase = -1; private set
    /** Longest request the server accepts, in bytes. */
    var maxRequestBytes = 65535 * 4; private set

    private var idBase = 0
    private var idMask = 0
    private var nextId = 1
    private var sequence = 0
    private val replies = ConcurrentHashMap<Int, LinkedBlockingQueue<ByteBuffer>>()
    @Volatile private var closed = false

    private fun le(size: Int) = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    private fun pad(n: Int) = (4 - n % 4) % 4

    private fun setup() {
        output.write(le(12).put('l'.code.toByte()).put(0).putShort(11).putShort(0).putShort(0).putShort(0).putShort(0).array())
        output.flush()
        val head = ByteArray(8).also(input::readFully)
        val status = head[0].toInt()
        val extra = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN).getShort(6).toInt() and 0xffff
        val body = le(extra * 4).also { input.readFully(it.array()) }
        if (status != 1) {
            throw IOException("X11 connection refused: " + String(body.array(), Charsets.ISO_8859_1).trim())
        }
        idBase = body.getInt(4)
        idMask = body.getInt(8)
        val vendorLength = body.getShort(16).toInt() and 0xffff
        val formats = body.get(21).toInt() and 0xff
        maxRequestBytes = (body.getShort(18).toInt() and 0xffff) * 4
        minKeycode = body.get(26).toInt() and 0xff
        maxKeycode = body.get(27).toInt() and 0xff
        val screen = 32 + vendorLength + pad(vendorLength) + formats * 8
        root = body.getInt(screen)
        screenWidth = body.getShort(screen + 20).toInt() and 0xffff
        screenHeight = body.getShort(screen + 22).toInt() and 0xffff
        Thread(::readLoop, "DextopX11Reader").apply { isDaemon = true; start() }
        queryExtension("XTEST")?.let { xtestOpcode = it.first }
        queryExtension("DAMAGE")?.let { (opcode, eventBase) ->
            damageOpcode = opcode
            damageEventBase = eventBase
            // DAMAGE requires a version handshake before any other request.
            request(le(12).put(opcode.toByte()).put(0).putShort(3).putInt(1).putInt(1), reply = true)
        }
        queryExtension("MIT-SHM")?.let { (opcode, _) ->
            // AttachFd needs MIT-SHM 1.2; it shares memory through a file descriptor (no SysV IPC).
            val version = request(le(4).put(opcode.toByte()).put(0).putShort(1), reply = true)
            val major = version?.getShort(8)?.toInt() ?: 0
            val minor = version?.getShort(10)?.toInt() ?: 0
            if (major > 1 || (major == 1 && minor >= 2)) shmOpcode = opcode
        }
        queryExtension("XFIXES")?.let { (opcode, eventBase) ->
            xfixesOpcode = opcode
            xfixesEventBase = eventBase
            request(le(12).put(opcode.toByte()).put(0).putShort(3).putInt(5).putInt(0), reply = true)
        }
        queryExtension("Composite")?.let { (opcode, _) ->
            compositeOpcode = opcode
            request(le(12).put(opcode.toByte()).put(0).putShort(3).putInt(0).putInt(4), reply = true)
        }
        Log.i(TAG, "connected root=0x${root.toString(16)} ${screenWidth}x$screenHeight " +
            "keycodes=$minKeycode..$maxKeycode xtest=$xtestOpcode damage=$damageOpcode composite=$compositeOpcode")
    }

    fun allocateId(): Int = idBase or (nextId++ and idMask)

    // region transport
    private fun readLoop() {
        try {
            while (!closed) {
                val packet = le(32)
                input.readFully(packet.array())
                when (val type = packet.get(0).toInt() and 0x7f) {
                    0 -> {
                        val seq = packet.getShort(2).toInt() and 0xffff
                        val code = packet.get(1).toInt() and 0xff
                        // BadWindow, BadMatch and BadDrawable for windows that just vanished are routine.
                        if (code != 3 && code != 8 && code != 9) {
                            Log.w(TAG, "X error code=$code seq=$seq major=${packet.get(10).toInt() and 0xff} minor=${packet.getShort(8)}")
                        }
                        replies[seq]?.offer(ERROR_REPLY)
                    }
                    1 -> {
                        val extra = packet.getInt(4)
                        val full = le(32 + extra * 4)
                        full.put(packet.array())
                        if (extra > 0) input.readFully(full.array(), 32, extra * 4)
                        replies[packet.getShort(2).toInt() and 0xffff]?.offer(full)
                    }
                    else -> onEvent?.invoke(type, packet)
                }
            }
        } catch (error: IOException) {
            if (!closed) Log.w(TAG, "X connection closed", error)
        } finally {
            closed = true
            onClosed?.invoke()
        }
    }

    /**
     * Sends [packet] (position = its length); with [reply], waits for and
     * returns the reply, or null when the server answered with an error.
     */
    private fun request(packet: ByteBuffer, reply: Boolean = false, fd: java.io.FileDescriptor? = null): ByteBuffer? {
        val queue = if (reply) LinkedBlockingQueue<ByteBuffer>(1) else null
        val seq: Int
        synchronized(output) {
            sequence = (sequence + 1) and 0xffff
            seq = sequence
            if (queue != null) replies[seq] = queue
            // The descriptor travels as SCM_RIGHTS with the first bytes of this request.
            if (fd != null) socket.setFileDescriptorsForSend(arrayOf(fd))
            output.write(packet.array(), 0, packet.position())
            output.flush()
            if (fd != null) socket.setFileDescriptorsForSend(null)
        }
        if (queue == null) return null
        return try {
            val result = queue.poll(5, TimeUnit.SECONDS) ?: throw IOException("X11 reply timeout (seq $seq)")
            result.takeIf { it !== ERROR_REPLY }
        } finally {
            replies.remove(seq)
        }
    }

    fun close() {
        closed = true
        runCatching { socket.close() }
    }
    // endregion

    // region core protocol
    private fun queryExtension(name: String): Pair<Int, Int>? {
        val bytes = name.toByteArray(Charsets.ISO_8859_1)
        val length = 8 + bytes.size + pad(bytes.size)
        val packet = le(length).put(98).put(0).putShort((length / 4).toShort()).putShort(bytes.size.toShort()).putShort(0)
        packet.put(bytes).position(length)
        val reply = request(packet, reply = true) ?: return null
        if (reply.get(8).toInt() == 0) return null
        return (reply.get(9).toInt() and 0xff) to (reply.get(10).toInt() and 0xff)
    }

    fun internAtom(name: String): Int {
        val bytes = name.toByteArray(Charsets.ISO_8859_1)
        val length = 8 + bytes.size + pad(bytes.size)
        val packet = le(length).put(16).put(0).putShort((length / 4).toShort()).putShort(bytes.size.toShort()).putShort(0)
        packet.put(bytes).position(length)
        return request(packet, reply = true)?.getInt(8) ?: 0
    }

    /** Window owning [selection], or 0. */
    fun selectionOwner(selection: Int): Int =
        request(le(8).put(23).put(0).putShort(2).putInt(selection), reply = true)?.getInt(8) ?: 0

    /** Round trip (GetInputFocus): every earlier request has been processed when this returns. */
    fun sync() {
        request(le(4).put(43).put(0).putShort(1), reply = true)
    }

    /** Keysyms for [count] keycodes from [first]; returns (keysyms per keycode, keysyms). */
    fun keyboardMapping(first: Int, count: Int): Pair<Int, IntArray> {
        val reply = request(le(8).put(101).put(0).putShort(2).put(first.toByte()).put(count.toByte()).putShort(0), reply = true)!!
        val perKeycode = reply.get(1).toInt() and 0xff
        val keysyms = IntArray(perKeycode * count) { reply.getInt(32 + it * 4) }
        return perKeycode to keysyms
    }

    fun changeKeyboardMapping(keycode: Int, keysyms: IntArray) {
        val length = 8 + keysyms.size * 4
        val packet = le(length).put(100).put(1).putShort((length / 4).toShort())
            .put(keycode.toByte()).put(keysyms.size.toByte()).putShort(0)
        keysyms.forEach(packet::putInt)
        request(packet)
    }

    fun changeWindowAttributes(window: Int, mask: Int, values: IntArray) {
        val packet = le(12 + values.size * 4).put(2).put(0).putShort((3 + values.size).toShort()).putInt(window).putInt(mask)
        values.forEach(packet::putInt)
        request(packet)
    }

    fun selectInput(window: Int, mask: Int) = changeWindowAttributes(window, 0x800, intArrayOf(mask))

    /** (override-redirect, map state 0 unmapped / 1 unviewable / 2 viewable), or null if the window is gone. */
    fun windowAttributes(window: Int): Pair<Boolean, Int>? {
        val reply = request(le(8).put(3).put(0).putShort(2).putInt(window), reply = true) ?: return null
        return (reply.get(27).toInt() != 0) to (reply.get(26).toInt() and 0xff)
    }

    /** Position relative to the parent and size, or null if the window is gone. */
    fun geometry(drawable: Int): Rect? {
        val reply = request(le(8).put(14).put(0).putShort(2).putInt(drawable), reply = true) ?: return null
        val x = reply.getShort(12).toInt()
        val y = reply.getShort(14).toInt()
        return Rect(x, y, x + (reply.getShort(16).toInt() and 0xffff), y + (reply.getShort(18).toInt() and 0xffff))
    }

    fun mapWindow(window: Int) {
        request(le(8).put(8).put(0).putShort(2).putInt(window))
    }

    /** ConfigureWindow; [values] in the bit order of [mask] (x, y, width, height, border, sibling, stack mode). */
    fun configureWindow(window: Int, mask: Int, values: IntArray) {
        val packet = le(12 + values.size * 4).put(12).put(0).putShort((3 + values.size).toShort())
            .putInt(window).putShort(mask.toShort()).putShort(0)
        values.forEach(packet::putInt)
        request(packet)
    }

    fun raise(window: Int) = configureWindow(window, CONFIG_STACK_MODE, intArrayOf(0))

    fun setInputFocus(window: Int) {
        // RevertTo PointerRoot, CurrentTime.
        request(le(12).put(42).put(1).putShort(3).putInt(window).putInt(0))
    }

    /** GetProperty: (type, format, value bytes), or null when unset. */
    fun property(window: Int, property: Int, type: Int = 0, maxBytes: Int = 4096, delete: Boolean = false): Triple<Int, Int, ByteArray>? {
        val reply = request(
            le(24).put(20).put(if (delete) 1 else 0).putShort(6).putInt(window).putInt(property).putInt(type).putInt(0).putInt((maxBytes + 3) / 4),
            reply = true,
        ) ?: return null
        val format = reply.get(1).toInt() and 0xff
        val actualType = reply.getInt(8)
        if (actualType == 0 || format == 0) return null
        val length = (reply.getInt(16) * (format / 8)).coerceIn(0, reply.capacity() - 32)
        val bytes = ByteArray(length)
        System.arraycopy(reply.array(), 32, bytes, 0, length)
        return Triple(actualType, format, bytes)
    }

    fun propertyAtoms(window: Int, property: Int): IntArray {
        val (_, format, bytes) = property(window, property) ?: return IntArray(0)
        if (format != 32) return IntArray(0)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return IntArray(bytes.size / 4) { buffer.getInt(it * 4) }
    }

    /** SendEvent of a 32-byte event to [destination]. */
    private fun sendEvent(destination: Int, mask: Int, event: ByteBuffer) {
        val packet = le(44).put(25).put(0).putShort(11).putInt(destination).putInt(mask)
        packet.put(event.array(), 0, 32)
        request(packet)
    }

    /** ClientMessage of format 32 to [window], as used for WM_DELETE_WINDOW. */
    fun clientMessage(window: Int, type: Int, data: IntArray) {
        val event = le(32).put(33).put(32).putShort(0).putInt(window).putInt(type)
        for (i in 0 until 5) event.putInt(data.getOrElse(i) { 0 })
        sendEvent(window, 0, event)
    }

    /** An unmapped 1x1 InputOnly window on the root, for owning and requesting selections. */
    fun createHiddenWindow(): Int {
        val id = allocateId()
        // CreateWindow: depth 0 (CopyFromParent), class InputOnly (2), visual CopyFromParent, no attributes.
        request(le(32).put(1).put(0).putShort(8).putInt(id).putInt(root)
            .putShort(-10).putShort(-10).putShort(1).putShort(1).putShort(0).putShort(2).putInt(0).putInt(0))
        return id
    }

    fun setSelectionOwner(owner: Int, selection: Int) {
        request(le(16).put(22).put(0).putShort(4).putInt(owner).putInt(selection).putInt(0))
    }

    /** Asks the owner of [selection] to store it as [target] in [property] of [requestor]. */
    fun convertSelection(requestor: Int, selection: Int, target: Int, property: Int) {
        request(le(24).put(24).put(0).putShort(6).putInt(requestor).putInt(selection).putInt(target).putInt(property).putInt(0))
    }

    /** ChangeProperty (Replace) of [data]: bytes for format 8, little-endian 32-bit values for format 32. */
    fun changeProperty(window: Int, property: Int, type: Int, format: Int, data: ByteArray) {
        val padded = data.size + pad(data.size)
        val units = if (format == 32) data.size / 4 else data.size
        val packet = le(24 + padded).put(18).put(0).putShort(((24 + padded) / 4).toShort())
            .putInt(window).putInt(property).putInt(type).put(format.toByte()).put(0).putShort(0).putInt(units)
        packet.put(data).position(24 + padded)
        request(packet)
    }

    /** Answers a SelectionRequest; [property] 0 refuses it. */
    fun sendSelectionNotify(requestor: Int, selection: Int, target: Int, property: Int, time: Int) {
        val event = le(32).put(31).put(0).putShort(0).putInt(time).putInt(requestor)
            .putInt(selection).putInt(target).putInt(property)
        sendEvent(requestor, 0, event)
    }

    /** XFIXES: report every change of [selection]'s owner to [window] (SetSelectionOwner mask). */
    fun selectSelectionInput(window: Int, selection: Int) {
        if (xfixesOpcode < 0) return
        request(le(16).put(xfixesOpcode.toByte()).put(2).putShort(4).putInt(window).putInt(selection).putInt(1))
    }

    fun killClient(resource: Int) {
        request(le(8).put(113).put(0).putShort(2).putInt(resource))
    }

    /**
     * GetImage in ZPixmap format; the reply holds 32-bit BGRX pixels from
     * byte 32 with a row stride of width * 4. Null when the drawable is gone.
     */
    fun image(drawable: Int, area: Rect): ByteBuffer? = request(
        le(20).put(73).put(2).putShort(5).putInt(drawable)
            .putShort(area.left.toShort()).putShort(area.top.toShort())
            .putShort(area.width().toShort()).putShort(area.height().toShort()).putInt(-1),
        reply = true,
    )

    /** XTEST FakeInput. Motion is absolute on the root window. */
    fun fakeInput(type: Int, detail: Int, x: Int = 0, y: Int = 0) {
        if (xtestOpcode < 0) return
        val packet = le(36).put(xtestOpcode.toByte()).put(2).putShort(9)
            .put(type.toByte()).put(detail.toByte()).putShort(0)
            .putInt(0).putInt(if (type == MOTION_NOTIFY) root else 0).putInt(0).putInt(0)
            .putShort(x.toShort()).putShort(y.toShort()).putInt(0).putShort(0).put(0).put(0)
        request(packet)
    }
    // endregion

    // region MIT-SHM
    /** Attaches shared memory [fd] as segment [segment] (MIT-SHM AttachFd, read-write). */
    fun shmAttachFd(segment: Int, fd: java.io.FileDescriptor) {
        request(le(12).put(shmOpcode.toByte()).put(6).putShort(3).putInt(segment).put(0).put(0).putShort(0), fd = fd)
    }

    fun shmDetach(segment: Int) {
        request(le(8).put(shmOpcode.toByte()).put(2).putShort(2).putInt(segment))
    }

    /**
     * ShmGetImage (ZPixmap) of [area] of [drawable] into [segment] at offset 0,
     * rows width * 4 bytes apart. Returns false when the drawable is gone.
     */
    fun shmGetImage(drawable: Int, area: Rect, segment: Int): Boolean = request(
        le(32).put(shmOpcode.toByte()).put(4).putShort(8).putInt(drawable)
            .putShort(area.left.toShort()).putShort(area.top.toShort())
            .putShort(area.width().toShort()).putShort(area.height().toShort())
            .putInt(-1).put(2).put(0).putShort(0).putInt(segment).putInt(0),
        reply = true,
    ) != null
    // endregion

    // region DAMAGE and Composite
    /** Reports every change to [drawable] as DamageNotify (level RawRectangles: one event per drawn rectangle, nothing to subtract). */
    fun damageCreate(drawable: Int): Int {
        val id = allocateId()
        request(le(16).put(damageOpcode.toByte()).put(1).putShort(4).putInt(id).putInt(drawable).put(0).put(0).putShort(0))
        return id
    }

    fun damageDestroy(damage: Int) {
        // Minor opcodes: 0 QueryVersion, 1 Create, 2 Destroy, 3 Subtract.
        request(le(8).put(damageOpcode.toByte()).put(2).putShort(2).putInt(damage))
    }

    /** Keeps every top-level window in its own offscreen pixmap (manual redirection: nothing reaches the root). */
    fun redirectSubwindows(window: Int) {
        request(le(12).put(compositeOpcode.toByte()).put(2).putShort(3).putInt(window).put(1).put(0).putShort(0))
    }

    /**
     * Names the offscreen pixmap holding [window]'s contents. Unlike the
     * window itself, it can be read with GetImage when the window is partly
     * off the screen. A new pixmap backs the window after each resize.
     */
    fun nameWindowPixmap(window: Int): Int {
        val pixmap = allocateId()
        request(le(12).put(compositeOpcode.toByte()).put(6).putShort(3).putInt(window).putInt(pixmap))
        return pixmap
    }

    fun freePixmap(pixmap: Int) {
        request(le(8).put(54).put(0).putShort(2).putInt(pixmap))
    }
    // endregion
}
