package moe.n4tsu.dextop.linux

import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

internal data class LinuxPrebuiltImage(val version: String, val url: String, val size: Long, val sha256: String)

/** Reads the signed description of the prebuilt Debian image (tool/linux_image/LinuxImageSigner.java). */
internal object LinuxImageManifest {
    const val URL = "https://naryuki.github.io/dextop-linux-images/manifest.json"
    const val MARKER = "etc/dextop/prebuilt-image.json"
    private const val PUBLIC_KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEccYY2CgBDKjHkOJd4W8eK/60XGM13A4XOPQS90ivUxNA/VMcWtyMUrtemVaRIJEfAflzEnjmcJBOJIi4Szo+8w=="
    private const val RELEASE_PREFIX = "https://github.com/NarYuki/dextop-linux-images/releases/download/"
    private const val DEBUG_PROPERTY = "debug.dextop.linux_image"

    /** LinuxSetupPlan phases already contained in the image. */
    val BAKED_PHASES = setOf("graphics", "desktop", "gpu")

    fun parse(text: String, publicKey: String = PUBLIC_KEY, protocol: Int): LinuxPrebuiltImage {
        val envelope = JSONObject(text)
        val payload = Base64.getDecoder().decode(envelope.getString("payload"))
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)))
        val verified = Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(payload)
            verify(Base64.getDecoder().decode(envelope.getString("signature")))
        }
        check(verified) { "Invalid manifest signature" }
        val manifest = JSONObject(String(payload, Charsets.UTF_8))
        check(manifest.getInt("format") == 1) { "Unsupported manifest format" }
        check(manifest.getString("distro") == "debian" && manifest.getString("arch") == LinuxCatalog.ARCHITECTURE) { "Manifest is for another system" }
        check(manifest.getInt("minProtocol") <= protocol) { "Image needs a newer Dextop" }
        val url = manifest.getString("url")
        check(url.startsWith(RELEASE_PREFIX) && url.endsWith(".tar.zst")) { "Unexpected image location" }
        val sha256 = manifest.getString("sha256")
        check(sha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid image digest" }
        val size = manifest.getLong("size")
        check(size > 0) { "Invalid image size" }
        return LinuxPrebuiltImage(manifest.getString("version"), url, size, sha256)
    }

    /** `adb shell setprop debug.dextop.linux_image linuxcontainers` keeps the original setup for debugging. */
    fun linuxContainersForced(): Boolean = runCatching {
        val process = ProcessBuilder("/system/bin/getprop", DEBUG_PROPERTY).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }.trim() == "linuxcontainers"
    }.getOrDefault(false)
}
