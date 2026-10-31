package moe.n4tsu.cardex

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class DextopIdentityTest {
    private val play = "moe.n4tsu.gpdextop"
    private val github = "moe.n4tsu.dextop"
    private val deployment = "70cac562ae9cb8085c707624ff259a43ae9cb03d4c4d2d26527de813a48810a9"
    private val classical = "9433ec4195c7490a49544bac71bbbc8e76be91acb1d1754206d36ca82b31c5c1"
    private val pqc = "bc022ef55ee45c64cffbea6178aed34c74f085e1714cee31e7efae41f056604e"
    private val release = "847833703f59daa31c28705ae694f1ceb11b6d0d4d1e93a5997c01a5303c34c5"

    @Test fun acceptsProvidedPlayCertificatesAndGithubRelease() {
        for (hash in listOf(deployment, classical, pqc)) {
            assertTrue(DextopIdentity.matches(play, setOf(hash.uppercase())))
        }
        assertTrue(DextopIdentity.matches(github, setOf(release)))
    }

    @Test fun rejectsWrongPackageOrSigner() {
        assertFalse(DextopIdentity.matches(play, setOf(release)))
        assertFalse(DextopIdentity.matches(github, setOf(deployment)))
        assertFalse(DextopIdentity.matches("$play.fake", setOf(deployment)))
        assertFalse(DextopIdentity.matches(play, setOf("00")))
        assertFalse(DextopIdentity.matches(play, emptySet()))
    }

    @Test fun allowsVerifiedRotationButRejectsUnknownAdditionalSigner() {
        assertTrue(DextopIdentity.matches(play, setOf(deployment, "new-rotated-signer")))
        assertFalse(DextopIdentity.matches(play, setOf(deployment, "unknown"), multipleSigners = true))
        assertTrue(DextopIdentity.matches(play, setOf(deployment, classical), multipleSigners = true))
    }
}
