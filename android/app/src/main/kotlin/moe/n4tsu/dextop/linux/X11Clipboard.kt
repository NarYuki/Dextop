package moe.n4tsu.dextop.linux

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue

/**
 * The X CLIPBOARD selection of one display, bridged to Android.
 *
 * Text copied by an X client is fetched as soon as XFIXES reports the new
 * owner and passed to [onText]. [setText] makes Dextop the owner and serves
 * the text to clients that paste (UTF8_STRING, STRING, TEXT).
 */
internal class X11Clipboard(private val x: X11Client, private val onText: (String) -> Unit) {
    companion object {
        private const val TAG = "DextopX11Clipboard"
        private const val SELECTION_CLEAR = 29
        private const val SELECTION_REQUEST = 30
        private const val SELECTION_NOTIFY = 31
        private const val ATOM_ATOM = 4
        private const val ATOM_STRING = 31
    }

    private val window = x.createHiddenWindow()
    private val clipboard = x.internAtom("CLIPBOARD")
    private val utf8 = x.internAtom("UTF8_STRING")
    private val targets = x.internAtom("TARGETS")
    private val text = x.internAtom("TEXT")
    private val transfer = x.internAtom("DEXTOP_CLIPBOARD")
    private val incr = x.internAtom("INCR")
    private val queue = LinkedBlockingQueue<Pair<Int, ByteBuffer>>()
    @Volatile private var owned: ByteArray? = null
    @Volatile private var running = true
    /** Last text seen in either direction, so a change is reported once. */
    @Volatile private var last: String? = null

    init {
        x.selectSelectionInput(window, clipboard)
        Thread(::loop, "DextopX11Clipboard").apply { isDaemon = true; start() }
    }

    /** Takes clipboard events off the reader thread; returns whether [code] was one. */
    fun offer(code: Int, event: ByteBuffer): Boolean {
        val mine = code == SELECTION_CLEAR || code == SELECTION_REQUEST || code == SELECTION_NOTIFY ||
            (x.xfixesEventBase >= 0 && code == x.xfixesEventBase)
        if (mine) queue.offer(code to event)
        return mine
    }

    /** Owns CLIPBOARD with [value] (text from Android). Runs on the caller's thread so a following paste sees it. */
    fun setText(value: String) {
        if (value == last && owned != null) return
        val bytes = value.toByteArray(Charsets.UTF_8)
        owned = if (bytes.size + 64 <= x.maxRequestBytes) bytes else bytes.copyOf(x.maxRequestBytes - 64)
        last = value
        x.setSelectionOwner(window, clipboard)
    }

    fun stop() {
        running = false
        queue.offer(-1 to ByteBuffer.allocate(0))
    }

    private fun loop() {
        while (running) {
            val (code, event) = runCatching { queue.take() }.getOrNull() ?: break
            if (code < 0) break
            runCatching { handle(code, event) }.onFailure { Log.w(TAG, "clipboard event $code failed", it) }
        }
    }

    private fun handle(code: Int, event: ByteBuffer) {
        when (code) {
            x.xfixesEventBase -> {
                // XFixesSelectionNotify: window@4, owner@8, selection@12.
                val owner = event.getInt(8)
                if (event.getInt(12) == clipboard && owner != 0 && owner != window) {
                    owned = null
                    x.convertSelection(window, clipboard, utf8, transfer)
                }
            }
            SELECTION_NOTIFY -> {
                // requestor@8, selection@12, target@16, property@20.
                if (event.getInt(8) != window || event.getInt(20) == 0) return
                val (type, _, bytes) = x.property(window, transfer, maxBytes = 1 shl 20, delete = true) ?: return
                if (type == incr) return
                val value = String(bytes, if (type == ATOM_STRING) Charsets.ISO_8859_1 else Charsets.UTF_8)
                if (value.isNotEmpty() && value != last) {
                    last = value
                    onText(value)
                }
            }
            SELECTION_REQUEST -> serve(event)
            SELECTION_CLEAR -> if (event.getInt(8) == window) owned = null
        }
    }

    private fun serve(event: ByteBuffer) {
        // time@4, owner@8, requestor@12, selection@16, target@20, property@24.
        val time = event.getInt(4)
        val requestor = event.getInt(12)
        val selection = event.getInt(16)
        val target = event.getInt(20)
        val property = event.getInt(24).takeIf { it != 0 } ?: target
        val data = owned
        val answered = when {
            selection != clipboard || data == null -> 0
            target == targets -> {
                val list = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(targets).putInt(utf8).putInt(ATOM_STRING).putInt(text).array()
                x.changeProperty(requestor, property, ATOM_ATOM, 32, list)
                property
            }
            target == utf8 || target == text -> {
                x.changeProperty(requestor, property, utf8, 8, data)
                property
            }
            target == ATOM_STRING -> {
                x.changeProperty(requestor, property, ATOM_STRING, 8,
                    String(data, Charsets.UTF_8).toByteArray(Charsets.ISO_8859_1))
                property
            }
            else -> 0
        }
        x.sendSelectionNotify(requestor, selection, target, answered, time)
    }
}
