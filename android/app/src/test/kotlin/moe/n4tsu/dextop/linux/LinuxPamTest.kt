package moe.n4tsu.dextop.linux

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxPamTest {
    private val debianPasswd = "#\n# The PAM configuration file for the Shadow `passwd' service\n#\n\n@include common-password\n\n"
    private val debianCommon = "password\t[success=1 default=ignore]\tpam_unix.so obscure yescrypt\n" +
        "password\trequisite\t\t\tpam_deny.so\npassword\trequired\t\t\tpam_permit.so\n"

    @Test fun passwdFollowsTheDistributionStackOnce() {
        val once = LinuxPam.passwdText(debianPasswd, debianCommon)
        assertTrue(once.startsWith("#\n# The PAM configuration file"))
        assertContains(once, "@include common-password\n")
        assertContains(once, "password  optional  pam_gnome_keyring.so\n")
        assertContains(once, "pam_exec.so expose_authtok quiet type=password /usr/bin/python3 ${LinuxPam.PASSWD_SYNC}\n")
        assertEquals(once, LinuxPam.passwdText(once, debianCommon))
    }

    @Test fun keyringLineIsNotDoubledWhenTheDistributionHasIt() {
        val text = LinuxPam.passwdText(debianPasswd, debianCommon + "password\toptional\tpam_gnome_keyring.so\n")
        assertFalse(text.contains("password  optional  pam_gnome_keyring.so"))
        assertContains(text, "pam_exec.so")
    }

    @Test fun serviceLogsInLikeADisplayManager() {
        val lines = LinuxPam.serviceText.lines().filterNot { it.startsWith("#") || it.isBlank() }
        assertEquals(listOf("auth", "auth", "auth", "account", "session"), lines.map { it.substringBefore(' ') })
        assertContains(LinuxPam.serviceText, "auth     optional   pam_gnome_keyring.so\n")
        assertContains(LinuxPam.serviceText, "session  optional   pam_gnome_keyring.so auto_start\n")
    }

    @Test fun stagesPamOnlyWithTheModule() {
        val root = Files.createTempDirectory("pam").toFile()
        try {
            File(root, "etc/pam.d").mkdirs()
            File(root, "etc/pam.d/passwd").writeText(debianPasswd)
            val script: (String) -> ByteArray? = { "#!/usr/bin/python3\n".toByteArray() }
            LinuxPam.stage(root, script)
            assertFalse(File(root, "etc/pam.d/${LinuxPam.SERVICE}").exists())
            assertEquals(debianPasswd, File(root, "etc/pam.d/passwd").readText())
            assertTrue(File(root, LinuxPam.LOGIN.removePrefix("/")).isFile)
            File(root, "usr/lib/aarch64-linux-gnu/security").mkdirs()
            File(root, "usr/lib/aarch64-linux-gnu/security/pam_gnome_keyring.so").writeText("")
            LinuxPam.stage(root, script)
            assertEquals(LinuxPam.serviceText, File(root, "etc/pam.d/${LinuxPam.SERVICE}").readText())
            assertContains(File(root, "etc/pam.d/passwd").readText(), LinuxPam.PASSWD_SYNC)
        } finally {
            root.deleteRecursively()
        }
    }
}
