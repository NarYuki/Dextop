package moe.n4tsu.dextop.linux

import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintJob
import android.print.PrintJobInfo
import android.print.PrintManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/** No background activity launch or silent printing: the user opens Android's print dialog. */
internal object LinuxPrintBridge {
    private const val CHANNEL = "linux_print"
    private val shown = ConcurrentHashMap.newKeySet<String>()
    fun poll(context: Context, service: ILinuxContainerService, containers: List<String>) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            moe.n4tsu.dextop.localText("Linuxから印刷", "Print from Linux"), NotificationManager.IMPORTANCE_DEFAULT))
        val active = HashSet<String>()
        for (id in containers) {
            val jobs = JSONObject(service.integrationStatus(id)).optJSONArray("printJobs") ?: JSONArray()
            for (index in 0 until jobs.length()) {
                val job = jobs.getJSONObject(index)
                val jobId = job.getString("id")
                val key = "$id:$jobId"
                active += key
                if (!shown.add(key)) continue
                val title = job.optString("title").ifBlank { "Linux document" }
                val open = PendingIntent.getActivity(context, key.hashCode(),
                    Intent(context, LinuxPrintActivity::class.java).putExtra("container", id).putExtra("job", jobId).putExtra("title", title),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val discard = PendingIntent.getBroadcast(context, key.hashCode(),
                    Intent(context, Dismiss::class.java).putExtra("container", id).putExtra("job", jobId),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                manager.notify(key.hashCode(), Notification.Builder(context, CHANNEL)
                    .setSmallIcon(moe.n4tsu.dextop.R.drawable.ic_qs_dextop)
                    .setContentTitle(title)
                    .setContentText(moe.n4tsu.dextop.localText("タップしてAndroidで印刷", "Tap to print with Android"))
                    .setContentIntent(open).setAutoCancel(false)
                    .addAction(Notification.Action.Builder(null, moe.n4tsu.dextop.localText("印刷", "Print"), open).build())
                    .addAction(Notification.Action.Builder(null, moe.n4tsu.dextop.localText("破棄", "Discard"), discard).build()).build())
            }
        }
        for (key in shown.toList()) if (key !in active) { manager.cancel(key.hashCode()); shown.remove(key) }
    }

    fun open(context: Context, id: String, job: String, title: String = "Linux document") {
        require(job.matches(Regex("[a-f0-9]{32}")))
        context.startActivity(Intent(context, LinuxPrintActivity::class.java).putExtra("container", id)
            .putExtra("job", job).putExtra("title", title.take(256)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun discard(context: Context, id: String, job: String) {
        LinuxContainerClient.call(context, { service -> service.integrationAction(id,
            JSONObject().put("op", "print-delete").put("job", job).toString()) }, {
            context.getSystemService(NotificationManager::class.java).cancel("$id:$job".hashCode())
            shown.remove("$id:$job")
        }, {})
    }
    class Dismiss : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pending = goAsync()
            val id = intent.getStringExtra("container") ?: return pending.finish()
            val job = intent.getStringExtra("job") ?: return pending.finish()
            LinuxContainerClient.call(context, { it.integrationAction(id, JSONObject().put("op", "print-delete").put("job", job).toString()) }, {
                context.getSystemService(NotificationManager::class.java).cancel("$id:$job".hashCode())
                shown.remove("$id:$job"); pending.finish()
            }, { pending.finish() })
        }
    }
}

class LinuxPrintActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra("container") ?: return finish()
        val jobId = intent.getStringExtra("job") ?: return finish()
        val title = intent.getStringExtra("title")?.take(256) ?: "Linux document"
        if (!jobId.matches(Regex("[a-f0-9]{32}"))) return finish()
        LinuxContainerClient.call(this, { service ->
            val file = File.createTempFile("dextop-print-", ".pdf", cacheDir)
            try {
                ParcelFileDescriptor.AutoCloseInputStream(service.openIntegrationPrint(id, jobId)).use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(65536)
                        var bytes = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            bytes += count
                            check(bytes <= 64L*1024*1024) { "Print document exceeds 64 MiB" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                val pages = PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { it.pageCount }
                check(pages > 0) { "The print document has no pages" }
                file to pages
            } catch (error: Throwable) { file.delete(); throw error }
        }, { (file, pages) ->
            if (isFinishing || isDestroyed) { file.delete(); return@call }
            var printJob: PrintJob? = null
            val adapter = object : PrintDocumentAdapter() {
                override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?, cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
                    if (cancellationSignal?.isCanceled == true) { callback.onLayoutCancelled(); return }
                    callback.onLayoutFinished(PrintDocumentInfo.Builder(title).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(pages).build(), oldAttributes != newAttributes)
                }
                override fun onWrite(ranges: Array<out PageRange>, destination: ParcelFileDescriptor, cancellation: CancellationSignal, callback: WriteResultCallback) {
                    Thread({
                        runCatching {
                            val all = ranges.any { it.start == 0 && it.end >= pages-1 }
                            val source = if (all) file.inputStream() else {
                                val service = checkNotNull(LinuxContainerClient.service()) { "Linux disconnected" }
                                val selection = JSONArray(ranges.map { range -> JSONArray(listOf(range.start, minOf(range.end, pages-1))) })
                                ParcelFileDescriptor.AutoCloseInputStream(service.filterIntegrationPrint(id, jobId, selection.toString()))
                            }
                            source.use { input -> FileOutputStream(destination.fileDescriptor).use { output ->
                                val buffer = ByteArray(65536)
                                while (true) {
                                    if (cancellation.isCanceled) return@use
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                }
                            } }
                        }.onSuccess { if (cancellation.isCanceled) callback.onWriteCancelled() else callback.onWriteFinished(if (ranges.any { it.start == 0 && it.end >= pages-1 }) arrayOf(PageRange.ALL_PAGES) else ranges) }
                            .onFailure { callback.onWriteFailed(it.message) }
                    }, "DextopPrintDocument").start()
                }
                override fun onFinish() {
                    val state = printJob?.info?.state
                    if (state in setOf(PrintJobInfo.STATE_QUEUED, PrintJobInfo.STATE_STARTED, PrintJobInfo.STATE_BLOCKED, PrintJobInfo.STATE_COMPLETED))
                        LinuxPrintBridge.discard(this@LinuxPrintActivity, id, jobId)
                    file.delete()
                    finish()
                }
            }
            runCatching { printJob = getSystemService(PrintManager::class.java).print(title, adapter, null) }
                .onFailure { file.delete(); failure(it) }
        }, { failure(it) })
    }
    private fun failure(error: Throwable) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this).setTitle(moe.n4tsu.dextop.localText("印刷できません", "Cannot print"))
            .setMessage(error.message).setPositiveButton(android.R.string.ok) { _, _ -> finish() }
            .setOnCancelListener { finish() }.show()
    }
}
