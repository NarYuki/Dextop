package moe.n4tsu.dextop.linux

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import android.os.ParcelFileDescriptor

/**
 * Plays a container's sound in Dextop's app process: raw 48 kHz stereo
 * s16le PCM from a private Binder descriptor, into an interactive AudioTrack with media
 * usage, so Android's media volume (and its volume keys) apply.
 */
internal class LinuxAudioPlayer(private val containerId: String, val generation: Int, private val outputBurst: String? = null,
    private val openStream: () -> ParcelFileDescriptor) {
    companion object {
        private const val TAG = "DextopLinuxAudio"
        private const val RATE = 48_000
        /** Silence this long pauses the AudioTrack, so no idle audio path stays open. */
        private const val IDLE_PAUSE_MS = 2_000L
    }

    @Volatile private var running = true
    @Volatile private var stream: ParcelFileDescriptor? = null

    init {
        Thread(::run, "DextopLinuxAudio-$containerId").apply { isDaemon = true; start() }
    }

    private fun run() {
        runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO) }
        val burst = LinuxAudioBuffering.burstFrames(outputBurst)
        val target = LinuxAudioBuffering.targetFrames(burst)
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder()
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setBufferSizeInBytes(LinuxAudioBuffering.capacityFrames(burst) * LinuxAudioBuffering.FRAME_BYTES)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.onFailure { Log.w(TAG, "AudioTrack unavailable", it) }.getOrNull() ?: return
        track.setBufferSizeInFrames(target)
        track.setStartThresholdInFrames(track.bufferSizeInFrames)
        Log.i(TAG, "audio mode=${track.performanceMode} rate=$RATE burst=$burst " +
            "bufferFrames=${track.bufferSizeInFrames} capacityFrames=${track.bufferCapacityInFrames}")
        val buffer = ByteArray(LinuxAudioBuffering.READ_BYTES)
        var underruns = track.underrunCount
        var lastTune = 0L
        var lastConnectError = 0L
        try {
            while (running) {
                val connection = runCatching { openStream() }.onFailure {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastConnectError >= 10_000) {
                        Log.w(TAG, "Cannot connect audio $containerId generation=$generation", it)
                        lastConnectError = now
                    }
                }.getOrNull()
                if (connection == null) {
                    SystemClock.sleep(500)
                    continue
                }
                stream = connection
                Log.i(TAG, "playing $containerId over Binder FD generation=$generation")
                try {
                    ParcelFileDescriptor.AutoCloseInputStream(connection).use { pcm ->
                        val input = java.io.DataInputStream(pcm)
                        var lastSound = 0L
                        while (running) {
                            // Backpressure must not turn a short rendering stall into seconds
                            // of permanently delayed sound. Keep the newest 20ms after a stall.
                            var stale = LinuxAudioBuffering.staleBytes(input.available())
                            while (running && stale > 0) {
                                val bytes = minOf(stale, buffer.size)
                                input.readFully(buffer, 0, bytes)
                                stale -= bytes
                            }
                            // Pipe reads can split a stereo frame; feed only complete frames.
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
                            if (now - lastTune >= 1000) {
                                val count = track.underrunCount
                                if (count > underruns && track.bufferSizeInFrames < track.bufferCapacityInFrames) {
                                    val size = (track.bufferSizeInFrames + burst).coerceAtMost(track.bufferCapacityInFrames)
                                    track.setBufferSizeInFrames(size)
                                    track.setStartThresholdInFrames(track.bufferSizeInFrames)
                                    Log.i(TAG, "audio buffer adapted frames=${track.bufferSizeInFrames} underruns=$count")
                                }
                                underruns = count
                                lastTune = now
                            }
                        }
                    }
                } catch (error: java.io.IOException) {
                    if (running) Log.w(TAG, "Audio connection lost for $containerId; reconnecting", error)
                } finally {
                    stream = null
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
        runCatching { stream?.close() }
    }
}
