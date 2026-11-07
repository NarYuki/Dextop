package moe.n4tsu.dextop.linux

import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A service-lifetime encrypted value. It never keeps the plaintext passed to it. */
internal class LinuxSealedSecret private constructor(private val iv: ByteArray, private val ciphertext: ByteArray) {
    companion object {
        private val key: SecretKey by lazy { KeyGenerator.getInstance("AES").apply { init(256) }.generateKey() }

        fun seal(plaintext: ByteArray): LinuxSealedSecret = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
            LinuxSealedSecret(cipher.iv, cipher.doFinal(plaintext))
        } finally { plaintext.fill(0) }
    }

    fun open(): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, this@LinuxSealedSecret.iv))
        doFinal(ciphertext)
    }

    fun <T> useBytes(block: (ByteArray) -> T): T {
        val plaintext = open()
        return try { block(plaintext) } finally { plaintext.fill(0) }
    }
}
