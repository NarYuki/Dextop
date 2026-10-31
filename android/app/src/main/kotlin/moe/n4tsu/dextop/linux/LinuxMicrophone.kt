package moe.n4tsu.dextop.linux

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileOutputStream

/**
 * Android's microphone for Linux: while a Linux application records from the container's
 * default source, AudioRecord's PCM goes to it; otherwise nothing records, so Android's
 * microphone indicator shows exactly when Linux listens.
 */
internal object LinuxMicrophone {
    private const val TAG = "DextopLinuxMic"
    private const val RATE = 48_000
    private val active = HashMap<String, Thread>()
    @Volatile private var started = false

    /** Polls who records, for the containers that have sound, as long as Dextop runs. */
    fun start(context: Context, service: () -> ILinuxContainerService?, containers: () -> Collection<String>) {
        if (started) return
        started = true
        val app = context.applicationContext
        Thread({
            while (true) {
                runCatching {
                    val linux = service()
                    val allowed = app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    val wanted = if (linux == null || !allowed) emptySet() else
                        containers().filter { runCatching { linux.microphoneWanted(it) }.getOrDefault(false) }.toSet()
                    synchronized(active) {
                        active.keys.filter { it !in wanted }.forEach { active.remove(it)?.interrupt() }
                        for (id in wanted) if (active[id]?.isAlive != true) {
                            active[id] = Thread({ record(linux!!, id) }, "DextopLinuxMic-$id").apply { isDaemon = true; start() }
                        }
                    }
                }
                try { Thread.sleep(1000) } catch (_: InterruptedException) { return@Thread }
            }
        }, "DextopLinuxMicWatch").apply { isDaemon = true; start() }
    }

    @SuppressLint("MissingPermission") // Checked by the watcher before it records.
    private fun record(linux: ILinuxContainerService, id: String) {
        val minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, RATE / 5 * 2))
        }.getOrElse { Log.w(TAG, "Cannot open the microphone", it); return }
        try {
            val stream: ParcelFileDescriptor = linux.openMicrophoneStream(id)
            FileOutputStream(stream.fileDescriptor).use { output ->
                recorder.startRecording()
                val buffer = ByteArray(RATE / 50 * 2) // 20 ms
                while (!Thread.currentThread().isInterrupted) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) output.write(buffer, 0, read) else if (read < 0) break
                }
            }
            stream.close()
        } catch (error: Exception) {
            if (!Thread.currentThread().isInterrupted) Log.w(TAG, "Linux microphone $id stopped", error)
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }
}
