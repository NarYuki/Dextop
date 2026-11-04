package moe.n4tsu.dextop.linux

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The keyring (Secret Service) of each Linux environment, as chosen in its setup:
 *  - "account": the Linux account password, stored encrypted with Android Keystore;
 *  - "auto": Dextop makes a strong random password, keeps it encrypted with a key of the
 *    Android Keystore (hardware-backed where the device has it, never leaving it), and unlocks
 *    the keyring with it whenever a session starts;
 *  - "manual": the user's own password creates the keyring at the first session; Dextop keeps
 *    nothing, and the keyring asks for it as on a desktop.
 * The password reaches the Linux service in memory only ([publish]); the service hands it to
 * each session through a file only Linux's user may read, removed as soon as it is read.
 */
internal object LinuxKeyring {
    const val AUTO = "auto"
    const val ACCOUNT = "account"
    const val MANUAL = "manual"
    private const val PREFS = "linux-keyring"
    private const val KEY_ALIAS = "dextop-linux-keyring"
    private const val TAG_BITS = 128

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mode(context: Context, containerId: String): String? = prefs(context).getString("mode_$containerId", null)

    /**
     * Sets up the keyring of [containerId]: [mode] [AUTO] makes and stores its password,
     * [ACCOUNT] stores the chosen account password; [MANUAL] passes [password] once.
     */
    fun setup(context: Context, containerId: String, mode: String, password: String?, service: ILinuxContainerService?) {
        LinuxPaths.container(containerId)
        when (mode) {
            AUTO, ACCOUNT -> {
                val secret = if (mode == ACCOUNT) requireNotNull(password).also { require(it.isNotEmpty()) } else generate()
                prefs(context).edit()
                    .putString("mode_$containerId", mode)
                    .putString("secret_$containerId", encrypt(secret))
                    .apply()
                service?.setKeyringSecret(containerId, secret, true)
            }
            MANUAL -> {
                require(!password.isNullOrEmpty()) { "A keyring password is required" }
                prefs(context).edit().putString("mode_$containerId", MANUAL).remove("secret_$containerId").apply()
                service?.setKeyringSecret(containerId, password, false)
            }
            else -> throw IllegalArgumentException("Unknown keyring mode $mode")
        }
    }

    /** Hands the stored passwords to a newly connected Linux service. */
    fun publish(context: Context, service: ILinuxContainerService) {
        val all = prefs(context).all
        all.keys.filter { it.startsWith("secret_") }.forEach { key ->
            val id = key.removePrefix("secret_")
            val secret = runCatching { decrypt(all[key] as String) }.getOrNull() ?: return@forEach
            runCatching { service.setKeyringSecret(id, secret, true) }
        }
    }

    /** Forgets [containerId]'s keyring password (the environment was removed). */
    fun forget(context: Context, containerId: String) {
        prefs(context).edit().remove("mode_$containerId").remove("secret_$containerId").apply()
    }

    /** 32 random bytes, as unpadded base64url: 256 bits, printable for the keyring prompt's stdin. */
    private fun generate(): String {
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }

    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val sealed = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, 12))
        return String(cipher.doFinal(sealed, 12, sealed.size - 12), Charsets.UTF_8)
    }
}
