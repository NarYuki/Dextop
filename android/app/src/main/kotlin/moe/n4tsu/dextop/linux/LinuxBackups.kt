package moe.n4tsu.dextop.linux

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Snapshots and backups of containers.
 *
 * A backup is a gzip-compressed tar file: first `dextop-backup.json` (what it holds), then the
 * container's directory under `container/` (config.json, settings.json, rootfs). What the
 * running system makes again by itself is left out: the session runtime and shared memory,
 * logs, /dev, /proc, /sys, /tmp and apt's downloaded packages. Snapshots are such files kept
 * on the device ([directory]); export copies one to a document, and import and restore unpack
 * one into a new container, so a backup never replaces an existing one.
 */
internal object LinuxBackups {
    const val FORMAT = 1
    const val EXTENSION = ".dextop-backup"
    private const val METADATA = "dextop-backup.json"
    private const val PREFIX = "container/"
    private val NAME = Regex("c[a-zA-Z0-9]+-[0-9]{8}-[0-9]{6}\\.dextop-backup")
    private val SKIPPED_CONTAINER = setOf("runtime", "shm", "rootfs-extract", "overlay-extract", "restore-staging")
    private val SKIPPED_ROOTFS = listOf("dev", "proc", "sys", "tmp", "run", "var/cache/apt/archives", "var/tmp")

    val directory: File get() = File(LinuxPaths.base, "snapshots")

    fun file(name: String): File {
        require(NAME.matches(name)) { "Invalid snapshot name" }
        return File(directory, name)
    }

    /** Snapshot files, newest first, with what each holds. */
    fun list(): JSONArray = JSONArray().apply {
        directory.listFiles().orEmpty().filter { NAME.matches(it.name) && it.isFile }
            .sortedByDescending { it.name.substringAfter('-') }
            .forEach { file ->
                val metadata = runCatching { readMetadata(file.inputStream()) }.getOrNull() ?: return@forEach
                put(JSONObject().put("file", file.name).put("size", file.length())
                    .put("container", metadata.optString("container")).put("name", metadata.optString("name"))
                    .put("distro", metadata.optString("distro")).put("created", metadata.optLong("created")))
            }
    }

    /** Writes [containerId] as a snapshot file; [onBytes] reports what was read so far. */
    fun create(containerId: String, onBytes: (Long) -> Unit): File {
        val config = JSONObject(LinuxPaths.config(containerId).readText())
        check(config.optString("state") == "installed") { "Only an installed container can be backed up" }
        directory.mkdirs()
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        val target = File(directory, "$containerId-$stamp$EXTENSION")
        val staged = File(directory, target.name + ".staged")
        try {
            staged.outputStream().buffered(1 shl 20).use { write(containerId, config, it, onBytes) }
            check(staged.renameTo(target)) { "Cannot save the snapshot" }
        } finally {
            staged.delete()
        }
        return target
    }

    private fun write(containerId: String, config: JSONObject, output: OutputStream, onBytes: (Long) -> Unit) {
        val gzip = object : GZIPOutputStream(output, 1 shl 20) { init { def.setLevel(Deflater.BEST_SPEED) } }
        TarArchiveOutputStream(gzip, "UTF-8").use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
            val metadata = JSONObject().put("format", FORMAT).put("created", System.currentTimeMillis())
                .put("container", containerId).put("name", config.optString("name"))
                .put("distro", config.optString("distro")).toString().toByteArray()
            tar.putArchiveEntry(TarArchiveEntry(METADATA).apply { size = metadata.size.toLong(); mode = "100644".toInt(8) })
            tar.write(metadata)
            tar.closeArchiveEntry()
            val root = LinuxPaths.container(containerId).toPath()
            val rootfs = root.resolve("rootfs")
            val skipped = SKIPPED_CONTAINER.map(root::resolve).toSet() + SKIPPED_ROOTFS.map(rootfs::resolve)
            val buffer = ByteArray(256 * 1024)
            var read = 0L
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (dir in skipped) return FileVisitResult.SKIP_SUBTREE
                    if (dir != root) add(dir, attrs)
                    return FileVisitResult.CONTINUE
                }
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (file in skipped || file.parent == root && file.fileName.toString().endsWith(".log")) return FileVisitResult.CONTINUE
                    add(file, attrs)
                    return FileVisitResult.CONTINUE
                }
                // A file the session left unreadable is skipped rather than ending the backup.
                override fun visitFileFailed(file: Path, exc: java.io.IOException): FileVisitResult = FileVisitResult.CONTINUE

                private fun add(path: Path, attrs: BasicFileAttributes) {
                    val name = PREFIX + root.relativize(path).joinToString("/")
                    val mode = runCatching { mode(Files.getPosixFilePermissions(path, NOFOLLOW_LINKS)) }.getOrDefault(if (attrs.isDirectory) 493 else 420)
                    when {
                        attrs.isSymbolicLink -> {
                            val entry = TarArchiveEntry(name, TarArchiveEntry.LF_SYMLINK)
                            entry.linkName = Files.readSymbolicLink(path).toString()
                            tar.putArchiveEntry(entry); tar.closeArchiveEntry()
                        }
                        attrs.isDirectory -> {
                            tar.putArchiveEntry(TarArchiveEntry("$name/").apply { this.mode = "40000".toInt(8) or mode })
                            tar.closeArchiveEntry()
                        }
                        attrs.isRegularFile -> {
                            val input = runCatching { Files.newInputStream(path) }.getOrNull() ?: return
                            input.use {
                                tar.putArchiveEntry(TarArchiveEntry(name).apply {
                                    size = attrs.size(); this.mode = "100000".toInt(8) or mode
                                    modTime = java.util.Date(attrs.lastModifiedTime().toMillis())
                                })
                                var left = attrs.size()
                                while (left > 0) {
                                    val count = it.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                                    if (count < 0) break
                                    tar.write(buffer, 0, count)
                                    left -= count
                                    read += count
                                    onBytes(read)
                                }
                                // A file that shrank while it was read is padded to its recorded size.
                                while (left > 0) { val n = minOf(buffer.size.toLong(), left).toInt(); java.util.Arrays.fill(buffer, 0, n, 0); tar.write(buffer, 0, n); left -= n }
                                tar.closeArchiveEntry()
                            }
                        }
                    }
                }
            })
            tar.finish()
        }
    }

    private fun mode(permissions: Set<PosixFilePermission>): Int =
        PosixFilePermission.entries.foldIndexed(0) { index, mode, permission -> if (permission in permissions) mode or (256 shr index) else mode }

    private fun readMetadata(input: InputStream): JSONObject = input.use {
        TarArchiveInputStream(GZIPInputStream(it.buffered(64 * 1024))).use { tar ->
            val entry = tar.nextEntry ?: error("Empty backup")
            require(entry.name == METADATA && entry.size in 1..65536) { "Not a Dextop backup" }
            JSONObject(String(tar.readBytes())).also { metadata ->
                require(metadata.optInt("format") in 1..FORMAT) { "This backup was made by a newer Dextop" }
            }
        }
    }

    /**
     * Unpacks the backup read from [open] (opened twice: once for what it holds, once to unpack)
     * as the new container [newId] and returns its config. [onBytes] reports what was written.
     */
    fun restore(open: () -> InputStream, newId: String, checkCancelled: () -> Unit, onBytes: (Long) -> Unit): JSONObject {
        val metadata = readMetadata(open())
        val container = LinuxPaths.container(newId)
        check(!container.exists()) { "Container $newId already exists" }
        val staging = File(LinuxPaths.containers, ".restore-$newId")
        LinuxRootfsArchive.deleteTree(staging)
        try {
            open().use { input ->
                LinuxRootfsArchive.extract(GZIPInputStream(input.buffered(1 shl 20)), staging, checkCancelled, onBytes)
            }
            val unpacked = File(staging, PREFIX.trimEnd('/'))
            check(File(unpacked, "config.json").isFile && File(unpacked, "rootfs").isDirectory) { "The backup holds no container" }
            check(unpacked.renameTo(container)) { "Cannot commit the restored container" }
        } finally {
            LinuxRootfsArchive.deleteTree(staging)
        }
        File(container, "rootfs/dev").mkdirs()
        File(container, "rootfs/tmp").apply { mkdirs(); setWritable(true, false); setExecutable(true, false); setReadable(true, false) }
        val config = JSONObject(File(container, "config.json").readText())
            .put("id", newId).put("state", "installed").put("created", System.currentTimeMillis())
            .put("name", metadata.optString("name").ifBlank { config0Name(container) })
            .put("restoredFrom", metadata.optString("container"))
        config.remove("error")
        File(container, "config.json").writeText(config.toString())
        return config
    }

    private fun config0Name(container: File) = runCatching { JSONObject(File(container, "config.json").readText()).optString("name") }.getOrDefault("Linux")
}
