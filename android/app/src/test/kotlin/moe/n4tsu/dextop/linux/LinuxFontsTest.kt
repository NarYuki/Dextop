package moe.n4tsu.dextop.linux

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinuxFontsTest {
    @Test fun regionsFollowTheLocale() {
        assertEquals("JP", LinuxFonts.region("ja_JP.UTF-8"))
        assertEquals("KR", LinuxFonts.region("ko-KR"))
        assertEquals("SC", LinuxFonts.region("zh_CN.UTF-8"))
        assertEquals("TC", LinuxFonts.region("zh_TW"))
        assertEquals("TC", LinuxFonts.region("zh-Hant-TW"))
        assertEquals("HK", LinuxFonts.region("zh_HK"))
        assertNull(LinuxFonts.region("en_US.UTF-8"))
        assertNull(LinuxFonts.region(null))
    }

    @Test fun latinFamiliesKeepTheirGlyphsAndCjkFollows() {
        val text = LinuxFonts.config("JP")
        assertTrue("<alias binding=\"same\"><family>Noto Sans</family><accept><family>Noto Sans CJK JP</family></accept></alias>" in text)
        assertTrue("<alias><family>sans-serif</family><prefer><family>Noto Sans</family><family>Noto Sans CJK JP</family></prefer></alias>" in text)
        assertTrue("Noto Sans Mono CJK JP" in text)
    }

    @Test fun containerLocaleWinsAndIsStaged() {
        val root = Files.createTempDirectory("fonts").toFile()
        try {
            File(root, "etc/default").mkdirs()
            File(root, "etc/default/locale").writeText("LANG=ko_KR.UTF-8\n")
            File(root, "etc/fonts/conf.d").mkdirs()
            LinuxFonts.stage(root)
            assertTrue("Noto Sans CJK KR" in File(root, LinuxFonts.CONFIG).readText())
        } finally { root.deleteRecursively() }
    }
}
