package moe.n4tsu.cardex

/** Pins each distribution's identity to its own certificates. No cross-package fallback. */
internal object DextopIdentity {
    private val certificates = mapOf(
        "moe.n4tsu.gpdextop" to setOf(
            // User-provided Play Console certificates.zip: deployment, hybrid classical, hybrid PQC.
            "70cac562ae9cb8085c707624ff259a43ae9cb03d4c4d2d26527de813a48810a9",
            "9433ec4195c7490a49544bac71bbbc8e76be91acb1d1754206d36ca82b31c5c1",
            "bc022ef55ee45c64cffbea6178aed34c74f085e1714cee31e7efae41f056604e",
        ),
        // Verified against the installed signed GitHub-distribution Dextop APK.
        "moe.n4tsu.dextop" to setOf(
            "847833703f59daa31c28705ae694f1ceb11b6d0d4d1e93a5997c01a5303c34c5",
        ),
    )

    fun matches(packageName: String, hashes: Set<String>, multipleSigners: Boolean = false): Boolean {
        val trusted = certificates[packageName] ?: return false
        if (hashes.isEmpty()) return false
        return if (multipleSigners) hashes.all { it.lowercase() in trusted }
        else hashes.any { it.lowercase() in trusted }
    }
}
