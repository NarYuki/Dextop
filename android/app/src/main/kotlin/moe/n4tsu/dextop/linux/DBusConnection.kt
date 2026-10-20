package moe.n4tsu.dextop.linux

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A D-Bus message: header fields plus the raw little-endian body. */
internal class DBusMessage(
    val type: Int,
    val serial: Int,
    val path: String? = null,
    val iface: String? = null,
    val member: String? = null,
    val errorName: String? = null,
    val replySerial: Int = 0,
    val destination: String? = null,
    val sender: String? = null,
    val signature: String = "",
    val body: ByteArray = ByteArray(0),
) {
    companion object {
        const val METHOD_CALL = 1
        const val METHOD_RETURN = 2
        const val ERROR = 3
        const val SIGNAL = 4
    }

    /** Reader over [body]; alignment is relative to the body start, which D-Bus aligns to 8. */
    fun reader() = DBusReader(ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN))
}

internal class DBusReader(private val buffer: ByteBuffer) {
    private fun align(n: Int) {
        val pad = (n - buffer.position() % n) % n
        buffer.position(buffer.position() + pad)
    }

    fun byte(): Int = buffer.get().toInt() and 0xff
    fun u32(): Int { align(4); return buffer.int }
    fun string(): String {
        val length = u32()
        val bytes = ByteArray(length).also(buffer::get)
        buffer.get()
        return String(bytes, Charsets.UTF_8)
    }
    fun strings(): List<String> {
        val length = u32()
        val end = buffer.position() + length
        val values = ArrayList<String>()
        while (buffer.position() < end) values += string()
        return values
    }
}

/** Little-endian D-Bus marshalling with alignment relative to the message start. */
internal class DBusWriter(private val start: Int = 0) {
    private val out = ByteArrayOutputStream()
    val size get() = start + out.size()
    fun bytes(): ByteArray = out.toByteArray()

    fun align(n: Int) { while (size % n != 0) out.write(0) }
    fun byte(value: Int) = out.write(value)
    fun u32(value: Int) {
        align(4)
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
    }
    fun string(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        u32(bytes.size)
        out.write(bytes)
        out.write(0)
    }
    fun signature(value: String) {
        out.write(value.length)
        out.write(value.toByteArray(Charsets.US_ASCII))
        out.write(0)
    }

    /** An array whose elements need [elementAlignment]; [content] writes them into this writer. */
    fun array(elementAlignment: Int, content: DBusWriter.() -> Unit) {
        align(4)
        val lengthAt = out.size()
        u32(0)
        align(elementAlignment)
        val begin = out.size()
        content()
        val length = out.size() - begin
        val raw = out.toByteArray()
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).putInt(lengthAt, length)
        out.reset()
        out.write(raw)
    }
}

/**
 * A client connection to a D-Bus bus on an abstract unix socket, with
 * EXTERNAL authentication. Just what Dextop's notification server needs.
 */
internal class DBusConnection private constructor(
    private val socket: LocalSocket,
    private val input: DataInputStream,
    private val output: OutputStream,
) {
    companion object {
        /** Connects to "unix:abstract=<name>"; [uids] are tried in order for EXTERNAL authentication. */
        fun connect(abstractName: String, uids: List<Int>): DBusConnection {
            var lastError: IOException? = null
            for (uid in uids) {
                val socket = LocalSocket(LocalSocket.SOCKET_STREAM)
                try {
                    socket.connect(LocalSocketAddress(abstractName, LocalSocketAddress.Namespace.ABSTRACT))
                    val output = socket.outputStream
                    val input = DataInputStream(socket.inputStream.buffered())
                    val hex = uid.toString().toByteArray().joinToString("") { "%02x".format(it) }
                    output.write(0)
                    output.write("AUTH EXTERNAL $hex\r\n".toByteArray())
                    output.flush()
                    val answer = readLine(input)
                    if (!answer.startsWith("OK")) throw IOException("D-Bus authentication refused: $answer")
                    output.write("BEGIN\r\n".toByteArray())
                    output.flush()
                    return DBusConnection(socket, input, output)
                } catch (error: IOException) {
                    lastError = error
                    runCatching { socket.close() }
                }
            }
            throw lastError ?: IOException("no D-Bus credentials to try")
        }

        private fun readLine(input: DataInputStream): String {
            val line = StringBuilder()
            while (true) {
                val c = input.read()
                if (c < 0) throw IOException("D-Bus closed during authentication")
                if (c == '\n'.code) return line.toString().trimEnd('\r')
                line.append(c.toChar())
            }
        }
    }

    private var serial = 0

    @Synchronized
    fun send(
        type: Int, path: String? = null, iface: String? = null, member: String? = null,
        destination: String? = null, replySerial: Int = 0, errorName: String? = null,
        signature: String = "", body: DBusWriter.() -> Unit = {},
    ): Int {
        serial += 1
        // The body is aligned as if it started at offset 0 (it starts at an 8-aligned offset).
        val content = DBusWriter().apply(body).bytes()
        val header = DBusWriter()
        header.byte('l'.code); header.byte(type); header.byte(0); header.byte(1)
        header.u32(content.size)
        header.u32(serial)
        header.array(8) {
            fun field(code: Int, sig: String, write: DBusWriter.() -> Unit) {
                align(8); byte(code); signature(sig); write()
            }
            path?.let { field(1, "o") { string(it) } }
            iface?.let { field(2, "s") { string(it) } }
            member?.let { field(3, "s") { string(it) } }
            errorName?.let { field(4, "s") { string(it) } }
            if (replySerial != 0) field(5, "u") { u32(replySerial) }
            destination?.let { field(6, "s") { string(it) } }
            if (signature.isNotEmpty()) field(8, "g") { signature(signature) }
        }
        header.align(8)
        output.write(header.bytes())
        output.write(content)
        output.flush()
        return serial
    }

    /** Blocks until the next message arrives. */
    fun receive(): DBusMessage {
        val fixed = ByteArray(16).also(input::readFully)
        val head = ByteBuffer.wrap(fixed).order(ByteOrder.LITTLE_ENDIAN)
        if (fixed[0] != 'l'.code.toByte()) throw IOException("big-endian D-Bus messages are not supported")
        val type = fixed[1].toInt()
        val bodyLength = head.getInt(4)
        val serial = head.getInt(8)
        val fieldsLength = head.getInt(12)
        val fieldsPadded = fieldsLength + (8 - (16 + fieldsLength) % 8) % 8
        val fields = ByteArray(fieldsPadded).also(input::readFully)
        val body = ByteArray(bodyLength).also(input::readFully)
        // Header fields are aligned relative to the message start (offset 16 here).
        val buffer = ByteBuffer.wrap(ByteArray(16) + fields).order(ByteOrder.LITTLE_ENDIAN).apply { position(16) }
        val values = HashMap<Int, Any>()
        while (buffer.position() < 16 + fieldsLength) {
            while (buffer.position() % 8 != 0) buffer.get()
            val code = buffer.get().toInt()
            val sigLength = buffer.get().toInt() and 0xff
            val sig = String(ByteArray(sigLength).also(buffer::get))
            buffer.get()
            when (sig) {
                "s", "o" -> {
                    while (buffer.position() % 4 != 0) buffer.get()
                    val length = buffer.int
                    values[code] = String(ByteArray(length).also(buffer::get), Charsets.UTF_8)
                    buffer.get()
                }
                "g" -> {
                    val length = buffer.get().toInt() and 0xff
                    values[code] = String(ByteArray(length).also(buffer::get))
                    buffer.get()
                }
                "u" -> {
                    while (buffer.position() % 4 != 0) buffer.get()
                    values[code] = buffer.int
                }
                else -> throw IOException("unexpected D-Bus header field type $sig")
            }
        }
        return DBusMessage(
            type, serial,
            path = values[1] as String?, iface = values[2] as String?, member = values[3] as String?,
            errorName = values[4] as String?, replySerial = values[5] as Int? ?: 0,
            destination = values[6] as String?, sender = values[7] as String?,
            signature = values[8] as String? ?: "", body = body,
        )
    }

    fun close() {
        runCatching { socket.close() }
    }
}
