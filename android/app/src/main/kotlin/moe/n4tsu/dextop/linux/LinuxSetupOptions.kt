package moe.n4tsu.dextop.linux

import org.json.JSONArray
import org.json.JSONObject

/** Persisted, validated choices shared by installation, repair and later setup. */
internal data class LinuxSetupOptions(
    val profile: String = "minimal",
    val locale: String = "en_US",
    val cjkFonts: Boolean = true,
    val ime: String = "none",
    val graphics: Boolean = false,
    val themes: Boolean = false,
    val apps: Set<String> = emptySet(),
) {
    init {
        require(profile in setOf("minimal", "complete")) { "Unknown desktop profile" }
        require(locale in LOCALES) { "Unsupported locale" }
        require(ime in setOf("none", "mozc", "pinyin", "hangul")) { "Unsupported input method" }
        require(apps.all { it in APPS }) { "Unknown application" }
    }

    fun validate(distro: LinuxDistro): LinuxSetupOptions = apply {
        require(distro.id != "ubuntu" || apps.none { it == "firefox" || it == "chromium" }) {
            "Firefox and Chromium require Snap on this Ubuntu image; choose Debian, Arch or Google Chrome instead"
        }
        require(distro.family == LinuxPackageFamily.APT || "chrome" !in apps) {
            "Google Chrome installation is available for Debian and Ubuntu"
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("profile", profile).put("locale", locale).put("cjkFonts", cjkFonts)
        .put("ime", ime).put("graphics", graphics).put("themes", themes)
        .put("apps", JSONArray(apps.sorted()))

    companion object {
        val LOCALES = setOf("en_US", "ja_JP", "zh_CN", "zh_TW", "ko_KR", "ru_RU", "pt_BR", "de_DE", "fr_FR", "es_ES")
        val APPS = setOf("firefox", "chromium", "chrome", "files", "editor", "office")

        fun fromJson(json: JSONObject?): LinuxSetupOptions {
            if (json == null) return LinuxSetupOptions()
            val apps = json.optJSONArray("apps") ?: JSONArray()
            return LinuxSetupOptions(
                json.optString("profile", "minimal"), json.optString("locale", "en_US"),
                json.optBoolean("cjkFonts", true), json.optString("ime", "none"),
                json.optBoolean("graphics", false), json.optBoolean("themes", false),
                (0 until apps.length()).map { apps.getString(it) }.toSet(),
            )
        }
    }
}

/** Scripts only interpolate allowlisted choices, never user-provided shell fragments. */
internal object LinuxSetupPlan {
    data class Step(val phase: String, val script: String)

    fun steps(distro: LinuxDistro, options: LinuxSetupOptions): List<Step> {
        options.validate(distro)
        val apt = distro.family == LinuxPackageFamily.APT
        fun install(packages: List<String>): String = if (packages.isEmpty()) "" else if (apt)
            "export DEBIAN_FRONTEND=noninteractive\napt-get install -y --no-install-recommends ${packages.joinToString(" ")}\n"
        else "pacman -S --noconfirm --needed ${packages.joinToString(" ")}\n"
        return buildList {
            val fonts = if (apt) mutableListOf("locales", "fontconfig", "fonts-noto-core", "fonts-noto-color-emoji")
                else mutableListOf("fontconfig", "noto-fonts", "noto-fonts-emoji")
            if (options.cjkFonts || options.ime != "none") fonts += if (apt) "fonts-noto-cjk" else "noto-fonts-cjk"
            add(Step("language", "set -e\n" + install(fonts) + """
                mkdir -p /etc/dextop /etc/default
                touch /etc/locale.gen
                for lang in en_US ${options.locale}; do
                    grep -qxF "${'$'}lang.UTF-8 UTF-8" /etc/locale.gen || printf '%s.UTF-8 UTF-8\n' "${'$'}lang" >>/etc/locale.gen
                done
                locale-gen
                printf 'LANG=${options.locale}.UTF-8\n' >/etc/default/locale
                printf 'LANG=${options.locale}.UTF-8\n' >/etc/locale.conf
                fc-cache -f
            """.trimIndent() + "\n"))

            val input = if (options.ime == "none") emptyList() else if (apt) listOf(
                "fcitx5", "fcitx5-config-qt", "fcitx5-frontend-gtk3", "fcitx5-frontend-qt5",
                when (options.ime) { "mozc" -> "fcitx5-mozc"; "pinyin" -> "fcitx5-chinese-addons"; else -> "fcitx5-hangul" },
            ) else listOf("fcitx5", "fcitx5-configtool", "fcitx5-gtk", "fcitx5-qt",
                when (options.ime) { "mozc" -> "fcitx5-mozc"; "pinyin" -> "fcitx5-chinese-addons"; else -> "fcitx5-hangul" })
            if (input.isNotEmpty()) add(Step("input", "set -e\n" + install(input)))

            if (options.themes) add(Step("desktop", "set -e\n" + install(if (apt)
                listOf("icewm-common", "adwaita-icon-theme", "gnome-themes-extra", "gtk2-engines-pixbuf")
                else listOf("adwaita-icon-theme", "gnome-themes-extra")) + """
                mkdir -p /root/.icewm /root/.config/gtk-3.0
                # Keep the user's theme once chosen; the setup only supplies an initial theme.
                if [ ! -f /root/.config/gtk-3.0/settings.ini ]; then
                    printf '[Settings]\ngtk-theme-name=Adwaita\ngtk-icon-theme-name=Adwaita\ngtk-font-name=Noto Sans 10\n' >/root/.config/gtk-3.0/settings.ini
                fi
                if [ ! -f /root/.icewm/theme ]; then
                    for theme in /usr/share/icewm/themes/*/default.theme; do
                        [ -f "${'$'}theme" ] || continue
                        printf 'Theme="%s/default.theme"\n' "${'$'}(basename "${'$'}(dirname "${'$'}theme")")" >/root/.icewm/theme
                        break
                    done
                fi
            """.trimIndent() + "\n"))

            if (options.graphics) add(Step("graphics", "set -e\n" + install(if (apt)
                listOf("libgl1-mesa-dri", "libegl-mesa0", "libglx-mesa0", "mesa-utils", "mesa-vulkan-drivers", "vulkan-tools")
                else listOf("mesa", "mesa-utils"))))
            // Turnip for the device's Adreno GPU through Android's KGSL driver; OpenGL runs on it through Zink.
            if (options.graphics && apt) add(Step("gpu", LinuxCatalog.turnipScript()))

            val packages = buildList {
                if ("firefox" in options.apps) add(if (apt) "firefox-esr" else "firefox")
                if ("chromium" in options.apps) add("chromium")
                if ("files" in options.apps) add("pcmanfm")
                if ("editor" in options.apps) add("mousepad")
                if ("office" in options.apps) add(if (apt) "libreoffice" else "libreoffice-fresh")
                if ("chrome" in options.apps) add("curl")
                if (apt && "firefox" in options.apps && options.locale != "en_US") add("firefox-esr-l10n-" + when (options.locale) {
                    "pt_BR" -> "pt-br"; "zh_CN" -> "zh-cn"; "zh_TW" -> "zh-tw"; "es_ES" -> "es-es"; else -> options.locale.substringBefore('_')
                })
            }
            if (packages.isNotEmpty()) add(Step("applications", "set -e\n" + install(packages) + if ("chrome" in options.apps) """
                package=/tmp/dextop-google-chrome-arm64.deb
                trap 'rm -f "${'$'}package"' EXIT
                curl --fail --location --retry 3 --proto '=https' -o "${'$'}package" https://dl.google.com/linux/direct/google-chrome-stable_current_arm64.deb
                apt-get install -y --no-install-recommends "${'$'}package"
            """.trimIndent() + "\n" else ""))

            add(Step("integration", integration(options, apt)))
        }
    }

    private fun integration(options: LinuxSetupOptions, apt: Boolean): String {
        val environment = buildString {
            appendLine("export LANG=${options.locale}.UTF-8")
            appendLine("export LANGUAGE=${options.locale}:${options.locale.substringBefore('_')}:en")
            // The renderer (VirGL or llvmpipe) follows the Linux settings; see LinuxGraphics.
            if (options.ime != "none") {
                appendLine("export GTK_IM_MODULE=fcitx QT_IM_MODULE=fcitx XMODIFIERS=@im=fcitx")
            }
        }
        val files = buildString {
            appendLine("set -e\nmkdir -p /etc/dextop /etc/default /usr/local/bin /root/.config/fcitx5 /root/.local/share/applications")
            appendLine("cat >/etc/dextop/session.env <<'DEXTOP_ENV'")
            append(environment)
            appendLine("DEXTOP_ENV")
            appendLine("cat >/etc/dextop/start-session.sh <<'DEXTOP_START'")
            appendLine(if (options.ime != "none") "fcitx5 -d >/tmp/dextop-fcitx-\${DISPLAY##*:}.log 2>&1" else ":")
            appendLine("DEXTOP_START")
        }
        val menu = if (options.themes) {
            val title = when (options.locale) {
                "ja_JP" -> "アプリケーション"; "zh_CN" -> "应用程序"; "zh_TW" -> "應用程式"; "ko_KR" -> "프로그램"
                "ru_RU" -> "Приложения"; "pt_BR" -> "Aplicativos"; "de_DE" -> "Anwendungen"; "fr_FR" -> "Applications"
                "es_ES" -> "Aplicaciones"; else -> "Applications"
            }
            """
                mkdir -p /root/.icewm
                if [ ! -f /root/.icewm/menu ]; then
                    cat >/root/.icewm/menu <<'DEXTOP_MENU'
                menuprog "$title" folder icewm-menu-fdo
                prog "Terminal" utilities-terminal xterm
                DEXTOP_MENU
                fi
            """.trimIndent() + "\n"
        } else ""
        return files + menu + (if (options.ime != "none") """
            if [ -f /root/.config/fcitx5/profile ] && [ ! -f /root/.config/fcitx5/profile.before-dextop ]; then
                cp /root/.config/fcitx5/profile /root/.config/fcitx5/profile.before-dextop
            fi
                cat >/root/.config/fcitx5/profile <<'DEXTOP_IME'
            [Groups/0]
            Name=Default
            Default Layout=us
            DefaultIM=${options.ime}
            [Groups/0/Items/0]
            Name=keyboard-us
            Layout=
            [Groups/0/Items/1]
            Name=${options.ime}
            Layout=
            [GroupOrder]
            0=Default
            DEXTOP_IME
        """.trimIndent() + "\n" else "") + browserLaunchers(options, apt)
    }

    private fun browserLaunchers(options: LinuxSetupOptions, apt: Boolean): String = buildString {
        for (browser in listOf("chromium", "chrome", "firefox").filter { it in options.apps }) {
            val command = when (browser) { "chrome" -> "google-chrome-stable"; "firefox" -> "firefox"; else -> "chromium" }
            val name = when (browser) { "chrome" -> "Google Chrome"; "firefox" -> "Firefox"; else -> "Chromium" }
            val icon = when (browser) { "chrome" -> "google-chrome"; "firefox" -> if (apt) "firefox-esr" else "firefox"; else -> "chromium" }
            val launcher = "/usr/local/bin/dextop-$browser"
            appendLine("cat >$launcher <<'DEXTOP_BROWSER'")
            appendLine("#!/bin/sh")
            if (browser == "firefox") appendLine(LinuxCatalog.FIREFOX_SANDBOX_ENVIRONMENT)
            else appendLine(LinuxCatalog.CHROMIUM_VIRGL_FLAGS)
            appendLine("exec $command ${if (browser != "firefox") "--no-sandbox --disable-dev-shm-usage " else ""}\"\$@\"")
            appendLine("DEXTOP_BROWSER\nchmod 755 $launcher")
            // XDG user override shared by IceWM and Plasma, keeping the package's desktop id.
            val desktop = when (browser) { "chrome" -> "google-chrome.desktop"; "firefox" -> if (apt) "firefox-esr.desktop" else "firefox.desktop"; else -> "chromium.desktop" }
            appendLine("cat >/root/.local/share/applications/$desktop <<'DEXTOP_ENTRY'")
            appendLine("[Desktop Entry]\nType=Application\nName=$name\nExec=$launcher %U\nIcon=$icon\nCategories=Network;WebBrowser;\nStartupWMClass=${if (browser == "chrome") "Google-chrome" else browser}")
            appendLine("DEXTOP_ENTRY")
        }
    }
}
