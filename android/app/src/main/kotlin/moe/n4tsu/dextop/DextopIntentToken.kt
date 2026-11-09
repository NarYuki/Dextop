package moe.n4tsu.dextop

import android.content.Context
import android.content.Intent
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Proof that an intent reaching the exported [MainActivity] came from Dextop's own shortcuts or
 * tiles. Its actions start or stop the desktop, which other apps must not be able to ask for.
 */
internal object DextopIntentToken {
    private const val PREFS = "dextop_intent_token"
    private const val KEY = "token"
    private const val EXTRA = "app.freedextop.extra.INTENT_TOKEN"

    @Synchronized
    fun value(context: Context): String {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        preferences.getString(KEY, null)?.let { return it }
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP).also {
            preferences.edit().putString(KEY, it).apply()
        }
    }

    fun attach(context: Context, intent: Intent): Intent = intent.putExtra(EXTRA, value(context))

    fun carriedBy(intent: Intent?): String? = runCatching { intent?.getStringExtra(EXTRA) }.getOrNull()

    fun isGenuine(context: Context, intent: Intent?): Boolean = matches(value(context), carriedBy(intent))

    internal fun matches(expected: String, provided: String?): Boolean =
        !provided.isNullOrEmpty() && MessageDigest.isEqual(expected.toByteArray(), provided.toByteArray())
}
