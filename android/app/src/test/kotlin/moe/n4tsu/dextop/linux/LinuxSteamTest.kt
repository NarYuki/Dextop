package moe.n4tsu.dextop.linux

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxSteamTest {
    @Test fun nativeClientRegistersSteamCommandForGeneratedGameShortcuts() {
        val root = Files.createTempDirectory("steam-shortcuts").toFile()
        try {
            LinuxSteam.stageCommandAlias(root)
            assertFalse(File(root, "usr/local/bin/steam").exists())
            File(root, "root/.local/share/Steam/steamrtarm64/steam").apply { parentFile.mkdirs(); writeText("client") }
            val launcher = File(root, "usr/local/bin/dextop-steam").apply { parentFile.mkdirs(); writeText("launcher") }
            LinuxSteam.stageCommandAlias(root)
            assertEquals(launcher.canonicalFile, LinuxDesktopEntries.resolve(root, "/usr/local/bin/steam")?.canonicalFile)
            val alias = File(root, "usr/local/bin/steam").toPath()
            val target = Files.readSymbolicLink(alias)
            LinuxSteam.stageCommandAlias(root)
            assertEquals(target, Files.readSymbolicLink(alias))
        } finally { root.deleteRecursively() }
    }

    @Test fun nativeSteamAliasPreservesVendorAndUserCommands() {
        val root = Files.createTempDirectory("steam-vendor").toFile()
        try {
            File(root, "root/.local/share/Steam/steamrtarm64/steam").apply { parentFile.mkdirs(); writeText("client") }
            val vendor = File(root, "usr/bin/steam").apply { parentFile.mkdirs(); writeText("vendor") }
            LinuxSteam.stageCommandAlias(root)
            assertFalse(Files.exists(File(root, "usr/local/bin/steam").toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
            assertEquals("vendor", vendor.readText())
            vendor.delete()
            val custom = File(root, "usr/local/bin/steam").apply { parentFile.mkdirs(); writeText("custom") }
            LinuxSteam.stageCommandAlias(root)
            assertEquals("custom", custom.readText())
        } finally { root.deleteRecursively() }
    }
    @Test fun nestedSteamDisplayDoesNotInheritXvfbPresentationFallbackOrOuterWayland() {
        val script = LinuxSteam.installScript(apt = true) { "" }
        val clean = script.indexOf("unset DISPLAY DBUS_SESSION_BUS_ADDRESS MESA_VK_WSI_DEBUG LIBGL_ALWAYS_SOFTWARE")
        assertTrue(clean >= 0)
        assertTrue(clean < script.indexOf("gamescope --backend wayland"))
        val inside = script.indexOf("export WAYLAND_DISPLAY= XDG_SESSION_TYPE=x11")
        assertTrue(inside > script.indexOf("gamescope --backend wayland"))
        assertTrue(inside < script.indexOf("Starting the Steam client"))
        assertTrue("SDL_VIDEODRIVER=x11" in script)
        val gbm = script.indexOf("export MESA_LOADER_DRIVER_OVERRIDE=zink", inside)
        assertTrue(gbm in inside until script.indexOf("Starting the Steam client"))
        assertTrue("if [ \"\${GALLIUM_DRIVER:-}\" = zink ]; then" in script)
    }

    @Test fun setupInstallsAndChecksCommonX86RuntimeBeforeProton() {
        val script = LinuxSteam.installScript(apt = true) { "" }
        val install = script.indexOf("HOME=/root /usr/local/bin/dextop-gaming install")
        assertTrue(install > script.indexOf("mv -f /usr/local/lib/dextop-gaming/runtime-check-i386.staged"))
        assertTrue(install < script.indexOf("/usr/local/bin/dextop-proton-arm64 install"))
        assertTrue(script.indexOf("mv -f /usr/local/lib/dextop-gaming/pulse-client.conf.staged") in 0 until install)
    }
    @Test fun freshInstallIncludesSteamWebUiProcessAuthenticationDependencies() {
        for (apt in listOf(true, false)) {
            var dependencies = emptyList<String>()
            val script = LinuxSteam.installScript(apt) { packages -> dependencies = packages; "install-required-packages\n" }
            assertTrue("lsof" in dependencies, "Steam WebUI authenticates loopback clients through lsof")
            assertTrue(script.indexOf("install-required-packages") < script.indexOf("HOME=/root bash"))
        }
    }

    @Test fun repairReplacesRuntimeFilesWithoutTruncatingMappedLibraries() {
        val script = LinuxSteam.installScript(apt = true) { "" }
        val paths = listOf("usr/local/bin/dextop-proton-arm64",
            "usr/local/lib/dextop-gaming/dextop-wine-sync",
            "usr/local/lib/dextop-gaming/preload/aarch64/libdextop_gamesock.so",
            "usr/local/lib/dextop-gaming/preload/i386/libblsession.so",
            "usr/local/lib/dextop-gaming/preload/x86_64/libblsession.so")
        for (path in paths) {
            val decode = script.lineSequence().single { "| base64 -d > /$path.staged" in it }
            assertFalse(decode.endsWith(" > /$path"))
            val permission = "chmod 755 /$path.staged"
            val replace = "mv -f /$path.staged /$path"
            assertTrue(script.indexOf(decode) < script.indexOf(permission))
            assertTrue(script.indexOf(permission) < script.indexOf(replace))
        }
    }

    @Test fun registersExistingClientWithoutReinstallingOrChangingItsData() {
        val root = Files.createTempDirectory("steam-migration").toFile()
        try {
            val client = File(root, "root/.local/share/Steam/steamrtarm64/steam")
            client.parentFile.mkdirs()
            client.writeText("existing client")
            val launcher = File(root, LinuxSteam.LAUNCHER.removePrefix("/"))
            launcher.parentFile.mkdirs()
            launcher.writeText("old launcher without compatibility preload")
            LinuxSteam.stage(root)
            LinuxSteam.stage(root)
            assertEquals("existing client", client.readText())
            assertEquals(LinuxSteam.launcherScript, launcher.readText())
            assertTrue(File(root, "root/.local/share/applications/dextop-steam.desktop").readText()
                .contains("Exec=${LinuxSteam.LAUNCHER} %U"))
            assertFalse(File(root, "usr/local/bin/droiddeck-steam-install").exists())
            val managed = File(root, "usr/local/bin/dextop-steam-install")
            assertTrue(managed.canExecute())
            assertTrue("--sync" in managed.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun ddGraphicsDefaultsAreScopedToDeckLauncher() {
        val deck = LinuxSteam.installScript(apt = true) { "" }
        for (setting in listOf("ZINK_DESCRIPTORS", "ZINK_DEBUG", "mesa_glthread", "MESA_NO_ERROR", "TU_DEBUG")) {
            assertTrue("export $setting=" in deck)
            assertFalse("export $setting=" in LinuxSteam.launcherScript)
        }
    }

    @Test fun desktopSteamLoadsSystemGlBeforeTheClientAndPreservesSessionPreloads() {
        val script = LinuxSteam.launcherScript
        val preload = "export LD_PRELOAD=libGL.so.1\${LD_PRELOAD:+:\$LD_PRELOAD}"
        assertTrue(preload in script)
        assertTrue(script.indexOf(preload) < script.indexOf("-nobigpicture"))
        assertTrue("-cef-force-gpu -cef-use-gl=angle -cef-use-angle=gl" in script)
        assertFalse("-cef-disable-gpu" in script)
        assertTrue("export MESA_VK_WSI_DEBUG=sw LIBGL_KOPPER_DRI2=true" in script)
        assertFalse("s/^export LIBGL_KOPPER_DISABLE=true" in script)
        assertTrue("flock -n 9" in script)
        assertTrue("steam_updates\" -ge 3" in script)
        assertFalse("-skipinitialbootstrap" in script)
        assertFalse("--sync steamdeck_publicbeta" in script)
    }

    @Test fun bothLaunchersLeaveUpdateApplicationToSteam() {
        for (script in listOf(LinuxSteam.launcherScript, LinuxSteam.installScript(apt = true) { "" })) {
            assertFalse("/usr/local/bin/dextop-steam-install --sync" in script)
            assertFalse("-skipinitialbootstrap" in script)
            assertTrue("steam_updates=0" in script)
            assertTrue("steam_ran\" -lt 60" in script)
            assertTrue("steam_updates\" -ge 3" in script)
            assertTrue("set --" in script)
        }
    }

    @Test fun deckRuntimeMigrationPreservesSystemGamescopeAndDesktopLauncher() {
        val root = Files.createTempDirectory("deck-runtime-migration").toFile()
        try {
            File(root, "root/.local/share/Steam/steamrtarm64/steam").apply {
                parentFile.mkdirs()
                writeText("existing Steam")
            }
            val system = File(root, "usr/games/gamescope").apply {
                parentFile.mkdirs()
                writeText("distribution gamescope")
            }
            LinuxSteam.stage(root)
            LinuxSteam.stage(root)
            assertEquals("distribution gamescope", system.readText())
            assertTrue(File(root, "usr/local/bin/dextop-deck-gamescope").canExecute())
            val payload = File(root, "usr/local/lib/dextop-gaming/deck-gamescope.tar.xz")
            val metadata = File(root, "usr/local/lib/dextop-gaming/deck-gamescope.json").readText()
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(payload.readBytes())
                .joinToString("") { "%02x".format(it) }
            assertTrue(hash in metadata)
            assertTrue("/usr/local/bin/dextop-deck-gamescope --backend wayland" in
                File(root, "usr/local/bin/dextop-steam-gamemode").readText())
            assertFalse("dextop-deck-gamescope" in LinuxSteam.launcherScript)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun doesNotRegisterSteamWhenClientIsAbsent() {
        val root = Files.createTempDirectory("steam-absent").toFile()
        try {
            LinuxSteam.stage(root)
            assertFalse(File(root, LinuxSteam.LAUNCHER.removePrefix("/")).exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
