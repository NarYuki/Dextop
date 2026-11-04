package moe.n4tsu.dextop.linux

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.*

class LinuxMacosLifecycleTest {
    private class Guest(var running: Boolean = true, val onWait: () -> Boolean) : Process() {
        var destroyed = false
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun isAlive() = running
        override fun exitValue(): Int { check(!running); return 0 }
        override fun waitFor(): Int { running = false; return 0 }
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = onWait().also { if (it) running = false }
        override fun destroy() { destroyed = true; running = false }
    }

    @Test fun requestsAllOwnedGuestsBeforeWaitingAndDoesNotTouchLinuxProcesses() {
        val root = Files.createTempDirectory("macos-controls").toFile()
        try {
            val lifecycle = LinuxMacosLifecycle()
            val first = lifecycle.prepare(root)
            val second = lifecycle.prepare(root)
            fun stopped(): Boolean { assertTrue(first.stop.isFile || !first.directory.exists()); assertTrue(second.stop.isFile); return true }
            val a = Guest(onWait = ::stopped)
            val b = Guest(onWait = ::stopped)
            val linux = Guest { fail("Unrelated Linux guest must not be awaited") }
            lifecycle.attach(a, first); lifecycle.attach(b, second)
            lifecycle.stop(listOf(a, b, linux), 100)
            assertFalse(a.destroyed); assertFalse(b.destroyed)
            assertTrue(linux.isAlive)
            assertFalse(first.directory.exists()); assertFalse(second.directory.exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun incompleteShutdownKeepsTheGuestAndControlForARetry() {
        val root = Files.createTempDirectory("macos-stop-retry").toFile()
        try {
            val lifecycle = LinuxMacosLifecycle()
            val control = lifecycle.prepare(root)
            val guest = Guest { false }
            lifecycle.attach(guest, control)
            assertFailsWith<IllegalStateException> { lifecycle.stop(listOf(guest), 1) }
            assertTrue(guest.isAlive); assertFalse(guest.destroyed); assertTrue(control.stop.isFile)
            guest.running = false
            lifecycle.stop(listOf(guest), 1)
            assertFalse(control.directory.exists())
        } finally { root.deleteRecursively() }
    }
}
