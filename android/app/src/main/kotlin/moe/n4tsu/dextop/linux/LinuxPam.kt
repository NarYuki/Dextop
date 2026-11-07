package moe.n4tsu.dextop.linux

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * The login keyring as a Linux desktop unlocks it: through PAM. pam_gnome_keyring (Debian's
 * libpam-gnome-keyring, part of Arch's gnome-keyring) takes the account password when the
 * session logs in ([SERVICE], used by dextop-pam-login the way GDM or LightDM use theirs) and
 * unlocks the login keyring with it; in the passwd stack it changes the keyring's password
 * with the account's, and dextop-passwd-sync hands the new one to Dextop
 * ([LinuxContainerService]'s account password socket).
 *
 * Without the module (a container set up before it was installed) nothing here applies and
 * sessions unlock the keyring directly with Dextop's password (LinuxCatalog's KEYRING_UNLOCK).
 */
internal object LinuxPam {
    const val SERVICE = "dextop-session"
    const val LOGIN = "/usr/local/libexec/dextop-pam-login"
    const val PASSWD_SYNC = "/usr/local/libexec/dextop-passwd-sync"
    /** The guest path of the account password socket (abstract, in the container's namespace). */
    const val PASSWORD_SOCKET = "/run/dextop/account-password"
    private const val MARKER = "# Dextop: the login keyring follows the account password"
    private val MODULE_DIRECTORIES = listOf(
        "usr/lib/x86_64-linux-gnu/security", "usr/lib/aarch64-linux-gnu/security", "lib/x86_64-linux-gnu/security",
        "lib/aarch64-linux-gnu/security", "usr/lib/security", "usr/lib64/security", "lib/security")

    fun moduleInstalled(root: File) = MODULE_DIRECTORIES.any { File(root, "$it/pam_gnome_keyring.so").isFile }

    /** A display manager's PAM service, keeping only what applies to a Dextop session. */
    val serviceText = """
        |#%PAM-1.0
        |$MARKER (written by Dextop).
        |# The session logs in like a display manager's: the account password, checked here, unlocks the login keyring.
        |auth     requisite  pam_nologin.so
        |auth     required   pam_unix.so nodelay
        |auth     optional   pam_gnome_keyring.so
        |account  required   pam_unix.so
        |session  optional   pam_gnome_keyring.so auto_start
        |""".trimMargin()

    /**
     * [passwd] (the passwd service) with the keyring's and Dextop's copies following a changed
     * password, after the distribution's own lines; [common] is Debian's common-password.
     */
    fun passwdText(passwd: String, common: String): String {
        val own = passwd.lineSequence().takeWhile { it != MARKER }.joinToString("\n").trimEnd('\n')
        val keyring = (own + "\n" + common).lineSequence()
            .any { line -> line.substringBefore('#').let { it.contains("pam_gnome_keyring.so") && it.trimStart().startsWith("password") } }
        val lines = buildList {
            add(MARKER)
            if (!keyring) add("password  optional  pam_gnome_keyring.so")
            add("password  optional  pam_exec.so expose_authtok quiet type=password /usr/bin/python3 $PASSWD_SYNC")
        }
        return own + "\n\n" + lines.joinToString("\n", postfix = "\n")
    }

    /** Stages the helpers, and the PAM configuration when [root] has pam_gnome_keyring. */
    fun stage(root: File, script: (String) -> ByteArray?) {
        stageFile(root, LOGIN, script("dextop-pam-login"), executable = true)
        stageFile(root, PASSWD_SYNC, script("dextop-passwd-sync"), executable = true)
        val service = File(root, "etc/pam.d/$SERVICE")
        if (!moduleInstalled(root) || !File(root, "etc/pam.d").isDirectory) {
            // Never leave a service behind whose module is gone: logins would fall back silently anyway.
            if (service.isFile && service.readText().contains(MARKER)) service.delete()
            return
        }
        stageFile(root, "/etc/pam.d/$SERVICE", serviceText.toByteArray(), executable = false)
        val passwd = File(root, "etc/pam.d/passwd")
        if (passwd.isFile) {
            val common = File(root, "etc/pam.d/common-password").takeIf { it.isFile }?.readText().orEmpty()
            stageFile(root, "/etc/pam.d/passwd", passwdText(passwd.readText(), common).toByteArray(), executable = false)
        }
    }

    private fun stageFile(root: File, path: String, bytes: ByteArray?, executable: Boolean) {
        bytes ?: return
        val target = File(root, path.removePrefix("/"))
        if (target.isFile && target.readBytes().contentEquals(bytes)) return
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, target.name + ".staged")
        temporary.writeBytes(bytes)
        temporary.setReadable(true, false)
        if (executable) temporary.setExecutable(true, false)
        Files.move(temporary.toPath(), target.toPath(), REPLACE_EXISTING)
    }
}
