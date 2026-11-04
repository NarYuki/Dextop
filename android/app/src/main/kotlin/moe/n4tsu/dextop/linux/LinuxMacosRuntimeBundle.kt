package moe.n4tsu.dextop.linux

import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/** Trust originates in the signed APK's build inputs, never in a caller-supplied manifest. */
internal object LinuxMacosRuntimeBundle {
    private const val BASE = "/moe/n4tsu/dextop/linux/macos/"
    const val PREPARE = "command -v python3 >/dev/null 2>&1 || { " +
        "command -v apt-get >/dev/null 2>&1 || { echo 'Python 3 is required for macOS compatibility' >&2; exit 1; }; " +
        "export DEBIAN_FRONTEND=noninteractive; apt-get update && apt-get install -y --no-install-recommends python3; }"
    data class Approval(val sha256: String, val sourceCommit: String) {
        init { require(sha256.matches(Regex("[a-f0-9]{64}")) && sourceCommit.matches(Regex("[a-f0-9]{40}"))) }
    }

    internal fun parseApproval(json: String): Approval {
        val value = JSONObject(json)
        return validatedApproval(value.optString("architecture"), value.optString("sha256"), value.optString("sourceCommit"))
    }

    internal fun validatedApproval(architecture: String, digest: String, source: String): Approval {
        check(architecture == "aarch64") { "Unsupported bundled macOS architecture" }
        return Approval(digest, source)
    }

    fun approval(): Approval? = javaClass.getResourceAsStream(BASE + "runtime-trust.json")?.use {
        parseApproval(it.bufferedReader().readText())
    }

    internal fun copyAndVerify(input: InputStream, target: File, digest: String) {
        val hash = MessageDigest.getInstance("SHA-256")
        // CREATE_NEW refuses existing files and symbolic links.
        Files.newOutputStream(target.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
                output.write(buffer, 0, count)
            }
        }
        check(hash.digest().joinToString("") { "%02x".format(it) } == digest) { "Bundled macOS runtime digest mismatch" }
    }

    fun copyArchive(target: File, approval: Approval) {
        val input = checkNotNull(javaClass.getResourceAsStream(BASE + "runtime.tar.gz")) { "Approved macOS archive is not packaged" }
        input.use { copyAndVerify(it, target, approval.sha256) }
    }

    fun command(approval: Approval): String =
        "python3 /usr/local/lib/dextop/macos/runtime_archive.py install " +
            "--archive /run/dextop-macos-runtime.tar.gz --target /opt/dextop/macos " +
            "--architecture aarch64 --sha256 ${approval.sha256} --source-commit ${approval.sourceCommit}"
}
