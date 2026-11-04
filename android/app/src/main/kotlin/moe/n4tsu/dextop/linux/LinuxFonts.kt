package moe.n4tsu.dextop.linux

import java.io.File
import java.util.Locale

/**
 * Chinese, Japanese and Korean text in Linux applications.
 *
 * Dextop sets GTK to "Noto Sans" and Qt to qt5ct/qt6ct, and neither Noto Sans nor Fusion's
 * default carries CJK glyphs: each character falls back to whichever Noto CJK face fontconfig
 * ranks first, which was Simplified Chinese for Japanese text (Chinese glyph shapes) unless the
 * session's language happened to say otherwise. This names the regional face for the generic
 * families and for Noto Sans/Serif/Mono themselves: the container's own locale decides, else
 * Android's language, else Japanese.
 */
internal object LinuxFonts {
    const val CONFIG = "etc/fonts/conf.d/56-dextop-cjk.conf"

    /** Android's language: the system configuration (also in the shell-side service), else the JVM's. */
    private fun androidLocale(): String = runCatching {
        android.content.res.Resources.getSystem().configuration.locales[0].toLanguageTag()
    }.getOrNull()?.takeIf { it.isNotBlank() && it != "und" } ?: Locale.getDefault().toLanguageTag()

    /** The Noto CJK region (JP, KR, SC, TC, HK) for a locale such as "ja_JP" or "zh-TW". */
    fun region(locale: String?): String? {
        val parts = locale.orEmpty().substringBefore('.').split('_', '-')
        val language = parts.firstOrNull()?.lowercase().orEmpty()
        val country = parts.getOrNull(1)?.uppercase().orEmpty()
        val script = parts.drop(1).firstOrNull { it.length == 4 }?.lowercase()
        return when (language) {
            "ja" -> "JP"
            "ko" -> "KR"
            "zh" -> when {
                country == "HK" || country == "MO" -> "HK"
                country == "TW" || script == "hant" -> "TC"
                else -> "SC"
            }
            else -> null
        }
    }

    /** The region for the container at [root]: its locale, then Android's, then Japanese. */
    fun regionFor(root: File): String {
        val containerLocale = runCatching {
            File(root, "etc/default/locale").readLines().firstOrNull { it.startsWith("LANG=") }?.substringAfter('=')?.trim('"')
        }.getOrNull()
        return region(containerLocale) ?: region(androidLocale()) ?: "JP"
    }

    fun config(region: String): String {
        // accept: right after the named family, so Latin text keeps that family's own glyphs.
        fun family(name: String, cjk: String) = """
            |  <alias binding="same"><family>$name</family><accept><family>$cjk</family></accept></alias>
        """.trimMargin()
        fun generic(name: String, latin: String, cjk: String) = """
            |  <alias><family>$name</family><prefer><family>$latin</family><family>$cjk</family></prefer></alias>
        """.trimMargin()
        val sans = "Noto Sans CJK $region"
        val serif = "Noto Serif CJK $region"
        val mono = "Noto Sans Mono CJK $region"
        return listOf(
            "<?xml version=\"1.0\"?>",
            "<!DOCTYPE fontconfig SYSTEM \"fonts.dtd\">",
            "<!-- Dextop: CJK text in the $region regional forms (LinuxFonts). Rewritten at session start. -->",
            "<fontconfig>",
            generic("sans-serif", "Noto Sans", sans),
            generic("serif", "Noto Serif", serif),
            generic("monospace", "Noto Sans Mono", mono),
            family("Noto Sans", sans),
            family("Noto Serif", serif),
            family("Noto Sans Mono", mono),
            family("Sans Serif", sans),
            family("DejaVu Sans", sans),
            "</fontconfig>",
            "",
        ).joinToString("\n")
    }

    /** Whether [root] has a Noto CJK font (Debian, Ubuntu, Arch and Fedora paths). */
    fun hasCjk(root: File): Boolean = listOf("usr/share/fonts/opentype/noto", "usr/share/fonts/noto-cjk",
        "usr/share/fonts/google-noto-cjk", "usr/share/fonts/opentype/noto-cjk", "usr/share/fonts/noto")
        .any { dir -> File(root, dir).listFiles().orEmpty().any { it.name.startsWith("NotoSansCJK") || it.name.startsWith("NotoSans-CJK") } }

    /** Whether CJK text is expected: the container's or Android's language is Chinese, Japanese or Korean. */
    fun wanted(root: File): Boolean = runCatching {
        File(root, "etc/default/locale").readLines().firstOrNull { it.startsWith("LANG=") }?.substringAfter('=')
    }.getOrNull().let(::region) != null || region(androidLocale()) != null

    /** Installs the Noto CJK fonts (as root) with the container's package manager. */
    const val INSTALL_SCRIPT = """
        if command -v apt-get >/dev/null 2>&1; then DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends fonts-noto-cjk
        elif command -v pacman >/dev/null 2>&1; then pacman -S --noconfirm --needed noto-fonts-cjk
        elif command -v dnf >/dev/null 2>&1; then dnf install -y google-noto-sans-cjk-fonts google-noto-serif-cjk-fonts
        else exit 0; fi
        command -v fc-cache >/dev/null 2>&1 && fc-cache -f >/dev/null 2>&1
        exit 0
    """

    /** Writes the configuration into [root] when fontconfig is there and it changed. */
    fun stage(root: File) {
        if (!File(root, "etc/fonts/conf.d").isDirectory) return
        val text = config(regionFor(root))
        val file = File(root, CONFIG)
        if (file.isFile && file.readText() == text) return
        val staged = File(file.parentFile, ".${file.name}.staged")
        staged.writeText(text)
        staged.setReadable(true, false)
        if (!staged.renameTo(file)) staged.delete()
    }
}
