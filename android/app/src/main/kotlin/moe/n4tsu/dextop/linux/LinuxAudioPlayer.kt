package moe.n4tsu.dextop.linux

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Plays a container's sound in Dextop's app process: raw 48 kHz stereo
 * s16le PCM from PulseAudio's loopback port, into an AudioTrack with media
 * usage, so Android's media volume (and its volume keys) apply.
 */
internal class LinuxAudioPlayer(private val containerId: String, val port: Int) {
    companion object {
        private const val TAG = "DextopLinuxAudio"
        private const val RATE = 48_000
        /** Silence this long pauses the AudioTrack, so no idle audio path stays open. */
        private const val IDLE_PAUSE_MS = 2_000L
    }

    @Volatile private var running = true
    @Volatile private var socket: Socket? = null

    init {
        Thread(::run, "DextopLinuxAudio-$containerId").apply { isDaemon = true; start() }
    }

    private fun run() {
        val minimum = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder()
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(minimum * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.onFailure { Log.w(TAG, "AudioTrack unavailable", it) }.getOrNull() ?: return
        val buffer = ByteArray((minimum.coerceAtLeast(4096) + 3) / 4 * 4)
        var lastConnectError = 0L
        try {
            while (running) {
                val connection = runCatching {
                    Socket().apply {
                        // Socket.port is 0 before connecting; use the container's port.
                        connect(InetSocketAddress("127.0.0.1", this@LinuxAudioPlayer.port), 1_000)
                        tcpNoDelay = true
                    }
                }.onFailure {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastConnectError >= 10_000) {
                        Log.w(TAG, "Cannot connect audio $containerId port=$port", it)
                        lastConnectError = now
                    }
                }.getOrNull()
                if (connection == null) {
                    SystemClock.sleep(500)
                    continue
                }
                socket = connection
                Log.i(TAG, "playing $containerId from port $port")
                try {
                    connection.use {
                        val input = java.io.DataInputStream(it.getInputStream())
                        var lastSound = 0L
                        while (running) {
                            // TCP reads can split a stereo frame; feed only complete frames.
                            input.readFully(buffer)
                            val read = buffer.size
                            var silent = true
                            for (i in 0 until read) if (buffer[i] != 0.toByte()) { silent = false; break }
                            val now = SystemClock.uptimeMillis()
                            if (!silent) lastSound = now
                            if (now - lastSound > IDLE_PAUSE_MS) {
                                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) { track.pause(); track.flush() }
                                continue
                            }
                            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
                            var offset = 0
                            while (running && offset < read) {
                                val written = track.write(buffer, offset, read - offset)
                                check(written > 0) { "AudioTrack write failed: $written" }
                                offset += written
                            }
                        }
                    }
                } catch (error: java.io.IOException) {
                    if (running) Log.w(TAG, "Audio connection lost for $containerId; reconnecting", error)
                } finally {
                    socket = null
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
                    track.flush()
                }
                if (running) SystemClock.sleep(500)
            }
        } catch (error: Throwable) {
            if (running) Log.w(TAG, "sound of $containerId stopped", error)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
    }
}
