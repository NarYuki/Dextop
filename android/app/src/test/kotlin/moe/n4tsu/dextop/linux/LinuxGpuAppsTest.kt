package moe.n4tsu.dextop.linux

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinuxGpuAppsTest {
    @Test fun programSkipsEnvAndAssignments() {
        assertEquals("gimp", LinuxGpuApps.program("gimp-2.10 %U".replace("gimp-2.10", "/usr/bin/gimp")))
        assertEquals("blender", LinuxGpuApps.program("env -u FOO BAR=1 --ignore-environment /opt/blender/blender"))
        assertEquals("code", LinuxGpuApps.program("QT_X=1 code --new-window"))
        assertEquals("firefox", LinuxGpuApps.program("\"/usr/lib/firefox/firefox\" -new-window"))
        assertNull(LinuxGpuApps.program("   "))
    }

    @Test fun lastMatchingLineWinsAndCommentsAreIgnored() {
        val root = Files.createTempDirectory("gpu-apps").toFile()
        try {
            val file = File(root, "root/${LinuxGpuApps.PATH}")
            file.parentFile.mkdirs()
            file.writeText("# programs\nsoftware gimp\ngpu gimp   # changed my mind\nsoftware org.kde.krita.desktop\nbogus line\n")
            assertEquals("gpu", LinuxGpuApps.choice(root, "org.gimp.GIMP.desktop", "/usr/bin/gimp %U"))
            assertEquals("software", LinuxGpuApps.choice(root, "org.kde.krita.desktop", "krita"))
            assertEquals("software", LinuxGpuApps.choice(root, "org.kde.krita.desktop".removeSuffix(".desktop") + ".desktop", "x"))
            assertNull(LinuxGpuApps.choice(root, "firefox.desktop", "firefox"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun missingFileSaysNothing() {
        val root = Files.createTempDirectory("gpu-apps-none").toFile()
        try {
            assertNull(LinuxGpuApps.choice(root, "a.desktop", "a"))
        } finally {
            root.deleteRecursively()
        }
    }
}
