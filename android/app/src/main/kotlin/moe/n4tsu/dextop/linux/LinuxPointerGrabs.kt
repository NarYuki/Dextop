package moe.n4tsu.dextop.linux

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

/** Passive RECORD observer: never grabs/ungrabs the guest's pointer to probe it. */
internal class LinuxPointerGrabs(display: Int) {
    private data class Request(val window: Int, val xi: Boolean)
    private val pending = ConcurrentHashMap<Pair<Int, Int>, Request>()
    private val owners = ConcurrentHashMap<Int, Int>()
    private val connection = run {
        val x = runCatching { X11Client.connect(display) }.getOrNull() ?: return@run null
        runCatching { check(x.recordPointerGrabs(::record)); x }.getOrElse { x.close(); null }
    }
    val requested get() = owners.isNotEmpty()
    fun requestedFor(window: Int, root: Int, focus: Int, parent: (Int) -> Int,
                     inputOnly: (Int) -> Boolean): Boolean = owners.values.any { grab ->
        LinuxPointerGrabTarget.matches(grab, window, root, focus, parent, inputOnly)
    }
    private fun record(reply: ByteBuffer) {
        val category = reply.get(1).toInt() and 255
        val owner = reply.getInt(12)
        if (category == 3) { owners.remove(owner); pending.keys.removeAll { it.first == owner }; return }
        if (category !in 0..1 || reply.capacity() < 36) return
        val data = reply.duplicate().order(if (reply.get(9).toInt() == 0) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        var offset = 32
        while (offset + 4 <= data.capacity()) {
            val opcode = data.get(offset).toInt() and 255
            val minor = data.get(offset + 1).toInt() and 255
            if (category == 1) {
                val bytes = (data.getShort(offset + 2).toInt() and 65535) * 4
                if (bytes < 4 || offset + bytes > data.capacity()) break
                val seq = reply.getInt(20) and 65535
                if ((opcode == 26 || opcode >= 128 && minor == 51) && bytes >= 24)
                    pending[owner to seq] = Request(data.getInt(offset + 4), opcode != 26)
                if (opcode == 27 || opcode >= 128 && minor == 52) owners.remove(owner)
                offset += bytes
            } else {
                if (offset + 32 > data.capacity()) break
                val seq = data.getShort(offset + 2).toInt() and 65535
                pending.remove(owner to seq)?.let { request ->
                    val status = data.get(offset + if (request.xi) 8 else 1).toInt() and 255
                    if (opcode == 1 && status == 0) owners[owner] = request.window
                }
                offset += 32 + data.getInt(offset + 4).coerceAtLeast(0) * 4
            }
        }
    }
    fun stop() { connection?.close(); pending.clear(); owners.clear() }
}

/** Wine's ClipCursor uses a root-level InputOnly helper, outside the game tree. */
internal object LinuxPointerGrabTarget {
    fun matches(grab: Int, window: Int, root: Int, focus: Int,
                parent: (Int) -> Int, inputOnly: (Int) -> Boolean): Boolean {
        fun belongs(start: Int): Boolean {
            var current = start
            repeat(64) {
                if (current == window) return true
                if (current == 0 || current == root) return false
                current = parent(current)
            }
            return false
        }
        if (grab == 0 || window == 0) return false
        if (grab == root || belongs(grab)) return true
        // A visible helper's active grab already routes physical input at the
        // X server. Capture for the focused client, without activating helpers
        // or treating another ordinary application window as this game's child.
        return belongs(focus) && parent(grab) == root && inputOnly(grab)
    }
}
