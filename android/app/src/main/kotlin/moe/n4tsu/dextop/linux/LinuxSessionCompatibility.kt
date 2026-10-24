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
        stageVulkan(root)
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
        replace("/usr/share/vulkan/implicit_layer.d/dextop-xvfb.json", manifest.toByteArray())
    }
}
