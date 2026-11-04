package moe.n4tsu.dextop.linux

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android's document picker for Linux: a file to open (an AppImage, a backup) or a document to
 * create (an exported backup). The picked document's URI goes to the waiting callback; null when
 * the user backed out.
 */
class LinuxDocumentActivity : android.app.Activity() {
    companion object {
        private const val EXTRA_REQUEST = "request"
        private const val EXTRA_CREATE = "create"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_MIME = "mime"
        private val next = AtomicInteger()
        private val waiting = ConcurrentHashMap<Int, (Uri?) -> Unit>()

        /** Opens the picker: [create] asks for a new document named [name]. */
        fun pick(context: Context, create: Boolean, name: String, mime: String, done: (Uri?) -> Unit) {
            val request = next.incrementAndGet()
            waiting[request] = done
            runCatching {
                context.startActivity(Intent(context, LinuxDocumentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_REQUEST, request).putExtra(EXTRA_CREATE, create)
                    .putExtra(EXTRA_NAME, name).putExtra(EXTRA_MIME, mime))
            }.onFailure { waiting.remove(request)?.invoke(null) }
        }
    }

    private var request = 0

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        request = intent.getIntExtra(EXTRA_REQUEST, 0)
        if (savedInstanceState != null) return
        val create = intent.getBooleanExtra(EXTRA_CREATE, false)
        val mime = intent.getStringExtra(EXTRA_MIME)?.takeIf { it.isNotBlank() } ?: "*/*"
        val picker = if (create) Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType(mime).putExtra(Intent.EXTRA_TITLE, intent.getStringExtra(EXTRA_NAME).orEmpty())
        else Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime)
        runCatching {
            @Suppress("DEPRECATION")
            startActivityForResult(picker, 1)
        }.onFailure { finishWith(null) }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        finishWith(data?.data?.takeIf { resultCode == RESULT_OK })
    }

    private fun finishWith(uri: Uri?) {
        waiting.remove(request)?.invoke(uri)
        finish()
    }

    override fun onDestroy() {
        // Destroyed without an answer (the task was removed): the caller is not left waiting.
        if (isFinishing) waiting.remove(request)?.invoke(null)
        super.onDestroy()
    }
}
