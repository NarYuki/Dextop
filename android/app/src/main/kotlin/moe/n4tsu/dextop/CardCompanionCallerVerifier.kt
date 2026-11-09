package moe.n4tsu.dextop

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

/** Verifies official Car Companion callers without requiring both apps to share one signer. */
internal object CardCompanionCallerVerifier {
    private val companionPackages = setOf(
        "moe.n4tsu.dextop.cardex",
        "moe.n4tsu.dextop.cardex.driving",
    )

    // Dextop GitHub release certificate and Google Play Car Companion certificates.
    private val trustedCertificateSha256 = setOf(
        "847833703f59daa31c28705ae694f1ceb11b6d0d4d1e93a5997c01a5303c34c5",
        // Parking: Play v3.0, then v3.2 hybrid classical and PQC used on API 37 and newer.
        "44b5a3b12a4245869c67f79a65a3b01bceb48e89f89886e754d137276262e6d5",
        "8cdc6ff6932c1cbb6c0ada774f1e7657a0dca24c7075ac712761bfb917a0caff",
        "1e590ff0261b6bbfee9d5796462c61f6feff4a5aa0bc3be6582081caa2614b98",
        // Driving: Play v3.0, then v3.2 hybrid classical and PQC used on API 37 and newer.
        "8f232a21b091ac6ddc64298f845116a0ef8fe862f2911f970964d31387a1cbea",
        "93cb924b9cbca2c2d3b2c26b3c8aba841c3586e1051156950adf69a8a48d59a3",
        "b0383a2153e333f42d426dbeb43253e591a8d91cc18c24aed6b00d290e968bac",
    )

    fun isTrusted(context: Context, uid: Int): Boolean {
        if (uid < 0) return false
        val packages = context.packageManager.getPackagesForUid(uid)?.toSet().orEmpty()
        val companionPackage = companionPackages.firstOrNull { it in packages } ?: return false
        if (BuildConfig.DEBUG) return true

        val info = runCatching {
            context.packageManager.getPackageInfo(
                companionPackage,
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
    ): Boolean = packages.any { it in companionPackages } &&
        certificateSha256.any { it.lowercase() in trustedCertificateSha256 }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { byte -> "%02x".format(byte) }
}
