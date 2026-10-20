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
        val buffer = ByteArray(minimum.coerceAtLeast(4096))
        try {
            while (running) {
                val connection = runCatching {
                    Socket().apply { connect(InetSocketAddress("127.0.0.1", port), 1_000); tcpNoDelay = true }
                }.getOrNull()
                if (connection == null) {
                    SystemClock.sleep(500)
                    continue
                }
                socket = connection
                Log.i(TAG, "playing $containerId from port $port")
                connection.use {
                    val input = it.getInputStream()
                    var lastSound = 0L
                    while (running) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        var silent = true
                        for (i in 0 until read) if (buffer[i] != 0.toByte()) { silent = false; break }
                        val now = SystemClock.uptimeMillis()
                        if (!silent) lastSound = now
                        if (now - lastSound > IDLE_PAUSE_MS) {
                            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) { track.pause(); track.flush() }
                            continue
                        }
                        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
                        track.write(buffer, 0, read)
                    }
                }
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
