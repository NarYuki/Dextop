package moe.n4tsu.dextop.linux

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinuxDesktopEntriesTest {
    @Test fun userLaunchersOverrideSystemAppsWithoutDuplicates() {
        val root = Files.createTempDirectory("linux-overrides").toFile()
        try {
            val system = File(root, "usr/share/applications").apply { mkdirs() }
            val user = File(root, "root/.local/share/applications").apply { mkdirs() }
            val entry = "[Desktop Entry]\nType=Application\nName=Browser\nExec=chromium\nIcon=chromium\n"
            File(system, "chromium.desktop").writeText(entry)
            File(user, "chromium.desktop").writeText(entry.replace("Exec=chromium", "Exec=/usr/local/bin/dextop-chromium"))
            assertEquals("/usr/local/bin/dextop-chromium", LinuxDesktopEntries.list(root, "en").single().exec)
            assertEquals("/usr/local/bin/dextop-chromium", LinuxDesktopEntries.find(root, "chromium.desktop")?.exec)
            File(user, "chromium.desktop").appendText("Hidden=true\n")
            assertTrue(LinuxDesktopEntries.list(root, "en").isEmpty())
            assertEquals(null, LinuxDesktopEntries.find(root, "chromium.desktop"))
        } finally { root.deleteRecursively() }
    }

    @Test fun searchesScalableAndThemeSpecificDirectoriesAndKeepsExtensions() {
        val root = Files.createTempDirectory("linux-icons").toFile()
        try {
            val theme = File(root, "usr/share/icons/TestTheme").apply { mkdirs() }
            File(theme, "index.theme").writeText("[Icon Theme]\nDirectories=apps/48,scalable/apps\nScaledDirectories=48x48@2/apps\n")
            val paths = LinuxDesktopEntries.iconCandidates(root, "editor").toList()
            assertTrue("/usr/share/icons/hicolor/scalable/apps/editor.svg" in paths)
            assertTrue("/usr/share/icons/TestTheme/apps/48/editor.png" in paths)
            assertTrue("/usr/share/icons/TestTheme/48x48@2/apps/editor.svg" in paths)
            assertTrue("/usr/share/pixmaps/editor.xpm" in paths)
            val xpm = LinuxDesktopEntries.iconCandidates(root, "xterm.xpm").toList()
            assertTrue("/usr/share/pixmaps/xterm.xpm" in xpm)
            assertTrue(xpm.none { it.endsWith(".xpm.png") })
            assertEquals(listOf("/opt/app/icon.svg"), LinuxDesktopEntries.iconCandidates(root, "/opt/app/icon.svg").toList())
        } finally { root.deleteRecursively() }
    }

    @Test fun findsPixmapsWithSizeSuffixes() {
        val root = Files.createTempDirectory("linux-pixmaps").toFile()
        try {
            val pixmaps = File(root, "usr/share/pixmaps").apply { mkdirs() }
            listOf("mini.xterm_32x32.xpm", "mini.xterm_48x48.xpm", "mini.xterm_256x256.xpm", "other_48x48.xpm")
                .forEach { File(pixmaps, it).writeText("x") }
            assertEquals(
                listOf("mini.xterm_48x48.xpm", "mini.xterm_256x256.xpm", "mini.xterm_32x32.xpm"),
                LinuxDesktopEntries.sizedPixmaps(root, "mini.xterm").map { it.name },
            )
        } finally { root.deleteRecursively() }
    }

    @Test fun preservesWindowClassIndependentlyOfLocalizedLauncherName() {
        val root = Files.createTempDirectory("linux-entries").toFile()
        try {
            val applications = File(root, "usr/share/applications").apply { mkdirs() }
            File(applications, "org.example.Editor.desktop").writeText("""
                [Desktop Entry]
                Type=Application
                Name=Editor
                Name[ja]=エディター
                Exec=editor %U
                Icon=org.example.Editor
                StartupWMClass=EditorWindow
                [Desktop Action New]
                Name=New window
                Icon=wrong-action-icon
            """.trimIndent())
            val entry = LinuxDesktopEntries.list(root, "ja-JP").single()
            assertEquals("エディター", entry.name)
            assertEquals("org.example.Editor", entry.icon)
            assertEquals("EditorWindow", entry.startupWMClass)
            assertEquals("editor", LinuxDesktopEntries.command(entry))
            assertEquals("EditorWindow", LinuxDesktopEntries.find(root, entry.id)?.startupWMClass)
        } finally { root.deleteRecursively() }
    }

    @Test fun missingWindowClassDoesNotHideApplication() {
        val root = Files.createTempDirectory("linux-entries").toFile()
        try {
            val directory = File(root, "usr/share/applications").apply { mkdirs() }
            File(directory, "xterm.desktop").writeText("[Desktop Entry]\nType=Application\nName=XTerm\nExec=xterm\nIcon=xterm-color_48x48.xpm\n")
            val entry = LinuxDesktopEntries.list(root, "en").single()
            assertTrue(entry.startupWMClass.isEmpty())
            assertEquals("xterm-color_48x48.xpm", entry.icon)
        } finally { root.deleteRecursively() }
    }
}
