package moe.n4tsu.dextop.linux

import org.json.JSONObject
import java.io.File

/** Guest account identity; it does not change the Android process's permissions. */
internal object LinuxUser {
    private val namePattern = Regex("[a-z][a-z0-9_-]{0,30}")
    fun validate(name: String) {
        require(namePattern.matches(name) && name !in setOf("root", "nobody", "daemon", "bin", "sys", "sync", "games", "mail", "news", "www-data", "backup", "messagebus", "sshd", "systemd-network", "systemd-timesync")) {
            moe.n4tsu.dextop.localText("uiUseANonSystemUsernameLowercaseLettersDigitsOrStartingWithALetterUpTo31Characters")
        }
    }

    fun name(root: File): String? {
        val file = File(root, "etc/dextop/username")
        if (!file.isFile) return null
        return file.readText().trim().also(::validate)
    }
    fun home(root: File): String = name(root)?.let { "/home/$it" } ?: "/root"
    fun home(id: String): String = home(LinuxPaths.rootfs(id))

    /** "/root" at the start of a path (not inside one, like Steam's ~/.steam/root, nor /rootfs). */
    private val ROOT_HOME = Regex("(?<![A-Za-z0-9_.~/-])/root(?![A-Za-z0-9_.-])")

    /** [script] with root's home moved to [home], for setup steps that run as the account. */
    fun relocateHome(script: String, home: String): String =
        if (home == "/root") script else script.replace(ROOT_HOME) { home }

    fun credentials(json: JSONObject): Pair<String, String> {
        val name = json.getString("username").also(::validate)
        val password = json.getString("accountPassword")
        require(password.length >= 8 && password.length <= 1024 && password.none { it == '\n' || it == '\r' || it == '\u0000' }) {
            moe.n4tsu.dextop.localText("uiUseAPasswordOf8To1024CharactersWithoutLineBreaks")
        }
        return name to password
    }
}
