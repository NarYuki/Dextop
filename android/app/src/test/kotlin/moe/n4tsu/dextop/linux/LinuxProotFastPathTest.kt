package moe.n4tsu.dextop.linux

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class LinuxProotFastPathTest {
    @Test fun orderedMountsAndRootDetermineBothSidesKey() {
        val root = File("/data/local/tmp/root")
        val first = LinuxProotFastPath.create(root, listOf("/dev", "/a:/guest"))!!
        assertEquals(first, LinuxProotFastPath.create(root, listOf("/dev", "/a:/guest")))
        assertNotEquals(first.key, LinuxProotFastPath.create(root, listOf("/a:/guest", "/dev"))!!.key)
        assertNotEquals(first.key, LinuxProotFastPath.create(File("/other/root"), first.binds)!!.key)
        assertEquals("PROOT_FP_KEY=${first.key}", first.guestEnvironment.last())
        assertEquals("PROOT_FP_BINDS=/dev|/a:/guest", first.guestEnvironment[1])
    }
    @Test fun unrepresentableMountsDisableFastPath() {
        assertNull(LinuxProotFastPath.create(File("/root"), listOf("/a|b:/c")))
        assertNull(LinuxProotFastPath.create(File("/root"), List(257) { "/mount$it" }))
        assertNull(LinuxProotFastPath.create(File("/root\nother"), emptyList()))
    }
}
