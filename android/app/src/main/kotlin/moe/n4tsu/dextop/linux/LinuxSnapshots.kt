package moe.n4tsu.dextop.linux

import android.content.Context
import android.os.ParcelFileDescriptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Snapshot files ([LinuxBackups] format), kept in Dextop's own storage in the app process.
 *
 * The shell user and Linux programs cannot read or change them there, unlike files under
 * /data/local/tmp. The Linux service never opens this directory: it gets a descriptor for
 * the one file a job writes or reads. A new snapshot is written as `<name>.staged` and only
 * takes its name once the service reports the job done ([settle]).
 */
internal object LinuxSnapshots {
    private val NAME = Regex("(c[a-zA-Z0-9]+)-[0-9]{8}-[0-9]{6}\\.dextop-backup")
    private const val STAGED = ".staged"
    private val opening = mutableSetOf<String>()

    private fun directory(context: Context) = File(context.noBackupFilesDir, "linux-snapshots")

    fun file(context: Context, name: String): File {
        require(NAME.matches(name)) { moe.n4tsu.dextop.localText("uiInvalidSnapshotName") }
        return File(directory(context), name)
    }

    fun newName(containerId: String): String {
        require(containerId.matches(Regex("c[a-zA-Z0-9]+"))) { moe.n4tsu.dextop.localText("uiInvalidContainerId") }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        return "$containerId-$stamp${LinuxBackups.EXTENSION}"
    }

    /** A descriptor the service writes snapshot [name] to; [release] once the service holds it. */
    @Synchronized fun openNew(context: Context, name: String): ParcelFileDescriptor {
        val target = file(context, name)
        check(!target.exists()) { moe.n4tsu.dextop.localText("uiCannotSaveTheSnapshot") }
        directory(context).mkdirs()
        opening += name
        return runCatching {
            ParcelFileDescriptor.open(File(target.path + STAGED),
                ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE)
        }.onFailure { opening -= name }.getOrThrow()
    }

    /** After the service accepted ([started]) or refused the job for [name]. */
    @Synchronized fun release(context: Context, name: String, started: Boolean) {
        opening -= name
        if (!started) File(file(context, name).path + STAGED).delete()
    }

    fun openRead(context: Context, name: String): ParcelFileDescriptor {
        val snapshot = file(context, name)
        check(snapshot.isFile) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiSnapshot0DoesNotExist"), name) }
        return ParcelFileDescriptor.open(snapshot, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    fun delete(context: Context, name: String): Boolean = file(context, name).delete()

    /**
     * Names the staged snapshot the service's [job] finished, and discards staged files no job
     * is writing any more (a failed job, or one that ended with the app process).
     */
    @Synchronized fun settle(context: Context, job: JSONObject?) {
        val writing = job?.optString("type") == "snapshot" && job.optString("state") == "running"
        directory(context).listFiles().orEmpty().filter { it.name.endsWith(STAGED) }.forEach { staged ->
            val name = staged.name.removeSuffix(STAGED)
            when {
                name in opening || writing -> Unit
                job?.optString("type") == "snapshot" && job.optString("state") == "done" &&
                    job.optString("result") == name && NAME.matches(name) -> {
                    if (!staged.renameTo(File(staged.parentFile, name))) staged.delete()
                }
                else -> staged.delete()
            }
        }
    }

    /** Snapshot files, newest first, with what each holds. */
    fun list(context: Context): JSONArray = JSONArray().apply {
        directory(context).listFiles().orEmpty().filter { NAME.matches(it.name) && it.isFile }
            .sortedByDescending { it.name.substringAfter('-') }
            .forEach { file ->
                val metadata = runCatching { LinuxBackups.readMetadata(file.inputStream()) }.getOrNull() ?: return@forEach
                put(JSONObject().put("file", file.name).put("size", file.length())
                    .put("container", metadata.optString("container")).put("name", metadata.optString("name"))
                    .put("distro", metadata.optString("distro")).put("created", metadata.optLong("created")))
            }
    }

    /** Bytes [containerId]'s snapshots take. */
    fun bytes(context: Context, containerId: String): Long = directory(context).listFiles().orEmpty()
        .filter { NAME.matchEntire(it.name)?.groupValues?.get(1) == containerId }.sumOf { it.length() }
}
