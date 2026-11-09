package moe.n4tsu.dextop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardCompanionCallerVerifierTest {
    private val packageName = setOf("moe.n4tsu.dextop.cardex")

    @Test
    fun acceptsGithubReleaseCertificate() {
        assertTrue(
            CardCompanionCallerVerifier.matchesOfficialIdentity(
                packageName,
                setOf("847833703f59daa31c28705ae694f1ceb11b6d0d4d1e93a5997c01a5303c34c5"),
            )
        )
    }

    @Test
    fun acceptsPlayAppSigningCertificate() {
        assertTrue(
            CardCompanionCallerVerifier.matchesOfficialIdentity(
                packageName,
                setOf("44B5A3B12A4245869C67F79A65A3B01BCEB48E89F89886E754D137276262E6D5"),
            )
        )
    }

    @Test
    fun acceptsPlayHybridCertificatesUsedOnApi37AndNewer() {
        listOf(
            "8cdc6ff6932c1cbb6c0ada774f1e7657a0dca24c7075ac712761bfb917a0caff",
            "1e590ff0261b6bbfee9d5796462c61f6feff4a5aa0bc3be6582081caa2614b98",
            "93cb924b9cbca2c2d3b2c26b3c8aba841c3586e1051156950adf69a8a48d59a3",
            "b0383a2153e333f42d426dbeb43253e591a8d91cc18c24aed6b00d290e968bac",
        ).forEach { certificate ->
            assertTrue(
                CardCompanionCallerVerifier.matchesOfficialIdentity(packageName, setOf(certificate)),
                certificate,
            )
        }
    }

    @Test
    fun rejectsUnknownCertificateAndLookalikePackage() {
        assertFalse(
            CardCompanionCallerVerifier.matchesOfficialIdentity(packageName, setOf("00"))
        )
        assertFalse(
            CardCompanionCallerVerifier.matchesOfficialIdentity(
                setOf("moe.n4tsu.dextop.cardex.fake"),
                setOf("847833703f59daa31c28705ae694f1ceb11b6d0d4d1e93a5997c01a5303c34c5"),
            )
        )
    }
}
