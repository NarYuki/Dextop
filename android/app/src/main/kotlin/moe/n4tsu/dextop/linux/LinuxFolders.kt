package moe.n4tsu.dextop.linux

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.LocalSocket
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap

/**
 * Folders picked in Android's folder picker, for Linux in the Android mode, which may not read
 * shared storage by path: Linux sees them as /media/android/<name>.
 *
 * Android grants such a folder as documents, not as a path, so Dextop keeps a tree of
 * placeholders bound at /media/android: directories, and sparse files of each document's size
 * and time, which listings and stat read as they are. The guest's preload
 * (linux_android_folders.c) asks the device bridge for the document's own descriptor when a
 * file is opened, and has Dextop create, delete and rename documents:
 *   "F O <flags> <path>"   open; answered with a descriptor
 *   "F M <path>"           mkdir
 *   "F D <path>"           unlink
 *   "F R <path>"           rmdir
 *   "F N <flags> <old>\t<new>"  rename within one folder
 *   "F L <path>"           read a directory again
 * Each answer is one status byte: 0, or the Linux errno for the application.
 */
internal object LinuxFolders {
    private const val TAG = "DextopLinuxFolders"
    private const val PREFS = "linux-folders"
    const val GUEST = "/media/android"
    private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"

    private const val EPERM = 1
    private const val ENOENT = 2
    private const val EIO = 5
    private const val EACCES = 13
    private const val EBUSY = 16
    private const val EEXIST = 17
    private const val EXDEV = 18
    private const val ENOTDIR = 20
    private const val EISDIR = 21
    private const val EINVAL = 22
    private const val ENOTEMPTY = 39
    private const val O_ACCMODE = 3
    private const val O_CREAT = 0x40
    private const val O_EXCL = 0x80
    private const val O_TRUNC = 0x200
    private const val RENAME_NOREPLACE = 1

    private class Errno(val code: Int) : Exception()
    private class Entry(val id: String, val name: String, val directory: Boolean, val size: Long, val modified: Long)
    private class Target(val name: String, val tree: Uri, val rel: String) {
        val parent get() = rel.substringBeforeLast('/', "")
        val leaf get() = rel.substringAfterLast('/')
    }

    private val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)
    /** Document ids by "<folder>/<path>"; ids of other providers are found by walking. */
    private val ids = ConcurrentHashMap<String, String>()
    private val lock = Any()

    /** The placeholder tree; inside the Android mode's storage, so a Linux reset removes it. */
    fun root(context: Context) = File(context.noBackupFilesDir, "linux-android/folders")

    /** Whether /media/android shows the picked folders: the Android mode (full privilege reads /storage). */
    fun active(context: Context) = LinuxExecutionMode.android(context)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun folders(context: Context): Map<String, Uri> =
        prefs(context).all.mapNotNull { (name, uri) -> (uri as? String)?.let { name to Uri.parse(it) } }.toMap()

    fun list(context: Context): List<Map<String, Any>> {
        val granted = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission && it.isWritePermission }.map { it.uri }.toSet()
        return folders(context).map { (name, uri) ->
            mapOf("name" to name, "path" to "$GUEST/$name", "granted" to (uri in granted))
        }.sortedBy { (it["name"] as String).lowercase() }
    }

    fun pick(context: Context) {
        context.startActivity(Intent(context, LinuxFolderPickerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Adds the folder the picker returned, named as Android names it. The list is saved before
     * this returns, so Linux settings shows it when the picker closes; the folder is read after.
     */
    fun add(context: Context, tree: Uri) {
        context.contentResolver.takePersistableUriPermission(tree,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val existing = folders(context)
        if (tree in existing.values) return
        val label = runCatching { info(context, tree, DocumentsContract.getTreeDocumentId(tree))?.name }.getOrNull()
            ?.replace('/', '_')?.trim()?.takeIf(::valid) ?: "Folder"
        var name = label
        var number = 2
        while (name in existing) name = "$label (${number++})"
        prefs(context).edit().putString(name, tree.toString()).commit()
        File(root(context), name).mkdirs()
        Thread({
            runCatching { refresh(context, Target(name, tree, "")) }.onFailure { Log.w(TAG, "Cannot read $name", it) }
        }, "DextopLinuxFolderRead").start()
    }

    fun remove(context: Context, name: String) {
        val tree = folders(context)[name] ?: return
        prefs(context).edit().remove(name).commit()
        if (tree !in folders(context).values) runCatching {
            context.contentResolver.releasePersistableUriPermission(tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        forget(name, "")
        synchronized(lock) { deleteTree(File(root(context), name)) }
    }

    /** Linux reset: every picked folder and its grant. */
    fun clear(context: Context) {
        for (name in folders(context).keys) remove(context, name)
        prefs(context).edit().clear().commit()
    }

    /** Brings the top of every placeholder tree up to date; Android may have changed the folders meanwhile. */
    fun syncAll(context: Context) {
        val folders = folders(context)
        synchronized(lock) {
            val root = root(context).apply { mkdirs() }
            root.list()?.forEach { if (it !in folders) deleteTree(File(root, it)) }
        }
        for ((name, tree) in folders) runCatching { refresh(context, Target(name, tree, "")) }
            .onFailure { Log.w(TAG, "Cannot read $name", it) }
    }

    fun serve(context: Context, socket: LocalSocket, request: String) {
        val status = try {
            handle(context, socket, request)
        } catch (e: Errno) {
            e.code
        } catch (e: FileNotFoundException) {
            ENOENT
        } catch (e: SecurityException) {
            EACCES
        } catch (e: UnsupportedOperationException) {
            EPERM
        } catch (e: Exception) {
            Log.w(TAG, "Folder request failed", e)
            EIO
        }
        if (status >= 0) socket.outputStream.run { write(status); flush() }
    }

    /** The status to answer, or -1 when the answer went out already. */
    private fun handle(context: Context, socket: LocalSocket, request: String): Int {
        val op = request.getOrNull(2) ?: throw Errno(EINVAL)
        val rest = request.drop(4)
        return when (op) {
            'O' -> open(context, socket, rest.substringBefore(' ').toInt(), target(context, rest.substringAfter(' ')))
            'M' -> mkdir(context, target(context, rest))
            'D' -> delete(context, target(context, rest), directory = false)
            'R' -> delete(context, target(context, rest), directory = true)
            'N' -> {
                val paths = rest.substringAfter(' ').split('\t')
                if (paths.size != 2) throw Errno(EINVAL)
                rename(context, target(context, paths[0]), target(context, paths[1]), rest.substringBefore(' ').toInt())
            }
            'L' -> if (refresh(context, target(context, rest))) 0 else ENOENT
            else -> EINVAL
        }
    }

    private fun valid(name: String) = name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\u0000' !in name

    private fun target(context: Context, path: String): Target {
        if (!path.startsWith("$GUEST/")) throw Errno(EACCES)
        val parts = path.removePrefix("$GUEST/").split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) throw Errno(EACCES)
        if (!parts.all(::valid)) throw Errno(EINVAL)
        val tree = folders(context)[parts[0]] ?: throw Errno(EACCES)
        return Target(parts[0], tree, parts.drop(1).joinToString("/"))
    }

    private fun shadow(context: Context, target: Target) =
        File(root(context), if (target.rel.isEmpty()) target.name else "${target.name}/${target.rel}")

    private fun uri(target: Target, id: String) = DocumentsContract.buildDocumentUriUsingTree(target.tree, id)

    private fun entry(cursor: Cursor) = Entry(cursor.getString(0), cursor.getString(1) ?: "",
        cursor.getString(2) == Document.MIME_TYPE_DIR,
        if (cursor.isNull(3)) 0 else cursor.getLong(3), if (cursor.isNull(4)) 0 else cursor.getLong(4))

    private fun info(context: Context, tree: Uri, id: String): Entry? = try {
        context.contentResolver.query(DocumentsContract.buildDocumentUriUsingTree(tree, id), columns, null, null, null)
            ?.use { if (it.moveToFirst()) entry(it) else null }
    } catch (_: FileNotFoundException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun children(context: Context, tree: Uri, id: String): List<Entry> =
        context.contentResolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, id), columns, null, null, null)
            ?.use { cursor -> buildList { while (cursor.moveToNext()) add(entry(cursor)) } }
            ?: throw IOException("No listing for $id")

    private fun resolve(context: Context, target: Target): Entry? {
        val treeId = DocumentsContract.getTreeDocumentId(target.tree)
        if (target.rel.isEmpty()) return info(context, target.tree, treeId)
        // Shared storage and USB volumes name documents by path.
        if (target.tree.authority == EXTERNAL_STORAGE)
            return info(context, target.tree, if (treeId.endsWith(":")) treeId + target.rel else "$treeId/${target.rel}")
        val key = "${target.name}/${target.rel}"
        ids[key]?.let { id -> info(context, target.tree, id)?.takeIf { it.name == target.leaf }?.let { return it } }
        val parent = resolve(context, Target(target.name, target.tree, target.parent)) ?: return null
        if (!parent.directory) return null
        return children(context, target.tree, parent.id).firstOrNull { it.name == target.leaf }?.also { ids[key] = it.id }
    }

    private fun forget(name: String, rel: String) {
        val key = if (rel.isEmpty()) name else "$name/$rel"
        ids.keys.removeAll { it == key || it.startsWith("$key/") }
    }

    private fun open(context: Context, socket: LocalSocket, flags: Int, target: Target): Int {
        var entry = resolve(context, target)
        if (entry == null) {
            if (flags and O_CREAT == 0) throw Errno(ENOENT)
            entry = create(context, target, directory = false)
        } else if (flags and O_CREAT != 0 && flags and O_EXCL != 0) throw Errno(EEXIST)
        if (entry.directory) throw Errno(EISDIR)
        val mode = when {
            flags and O_ACCMODE == 0 -> "r"
            flags and O_TRUNC != 0 -> "rwt"
            else -> "rw"
        }
        val descriptor = context.contentResolver.openFileDescriptor(uri(target, entry.id), mode) ?: throw Errno(EIO)
        descriptor.use {
            socket.setFileDescriptorsForSend(arrayOf(it.fileDescriptor))
            socket.outputStream.write(0)
            socket.outputStream.flush()
            socket.setFileDescriptorsForSend(null)
        }
        return -1
    }

    private fun create(context: Context, target: Target, directory: Boolean): Entry {
        if (target.rel.isEmpty()) throw Errno(EEXIST)
        val parent = resolve(context, Target(target.name, target.tree, target.parent)) ?: throw Errno(ENOENT)
        if (!parent.directory) throw Errno(ENOTDIR)
        val mime = if (directory) Document.MIME_TYPE_DIR else MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(target.leaf.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
        val resolver = context.contentResolver
        var uri = DocumentsContract.createDocument(resolver, uri(target, parent.id), mime, target.leaf) ?: throw Errno(EIO)
        var entry = info(context, target.tree, DocumentsContract.getDocumentId(uri)) ?: throw Errno(EIO)
        // Providers may adjust the name (an extension for the type, a number on a clash).
        if (entry.name != target.leaf) {
            DocumentsContract.renameDocument(resolver, uri, target.leaf)?.let { uri = it }
            entry = info(context, target.tree, DocumentsContract.getDocumentId(uri)) ?: throw Errno(EIO)
        }
        ids["${target.name}/${target.rel}"] = entry.id
        synchronized(lock) {
            val file = shadow(context, target)
            if (directory) file.mkdirs() else placeholder(file, 0, System.currentTimeMillis())
        }
        return entry
    }

    private fun mkdir(context: Context, target: Target): Int {
        if (target.rel.isEmpty() || resolve(context, target) != null) return EEXIST
        create(context, target, directory = true)
        return 0
    }

    private fun delete(context: Context, target: Target, directory: Boolean): Int {
        if (target.rel.isEmpty()) return EBUSY
        val entry = resolve(context, target) ?: return ENOENT
        if (directory && !entry.directory) return ENOTDIR
        if (!directory && entry.directory) return EISDIR
        // A provider deletes a directory with everything in it; rmdir only takes an empty one.
        if (directory && children(context, target.tree, entry.id).isNotEmpty()) return ENOTEMPTY
        if (!DocumentsContract.deleteDocument(context.contentResolver, uri(target, entry.id))) return EIO
        forget(target.name, target.rel)
        synchronized(lock) { deleteTree(shadow(context, target)) }
        return 0
    }

    private fun rename(context: Context, from: Target, to: Target, flags: Int): Int {
        if (from.name != to.name) return EXDEV
        if (from.rel.isEmpty() || to.rel.isEmpty()) return EBUSY
        val source = resolve(context, from) ?: return ENOENT
        if (from.rel == to.rel) return 0
        if (to.rel.startsWith("${from.rel}/")) return EINVAL
        val resolver = context.contentResolver
        resolve(context, to)?.let { existing ->
            if (flags and RENAME_NOREPLACE != 0) return EEXIST
            if (existing.directory) {
                if (!source.directory) return EISDIR
                if (children(context, to.tree, existing.id).isNotEmpty()) return ENOTEMPTY
            } else if (source.directory) return ENOTDIR
            if (!DocumentsContract.deleteDocument(resolver, uri(to, existing.id))) return EIO
            forget(to.name, to.rel)
        }
        var uri = uri(from, source.id)
        if (from.parent != to.parent) {
            val newParent = resolve(context, Target(to.name, to.tree, to.parent)) ?: return ENOENT
            if (!newParent.directory) return ENOTDIR
            val oldParent = resolve(context, Target(from.name, from.tree, from.parent)) ?: return ENOENT
            // Without move support, mv copies and deletes instead.
            uri = try {
                DocumentsContract.moveDocument(resolver, uri, uri(from, oldParent.id), uri(to, newParent.id))
            } catch (_: UnsupportedOperationException) {
                null
            } ?: return EXDEV
        }
        if (from.leaf != to.leaf) DocumentsContract.renameDocument(resolver, uri, to.leaf) ?: return EIO
        forget(from.name, from.rel)
        synchronized(lock) {
            val old = shadow(context, from)
            val new = shadow(context, to)
            deleteTree(new)
            new.parentFile?.mkdirs()
            if (!old.renameTo(new)) deleteTree(old)
        }
        if (!shadow(context, to).exists()) runCatching { refresh(context, Target(to.name, to.tree, to.parent)) }
        return 0
    }

    /**
     * Matches the placeholders of [target]'s directory to its documents. Directories below are
     * read when Linux opens or enters them (linux_android_folders.c), so a large folder costs
     * nothing until used.
     */
    private fun refresh(context: Context, target: Target): Boolean {
        val directory = resolve(context, target) ?: return false
        if (!directory.directory) return false
        val entries = children(context, target.tree, directory.id)
        synchronized(lock) {
            val dir = shadow(context, target)
            if (!dir.isDirectory || Files.isSymbolicLink(dir.toPath())) { deleteTree(dir); dir.mkdirs() }
            val names = HashSet<String>()
            for (entry in entries) {
                if (!valid(entry.name) || !names.add(entry.name)) continue
                val child = Target(target.name, target.tree, if (target.rel.isEmpty()) entry.name else "${target.rel}/${entry.name}")
                ids["${child.name}/${child.rel}"] = entry.id
                val file = File(dir, entry.name)
                if (entry.directory) {
                    if (!file.isDirectory || Files.isSymbolicLink(file.toPath())) { deleteTree(file); file.mkdir() }
                } else placeholder(file, entry.size, entry.modified)
            }
            dir.list()?.forEach { if (it !in names) deleteTree(File(dir, it)) }
        }
        return true
    }

    /** A sparse file of the document's size and time: stat and listings see the document. */
    private fun placeholder(file: File, size: Long, modified: Long) {
        if (Files.isSymbolicLink(file.toPath()) || file.isDirectory) deleteTree(file)
        if (!file.exists()) file.createNewFile()
        if (file.length() != size) RandomAccessFile(file, "rw").use { it.setLength(size) }
        if (modified > 0 && file.lastModified() != modified) file.setLastModified(modified)
    }

    /** Deletes [file] and what is under it without following links, which Linux may have made. */
    private fun deleteTree(file: File) {
        val path = file.toPath()
        if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        runCatching {
            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.deleteIfExists(file); return FileVisitResult.CONTINUE
                }
                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    Files.deleteIfExists(dir); return FileVisitResult.CONTINUE
                }
            })
        }.onFailure { Log.w(TAG, "Cannot remove $file", it) }
    }
}

/** Android's folder picker for Linux settings → Compatibility and permissions → Storage. */
class LinuxFolderPickerActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) runCatching {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 1)
        }.onFailure { finish() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val tree = data?.data
        if (resultCode == RESULT_OK && tree != null) {
            runCatching { LinuxFolders.add(applicationContext, tree) }.onFailure { Log.w("DextopLinuxFolders", "Cannot add folder", it) }
        }
        finish()
    }
}
