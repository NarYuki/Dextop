package moe.n4tsu.dextop.linux

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class LinuxProotRunTest {
    private val runner = LinuxProot("unused")
    private val windows = System.getProperty("os.name").startsWith("Windows")
    private fun command(windowsScript: String, unixScript: String) = if (windows) ProcessBuilder(
        File(System.getenv("SystemRoot"), "System32/WindowsPowerShell/v1.0/powershell.exe").path,
        "-NoProfile", "-NonInteractive", "-Command", windowsScript
    ) else ProcessBuilder("/bin/sh", "-c", unixScript)
    private fun sleeper() = command("Start-Sleep -Seconds 30", "sleep 30")

    @Test(timeout = 15000) fun drainsOutputWhileSupplyingInput() {
        val observed = AtomicReference<String>()
        val process = command(
            "1..256 | ForEach-Object { [Console]::Out.WriteLine(('x'*1024)) }; " +
                "[Console]::Out.Flush(); \$value=[Console]::In.ReadToEnd(); [Console]::Out.WriteLine(('READ='+\$value.Length))",
            "yes x | head -c 262144; printf '\\nREAD='; wc -c"
        )
        val result = runner.run(process, 8, input = "p".repeat(262144)) {
            if (it.startsWith("READ=")) observed.set(it.replace(" ", ""))
        }
        assertEquals(0, result.exitCode)
        assertEquals("READ=262144", observed.get())
    }

    @Test(timeout = 10000) fun secretByteInputIsClearedAfterWriting() {
        val secret = "synthetic-test-password".toByteArray()
        val process = command("[Console]::In.ReadToEnd() | Out-Null", "cat >/dev/null")
        assertEquals(0, runner.run(process, 5, inputBytes = secret).exitCode)
        assertTrue(secret.all { it == 0.toByte() })
    }

    @Test(timeout = 10000) fun secretByteInputIsClearedBeforeWriteFailure() {
        val secret = "synthetic-test-password".toByteArray()
        assertFailsWith<IllegalStateException> { runner.run(sleeper(), inputBytes = secret, onProcess = { error("failure") }) }
        assertTrue(secret.all { it == 0.toByte() })
        val notStarted = "synthetic-test-password".toByteArray()
        assertFailsWith<java.io.IOException> { runner.run(ProcessBuilder("dextop-nonexistent-test-command"), inputBytes = notStarted) }
        assertTrue(notStarted.all { it == 0.toByte() })
    }

    @Test(timeout = 15000) fun timeoutIncludesABlockedStdinWrite() {
        lateinit var child: Process
        val result = runner.run(sleeper(), 1, onProcess = { child = it }, input = "p".repeat(2_000_000))
        assertEquals(-1, result.exitCode)
        assertTrue(child.waitFor(3, TimeUnit.SECONDS))
        assertFalse(child.isAlive)
    }

    @Test(timeout = 10000) fun failingProcessCallbackDoesNotLeaveAChild() {
        lateinit var child: Process
        assertFailsWith<IllegalStateException> {
            runner.run(sleeper(), onProcess = { child = it; error("callback failed") })
        }
        assertTrue(child.waitFor(3, TimeUnit.SECONDS))
        assertFalse(child.isAlive)
    }

    @Test(timeout = 10000) fun interruptionStopsTheChild() {
        val ready = CountDownLatch(1)
        val child = AtomicReference<Process>()
        val failure = AtomicReference<Throwable>()
        val worker = Thread {
            try { runner.run(sleeper(), onProcess = { child.set(it); ready.countDown() }) }
            catch (error: Throwable) { failure.set(error) }
        }
        worker.start()
        assertTrue(ready.await(3, TimeUnit.SECONDS))
        worker.interrupt()
        worker.join(4000)
        assertFalse(worker.isAlive)
        assertTrue(failure.get() is InterruptedException)
        assertTrue(child.get().waitFor(3, TimeUnit.SECONDS))
        assertFalse(child.get().isAlive)
    }
}
