package moe.n4tsu.dextop.linux

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** DroidDeck's glibc session hooks, shared by every process in a Linux container. */
internal object LinuxSessionCompatibility {
    const val LIBRARY = "libdextop_blsession.so"
    const val GUEST_PRELOAD = "/usr/local/lib/libblsession.so"
    const val GUEST_SOCKET_SHIM = "${LinuxProot.GUEST_LIBRARY_DIR}/libdextop_unixsock.so"
    const val WSI_CONFIG = "/etc/dextop/vulkan-wsi"

    /** Installation/repair commits an automatically loaded runtime and persistent display policy. */
    fun install(root: File, nativeLibraryDir: String, socketNamespace: String, xvfb: Boolean) {
        stage(root, nativeLibraryDir, socketNamespace)
        val environment = File(root, WSI_CONFIG.removePrefix("/"))
        val current = if (environment.isFile) environment.readText() else ""
        val content = if (xvfb) "xvfb\n" else "wayland\n"
        if (current != content) {
            environment.parentFile?.mkdirs()
            val temporary = File(environment.parentFile, environment.name + ".staged")
            temporary.writeText(content)
            Files.move(temporary.toPath(), environment.toPath(), REPLACE_EXISTING)
        }
    }

    fun stage(root: File, nativeLibraryDir: String, socketNamespace: String? = null) {
        stageScript(root, "dextop-path.sh", "etc/profile.d/dextop-path.sh")
        stageScript(root, "dextop-pipewire-client.conf", "etc/pipewire/client.conf.d/99-dextop-session.conf")
        stageScript(root, "dextop-pipewire-client.conf", "etc/pipewire/client-rt.conf.d/99-dextop-session.conf")
        stageVulkan(root)
        stageSystemPrompter(root)
        stageSystemServices(root)
        stageWindowsHelper(root)
        stageScript(root, "dextop-apt-heal", "usr/local/sbin/dextop-apt-heal")
        val source = File(nativeLibraryDir, LIBRARY)
        check(source.isFile) { "Linux session compatibility library is not packaged" }
        val target = File(root, GUEST_PRELOAD.removePrefix("/"))
        target.parentFile?.mkdirs()
        val bytes = source.readBytes()
        if (!target.isFile || !target.readBytes().contentEquals(bytes)) {
            val temporary = File(target.parentFile, target.name + ".staged")
            temporary.writeBytes(bytes)
            check(temporary.setReadable(true, false)) { "Cannot read Linux session compatibility library" }
            Files.move(temporary.toPath(), target.toPath(), REPLACE_EXISTING)
        }
        val preload = File(root, "etc/ld.so.preload")
        val current = if (preload.isFile) preload.readText() else ""
        // Keep distribution/user preloads and comments; do not add ours twice.
        val entries = current.lineSequence().flatMap { it.substringBefore('#').split(Regex("[\\s:]+")) }.toSet()
        val managed = listOf(GUEST_PRELOAD, GUEST_SOCKET_SHIM, LinuxProotFastPath.LIBRARY)
        val missing = managed.filterNot { it in entries }
        if (missing.isNotEmpty()) {
            preload.parentFile?.mkdirs()
            val temporary = File(preload.parentFile, preload.name + ".staged")
            val remaining = current.lineSequence().map { line ->
                val content = line.substringBefore('#').split(Regex("[\\s:]+"))
                    .filter { it.isNotEmpty() && it !in managed }.joinToString(" ")
                val comment = if ('#' in line) "#" + line.substringAfter('#') else ""
                listOf(content, comment).filter { it.isNotEmpty() }.joinToString(" ")
            }.filter { it.isNotEmpty() }.joinToString("\n", postfix = if (current.isNotEmpty()) "\n" else "")
            temporary.writeText(managed.joinToString("\n", postfix = "\n") + remaining)
            Files.move(temporary.toPath(), preload.toPath(), REPLACE_EXISTING)
        }
        if (socketNamespace != null) {
            require(socketNamespace.matches(Regex("dextop-c[a-zA-Z0-9]+")))
            File(root, "etc/dextop/socket-namespace").apply { parentFile?.mkdirs(); writeText("$socketNamespace\n") }
        }
    }

    /** A D-Bus helper has no initiating app's toolkit environment. Use X11 for its
     * security dialogs so they can parent to both X11 and Xwayland applications. */
    private fun stageSystemPrompter(root: File) {
        stageScript(root, "dextop-keyring-repair", "usr/local/bin/dextop-keyring-repair")
        val system = File(root, "usr/share/dbus-1/services/org.gnome.keyring.SystemPrompter.service")
        if (!system.isFile) return
        val marker = "# Dextop system prompt display compatibility\n"
        val target = File(root, "root/.local/share/dbus-1/services/org.gnome.keyring.SystemPrompter.service")
        if (target.isFile && !target.readText().startsWith(marker)) return
        val content = marker + system.readText().replace(Regex("(?m)^Exec=(.+)$")) {
            "Exec=/usr/bin/env GDK_BACKEND=x11 " + it.groupValues[1]
        }
        if (target.isFile && target.readText() == content) return
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, target.name + ".staged")
        temporary.writeText(content)
        Files.move(temporary.toPath(), target.toPath(), REPLACE_EXISTING)
    }

    /** D-Bus activation does not preserve an initiating application's bus address.
     * Keep helpers on this container's bus. Only polkit's privilege-drop operation
     * needs fakeroot; packagekit itself clears its environment and must stay real-uid. */
    internal fun stageSystemServices(root: File) {
        val directory = File(root, "etc/dextop/system-services").apply { mkdirs() }
        val sources = File(root, "usr/share/dbus-1/system-services").listFiles()
            .orEmpty().filter { it.name.endsWith(".service") }
        directory.listFiles().orEmpty().filter { target -> sources.none { it.name == target.name } }.forEach { it.delete() }
        for (source in sources) {
            val content = source.readText().replace(Regex("(?m)^Exec=(.+)$")) {
                val command = it.groupValues[1]
                val privilege = if (command.substringBefore(' ').endsWith("/polkitd")) "/usr/bin/fakeroot-tcp " else ""
                "Exec=/usr/bin/env DBUS_SYSTEM_BUS_ADDRESS=${LinuxCatalog.SYSTEM_BUS_ADDRESS} $privilege$command"
            }.replace(Regex("(?m)^User=.+$"), "User=root")
            val target = File(directory, source.name)
            if (!target.isFile || target.readText() != content) {
                val temporary = File(directory, source.name + ".staged")
                temporary.writeText(content)
                Files.move(temporary.toPath(), target.toPath(), REPLACE_EXISTING)
            }
        }
    }

    /** Keeps installed Windows applications' launcher (dextop-windows) as current as Dextop. */
    private fun stageWindowsHelper(root: File) {
        if (File(root, "usr/local/bin/dextop-windows").isFile) stageScript(root, "dextop-windows", "usr/local/bin/dextop-windows")
    }

    /** A script of Dextop's resources at [path] in the container, with Unix line ends. */
    internal fun script(name: String): ByteArray? =
        javaClass.getResourceAsStream("/moe/n4tsu/dextop/linux/$name")?.use { it.readBytes() }
            ?.toString(Charsets.UTF_8)?.replace("\r\n", "\n")?.toByteArray()

    private fun stageScript(root: File, name: String, path: String) {
        val target = File(root, path)
        val bytes = script(name) ?: return
        if (target.isFile && target.readBytes().contentEquals(bytes)) return
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, target.name + ".staged")
        temporary.writeBytes(bytes)
        temporary.setExecutable(true, false)
        temporary.setReadable(true, false)
        Files.move(temporary.toPath(), target.toPath(), REPLACE_EXISTING)
    }

    private fun stageVulkan(root: File) {
        val library = "/usr/local/lib/dextop/libdextop_vk_xvfb.so"
        val manifest = """{"file_format_version":"1.2.0","layer":{"name":"VK_LAYER_DEXTOP_xvfb","type":"GLOBAL","library_path":"$library","api_version":"1.3.0","implementation_version":"1","description":"Dextop Xvfb presentation capabilities","disable_environment":{"DEXTOP_DISABLE_XVFB_WSI_LAYER":"1"}}}"""
        fun replace(path: String, bytes: ByteArray) {
            val target = File(root, path.removePrefix("/"))
            if (target.isFile && target.readBytes().contentEquals(bytes)) return
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, target.name + ".staged")
            temporary.writeBytes(bytes)
            check(temporary.setReadable(true, false)) { "Cannot read Vulkan capability layer" }
            Files.move(temporary.toPath(), target.toPath(), REPLACE_EXISTING)
        }
        val bytes = checkNotNull(javaClass.getResourceAsStream(
            "/moe/n4tsu/dextop/linux/gaming/libdextop_vk_xvfb-aarch64.bin")) {
            "Xvfb Vulkan capability layer is not packaged"
        }.use { it.readBytes() }
        val metadata = checkNotNull(javaClass.getResourceAsStream(
            "/moe/n4tsu/dextop/linux/gaming/dextop-wine-tray-x86_64.exe")) { "Wine tray metadata bridge is not packaged" }.use { it.readBytes() }
        replace(LinuxProotFastPath.LIBRARY, checkNotNull(javaClass.getResourceAsStream(
            "/moe/n4tsu/dextop/linux/gaming/libblfastpath-aarch64.bin")) { "PRoot fast path is not packaged" }.use { it.readBytes() })
        replace("/usr/local/lib/dextop/dextop-wine-tray.exe", metadata)
        replace(library, bytes)
        // Flatpak replaces /usr but preserves /usr/local. The WSI capability
        // correction must remain discoverable inside those runtimes as well.
        replace("/usr/local/share/vulkan/implicit_layer.d/dextop-xvfb.json", manifest.toByteArray())
        File(root, "usr/share/vulkan/implicit_layer.d/dextop-xvfb.json").takeIf {
            it.isFile && it.readText() == manifest
        }?.delete()
    }
}
