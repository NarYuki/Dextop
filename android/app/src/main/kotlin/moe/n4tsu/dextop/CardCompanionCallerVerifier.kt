package moe.n4tsu.dextop

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

/** Verifies official Car Companion callers without requiring both apps to share one signer. */
internal object CardCompanionCallerVerifier {
    private const val COMPANION_PACKAGE = "moe.n4tsu.dextop.cardex"

    // Dextop GitHub release certificate and Google Play Car Companion certificate.
    private val trustedCertificateSha256 = setOf(
        "847833703f59daa31c28705ae694f1ceb11b6d0d4d1e93a5997c01a5303c34c5",
        "44b5a3b12a4245869c67f79a65a3b01bceb48e89f89886e754d137276262e6d5",
    )

    fun isTrusted(context: Context, uid: Int): Boolean {
        if (uid < 0) return false
        val packages = context.packageManager.getPackagesForUid(uid)?.toSet().orEmpty()
        if (COMPANION_PACKAGE !in packages) return false
        if (BuildConfig.DEBUG) return true

        val info = runCatching {
            context.packageManager.getPackageInfo(
                COMPANION_PACKAGE,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
        }.getOrNull() ?: return false
        if (info.applicationInfo?.uid != uid) return false

        val signingInfo = info.signingInfo ?: return false
        val certificates = if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        }
        return matchesOfficialIdentity(
            packages,
            certificates.mapTo(mutableSetOf()) { it.toByteArray().sha256() },
        )
    }

    internal fun matchesOfficialIdentity(
        packages: Set<String>,
        certificateSha256: Set<String>,
    ): Boolean = COMPANION_PACKAGE in packages &&
        certificateSha256.any { it.lowercase() in trustedCertificateSha256 }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { byte -> "%02x".format(byte) }
}
