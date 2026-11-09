package moe.n4tsu.dextop.linux

import org.json.JSONObject
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LinuxImageManifestTest {
    private val keys: KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded)
    private val digest = "a".repeat(64)

    private fun payload(url: String = "https://github.com/NarYuki/dextop-linux-images/releases/download/linux-image-1/debian.tar.zst", minProtocol: Int = 44) =
        JSONObject().put("format", 1).put("distro", "debian").put("arch", "arm64").put("minProtocol", minProtocol)
            .put("version", "1").put("url", url).put("size", 10L).put("sha256", digest).toString().toByteArray()

    private fun envelope(body: ByteArray, signer: KeyPair = keys): String {
        val signature = Signature.getInstance("SHA256withECDSA").run { initSign(signer.private); update(body); sign() }
        return JSONObject().put("payload", Base64.getEncoder().encodeToString(body))
            .put("signature", Base64.getEncoder().encodeToString(signature)).toString()
    }

    @Test
    fun acceptsSignedManifest() {
        val image = LinuxImageManifest.parse(envelope(payload()), publicKey, 44)
        assertEquals("1", image.version)
        assertEquals(digest, image.sha256)
    }

    @Test
    fun rejectsAnotherSigner() {
        val other = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        assertFailsWith<IllegalStateException> { LinuxImageManifest.parse(envelope(payload(), other), publicKey, 44) }
    }

    @Test
    fun rejectsChangedPayload() {
        val signed = JSONObject(envelope(payload()))
        signed.put("payload", Base64.getEncoder().encodeToString(payload(minProtocol = 1)))
        assertFailsWith<IllegalStateException> { LinuxImageManifest.parse(signed.toString(), publicKey, 44) }
    }

    @Test
    fun rejectsOtherLocationsAndNewerProtocols() {
        assertFailsWith<IllegalStateException> { LinuxImageManifest.parse(envelope(payload(url = "https://example.com/debian.tar.zst")), publicKey, 44) }
        assertFailsWith<IllegalStateException> { LinuxImageManifest.parse(envelope(payload(minProtocol = 45)), publicKey, 44) }
    }
}
