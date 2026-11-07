package moe.n4tsu.dextop.linux

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LinuxStorageTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun put(path: String, size: Int) {
        val file = temporary.root.resolve(path)
        file.parentFile.mkdirs()
        file.writeBytes(ByteArray(size))
    }
    @Test fun drilldownUsesTheSameExclusiveCategoryTotals() {
        put("usr/bin/tool", 5)
        put("usr/share/fonts/font.ttf", 10)
        put("usr/share/data/file", 7)
        put("home/dextop/.cache/cache", 11)
        put("home/dextop/document", 3)
        put("etc/config", 4)
        val usage = LinuxStorage.usage(temporary.root, "/home/dextop")
        val list = usage.getJSONArray("parts")
        for (i in 0 until list.length()) {
            val category = list.getJSONObject(i)
            val detail = LinuxStorage.breakdown(temporary.root, "/home/dextop", category.getString("id"), category.getString("path"))
            assertEquals(category.getLong("bytes"), detail.getLong("total"))
        }
        val usr = LinuxStorage.breakdown(temporary.root, "/home/dextop", "usr", "/usr")
        assertEquals(12, usr.getLong("total"))
        assertEquals(7, LinuxStorage.breakdown(temporary.root, "/home/dextop", "usr", "/usr/share").getLong("total"))
    }
    @Test fun refusesTraversalAndCrossCategoryBrowsing() {
        put("usr/bin/tool", 5)
        for (path in listOf("/usr/../etc", "/etc", "usr", "/usr/./bin")) {
            assertFailsWith<IllegalArgumentException> { LinuxStorage.breakdown(temporary.root, "/root", "usr", path) }
        }
        assertFailsWith<IllegalArgumentException> { LinuxStorage.breakdown(temporary.root, "/root", "unknown", "/") }
    }
    @Test fun largeDirectoriesArePagedWithoutLosingTheirTotals() {
        repeat(205) { put("usr/bin/tool$it", 1) }
        val first = LinuxStorage.breakdown(temporary.root, "/root", "usr", "/usr/bin")
        val next = LinuxStorage.breakdown(temporary.root, "/root", "usr", "/usr/bin", first.getInt("nextOffset"))
        assertEquals(205, first.getInt("count"))
        assertEquals(200, first.getJSONArray("entries").length())
        assertEquals(5, next.getJSONArray("entries").length())
        assertEquals(205, next.getLong("total"))
    }
}
