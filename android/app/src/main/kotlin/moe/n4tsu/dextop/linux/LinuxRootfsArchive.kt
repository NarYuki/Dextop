package moe.n4tsu.dextop.linux

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFileAttributeView

/** Extract into a fresh staging directory without executing Android programs under PRoot. */
internal object LinuxRootfsArchive {
    fun extract(input: InputStream, directory: File, checkCancelled: () -> Unit = {},
                onBytes: (Long) -> Unit = {}) = extractRooted(input, directory, "", checkCancelled, onBytes)

    fun extractBackup(input: InputStream, directory: File, checkCancelled: () -> Unit,
                      onBytes: (Long) -> Unit, sourceRoot: String? = null) =
        extractRooted(input, directory, "container/rootfs", checkCancelled, onBytes, sourceRoot)

    private fun extractRooted(input: InputStream, directory: File, guestPrefix: String,
                              checkCancelled: () -> Unit, onBytes: (Long) -> Unit, sourceRoot: String? = null) {
        val root = directory.toPath().toAbsolutePath().normalize()
        Files.createDirectories(root)
        require(Files.list(root).use { !it.findAny().isPresent }) { moe.n4tsu.dextop.localText("uiExtractionDirectoryMustBeEmpty") }
        fun path(name: String): Path {
            // Backslashes are ordinary on Linux (systemd escapes units like system-systemd\x2dveritysetup.slice).
            require(!name.startsWith('/')) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiUnsafeArchivePath0"), name) }
            return root.resolve(name).normalize().also {
                require(it.startsWith(root)) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiArchivePathEscapesRootfs0"), name) }
            }
        }
        val symlinks = linkedMapOf<Path, Path>()
        val hardlinks = linkedMapOf<Path, Path>()
        val directories = linkedMapOf<Path, Int>()
        val buffer = ByteArray(256 * 1024)
        var written = 0L
        TarArchiveInputStream(input).use { tar ->
            while (true) {
                checkCancelled()
                val entry = tar.nextEntry ?: break
                require(entry.isCheckSumOK) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiInvalidTarHeaderChecksum0"), entry.name) }
                val target = path(entry.name)
                if (target == root) continue
                // /dev is always bound to Android's /dev when the guest starts.
                if (target.startsWith(root.resolve("dev"))) continue
                try {
                    when {
                        entry.isDirectory -> {
                            Files.createDirectories(target)
                            directories[target] = entry.mode
                        }
                        entry.isSymbolicLink -> {
                            // Resolved the way the guest sees it: ".." stops at the guest's /.
                            val guestRoot = root.resolve(guestPrefix)
                            val linkRoot = if (target.startsWith(guestRoot)) guestRoot else root
                            val original = entry.linkName
                            val portable = if (sourceRoot != null &&
                                (original == sourceRoot || original.startsWith("$sourceRoot/")))
                                "/" + original.removePrefix(sourceRoot).trimStart('/') else original
                            val destination = guestPath(linkRoot, target.parent, portable)
                            // Relative equivalents keep absolute Linux links inside the rootfs on the host too.
                            val relative = target.parent.relativize(destination)
                            symlinks[target] = if (relative.toString().isEmpty()) Path.of(".") else relative
                        }
                        entry.isLink -> hardlinks.put(target, path(entry.linkName.removePrefix("/")))
                        entry.isFile -> {
                            Files.createDirectories(target.parent)
                            Files.newOutputStream(target).use { output ->
                                while (true) {
                                    checkCancelled()
                                    val count = tar.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                    written += count
                                    onBytes(written)
                                }
                            }
                            chmod(target, entry.mode)
                        }
                        // Like DroidDeck's extractor, FIFOs and device nodes outside /dev are left out.
                        else -> Unit
                    }
                } catch (error: InterruptedException) {
                    throw error
                } catch (error: Exception) {
                    throw java.io.IOException(moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiCannotExtract01"), entry.name, error.message), error)
                }
            }
        }
        // No symlinks exist while files are written, so archive links cannot redirect writes.
        while (hardlinks.isNotEmpty()) {
            val ready = hardlinks.filterValues { Files.isRegularFile(it, NOFOLLOW_LINKS) }
            // Links to files the archive never wrote are skipped, as DroidDeck does.
            if (ready.isEmpty()) break
            ready.forEach { (link, target) ->
                checkCancelled()
                Files.createDirectories(link.parent)
                try {
                    Files.createLink(link, target)
                } catch (_: java.io.IOException) {
                    // Samsung denies hard links to the shell user; a copy is equivalent for a fresh rootfs.
                    Files.copy(target, link, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES)
                    Files.setPosixFilePermissions(link, Files.getPosixFilePermissions(target, NOFOLLOW_LINKS))
                }
                hardlinks.remove(link)
            }
        }
        symlinks.forEach { (link, target) ->
            checkCancelled()
            Files.createDirectories(link.parent)
            Files.createSymbolicLink(link, target)
        }
        directories.entries.sortedByDescending { it.key.nameCount }.forEach { (dir, mode) -> chmod(dir, mode) }
        checkCancelled()
    }

    private fun guestPath(root: Path, parent: Path, link: String): Path {
        val parts = ArrayDeque<String>()
        val base = if (link.startsWith('/')) "" else root.relativize(parent).joinToString("/")
        for (part in "$base/$link".split('/')) when (part) {
            "", "." -> Unit
            ".." -> parts.removeLastOrNull()
            else -> parts.addLast(part)
        }
        return parts.fold(root) { path, part -> path.resolve(part) }
    }

    private fun chmod(path: Path, mode: Int) {
        val permissions = PosixFilePermission.entries.filterIndexed { index, _ -> mode and (256 shr index) != 0 }.toSet()
        try {
            Files.setPosixFilePermissions(path, permissions)
        } catch (_: UnsupportedOperationException) {
            // JVM unit tests on Windows have no POSIX attributes; Android supports them.
            path.toFile().setExecutable(mode and 73 != 0)
        }
    }

    /**
     * Moves everything under [source] into [target], replacing what is
     * there: a package extracted with [extract] laid over a root filesystem.
     * Directories merge; files and symlinks replace (never followed).
     */
    fun merge(source: File, target: File) {
        val from = source.toPath()
        val to = target.toPath()
        Files.walkFileTree(from, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val destination = to.resolve(from.relativize(dir).toString())
                if (Files.isSymbolicLink(destination) || (Files.exists(destination, NOFOLLOW_LINKS) && !Files.isDirectory(destination, NOFOLLOW_LINKS)))
                    Files.delete(destination)
                Files.createDirectories(destination)
                // Entries leave read-only package directories (0555) on both sides.
                dir.toFile().setWritable(true, true)
                destination.toFile().setWritable(true, true)
                return FileVisitResult.CONTINUE
            }
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val destination = to.resolve(from.relativize(file).toString())
                if (Files.isDirectory(destination, NOFOLLOW_LINKS)) deleteTree(destination.toFile())
                Files.move(file, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                return FileVisitResult.CONTINUE
            }
        })
    }

    /** walkFileTree does not follow symlinks, including links in an interrupted extraction. */
    fun deleteTree(directory: File) {
        if (!Files.exists(directory.toPath(), NOFOLLOW_LINKS)) return
        Files.walkFileTree(directory.toPath(), object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                // Android rejects getFileStore even when POSIX attributes are available.
                // Ask for the view directly; Windows returns null for unsupported attributes.
                Files.getFileAttributeView(dir, PosixFileAttributeView::class.java, NOFOLLOW_LINKS)?.let { view ->
                    view.setPermissions(view.readAttributes().permissions() +
                        PosixFilePermission.OWNER_WRITE + PosixFilePermission.OWNER_EXECUTE)
                }
                return FileVisitResult.CONTINUE
            }
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, error: java.io.IOException?): FileVisitResult {
                if (error != null) throw error
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }
}
