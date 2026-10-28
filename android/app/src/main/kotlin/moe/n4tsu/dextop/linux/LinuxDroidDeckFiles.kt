package moe.n4tsu.dextop.linux

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * DroidDeck's session files, staged into its runtime the way DroidDeck's
 * SessionFiles.stage() and LegacyNames.migrateRootfs() do (Droid-Deck/DroidDeck,
 * GPL-3.0, commit in droiddeck/source.env). The runtime image does not carry
 * them: DroidDeck ships them in its APK and copies them before every session.
 * tool/sync_droiddeck_linuxfs.sh refreshes the copies under resources.
 */
internal object LinuxDroidDeckFiles {
    private const val BASE = "/moe/n4tsu/dextop/linux/droiddeck/linuxfs"
    private const val BLSESSION = "libdextop_blsession.so"
    private val LEGACY_SCRIPTS = listOf(
        "appimage-run", "bwrap", "desktop-games", "flatpak", "flatpak-run", "flatpak-setup",
        "game-env", "login1", "netmanager", "pad-defaults", "proton-extra", "script-run",
        "seed-redists", "session", "steam-games", "steam-install", "steam-launch",
        "steam-library", "steam-shim", "steam-shortcuts",
    )
    private val LINKED_DIRS = listOf("etc/bannerlator" to "etc/droiddeck", "usr/local/share/bannerlator" to "usr/local/share/droiddeck")
    private val MOVED_FILES = listOf(
        "etc/bannerlator-net" to "etc/droiddeck-net",
        "etc/bannerlator-network.json" to "etc/droiddeck-network.json",
        "etc/bannerlator-wifi-scan-request" to "etc/droiddeck-wifi-scan-request",
    )

    private val files: List<String> by lazy {
        resource("index.txt").bufferedReader().readLines().filter { it.isNotBlank() }
    }

    private fun resource(path: String) = requireNotNull(LinuxDroidDeckFiles::class.java.getResourceAsStream("$BASE/$path")) {
        "DroidDeck session file $path is not packaged"
    }

    /** Copies every session file into [root] and writes DroidDeck's preload list. */
    @Synchronized
    fun stage(root: File, nativeLibraryDir: String, log: (String) -> Unit) {
        // Desktop scripts are staged only once the KDE package is in place, as DroidDeck does.
        val desktop = File(root, "usr/bin/kwin_wayland").isFile
        val desktopOnly = setOf("droiddeck-desktop", "droiddeck-gpu", "droiddeck-desktop-gpu",
            "droiddeck-desktop-window-rules", "kwin_wayland_wrapper", "droiddeck.js")
        for (relative in files) {
            if (!desktop && relative.substringAfterLast('/') in desktopOnly) continue
            val target = File(root, relative)
            val staged = File(target.parentFile, target.name + ".staged")
            try {
                target.parentFile?.mkdirs()
                resource(relative).use { input -> staged.writeBytes(LinuxScript.resource(input.readBytes())) }
                check(staged.setExecutable(true, false) && staged.setReadable(true, false) && staged.renameTo(target))
            } catch (error: Exception) {
                staged.delete()
                log("$relative NOT staged: ${error.message}")
            }
        }
        File(root, "usr/local/bin/bannerlator-steam-compat").delete()
        migrateRootfs(root)
        // DroidDeck's libblsession.so is Dextop's libdextop_blsession.so, built from the same source.
        val lib = File(root, "usr/local/lib").apply { mkdirs() }
        File(nativeLibraryDir, BLSESSION).takeIf { it.isFile }?.let { built ->
            val staged = File(lib, "libblsession.so.staged")
            runCatching {
                built.copyTo(staged, overwrite = true)
                check(staged.setReadable(true, false) && staged.renameTo(File(lib, "libblsession.so")))
            }.onFailure { staged.delete(); log("libblsession.so NOT staged: ${it.message}") }
        }
        val etc = File(root, "etc").apply { mkdirs() }
        LinuxSessionCompatibility.stage(root, nativeLibraryDir)
        write(File(etc, "steam-compatibility-label"), "Compatible")
        if (File(root, ".dextop-dd-stock-experiment").isFile) {
            // Dextop maps guest Unix sockets to abstract sockets: no inode is
            // created for DD's -S readiness check. Verify the actual bus instead.
            val session = File(root, "usr/local/bin/droiddeck-session")
            val ready = "dbus-send --system --type=method_call --print-reply --reply-timeout=500 --dest=org.freedesktop.DBus / org.freedesktop.DBus.ListNames >/dev/null 2>&1"
            write(session, session.readText().replace("[ -S /run/dbus/system_bus_socket ]", ready))
            session.setExecutable(true, false)
        }
    }

    private fun write(target: File, text: String) {
        val staged = File(target.parentFile, target.name + ".staged")
        staged.writeText(text)
        if (!staged.renameTo(target)) staged.delete()
    }

    // region LegacyNames.migrateRootfs: linuxfs images still carry bannerlator-* names.
    private fun migrateRootfs(root: File) {
        for ((old, new) in LINKED_DIRS) moveAndLink(File(root, old), File(root, new))
        File(root, "etc/droiddeck/empty").mkdirs()
        for ((old, new) in MOVED_FILES) move(File(root, old), File(root, new))
        val bin = File(root, "usr/local/bin")
        for (name in LEGACY_SCRIPTS) {
            val target = File(bin, "droiddeck-$name")
            val legacy = File(bin, "bannerlator-$name")
            if (!target.isFile || isLink(target)) continue
            if (isLink(legacy)) {
                if (runCatching { Files.readSymbolicLink(legacy.toPath()).toString() }.getOrNull() == target.name) continue
            } else if (!legacy.exists()) continue
            replaceWithLink(legacy, target)
        }
        File(root, "usr/local/share/applications").listFiles { f -> f.isFile && f.name.startsWith("droiddeck-") && f.name.endsWith(".desktop") }
            ?.forEach { entry ->
                val text = entry.readText()
                if ("/usr/local/bin/bannerlator-" in text) write(entry, text.replace("/usr/local/bin/bannerlator-", "/usr/local/bin/droiddeck-"))
            }
    }

    private fun isLink(f: File) = Files.isSymbolicLink(f.toPath())

    private fun move(legacy: File, target: File) {
        if (isLink(legacy) || !legacy.exists()) return
        if (legacy.isDirectory && !isLink(target) && (target.isDirectory || !target.exists())) {
            target.mkdirs()
            legacy.listFiles()?.forEach { move(it, File(target, it.name)) }
            legacy.delete()
            return
        }
        if (target.exists() || isLink(target)) LinuxRootfsArchive.deleteTree(legacy) else legacy.renameTo(target)
    }

    private fun moveAndLink(legacy: File, target: File) {
        if (isLink(legacy) || !legacy.exists()) return
        move(legacy, target)
        if (!legacy.exists()) replaceWithLink(legacy, target)
    }

    private fun replaceWithLink(legacy: File, target: File) {
        val link = File(legacy.parentFile, legacy.name + ".link")
        link.delete()
        runCatching {
            Files.createSymbolicLink(link.toPath(), Paths.get(target.name))
            if (!link.renameTo(legacy)) link.delete()
        }.onFailure { link.delete() }
    }
    // endregion
}
