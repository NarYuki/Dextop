package moe.n4tsu.dextop.linux

import java.security.SecureRandom
import java.util.Base64
import org.apache.commons.codec.digest.Sha2Crypt

/** Salted crypt(3) hash for account creation; the installer never needs the original password. */
internal object LinuxPasswordHash {
    fun create(password: ByteArray): String = try {
        require(password.isNotEmpty() && password.size <= 4096) { moe.n4tsu.dextop.localText("uiInvalidLinuxAccountPasswordLength") }
        val random = ByteArray(12).also(SecureRandom()::nextBytes)
        val salt = Base64.getEncoder().withoutPadding().encodeToString(random).replace('+', '.')
        Sha2Crypt.sha512Crypt(password, "\$6\$rounds=100000\$$salt")
    } finally { password.fill(0) }
}
