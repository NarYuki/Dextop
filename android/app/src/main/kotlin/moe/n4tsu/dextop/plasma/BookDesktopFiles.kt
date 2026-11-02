package moe.n4tsu.dextop.plasma

import java.util.Base64

/** Shell output is framed and names are encoded: newlines in a filename are not records. */
internal object BookDesktopFiles {
    // xdg-open may fall back to a web browser when there is no desktop-session helper.
    // Directly invoke an installed file manager for directories instead.
    val openScript = """if [ -d "${'$'}P" ]; then
        for manager in pcmanfm-qt pcmanfm thunar dolphin nautilus nemo caja; do
            if command -v "${'$'}manager" >/dev/null 2>&1; then exec "${'$'}manager" "${'$'}P"; fi
        done
        printf '%s\n' 'No Linux file manager is installed' >&2; exit 1
        fi
        case "${'$'}P" in *.desktop) exec gio launch "${'$'}P";; *) exec xdg-open "${'$'}P";; esac
    """.trimIndent()

    val directoryScript = """D="${'$'}(xdg-user-dir DESKTOP 2>/dev/null)"; [ -n "${'$'}D" ] || D="${'$'}{HOME:-/root}/Desktop"
        case "${'$'}D" in /*) ;; *) exit 1;; esac
        mkdir -p "${'$'}D" || exit 1
        n() { printf '%s' "${'$'}1" | base64 -d; }
    """.trimIndent()

    val listingScript = """cd "${'$'}D" || exit 1
        for f in *; do
            [ -e "${'$'}f" ] || [ -L "${'$'}f" ] || continue
            if [ -d "${'$'}f" ]; then printf 'd/'; else printf 'f/'; fi
            printf '%s' "${'$'}f" | base64 -w0; printf '\n'
        done
        printf 'DEXTOP_DESKTOP_END\n'
    """.trimIndent()

    fun parse(output: String): List<Pair<String, Boolean>>? {
        val lines = output.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.lastOrNull() != "DEXTOP_DESKTOP_END") return null
        return runCatching {
            lines.dropLast(1).filter { it.startsWith("d/") || it.startsWith("f/") }.map {
                val name = String(Base64.getDecoder().decode(it.substring(2)), Charsets.UTF_8)
                require(validName(name))
                name to it.startsWith("d/")
            }.distinctBy { it.first }.sortedBy { it.first.lowercase() }
        }.getOrNull()
    }

    fun encode(files: List<Pair<String, Boolean>>): String = files.joinToString("") { (name, directory) ->
        require(validName(name))
        "${if (directory) 'd' else 'f'}/${Base64.getEncoder().encodeToString(name.toByteArray(Charsets.UTF_8))}\n"
    } + "DEXTOP_DESKTOP_END\n"

    fun validName(name: String) = name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\u0000' !in name
}
