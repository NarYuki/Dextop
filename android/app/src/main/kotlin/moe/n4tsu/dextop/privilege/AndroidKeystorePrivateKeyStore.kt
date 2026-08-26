package moe.n4tsu.dextop.privilege

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.flyfishxu.kadb.cert.KadbPrivateKeyStore
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the ADB identity with a non-exportable Android Keystore key. */
internal class AndroidKeystorePrivateKeyStore(context: Context) : KadbPrivateKeyStore {
    private val encryptedFile = context.filesDir.resolve("dextop-embedded-adb-identity.bin")
    private val legacyPlaintextFile = context.filesDir.resolve("dextop-embedded-adb-key.pem")
    private val lock = Any()

    override fun readPrivateKeyPem(): ByteArray? = synchronized(lock) {
        migrateLegacyPlaintextIfNeeded()
        if (!encryptedFile.isFile) return@synchronized null
        val payload = encryptedFile.readBytes()
        require(payload.size > HEADER_SIZE && payload[0] == FORMAT_VERSION) {
            "Invalid encrypted ADB identity"
        }
        val iv = payload.copyOfRange(1, HEADER_SIZE)
        val ciphertext = payload.copyOfRange(HEADER_SIZE, payload.size)
        Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            doFinal(ciphertext)
        }
    }

    override fun writePrivateKeyPemAtomic(privateKeyPem: ByteArray) = synchronized(lock) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
        val payload = byteArrayOf(FORMAT_VERSION) + cipher.iv + cipher.doFinal(privateKeyPem)
        val temporary = File(encryptedFile.parentFile, "${encryptedFile.name}.tmp")
        temporary.outputStream().use { stream ->
            stream.write(payload)
            stream.fd.sync()
        }
        check(temporary.renameTo(encryptedFile)) { "Unable to commit encrypted ADB identity" }
        eraseLegacyPlaintext()
    }

    override fun clear() = synchronized(lock) {
        encryptedFile.delete()
        encryptedFile.resolveSibling("${encryptedFile.name}.tmp").delete()
        eraseLegacyPlaintext()
        keyStore().deleteEntry(KEY_ALIAS)
    }

    fun hasStoredIdentity(): Boolean = synchronized(lock) {
        legacyPlaintextFile.isFile ||
            (encryptedFile.isFile && keyStore().containsAlias(KEY_ALIAS))
    }

    private fun migrateLegacyPlaintextIfNeeded() {
        if (encryptedFile.isFile || !legacyPlaintextFile.isFile) return
        val plaintext = legacyPlaintextFile.readBytes()
        try {
            writePrivateKeyPemAtomic(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun eraseLegacyPlaintext() {
        if (!legacyPlaintextFile.isFile) return
        runCatching {
            legacyPlaintextFile.outputStream().use { stream ->
                stream.write(ByteArray(legacyPlaintextFile.length().toInt()))
                stream.fd.sync()
            }
        }
        legacyPlaintextFile.delete()
    }

    private fun secretKey(): SecretKey {
        val existing = keyStore().getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun File.resolveSibling(name: String) = File(parentFile, name)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "moe.n4tsu.dextop.embedded_adb_identity"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val IV_BYTES = 12
        const val HEADER_SIZE = 1 + IV_BYTES
        const val FORMAT_VERSION: Byte = 1
    }
}
