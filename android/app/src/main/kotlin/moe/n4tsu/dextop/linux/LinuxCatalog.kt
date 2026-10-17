package moe.n4tsu.dextop.linux

import org.json.JSONArray
import org.json.JSONObject

/** Package manager family; decides the configuration and provisioning scripts. */
internal enum class LinuxPackageFamily { APT, PACMAN }

/**
 * An installable distribution. Root filesystems come from the official
 * linuxcontainers.org image server (images.linuxcontainers.org), which
 * publishes daily arm64 builds with a SHA256SUMS file per build.
 */
internal data class LinuxDistro(
    val id: String,
    val displayName: String,
    val imageDistro: String,
    val imageRelease: String,
    val family: LinuxPackageFamily,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", displayName)
        .put("family", family.name.lowercase())
}

internal object LinuxCatalog {
    const val IMAGE_SERVER = "https://images.linuxcontainers.org"
    const val IMAGE_INDEX = "$IMAGE_SERVER/meta/1.0/index-system"
    const val ARCHITECTURE = "arm64"
    const val VARIANT = "default"

    val distros = listOf(
        LinuxDistro("debian", "Debian 13 (trixie)", "debian", "trixie", LinuxPackageFamily.APT),
        LinuxDistro("ubuntu", "Ubuntu 24.04 LTS (noble)", "ubuntu", "noble", LinuxPackageFamily.APT),
        LinuxDistro("archlinux", "Arch Linux ARM", "archlinux", "current", LinuxPackageFamily.PACMAN),
    )

    fun find(id: String): LinuxDistro? = distros.firstOrNull { it.id == id }

    fun toJson(): String = JSONArray().apply { distros.forEach { put(it.toJson()) } }.toString()

    /**
     * Run once after extraction, as the container's root. Android has no
     * /etc/resolv.conf to inherit, and proot cannot switch users, so APT's
     * download sandbox and pacman's Landlock sandbox are disabled.
     */
    fun configureScript(family: LinuxPackageFamily): String {
        val common = """
            set -e
            rm -f /etc/resolv.conf
            printf 'nameserver 8.8.8.8\nnameserver 1.1.1.1\n' > /etc/resolv.conf
            printf '127.0.0.1 localhost\n::1 localhost ip6-localhost\n' > /etc/hosts
            mkdir -p /tmp /root && chmod 1777 /tmp
        """.trimIndent()
        val specific = when (family) {
            LinuxPackageFamily.APT -> """
                mkdir -p /etc/apt/apt.conf.d
                echo 'APT::Sandbox::User "root";' > /etc/apt/apt.conf.d/01-dextop-sandbox
            """.trimIndent()
            LinuxPackageFamily.PACMAN -> """
                grep -q '^DisableSandbox' /etc/pacman.conf || sed -i 's/^\[options\]/[options]\nDisableSandbox/' /etc/pacman.conf
                pacman-key --init
                pacman-key --populate
            """.trimIndent()
        }
        return common + "\n" + specific + "\n"
    }

    /** Refreshes the package index; used to verify that networking and the package manager work. */
    fun refreshScript(family: LinuxPackageFamily): String = when (family) {
        LinuxPackageFamily.APT -> "set -e\nDEBIAN_FRONTEND=noninteractive apt-get update\n"
        LinuxPackageFamily.PACMAN -> "set -e\npacman -Sy --noconfirm\n"
    }

    /**
     * Installs the X stack Dextop drives: Xvfb (the display, with XTEST and
     * DAMAGE), IceWM for desktop mode, xterm as a first app, CJK fonts and
     * D-Bus for desktop applications.
     */
    fun provisionScript(family: LinuxPackageFamily): String = when (family) {
        LinuxPackageFamily.APT -> """
            set -e
            export DEBIAN_FRONTEND=noninteractive
            # Finish package configuration after a cancelled/interrupted setup.
            if [ -n "${'$'}(dpkg --audit)" ]; then
                dpkg --configure -a || apt-get install -f -y
            fi
            apt-get install -y --no-install-recommends \
                xvfb x11-xserver-utils xauth icewm xterm dbus-x11 \
                fonts-dejavu-core xdg-utils ca-certificates
        """.trimIndent() + "\n"
        LinuxPackageFamily.PACMAN -> """
            set -e
            pacman -S --noconfirm --needed \
                xorg-server-xvfb xorg-xrdb xorg-xauth icewm xterm dbus \
                ttf-dejavu xdg-utils ca-certificates
        """.trimIndent() + "\n"
    }

    /**
     * Starts Xvfb on display [display] with its framebuffer in
     * /tmp/dextop-fb-<display> (mmapped by the service), then the session
     * ([session] is "icewm" for desktop mode). Runs until the session ends.
     */
    fun desktopScript(display: Int, width: Int, height: Int, dpi: Int, session: String): String = """
        D=$display
        rm -f /tmp/.X${'$'}D-lock /tmp/.X11-unix/X${'$'}D
        mkdir -p /tmp/.X11-unix /tmp/dextop-fb-${'$'}D
        chmod 1777 /tmp/.X11-unix
        Xvfb :${'$'}D -screen 0 ${width}x${height}x24 -dpi ${xDpi(dpi)} -fbdir /tmp/dextop-fb-${'$'}D \
            -nolisten tcp -ac +extension XTEST +extension DAMAGE >/tmp/xvfb-${'$'}D.log 2>&1 &
        XPID=${'$'}!
        trap 'kill ${'$'}XPID ${'$'}DBPID 2>/dev/null' EXIT HUP INT TERM
        i=0
        while [ ! -e /tmp/.X11-unix/X${'$'}D ] && [ ${'$'}i -lt 100 ]; do sleep 0.1; i=${'$'}((i + 1)); done
        kill -0 ${'$'}XPID 2>/dev/null || { cat /tmp/xvfb-${'$'}D.log >&2; exit 1; }
        export DISPLAY=:${'$'}D XDG_RUNTIME_DIR=/tmp/runtime-root
        mkdir -p ${'$'}XDG_RUNTIME_DIR && chmod 700 ${'$'}XDG_RUNTIME_DIR
        ${sessionSetup(xDpi(dpi))}
        echo dextop-x-ready
        case "$session" in
            icewm) icewm-session >/tmp/session-${'$'}D.log 2>&1 || icewm >>/tmp/session-${'$'}D.log 2>&1 ;;
            *) wait ${'$'}XPID ;;
        esac
        kill ${'$'}XPID 2>/dev/null
    """.trimIndent() + "\n"

    /**
     * Starts Xvfb for application windows: no window manager (Dextop is
     * the window manager, from outside), Composite for offscreen windows,
     * and the session bus. Runs until Xvfb exits.
     */
    fun appsScript(display: Int, width: Int, height: Int, dpi: Int): String = """
        D=$display
        rm -f /tmp/.X${'$'}D-lock /tmp/.X11-unix/X${'$'}D
        mkdir -p /tmp/.X11-unix && chmod 1777 /tmp/.X11-unix
        Xvfb :${'$'}D -screen 0 ${width}x${height}x24 -dpi ${xDpi(dpi)} \
            -nolisten tcp -ac +extension XTEST +extension DAMAGE +extension Composite >/tmp/xvfb-${'$'}D.log 2>&1 &
        XPID=${'$'}!
        trap 'kill ${'$'}XPID ${'$'}DBPID 2>/dev/null' EXIT HUP INT TERM
        i=0
        while [ ! -e /tmp/.X11-unix/X${'$'}D ] && [ ${'$'}i -lt 100 ]; do sleep 0.1; i=${'$'}((i + 1)); done
        kill -0 ${'$'}XPID 2>/dev/null || { cat /tmp/xvfb-${'$'}D.log >&2; exit 1; }
        export DISPLAY=:${'$'}D XDG_RUNTIME_DIR=/tmp/runtime-root
        mkdir -p ${'$'}XDG_RUNTIME_DIR && chmod 700 ${'$'}XDG_RUNTIME_DIR
        ${sessionSetup(xDpi(dpi))}
        echo dextop-x-ready
        wait ${'$'}XPID
    """.trimIndent() + "\n"

    /** Runs [command] (a desktop entry's Exec line) as an application of display [display]. */
    fun launchScript(display: Int, command: String): String = """
        export DISPLAY=:$display XDG_RUNTIME_DIR=/tmp/runtime-root
        export DBUS_SESSION_BUS_ADDRESS=unix:abstract=/tmp/dextop-dbus-$display
        [ ! -f /etc/dextop/session.env ] || . /etc/dextop/session.env
        $RENDERER_OVERRIDE
        cd /root
        exec sh -c ${shellQuote(command)} >>/tmp/apps-$display.log 2>&1
    """.trimIndent() + "\n"

    /**
     * The session bus, on an abstract socket because the shell user cannot
     * bind filesystem unix sockets here (X does the same), and the font DPI.
     * Expects D to be the display number.
     */
    private fun sessionSetup(dpi: Int): String = """
        [ ! -f /etc/dextop/session.env ] || . /etc/dextop/session.env
        $RENDERER_OVERRIDE
        $BROWSER_LAUNCHER_MIGRATION
        if command -v dbus-daemon >/dev/null; then
            # Images ship "uninitialized" (13 characters), which D-Bus and Chrome reject.
            grep -qE '^[0-9a-f]{32}${'$'}' /etc/machine-id 2>/dev/null || dbus-uuidgen >/etc/machine-id 2>/dev/null
            dbus-uuidgen --ensure=/etc/machine-id 2>/dev/null
            mkdir -p /var/lib/dbus && cp -f /etc/machine-id /var/lib/dbus/machine-id 2>/dev/null
            export DBUS_SESSION_BUS_ADDRESS=unix:abstract=/tmp/dextop-dbus-${'$'}D
            dbus-daemon --session --address=${'$'}DBUS_SESSION_BUS_ADDRESS --fork --print-pid >/tmp/dbus-${'$'}D.log 2>&1
            DBPID=${'$'}(head -1 /tmp/dbus-${'$'}D.log)
        fi
        # xterm's default bitmap font is not installed; use the Xft fonts (CJK included).
        command -v xrdb >/dev/null && printf '%s\n' 'Xft.dpi: $dpi' \
            'XTerm*faceName: DejaVu Sans Mono' 'XTerm*faceNameDoublesize: Noto Sans Mono CJK JP' \
            'XTerm*faceSize: 11' 'XTerm*locale: true' | xrdb -merge
        [ ! -f /etc/dextop/start-session.sh ] || sh /etc/dextop/start-session.sh
    """.trimIndent()

    /**
     * X DPI matching Android's scale: Android lays out 1 dp per px at 160
     * dpi, X toolkits 1 pt per px at 96 dpi.
     */
    private fun xDpi(androidDpi: Int) = (androidDpi * 96 / 160).coerceAtLeast(96)

    /**
     * Containers set up before the renderer setting existed export
     * LIBGL_ALWAYS_SOFTWARE in session.env, which would hide VirGL.
     */
    private val RENDERER_OVERRIDE = "[ \"${'$'}GALLIUM_DRIVER\" != virpipe ] || unset LIBGL_ALWAYS_SOFTWARE"

    /**
     * Chromium-based browsers draw no text when they rasterize on VirGL
     * (glyph atlases); GPU compositing and WebGL keep using it.
     */
    val CHROMIUM_VIRGL_FLAGS = "[ \"${'$'}GALLIUM_DRIVER\" != virpipe ] || set -- --disable-gpu-rasterization \"${'$'}@\""

    /** Adds [CHROMIUM_VIRGL_FLAGS] to browser launchers written before it existed. */
    private val BROWSER_LAUNCHER_MIGRATION = """
        for launcher in /usr/local/bin/dextop-chrome /usr/local/bin/dextop-chromium; do
            [ -f "${'$'}launcher" ] && ! grep -q disable-gpu-rasterization "${'$'}launcher" &&
                sed -i '/^exec /i [ "${'$'}GALLIUM_DRIVER" != virpipe ] || set -- --disable-gpu-rasterization "${'$'}@"' "${'$'}launcher"
        done
    """.trimIndent()

    private fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
