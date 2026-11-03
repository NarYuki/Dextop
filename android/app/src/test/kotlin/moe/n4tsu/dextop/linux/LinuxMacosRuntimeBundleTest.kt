package moe.n4tsu.dextop.linux

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.*

class LinuxMacosRuntimeBundleTest {
    @Test fun approvalsCannotInjectInstallerArguments() {
        val digest = "ab".repeat(32)
        val source = "cd".repeat(20)
        val approval = LinuxMacosRuntimeBundle.validatedApproval("aarch64", digest, source)
        assertContains(LinuxMacosRuntimeBundle.command(approval), "--sha256 $digest --source-commit $source")
        assertFailsWith<IllegalArgumentException> { LinuxMacosRuntimeBundle.Approval("$digest;true", source) }
        assertFailsWith<IllegalStateException> {
            LinuxMacosRuntimeBundle.validatedApproval("x86_64", digest, source)
        }
    }

    @Test fun bundledCopyChecksDigestAndNeverOverwritesAnExistingFile() {
        val root = Files.createTempDirectory("macos-bundle-copy").toFile()
        try {
            val bytes = "owned runtime fixture".toByteArray()
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val target = java.io.File(root, "runtime.tar.gz")
            LinuxMacosRuntimeBundle.copyAndVerify(ByteArrayInputStream(bytes), target, digest)
            assertContentEquals(bytes, target.readBytes())
            assertFailsWith<java.nio.file.FileAlreadyExistsException> {
                LinuxMacosRuntimeBundle.copyAndVerify(ByteArrayInputStream(byteArrayOf(0)), target, digest)
            }
            assertContentEquals(bytes, target.readBytes())
            assertFailsWith<IllegalStateException> {
                LinuxMacosRuntimeBundle.copyAndVerify(ByteArrayInputStream(bytes), java.io.File(root, "bad"), "0".repeat(64))
            }
        } finally { root.deleteRecursively() }
    }
}
