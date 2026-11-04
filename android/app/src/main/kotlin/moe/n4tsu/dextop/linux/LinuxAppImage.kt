package moe.n4tsu.dextop.linux

import java.io.File
import java.io.RandomAccessFile

/**
 * AppImages without FUSE. An AppImage (type 2) is a small ELF runtime followed by a SquashFS
 * image; the runtime would mount that image through FUSE, which Android does not offer. Here
 * the image's offset is read from the ELF header (where the runtime's section headers end) and
 * the image is unpacked with unsquashfs into `~/Applications/.<name>`; the application then runs
 * from its own AppRun, as the mounted image would. Its desktop entry is written to the user's
 * applications, with its own name, icon and categories.
 */
internal object LinuxAppImage {
    const val ENTRY_PREFIX = "dextop-appimage-"
    private const val MACHINE_AARCH64 = 183
    private const val MACHINE_X86_64 = 62

    data class Image(val offset: Long, val machine: Int)

    /** The SquashFS offset of [file], or an error saying why it is not an AppImage this can run. */
    fun inspect(file: File): Image = RandomAccessFile(file, "r").use { input ->
        val header = ByteArray(64)
        require(input.length() > 64 && input.read(header) == 64) { "The file is too small to be an AppImage" }
        require(header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() && header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte()) {
            "Not an AppImage (no ELF runtime)"
        }
        require(header[4].toInt() == 2 && header[5].toInt() == 1) { "Only 64-bit AppImages are supported" }
        fun u16(at: Int) = (header[at].toInt() and 0xff) or ((header[at + 1].toInt() and 0xff) shl 8)
        fun u64(at: Int) = (0 until 8).fold(0L) { value, i -> value or ((header[at + i].toLong() and 0xff) shl (8 * i)) }
        val machine = u16(18)
        require(machine != MACHINE_X86_64) { "This AppImage is built for x86-64; Linux here runs ARM64 programs. Look for an aarch64 (ARM64) build." }
        require(machine == MACHINE_AARCH64) { "This AppImage is not built for ARM64" }
        val offset = u64(40) + u16(58).toLong() * u16(60)
        require(offset in 64 until input.length()) { "Not an AppImage (no image after its runtime)" }
        input.seek(offset)
        val magic = ByteArray(4)
        input.readFully(magic)
        require(String(magic, Charsets.ISO_8859_1) == "hsqs") { "Only type 2 AppImages (SquashFS) are supported" }
        Image(offset, machine)
    }

    /** A file and directory name from [fileName]: letters, digits, dot, dash and underscore. */
    fun slug(fileName: String): String {
        val base = fileName.substringAfterLast('/').removeSuffix(".AppImage").removeSuffix(".appimage")
        val cleaned = base.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-', '.').take(80)
        return cleaned.ifEmpty { "app" }
    }

    /** The guest script that unpacks [file] (guest path) at [offset] into [directory]. */
    fun unpackScript(file: String, offset: Long, directory: String): String = """
        set -e
        command -v unsquashfs >/dev/null 2>&1 || { echo "unsquashfs is not installed"; exit 3; }
        rm -rf '$directory.new'
        unsquashfs -no-xattrs -q -n -o $offset -d '$directory.new' '$file' >/dev/null
        [ -e '$directory.new/AppRun' ] || { echo "The AppImage has no AppRun"; rm -rf '$directory.new'; exit 4; }
        rm -rf '$directory'
        mv '$directory.new' '$directory'
        chmod -R u+rwX,go+rX '$directory'
    """.trimIndent() + "\n"

    /** Installs unsquashfs (as root) on the container's package manager. */
    const val TOOLS_SCRIPT = """
        command -v unsquashfs >/dev/null 2>&1 && exit 0
        if command -v apt-get >/dev/null 2>&1; then DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends squashfs-tools
        elif command -v pacman >/dev/null 2>&1; then pacman -S --noconfirm --needed squashfs-tools
        elif command -v dnf >/dev/null 2>&1; then dnf install -y squashfs-tools
        elif command -v zypper >/dev/null 2>&1; then zypper --non-interactive install squashfs
        elif command -v apk >/dev/null 2>&1; then apk add squashfs-tools
        else echo "No package manager to install squashfs-tools"; exit 2; fi
    """

    /**
     * The desktop entry for the unpacked application in [root] at guest path [directory], from
     * the entry the AppImage carries. Returns (file name, contents, display name).
     */
    fun desktopEntry(root: File, directory: String, appImage: String, slug: String): Triple<String, String, String> {
        val unpacked = File(root, directory.removePrefix("/"))
        val source = unpacked.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".desktop") }.minByOrNull { it.name }
        val values = LinkedHashMap<String, String>()
        source?.readLines()?.let { lines ->
            var inEntry = false
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("[")) inEntry = trimmed == "[Desktop Entry]"
                else if (inEntry && '=' in trimmed && !trimmed.startsWith("#")) {
                    val key = trimmed.substringBefore('=').trim()
                    if (key !in values) values[key] = trimmed.substringAfter('=').trim()
                }
            }
        }
        val name = values["Name"]?.takeIf { it.isNotBlank() } ?: slug
        // The program's own field codes (%U, %F…) follow AppRun, which starts it.
        val codes = values["Exec"].orEmpty().split(Regex("\\s+")).filter { it.matches(Regex("%[fFuU]")) }.distinct()
        // Electron applications cannot start their sandbox under PRoot.
        val electron = File(unpacked, "chrome-sandbox").exists() || File(unpacked, "resources/app.asar").exists()
        val icon = icon(unpacked, values["Icon"].orEmpty())?.let { "$directory/" + unpacked.toPath().relativize(it.toPath()).joinToString("/") }
        val exec = buildList {
            add("env"); add("APPDIR=$directory"); add("APPIMAGE=$appImage"); add("$directory/AppRun")
            if (electron) add("--no-sandbox")
            addAll(codes)
        }.joinToString(" ") { if (it.any(Char::isWhitespace)) "\"$it\"" else it }
        val contents = buildString {
            appendLine("[Desktop Entry]")
            appendLine("Type=Application")
            appendLine("Name=$name")
            values["GenericName"]?.let { appendLine("GenericName=$it") }
            values["Comment"]?.let { appendLine("Comment=$it") }
            appendLine("Exec=$exec")
            icon?.let { appendLine("Icon=$it") }
            values["Categories"]?.let { appendLine("Categories=$it") }
            values["MimeType"]?.let { appendLine("MimeType=$it") }
            values["StartupWMClass"]?.let { appendLine("StartupWMClass=$it") }
            values["Terminal"]?.let { appendLine("Terminal=$it") }
            appendLine("X-Dextop-AppImage=$directory")
        }
        return Triple("$ENTRY_PREFIX$slug.desktop", contents, name)
    }

    /** The icon file of an unpacked AppImage: its entry's Icon, else .DirIcon. */
    private fun icon(unpacked: File, name: String): File? {
        val candidates = if (name.isNotBlank()) listOf(".png", ".svg", ".xpm", "").map { File(unpacked, name + it) } +
            listOf("usr/share/icons/hicolor/256x256/apps", "usr/share/icons/hicolor/128x128/apps", "usr/share/icons/hicolor/scalable/apps",
                "usr/share/pixmaps").flatMap { dir -> listOf(".png", ".svg").map { File(unpacked, "$dir/$name$it") } }
            else emptyList()
        return (candidates + File(unpacked, ".DirIcon")).firstOrNull { file ->
            val path = file.toPath()
            // A link that points outside the unpacked image is not followed.
            java.nio.file.Files.exists(path) && runCatching { path.toRealPath().startsWith(unpacked.toPath().toRealPath()) }.getOrDefault(false)
        }
    }
}
