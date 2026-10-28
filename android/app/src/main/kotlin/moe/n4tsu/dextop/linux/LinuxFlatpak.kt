package moe.n4tsu.dextop.linux

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

/** Opt-in compatibility execution of deployed ARM64 Flatpaks using PRoot's filesystem
 * translation. There is deliberately no claim of Flatpak sandbox/portal enforcement. */
internal object LinuxFlatpak {
    data class Plan(val binds: List<String>, val environment: List<String>, val command: String)
    private val component = Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,199}")
    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    internal fun metadata(file: File): Map<String, Map<String, String>> {
        val sections = linkedMapOf<String, MutableMap<String, String>>()
        var section = ""
        file.forEachLine { raw ->
            val line = raw.trim()
            when {
                line.startsWith('[') && line.endsWith(']') -> section = line.substring(1, line.length - 1)
                '=' in line && !line.startsWith('#') -> sections.getOrPut(section) { linkedMapOf() }
                    .put(line.substringBefore('='), line.substringAfter('='))
            }
        }
        return sections
    }

    fun plan(rootfs: File, id: String): Plan {
        require(component.matches(id)) { "Invalid Flatpak application ID" }
        val base = "/root/.local/share/flatpak"
        val active = LinuxDesktopEntries.resolve(rootfs, "$base/app/$id/current/active")
            ?: error("Flatpak $id is not deployed; install it from Flathub first")
        val appMetadata = metadata(File(active, "metadata"))
        val application = appMetadata["Application"] ?: error("Missing Flatpak application metadata")
        val reference = application["runtime"].orEmpty().split('/')
        require(reference.size == 3 && reference.all(component::matches) && reference[1] == "aarch64") {
            "Compatibility execution supports ARM64 Flatpaks only"
        }
        val runtime = LinuxDesktopEntries.resolve(rootfs, "$base/runtime/${reference.joinToString("/")}/active")
            ?: error("Flatpak runtime is not deployed")
        val runtimeFiles = File(runtime, "files")
        val appFiles = File(active, "files")
        check(runtimeFiles.isDirectory && appFiles.isDirectory) { "Incomplete Flatpak deployment" }
        val executable = application["command"].orEmpty()
        require(executable.matches(Regex("[A-Za-z0-9_./+-]+"))) { "Unsupported Flatpak command" }
        val binds = mutableListOf("${runtimeFiles.absolutePath}:/usr", "${appFiles.absolutePath}:/app")
        // Preserve Dextop's glibc socket/session hooks when replacing /usr.
        binds += "${File(rootfs, "usr/local").absolutePath}:/usr/local"
        binds += executableBinds(rootfs, id, active, appFiles)
        // /usr now belongs to the runtime: the distro's /etc/fonts refers to
        // paths that no longer exist. Use the matching runtime configuration.
        val runtimeFonts = File(runtimeFiles, "etc/fonts")
        val fontEnvironment = mutableListOf<String>()
        if (runtimeFonts.isDirectory) {
            binds += "${runtimeFonts.absolutePath}:/etc/fonts"
            fontEnvironment += "FONTCONFIG_PATH=/etc/fonts"
            fontEnvironment += "FONTCONFIG_FILE=/etc/fonts/fonts.conf"
        }
        // Flatpak normally supplies these host fonts; PRoot must map them too,
        // especially CJK fonts which are absent from many runtimes.
        listOf("usr/share/fonts" to "fonts", "usr/local/share/fonts" to "local-fonts",
            "root/.local/share/fonts" to "user-fonts", "root/.fonts" to "legacy-fonts").forEach { (path, target) ->
            val fonts = File(rootfs, path)
            if (fonts.isDirectory) binds += "${fonts.absolutePath}:/run/host/$target"
        }
        val fontDirs = File(rootfs, "var/tmp/dextop-flatpak-font-dirs.xml").apply {
            parentFile.mkdirs()
            writeText("<?xml version=\"1.0\"?><fontconfig><dir>/run/host/legacy-fonts</dir></fontconfig>\n")
        }
        binds += "${fontDirs.absolutePath}:/run/host/font-dirs.xml"
        val empty = File(rootfs, "var/tmp/dextop-flatpak-empty-cache").apply { parentFile.mkdirs(); if (!exists()) writeText("") }
        binds += "${empty.absolutePath}:/etc/ld.so.cache"
        val libraryPaths = mutableListOf("/app/lib", "/app/lib/aarch64-linux-gnu", "/usr/lib/aarch64-linux-gnu", "/usr/lib")
        // Some runtime probes dlopen the development name while Flatpak only
        // ships the stable SONAME. Resolve it within the selected runtime.
        val openal = listOf("lib/aarch64-linux-gnu/libopenal.so.1", "lib/libopenal.so.1")
            .firstOrNull { File(runtimeFiles, it).isFile }
        if (openal != null && !File(runtimeFiles, openal.removeSuffix(".1")).exists()) {
            val aliasPath = "/usr/local/lib/dextop-flatpak/$id/aliases"
            val aliases = File(rootfs, aliasPath.removePrefix("/")).apply { mkdirs() }
            val alias = File(aliases, "libopenal.so")
            val target = "/usr/$openal"
            if (Files.isSymbolicLink(alias.toPath()) && Files.readSymbolicLink(alias.toPath()).toString() != target) {
                Files.delete(alias.toPath())
            }
            if (!Files.isSymbolicLink(alias.toPath()) && !alias.exists()) {
                Files.createSymbolicLink(alias.toPath(), File(target).toPath())
            }
            libraryPaths.add(0, aliasPath)
        }
        // Mount installed extensions such as Mesa GL.default, locales and codecs.
        fun extensions(data: Map<String, Map<String, String>>, prefix: String) {
            val runtimes = LinuxDesktopEntries.resolve(rootfs, "$base/runtime") ?: return
            data.filterKeys { it.startsWith("Extension ") }.forEach { (section, config) ->
                val extensionId = section.removePrefix("Extension ")
                val directory = config["directory"].orEmpty()
                if (!component.matches(extensionId) || directory.startsWith('/') ||
                    directory.split('/').any { it == ".." } || directory.isEmpty()) return@forEach
                val candidates = runtimes.listFiles().orEmpty().filter {
                    it.name == extensionId || config["subdirectories"] == "true" && it.name.startsWith("$extensionId.")
                }
                candidates.forEach { candidate ->
                    val versions = (config["versions"] ?: config["version"] ?: reference[2]).split(';').filter(String::isNotEmpty)
                    val extension = versions.firstNotNullOfOrNull { version ->
                        if (!component.matches(version)) null else LinuxDesktopEntries.resolve(rootfs,
                            "$base/runtime/${candidate.name}/aarch64/$version/active/files")
                    } ?: return@forEach
                    val suffix = if (config["subdirectories"] == "true") "/" + candidate.name.removePrefix("$extensionId.") else ""
                    val target = "$prefix/$directory$suffix"
                    binds += "${extension.absolutePath}:$target"
                    if (".GL" in extensionId) libraryPaths.add(0, "$target/lib")
                }
            }
        }
        extensions(metadata(File(runtime, "metadata")), "/usr")
        extensions(appMetadata, "/app")
        val data = "/root/.var/app/$id"
        listOf("config", "data", "cache").forEach { File(rootfs, "$data/$it".removePrefix("/")).mkdirs() }
        val environment = fontEnvironment + listOf("PATH=/app/bin:/usr/local/bin:/usr/bin:/bin", "LD_LIBRARY_PATH=${libraryPaths.joinToString(":")}",
            "XDG_CONFIG_HOME=$data/config", "XDG_DATA_HOME=$data/data", "XDG_CACHE_HOME=$data/cache",
            "XDG_DATA_DIRS=/app/share:/usr/share:/usr/local/share", "GTK_USE_PORTAL=0", "DEXTOP_FLATPAK_COMPAT=1")
        // Keep the container's selected renderer. The runtime's own Mesa/GL
        // extensions provide OpenGL; the KGSL Turnip ICD remains under /opt.
        // A software retry is selected by the caller, not forced for Flatpaks.
        // The common launcher can prepend the shared GPU runtime after these
        // defaults enter the process environment. Do not overwrite that choice.
        val command = "env " + environment.filterNot { it.startsWith("LD_LIBRARY_PATH=") }.joinToString(" ") {
            it.substringBefore('=') + "=" + quote(it.substringAfter('='))
        } +
            " " + quote(
            if (executable.startsWith('/')) executable else "/app/bin/$executable")
        return Plan(binds, environment, command)
    }

    /** Android deployments emulate hardlinks through OSTree object symlinks.
     * /proc/self/exe then names an object hash, breaking Qt/Java launchers that
     * locate companion resources relative to their executable. Keep a private
     * executable inode with a matching prefix, without modifying the deployment. */
    internal fun executableBinds(rootfs: File, id: String, active: File, app: File): List<String> {
        val revision = MessageDigest.getInstance("SHA-256").digest(active.absolutePath.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
        val prefix = File(rootfs, "var/tmp/dextop-flatpak-launch/$id/$revision")
        val bin = File(prefix, "bin").apply { mkdirs() }
        fun link(file: File, target: String) {
            if (!Files.isSymbolicLink(file.toPath()) && !file.exists()) {
                Files.createSymbolicLink(file.toPath(), File(target).toPath())
            }
        }
        app.listFiles().orEmpty().filter { it.name != "bin" }.forEach {
            link(File(prefix, it.name), "/app/${it.name}")
        }
        return File(app, "bin").listFiles().orEmpty().mapNotNull { source ->
            if (!source.isFile) return@mapNotNull null
            val elf = source.inputStream().use { input ->
                val magic = ByteArray(4)
                input.read(magic) == 4 && magic.contentEquals(byteArrayOf(127, 69, 76, 70))
            }
            val copy = File(bin, source.name)
            if (!elf) {
                link(copy, "/app/bin/${source.name}")
                return@mapNotNull null
            }
            if (!copy.isFile || copy.length() != source.length() || copy.lastModified() != source.lastModified()) {
                val pending = File(bin, "${source.name}.staged")
                source.copyTo(pending, overwrite = true)
                check(pending.setExecutable(true, false)) { "Cannot prepare Flatpak executable" }
                pending.setLastModified(source.lastModified())
                Files.move(pending.toPath(), copy.toPath(), REPLACE_EXISTING)
            }
            "${copy.absolutePath}:/app/bin/${source.name}"
        }
    }
}
