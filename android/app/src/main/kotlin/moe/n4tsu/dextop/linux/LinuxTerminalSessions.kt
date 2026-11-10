package moe.n4tsu.dextop.linux

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The terminal tabs of each container. Shells belong to the app process, so closing a window
 * leaves them running; tabs and their output are also saved for the next start of the app.
 */
internal object LinuxTerminalSessions {
    private const val TAG = "DextopLinuxTerminal"
    private const val SAVE_DELAY_SECONDS = 2L

    /** Receives a window's view of the tabs; called off the main thread. */
    interface Sink {
        fun output(id: String, bytes: ByteArray, count: Int)
        fun notice(id: String, message: String)
        /** Kept output follows, drawn at the size it was recorded at. */
        fun replayStart(id: String, rows: Int, cols: Int)
        /** Kept output has been sent; a non-null [restored] means its program is gone. */
        fun replayEnd(id: String, restored: String?)
        fun ended(id: String, remove: Boolean)
    }

    private val groups = ConcurrentHashMap<String, Group>()
    private val saver = Executors.newSingleThreadScheduledExecutor { Thread(it, "DextopTerminalSave").apply { isDaemon = true } }

    private fun root(context: Context) = File(context.noBackupFilesDir, "linux-terminal")

    fun group(context: Context, container: String): Group = groups.getOrPut(container) {
        Group(container, LinuxTerminalStore(File(root(context), LinuxTerminalNames.directory(container))))
    }

    /** Ends the container's shells and deletes what was saved for them. */
    fun forget(context: Context, container: String) {
        groups.remove(container)?.closeAll()
        LinuxTerminalStore(File(root(context), LinuxTerminalNames.directory(container))).clear()
    }

    fun forgetAll(context: Context) {
        groups.keys.toList().forEach { groups.remove(it)?.closeAll() }
        root(context).deleteRecursively()
    }

    class Session(val id: String, @Volatile var title: String, @Volatile var directory: String?, output: ByteArray, val restored: Boolean) {
        val scrollback = LinuxTerminalScrollback(initial = output)
        @Volatile var pty: ParcelFileDescriptor? = null
        @Volatile var stream: FileOutputStream? = null
        @Volatile var opening = false
        @Volatile var exitRequested = false
        @Volatile var rows = 24
        @Volatile var cols = 80
        @Volatile var closed = false
        @Volatile var attached = false
        @Volatile var outputDirty = false
        val writer = Executors.newSingleThreadExecutor { Thread(it, "DextopTerminalWrite").apply { isDaemon = true } }

        fun write(bytes: ByteArray) {
            if (!closed) runCatching { writer.execute { runCatching { stream?.write(bytes) } } }
        }

        fun resize(rows: Int, cols: Int) {
            if (rows > 0 && cols > 0) { this.rows = rows; this.cols = cols }
            val fd = pty?.fd ?: return
            if (rows > 0 && cols > 0) LinuxPty.nativeResize(fd, rows, cols)
        }
    }

    class Group internal constructor(private val container: String, private val store: LinuxTerminalStore) {
        private val sessions = LinkedHashMap<String, Session>()
        private val saveScheduled = AtomicBoolean(false)
        @Volatile private var sink: Sink? = null
        @Volatile var active: String? = null
            private set

        init {
            val saved = store.load()
            saved.tabs.forEach { tab ->
                sessions[tab.id] = Session(tab.id, tab.title, tab.directory, store.loadOutput(tab.id), restored = true).also {
                    if (tab.rows > 0 && tab.cols > 0) { it.rows = tab.rows; it.cols = tab.cols }
                }
            }
            active = saved.active ?: saved.tabs.firstOrNull()?.id
        }

        @Synchronized fun tabs(): List<Session> = sessions.values.toList()

        @Synchronized fun session(id: String): Session? = sessions[id]

        @Synchronized fun add(title: String): Session {
            val id = "t" + UUID.randomUUID().toString().replace("-", "").take(12)
            val inherited = sessions[active]?.directory
            return Session(id, title, inherited, ByteArray(0), restored = false).also {
                sessions[id] = it
                active = id
                saveSoon()
            }
        }

        @Synchronized fun activate(id: String) {
            if (id in sessions && active != id) { active = id; saveSoon() }
        }

        /** Connects a window. Output from here on goes to [value] once each tab has been attached. */
        @Synchronized fun attach(value: Sink) {
            sessions.values.forEach { it.attached = false }
            sink = value
        }

        @Synchronized fun detach(value: Sink) {
            if (sink !== value) return
            sink = null
            sessions.values.forEach { it.attached = false }
            saveNow()
        }

        /** Sends [session]'s kept output to the window, then starts its shell if it has none. */
        fun open(context: Context, session: Session, rows: Int, cols: Int) {
            val target = sink ?: return
            val kept = synchronized(session) {
                session.attached = true
                session.scrollback.snapshot()
            }
            val running = session.pty != null || session.opening
            if (kept.isNotEmpty()) {
                // Shells position their own line edits for the width they saw, so output is only
                // right at that width; the terminal rewraps it to the current one afterwards.
                target.replayStart(session.id, session.rows, session.cols)
                target.output(session.id, kept, kept.size)
                target.replayEnd(session.id, if (session.restored && !running) moe.n4tsu.dextop.localText("uiPreviousTerminalSessionRestored") else null)
            }
            session.resize(rows, cols)
            if (running || session.closed) return
            spawn(context, session)
        }

        /** Starts a new shell in a tab whose previous one has ended. */
        fun restart(context: Context, session: Session) {
            if (session.pty == null && !session.closed) spawn(context, session)
        }

        private fun spawn(context: Context, session: Session) {
            if (session.opening) return
            session.opening = true
            val (rows, cols) = session.rows to session.cols
            LinuxContainerClient.connect(context)
            // The foreground service keeps running shells alive and online once Dextop is in the background.
            LinuxSessionService.start(context)
            val directory = session.directory.orEmpty()
            LinuxContainerClient.call(context, { it.openTerminalIn(container, rows, cols, directory) }, { descriptor ->
                session.opening = false
                if (session.closed) descriptor.close() else started(session, descriptor)
            }) { error ->
                session.opening = false
                sink?.notice(session.id, error.message ?: error.toString())
            }
        }

        private fun started(session: Session, descriptor: ParcelFileDescriptor) {
            session.pty = descriptor
            session.stream = FileOutputStream(descriptor.fileDescriptor)
            session.exitRequested = false
            Thread({ read(session, descriptor) }, "DextopTerminalRead").apply { isDaemon = true; start() }
        }

        private fun read(session: Session, descriptor: ParcelFileDescriptor) {
            val buffer = ByteArray(16 * 1024)
            runCatching {
                FileInputStream(descriptor.fileDescriptor).use { input ->
                    while (!session.closed) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        synchronized(session) {
                            session.scrollback.append(buffer, count)
                            session.exitRequested = LinuxTerminalExit.update(session.exitRequested, buffer, count)
                            session.outputDirty = true
                            if (session.attached) sink?.output(session.id, buffer.copyOf(count), count)
                        }
                        saveSoon()
                    }
                }
            }.onFailure { if (!session.closed) Log.i(TAG, "terminal ended: ${it.message}") }
            if (session.closed) return
            // Only the shell's own exit closes the tab. Output ends the same way when the app or
            // the device goes down, and that must leave the tab to be restored.
            if (session.exitRequested) {
                close(session.id)
                sink?.ended(session.id, remove = true)
            } else {
                release(session)
                session.pty = null
                sink?.ended(session.id, remove = false)
            }
        }

        private fun release(session: Session) {
            runCatching { session.pty?.close() }
            session.stream = null
        }

        /** Closes a tab; ending its pseudo-terminal hangs up the shell. */
        fun close(id: String): Boolean {
            val empty = synchronized(this) {
                val session = sessions.remove(id) ?: return sessions.isEmpty()
                session.closed = true
                release(session)
                session.writer.shutdown()
                store.remove(id)
                if (active == id) active = sessions.keys.lastOrNull()
                saveSoon()
                sessions.isEmpty()
            }
            return empty
        }

        fun rename(id: String, title: String) {
            session(id)?.let { if (it.title != title) { it.title = title; saveSoon() } }
        }

        fun directory(id: String, path: String) {
            val value = LinuxTerminalState.directory(path) ?: return
            session(id)?.let { if (it.directory != value) { it.directory = value; saveSoon() } }
        }

        @Synchronized fun closeAll() {
            sessions.values.forEach {
                it.closed = true
                release(it)
                it.writer.shutdown()
            }
            sessions.clear()
            sink = null
        }

        private fun saveSoon() {
            if (saveScheduled.compareAndSet(false, true)) saver.schedule({
                saveScheduled.set(false)
                runCatching { save() }.onFailure { Log.w(TAG, "saving terminal tabs failed: ${it.message}") }
            }, SAVE_DELAY_SECONDS, TimeUnit.SECONDS)
        }

        fun saveNow() {
            saver.execute { runCatching { save() }.onFailure { Log.w(TAG, "saving terminal tabs failed: ${it.message}") } }
        }

        private fun save() {
            val (tabs, current) = synchronized(this) { sessions.values.toList() to active }
            tabs.filter { it.outputDirty }.forEach {
                it.outputDirty = false
                store.saveOutput(it.id, it.scrollback.snapshot())
            }
            store.saveState(LinuxTerminalState(tabs.map { LinuxTerminalTab(it.id, it.title, it.directory, it.rows, it.cols) }, current))
        }
    }

}

internal object LinuxTerminalNames {
    /** Container ids are checked by LinuxPaths; this keeps a stray one inside the terminal folder. */
    fun directory(container: String): String = container.filter { it.isLetterOrDigit() }.ifEmpty { "default" }
}
