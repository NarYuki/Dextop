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
