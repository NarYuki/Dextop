package moe.n4tsu.dextop.linux

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LinuxResumableDownloadTest {
    private val body = ByteArray(1_500_000) { (it % 251).toByte() }
    private val directory = Files.createTempDirectory("resume").toFile()
    private val target = File(directory, "image.part")
    private val servers = mutableListOf<ServerSocket>()
    private val offsets = CopyOnWriteArrayList<Long>()

    @AfterTest
    fun cleanUp() {
        servers.forEach { it.close() }
        directory.deleteRecursively()
    }

    private class Reply(private val output: OutputStream) {
        fun head(status: Int, length: Long, extra: String = "") =
            output.write("HTTP/1.1 $status X\r\nContent-Length: $length\r\nConnection: close\r\n$extra\r\n".toByteArray())

        fun write(bytes: ByteArray, from: Int = 0, to: Int = bytes.size) {
            output.write(bytes, from, to - from)
            output.flush()
        }
    }

    private fun serve(handler: (Reply, Long, Int) -> Unit): URL {
        val socket = ServerSocket(0, 16, InetAddress.getLoopbackAddress())
        servers += socket
        thread(isDaemon = true) {
            var request = 0
            while (true) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                client.use {
                    val lines = it.getInputStream().bufferedReader().lineSequence().takeWhile(String::isNotEmpty).toList()
                    val offset = lines.firstOrNull { line -> line.startsWith("Range:", ignoreCase = true) }
                        ?.substringAfter("bytes=")?.removeSuffix("-")?.toLong() ?: 0L
                    offsets += offset
                    request++
                    handler(Reply(it.getOutputStream()), offset, request)
                }
            }
        }
        return URL("http://127.0.0.1:${socket.localPort}/image")
    }

    private fun Reply.partial(offset: Long) {
        head(206, body.size - offset, "Content-Range: bytes $offset-${body.size - 1}/${body.size}\r\n")
        write(body, offset.toInt())
    }

    private fun Reply.whole() {
        head(200, body.size.toLong())
        write(body)
    }

    private fun run(url: URL, cancel: () -> Unit = {}) = LinuxResumableDownload.download(
        target, body.size.toLong(),
        { offset -> (url.openConnection() as HttpURLConnection).apply { if (offset > 0) setRequestProperty("Range", "bytes=$offset-") } },
        cancel, {}, {},
    )

    @Test
    fun continuesAfterAnInterruptedConnection() {
        val url = serve { reply, offset, request ->
            if (request == 1) {
                reply.head(200, body.size.toLong())
                reply.write(body, 0, 600_000)
            } else reply.partial(offset)
        }
        run(url)
        assertContentEquals(body, target.readBytes())
        assertTrue(offsets.last() > 0, "the second request must not start over")
    }

    @Test
    fun continuesFromAnEarlierAttemptsFile() {
        target.writeBytes(body.copyOf(700_000))
        run(serve { reply, offset, _ -> reply.partial(offset) })
        assertEquals(listOf(700_000L), offsets.toList())
        assertContentEquals(body, target.readBytes())
    }

    @Test
    fun startsOverWhenTheServerIgnoresTheRange() {
        target.writeBytes(ByteArray(700_000) { 9 })
        run(serve { reply, _, _ -> reply.whole() })
        assertContentEquals(body, target.readBytes())
    }

    @Test
    fun replacesAnOversizedFile() {
        target.writeBytes(ByteArray(body.size + 5))
        run(serve { reply, _, _ -> reply.whole() })
        assertContentEquals(body, target.readBytes())
    }

    @Test
    fun startsOverAfterARejectedRange() {
        target.writeBytes(body.copyOf(10))
        run(serve { reply, _, request -> if (request == 1) reply.head(416, 0) else reply.whole() })
        assertContentEquals(body, target.readBytes())
    }

    @Test
    fun stopsAtOnceOnAnHttpError() {
        val url = serve { reply, _, _ -> reply.head(404, 0) }
        assertFailsWith<IllegalStateException> { run(url) }
        assertEquals(1, offsets.size)
    }

    @Test
    fun givesUpWhenNothingArrivesAndKeepsWhatItHas() {
        target.writeBytes(body.copyOf(1000))
        val url = serve { _, _, _ -> }
        assertFailsWith<IOException> { run(url) }
        assertEquals(1000, target.length())
    }

    @Test
    fun cancellationKeepsThePartialFile() {
        target.writeBytes(body.copyOf(1000))
        val url = serve { reply, offset, _ -> reply.partial(offset) }
        assertFailsWith<InterruptedException> { run(url) { throw InterruptedException("cancelled") } }
        assertEquals(1000, target.length())
    }
}
