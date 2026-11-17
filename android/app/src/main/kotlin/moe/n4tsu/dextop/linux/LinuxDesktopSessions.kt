package moe.n4tsu.dextop.linux

/**
 * What runs on a container's full Linux desktop ("Open desktop"): Dextop's IceWM, or the user's own
 * command on the desktop's X display (any window manager or session they installed). A custom
 * session that ends within seconds falls back to IceWM, with the reason in
 * /tmp/session-<display>.log. The session itself is run by /usr/local/libexec/dextop-desktop-session
 * (resources/…/linux/dextop-desktop-session).
 */
internal enum class LinuxDesktopSessions(
    val id: String,
    /** The program the session runs (none for a custom command). */
    val program: String,
    /** XDG_CURRENT_DESKTOP for applications (autostart, menus, toolkits). */
    val desktopName: String,
    /** It claims the X display as its window manager (WM_S0); Dextop waits for that. */
    val ownsX: Boolean = true,
) {
    ICEWM("icewm", "icewm", "ICEWM"),
    /** Experimental: KDE Plasma 6 on X11 (kwin_x11), installed from the settings with [PLASMA_PACKAGES]. */
    PLASMA("plasma", "startplasma-x11", "KDE"),
    /** It may or may not run an X window manager. */
    CUSTOM("custom", "", "", ownsX = false);

    companion object {
        const val MAX_COMMAND = 2000
        /**
         * Debian's minimal Plasma desktop with the X11 window manager, plus what a desktop is expected
         * to have: a terminal, files, the volume applet, Breeze for GTK applications, KDE's portal
         * (file dialogs), screenshots and archives.
         */
        val PLASMA_PACKAGES = listOf("plasma-desktop", "kwin-x11", "konsole", "dolphin", "plasma-pa",
            "breeze-gtk-theme", "xdg-desktop-portal-kde", "kde-spectacle", "ark")
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: ICEWM

        /** A custom command: one line of shell, no NUL. */
        fun validCommand(command: String) =
            command.length <= MAX_COMMAND && command.none { it == '\u0000' || it == '\n' || it == '\r' }
    }
}

/** The session chosen for a container, with the custom command when it is [LinuxDesktopSessions.CUSTOM]. */
internal data class LinuxDesktopChoice(val session: LinuxDesktopSessions, val command: String = "") {
    /** A custom session without a command is IceWM. */
    val effective get() = if (session == LinuxDesktopSessions.CUSTOM && command.isBlank()) LinuxDesktopSessions.ICEWM else session
}
