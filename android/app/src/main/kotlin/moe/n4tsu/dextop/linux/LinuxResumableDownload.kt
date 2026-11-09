package moe.n4tsu.dextop.linux

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection

/** Fetches a file of known size into [target], continuing from what an earlier attempt left there. */
internal object LinuxResumableDownload {
    private const val BUFFER = 256 * 1024
    private const val MAX_STALLED_ATTEMPTS = 8

    /**
     * [connect] opens the URL, asking for the bytes from the given offset when it is above zero.
     * Network errors retry until [MAX_STALLED_ATTEMPTS] attempts in a row add nothing; an HTTP error
     * ends at once. [target] stays in place on any failure or cancellation for the next attempt.
     */
    fun download(
        target: File,
        size: Long,
        connect: (Long) -> HttpURLConnection,
        checkCancelled: () -> Unit,
        onProgress: (Long) -> Unit,
        pause: (Long) -> Unit = Thread::sleep,
    ) {
        var stalled = 0
        while (true) {
            checkCancelled()
            if (target.length() > size) target.delete()
            val before = target.length()
            if (before == size) return
            val failure = try {
                transfer(target, size, connect, checkCancelled, onProgress)
                null
            } catch (error: IOException) {
                error
            }
            val after = target.length()
            if (after == size) return
            stalled = if (after > before) 0 else stalled + 1
            if (stalled >= MAX_STALLED_ATTEMPTS) throw failure ?: IOException(moe.n4tsu.dextop.localText("uiTheLinuxImageDownloadWasInterruptedCheckYourConnectionAndTryAgain"))
            if (failure != null) pause(minOf(1_000L * stalled.coerceAtLeast(1), 8_000L))
        }
    }

    private fun transfer(
        target: File,
        size: Long,
        connect: (Long) -> HttpURLConnection,
        checkCancelled: () -> Unit,
        onProgress: (Long) -> Unit,
    ) {
        val offset = target.length()
        val connection = connect(offset)
        try {
            val append = when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> {
                    if (connection.getHeaderField("Content-Range")?.startsWith("bytes $offset-") != true) {
                        throw IOException(moe.n4tsu.dextop.localText("uiTheLinuxImageDownloadWasInterruptedCheckYourConnectionAndTryAgain"))
                    }
                    true
                }
                HttpURLConnection.HTTP_OK -> false
                416 -> {
                    target.delete()
                    return
                }
                else -> throw IllegalStateException(
                    moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiHTTP0For1"), code, connection.url),
                )
            }
            var received = if (append) offset else 0L
            connection.inputStream.use { input ->
                FileOutputStream(target, append).use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (received < size) {
                        checkCancelled()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        received += read
                        onProgress(received)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
