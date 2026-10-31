package moe.n4tsu.cardex

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import java.security.MessageDigest

/** Package IDs differ by distribution; component class names stay in the original namespace. */
internal object DextopTarget {
    private val packages = listOf("moe.n4tsu.gpdextop", "moe.n4tsu.dextop")
    private const val RELAY = "moe.n4tsu.dextop.CardexRelayService"

    fun isInstalled(pm: PackageManager): Boolean = packages.any {
        runCatching { pm.getPackageInfo(it, 0) }.isSuccess
    }

    fun find(pm: PackageManager): String? = choose(
        installed = { isTrusted(pm, it) },
        hasRelay = { pm.resolveService(relayIntent(it), 0) != null },
    )

    fun isTrusted(pm: PackageManager, packageName: String): Boolean = runCatching {
        val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signing = info.signingInfo ?: return false
        val multiple = signing.hasMultipleSigners()
        val signers = if (multiple) signing.apkContentsSigners else signing.signingCertificateHistory
        val hashes = signers.mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }
        DextopIdentity.matches(packageName, hashes, multiple)
    }.getOrDefault(false)

    internal fun choose(installed: (String) -> Boolean, hasRelay: (String) -> Boolean): String? {
        val available = packages.filter(installed)
        return available.firstOrNull(hasRelay) ?: available.firstOrNull()
    }

    fun relayIntent(packageName: String) = Intent().setComponent(ComponentName(packageName, RELAY))
}
