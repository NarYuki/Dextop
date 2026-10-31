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
    /** A prebuilt root filesystem used instead of the image server's. */
    val image: LinuxImage? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", displayName)
        .put("family", family.name.lowercase())
}

/** A root filesystem tarball (.tar.xz or .tar.zst) pinned by SHA-256. */
internal data class LinuxImage(val url: String, val sha256: String, val size: Long) {
    /** The GitHub release tag the image was published under (linuxfs-r9, desktop-kde-r2). */
    fun release(): String = url.substringBeforeLast('/').substringAfterLast('/')
}

internal object LinuxCatalog {
    const val SYSTEM_BUS_ADDRESS = "unix:abstract=/tmp/dextop-dbus-system"
    /**
     * Shell function: whether the Dextop bus named $1 (a display number or "system") runs.
     * Android denies /proc/net/unix to app UIDs (the Android execution mode); there a
     * connection attempt answers instead of waiting out the whole timeout.
     */
    val BUS_PROBE = "dextop_bus() { if cat /proc/net/unix >/dev/null 2>&1; then " +
        "grep -q \"@/tmp/dextop-dbus-${'$'}1${'$'}\" /proc/net/unix; else " +
        "dbus-send --bus=unix:abstract=/tmp/dextop-dbus-${'$'}1 --dest=org.freedesktop.DBus " +
        "/org/freedesktop/DBus org.freedesktop.DBus.Peer.Ping >/dev/null 2>&1; fi; }"
    /** Xvfb's default mode has no clock. Wine caches that as 0 Hz, which
     * leaves clients polling raster status before their first visible frame.
     * Publish timing before any desktop or application initializes Wine. */
    internal fun virtualDisplayTiming(width: Int, height: Int, refreshRate: Float = 60f): String =
        virtualDisplayMode(width, height, refreshRate, select = true)

    private fun virtualDisplayMode(width: Int, height: Int, refreshRate: Float, select: Boolean): String {
        require(width in 1..16384 && height in 1..16384)
        val rate = LinuxDisplayConfiguration.create(width, height, 160, refreshRate).refreshMilliHz
        val horizontal = width + 160
        val vertical = height + 35
        val clock = String.format(java.util.Locale.ROOT, "%.6f", horizontal.toLong() * vertical * rate / 1_000_000_000.0)
        val label = if (rate % 1000 == 0) "${rate / 1000}" else "${rate / 1000}_${rate % 1000}"
        val mode = "dextop_${width}x${height}_$label"
        return """
            command -v xrandr >/dev/null 2>&1 || { echo 'Xvfb timing setup requires xrandr' >&2; exit 1; }
            xrandr --newmode $mode $clock $width ${width + 48} ${width + 128} $horizontal $height ${height + 3} ${height + 8} $vertical +hsync -vsync 2>/dev/null || true
            xrandr --addmode screen $mode 2>/dev/null || true
            ${if (select) "xrandr --output screen --mode $mode || { echo 'Cannot configure Xvfb display timing' >&2; exit 1; }" else ":"}
        """.trimIndent()
    }

    internal fun applicationDisplayTiming(width: Int, height: Int, refreshRate: Float = 60f): String {
        val modes = listOf(640 to 480, 800 to 600, 1024 to 768, 1280 to 720,
            1600 to 900, 1920 to 1080, height to width).distinct().filterNot { it == width to height } + (width to height)
        return modes.joinToString("\n") { (w, h) -> virtualDisplayMode(w, h, refreshRate, select = w == width && h == height) }
    }

    internal fun updateDisplayScript(display: Int, config: LinuxDisplayConfiguration, apps: Boolean) = """
        set -e
        export DISPLAY=:$display
        ${if (apps) applicationDisplayTiming(config.width, config.height, config.refreshRate) else virtualDisplayTiming(config.width, config.height, config.refreshRate)}
        xrandr --dpi ${xDpi(config.dpi)}
        if command -v xrdb >/dev/null 2>&1; then printf '%s\n' 'Xft.dpi: ${xDpi(config.dpi)}' | xrdb -nocpp -merge; fi
    """.trimIndent() + "\n"

    const val IMAGE_SERVER = "https://images.linuxcontainers.org"
    const val IMAGE_INDEX = "$IMAGE_SERVER/meta/1.0/index-system"
    const val ARCHITECTURE = "arm64"
    const val VARIANT = "default"

    val distros = listOf(
        LinuxDistro("debian", "Debian 13 (trixie)", "debian", "trixie", LinuxPackageFamily.APT),
        LinuxDistro("ubuntu", "Ubuntu 24.04 LTS (noble)", "ubuntu", "noble", LinuxPackageFamily.APT),
        LinuxDistro("archlinux", "Arch Linux ARM", "archlinux", "current", LinuxPackageFamily.PACMAN),
        // DroidDeck's Linux runtime (GPL-3.0, Droid-Deck/DroidDeck tools/linuxfs): Arch Linux ARM
        // with gamescope, Xwayland, Mesa 26.2 (Turnip for KGSL, Zink) and its Steam session
        // scripts. It contains no Valve software; Steam is fetched from Valve at first use.
        LinuxDistro(
            DROIDDECK_RUNTIME, "DroidDeck runtime (Arch Linux ARM, Steam)", "archlinux", "current",
            LinuxPackageFamily.PACMAN,
            LinuxImage(
                "https://github.com/Droid-Deck/DroidDeck-Components/releases/download/linuxfs-r9/linuxfs.tar.zst",
                "d3240c1531531615b0dd06b6ad56e92db9015a8641eb18c3a613835ae45992e7",
                790_907_606,
            ),
        ),
    )

    const val DROIDDECK_RUNTIME = "droiddeck"

    /**
     * DroidDeck's desktop package (tools/desktop-kde, release.env): KDE Plasma
     * 6 as SteamOS's desktop mode, with KWin nested in a host compositor.
     * Extracted over the DroidDeck runtime.
     */
    val DROIDDECK_DESKTOP = LinuxImage(
        "https://github.com/Droid-Deck/DroidDeck-Components/releases/download/desktop-kde-r2/desktop-kde.tar.zst",
        "9ba832677b498654136c69d8c8330434d17e4d53cd099acac9bde3ba429b35bf",
        846_933_224,
    )

    fun find(id: String): LinuxDistro? = distros.firstOrNull { it.id == id }

    // Keep the legacy descriptor for installed containers; do not offer new DD containers.
    fun toJson(): String = JSONArray().apply {
        distros.filterNot { it.id == DROIDDECK_RUNTIME }.forEach { put(it.toJson()) }
    }.toString()

    /**
     * Run once after extraction, as the container's root. Android has no
     * /etc/resolv.conf to inherit, and proot cannot switch users, so APT's
     * download sandbox and pacman's Landlock sandbox are disabled.
     */
    private val CONFIGURE_COMMON = """
        set -e
        rm -f /etc/resolv.conf
        printf 'nameserver 8.8.8.8\nnameserver 1.1.1.1\n' > /etc/resolv.conf
        printf '127.0.0.1 localhost\n::1 localhost ip6-localhost\n' > /etc/hosts
        mkdir -p /tmp /root && chmod 1777 /tmp
    """.trimIndent()

    /**
     * DroidDeck's runtime only gets name resolution: its Mesa, gamescope and
     * other builds are not in pacman's database, so pacman stays untouched.
     */
    fun runtimeConfigureScript(): String = CONFIGURE_COMMON + "\n"

    fun configureScript(family: LinuxPackageFamily): String {
        val common = CONFIGURE_COMMON
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
                xvfb x11-xserver-utils xauth icewm xterm dbus-x11 pulseaudio pulseaudio-utils \
                fonts-dejavu-core xdg-utils ca-certificates fakeroot adwaita-icon-theme xz-utils procps
            if apt-cache show adwaita-icon-theme-legacy >/dev/null 2>&1; then
                apt-get install -y --no-install-recommends adwaita-icon-theme-legacy
            fi
        """.trimIndent() + "\n"
        LinuxPackageFamily.PACMAN -> """
            set -e
            pacman -S --noconfirm --needed \
                xorg-server-xvfb xorg-xrdb xorg-xrandr xorg-xauth icewm xterm dbus pulseaudio \
                ttf-dejavu xdg-utils ca-certificates fakeroot adwaita-icon-theme xz procps-ng
            if pacman -Si adwaita-icon-theme-legacy >/dev/null 2>&1; then
                pacman -S --noconfirm --needed adwaita-icon-theme-legacy
            fi
        """.trimIndent() + "\n"
    }

    /**
     * Starts Xvfb on display [display] with its framebuffer in
     * /tmp/dextop-fb-<display> (mmapped by the service), then the session
     * ([session] is "icewm" for desktop mode). Runs until the session ends.
     */
    fun desktopScript(display: Int, width: Int, height: Int, dpi: Int, session: String, xwayland: Boolean = false): String = """
        D=$display
        rm -f /tmp/.X${'$'}D-lock /tmp/.X11-unix/X${'$'}D
        mkdir -p /tmp/.X11-unix /tmp/dextop-fb-${'$'}D
        chmod 1777 /tmp/.X11-unix
        XROOT=${'$'}(command -v fakeroot-tcp || command -v fakeroot)
        ${if (xwayland) xwaylandCommand(display, width, height, dpi) else """
        $X_SERVER_ENV "${'$'}XROOT" Xvfb :${'$'}D -nolock -screen 0 ${maxOf(width, height, 1920)}x${maxOf(width, height, 1920)}x24 -dpi ${xDpi(dpi)} -fbdir /tmp/dextop-fb-${'$'}D \
            -noreset -nolisten tcp -ac +extension XTEST +extension DAMAGE >/tmp/xvfb-${'$'}D.log 2>&1 &
        """.trimIndent()}
        XPID=${'$'}!
        trap 'kill ${'$'}XPID ${'$'}DBPID 2>/dev/null' EXIT HUP INT TERM
        i=0
        while [ ! -e /tmp/.X11-unix/X${'$'}D ] && [ ${'$'}i -lt 100 ]; do sleep 0.1; i=${'$'}((i + 1)); done
        kill -0 ${'$'}XPID 2>/dev/null || { cat /tmp/xvfb-${'$'}D.log >&2; exit 1; }
        export DISPLAY=:${'$'}D XDG_RUNTIME_DIR=/tmp/runtime-root
        ${if (xwayland) ":" else virtualDisplayTiming(width, height)}
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
    fun appsScript(display: Int, width: Int, height: Int, dpi: Int, xwayland: Boolean = false): String = """
        D=$display
        rm -f /tmp/.X${'$'}D-lock /tmp/.X11-unix/X${'$'}D
        mkdir -p /tmp/.X11-unix && chmod 1777 /tmp/.X11-unix
        XROOT=${'$'}(command -v fakeroot-tcp || command -v fakeroot)
        ${if (xwayland) xwaylandCommand(display, width, height, dpi) else """
        $X_SERVER_ENV "${'$'}XROOT" Xvfb :${'$'}D -nolock -screen 0 ${maxOf(width, height, 1920)}x${maxOf(width, height, 1080)}x24 -dpi ${xDpi(dpi)} \
            -noreset -nolisten tcp -ac +extension XTEST +extension DAMAGE +extension Composite >/tmp/xvfb-${'$'}D.log 2>&1 &
        """.trimIndent()}
        XPID=${'$'}!
        trap 'kill ${'$'}XPID ${'$'}DBPID 2>/dev/null' EXIT HUP INT TERM
        i=0
        while [ ! -e /tmp/.X11-unix/X${'$'}D ] && [ ${'$'}i -lt 100 ]; do sleep 0.1; i=${'$'}((i + 1)); done
        kill -0 ${'$'}XPID 2>/dev/null || { cat /tmp/xvfb-${'$'}D.log >&2; exit 1; }
        export DISPLAY=:${'$'}D XDG_RUNTIME_DIR=/tmp/runtime-root
        ${if (xwayland) ":" else applicationDisplayTiming(width, height)}
        mkdir -p ${'$'}XDG_RUNTIME_DIR && chmod 700 ${'$'}XDG_RUNTIME_DIR
        ${sessionSetup(xDpi(dpi))}
        echo dextop-x-ready
        wait ${'$'}XPID
    """.trimIndent() + "\n"

    // The SHM server needs its distro GLX provider to advertise visuals. Client
    // Zink and the separately packaged Mesa must not replace that provider.
    private const val X_SERVER_ENV = "env -u LD_LIBRARY_PATH -u LIBGL_DRIVERS_PATH " +
        "-u MESA_LOADER_DRIVER_OVERRIDE -u __EGL_VENDOR_LIBRARY_FILENAMES GALLIUM_DRIVER=llvmpipe"

    internal fun xwaylandCommand(display: Int, width: Int, height: Int, dpi: Int): String = """
        export XDG_RUNTIME_DIR=/tmp/runtime-root
        mkdir -p ${'$'}XDG_RUNTIME_DIR && chmod 700 ${'$'}XDG_RUNTIME_DIR
        command -v Xwayland >/dev/null || { echo 'Xwayland is missing; run container setup again' >&2; exit 1; }
        XROOT=${'$'}(command -v fakeroot-tcp || command -v fakeroot)
        WAYLAND_DISPLAY=${LinuxXwaylandHost.displayName(display)} $X_SERVER_ENV "${'$'}XROOT" Xwayland :${'$'}D -nolock -geometry ${width}x$height -dpi ${xDpi(dpi)} \
            -shm -noreset -nolisten tcp -ac +extension XTEST +extension DAMAGE +extension Composite >/tmp/xvfb-${'$'}D.log 2>&1 &
    """.trimIndent()

    /**
     * The guest half of a Wayland-only display (DroidDeck's runtime, which
     * has no Xvfb): the runtime directory Dextop's compositor listens in and
     * the session bus. Runs until the session is stopped.
     */
    fun waylandAppsScript(display: Int): String = """
        D=$display
        export XDG_RUNTIME_DIR=/tmp/runtime-root
        mkdir -p ${'$'}XDG_RUNTIME_DIR && chmod 700 ${'$'}XDG_RUNTIME_DIR
        trap 'kill ${'$'}DBPID 2>/dev/null' EXIT HUP INT TERM
        if command -v dbus-daemon >/dev/null; then
            grep -qE '^[0-9a-f]{32}${'$'}' /etc/machine-id 2>/dev/null || { chmod u+w /etc/machine-id 2>/dev/null; dbus-uuidgen >/etc/machine-id 2>/dev/null; }
            export DBUS_SESSION_BUS_ADDRESS=unix:abstract=/tmp/dextop-dbus-${'$'}D
            mkdir -p /etc/dextop
            printf '%s\n' '<!DOCTYPE busconfig PUBLIC "-//freedesktop//DTD D-Bus Bus Configuration 1.0//EN"' \
                ' "http://www.freedesktop.org/standards/dbus/1.0/busconfig.dtd">' \
                '<busconfig><include>/usr/share/dbus-1/session.conf</include>' \
                '<auth>EXTERNAL</auth><auth>ANONYMOUS</auth><allow_anonymous/></busconfig>' >/etc/dextop/dbus-session.conf
            dbus-daemon --config-file=/etc/dextop/dbus-session.conf --address=${'$'}DBUS_SESSION_BUS_ADDRESS \
                --fork --print-pid >/tmp/dbus-${'$'}D.log 2>&1
            DBPID=${'$'}(head -1 /tmp/dbus-${'$'}D.log)
            # A system bus too: many applications (software centres, network and disk tools)
            # give up without one. Its own permissive configuration, since the stock one
            # switches to the messagebus user, which PRoot's shell user cannot.
            export DBUS_SYSTEM_BUS_ADDRESS=$SYSTEM_BUS_ADDRESS
            mkdir -p /run/dbus
            $BUS_PROBE
            if ! dextop_bus system; then
            # No bus runs: a socket file left by an earlier session would make its bind fail.
            rm -f /run/dbus/system_bus_socket
            printf '%s\n' '<!DOCTYPE busconfig PUBLIC "-//freedesktop//DTD D-Bus Bus Configuration 1.0//EN"' \
                ' "http://www.freedesktop.org/standards/dbus/1.0/busconfig.dtd">' \
                "<busconfig><type>system</type><listen>${'$'}DBUS_SYSTEM_BUS_ADDRESS</listen><keep_umask/>" \
                '<listen>unix:path=/run/dbus/system_bus_socket</listen><servicedir>/etc/dextop/system-services</servicedir>' \
                '<auth>EXTERNAL</auth><auth>ANONYMOUS</auth><allow_anonymous/>' \
                '<policy context="default"><allow user="*"/><allow own="*"/>' \
                '<allow send_destination="*" eavesdrop="true"/><allow eavesdrop="true"/></policy>' \
                '</busconfig>' >/etc/dextop/dbus-system-${'$'}D.conf
            pkill -f "^dbus-daemon --config-file=/etc/dextop/dbus-system-${'$'}D.conf" 2>/dev/null && sleep 0.3
            dbus-daemon --config-file=/etc/dextop/dbus-system-${'$'}D.conf --fork >/tmp/dbus-system-${'$'}D.log 2>&1
            fi
        fi
        echo dextop-wayland-ready
        while :; do sleep 3600 || exit; done
    """.trimIndent() + "\n"

    /** Runs [command] (a desktop entry's Exec line) as an application of display [display]. */
    fun launchScript(display: Int, command: String, wayland: Boolean = false, waylandGpu: Boolean = true,
        appLog: String? = null, androidWindows: Boolean = true): String {
        require(appLog == null || appLog.matches(Regex("[A-Za-z0-9._-]+\\.log")))
        val logs = "/tmp/apps-$display.log" + (appLog?.let { " /tmp/$it" } ?: "")
        return """
        export DISPLAY=:$display XDG_RUNTIME_DIR=/tmp/runtime-root
        export DBUS_SESSION_BUS_ADDRESS=unix:abstract=/tmp/dextop-dbus-$display
        export DBUS_SYSTEM_BUS_ADDRESS=$SYSTEM_BUS_ADDRESS
        # No accessibility bus runs in the session.
        export GTK_A11Y=none
        # An application started before the session bus misses it for good (Firefox then
        # cannot take over URLs from later launches: "Firefox is already running").
        i=0
        $BUS_PROBE
        while ! dextop_bus $display && [ ${'$'}i -lt 150 ]; do
            sleep 0.1; i=${'$'}((i + 1))
        done
        [ ! -f /etc/dextop/session.env ] || . /etc/dextop/session.env
        # Light or dark, as Dextop's Linux theme setting says (LinuxTheme).
        [ ! -f /etc/dextop/theme.env ] || . /etc/dextop/theme.env
        [ ! -r /etc/profile.d/dextop-path.sh ] || . /etc/profile.d/dextop-path.sh
        # Dextop manages these application windows without reparenting them.
        # AWT otherwise waits for a reparent event and leaves its content at
        # the initial size (Arduino/Swing appears blank after window resize).
        export _JAVA_AWT_WM_NONREPARENTING=${'$'}{_JAVA_AWT_WM_NONREPARENTING:-1}
        ${if (androidWindows) ANDROID_WINDOW_DECORATIONS else ""}
        $RENDERER_OVERRIDE
        $GTK_FONT_SCHEMA_FIX
        ${if (wayland) (if (waylandGpu) WAYLAND_ENVIRONMENT else WAYLAND_TOOLKIT_ENVIRONMENT) else "unset WAYLAND_DISPLAY DEXTOP_WAYLAND_KGSL MESA_LOADER_DRIVER_OVERRIDE; export GDK_BACKEND=x11 QT_QPA_PLATFORM=xcb SDL_VIDEODRIVER=x11 SDL_VIDEO_DRIVER=x11 MOZ_ENABLE_WAYLAND=0 ELECTRON_OZONE_PLATFORM_HINT=x11"}
        $HARDWARE_TOOLKIT_ENVIRONMENT
        cd /root
        cmd=${shellQuote(command)}
        $CHROMIUM_SANDBOX_FIX
        # The output also goes to Dextop, which shows it while the window opens; the marker
        # carries the exit code through the pipe.
        { printf '\n[dextop-launch] %s\n' "$(date -Iseconds)"; sh -c "${'$'}cmd" 2>&1; echo "$LAUNCH_EXIT_MARKER${'$'}?"; } | tee -a $logs
    """.trimIndent() + "\n"
    }

    /**
     * As Android windows, Android draws each window's caption with its own close and minimize
     * buttons, so GTK's client-side title bars leave theirs out. A system settings file only for
     * these launches; Dextop Plasma and Book keep them, since they leave client-decorated windows
     * unframed, and so does the desktop session for its window manager.
     */
    private val ANDROID_WINDOW_DECORATIONS = """
        if [ ! -f /etc/dextop/app-xdg/gtk-4.0/settings.ini ]; then
            for version in 3.0 4.0; do
                mkdir -p /etc/dextop/app-xdg/gtk-${'$'}version
                printf '[Settings]\ngtk-decoration-layout=menu:\n' >/etc/dextop/app-xdg/gtk-${'$'}version/settings.ini
            done
        fi
        export XDG_CONFIG_DIRS=/etc/dextop/app-xdg:${'$'}{XDG_CONFIG_DIRS:-/etc/xdg}
    """.trimIndent()

    /** Printed after a launched command ends, followed by its exit code. */
    const val LAUNCH_EXIT_MARKER = "dextop-launch-exit:"

    /**
     * Chromium and Electron applications abort when their sandbox cannot start, and under
     * PRoot neither the setuid helper (chrome-sandbox) nor user namespaces work. Such a
     * command gets --no-sandbox: a Chromium-family executable, or one from a package that
     * ships chrome-sandbox (Electron apps started through a wrapper script). Expects cmd.
     */
    private val CHROMIUM_SANDBOX_FIX = """
        exe=${'$'}(printf '%s\n' "${'$'}cmd" | awk '{ for (i = 1; i <= NF; i++) if (${'$'}i != "env" && ${'$'}i !~ /^[A-Za-z_][A-Za-z0-9_]*=/) { print ${'$'}i; exit } }' | tr -d "'\"")
        real=${'$'}(readlink -f "${'$'}(command -v "${'$'}exe" 2>/dev/null)" 2>/dev/null)
        nosandbox=
        case "${'$'}{real##*/}" in
            chromium|chromium-browser|chrome|google-chrome*|dextop-chrome|dextop-chromium|electron*|code|code-oss|codium|brave*|vivaldi*|microsoft-edge*|opera*) nosandbox=1 ;;
        esac
        if [ -z "${'$'}nosandbox" ] && [ -n "${'$'}real" ]; then
            if [ -e "${'$'}{real%/*}/chrome-sandbox" ]; then nosandbox=1
            else
                pkg=${'$'}(dpkg -S "${'$'}real" 2>/dev/null | head -n 1 | cut -d: -f1)
                [ -n "${'$'}pkg" ] && dpkg -L "${'$'}pkg" 2>/dev/null | grep -q '/chrome-sandbox${'$'}' && nosandbox=1
            fi
        fi
        if [ -n "${'$'}nosandbox" ]; then
            case "${'$'}cmd" in *--no-sandbox*) ;; *) cmd="${'$'}cmd --no-sandbox" ;; esac
            # PRoot cannot reliably clone Chromium's zygote/GPU subprocess
            # state. Use exec-based renderer children and an in-process GPU,
            # retaining actual GPU rendering in hardware mode.
            case " ${'$'}cmd " in *" --in-process-gpu "*) ;; *) cmd="${'$'}cmd --in-process-gpu" ;; esac
            case " ${'$'}cmd " in *" --no-zygote "*) ;; *) cmd="${'$'}cmd --no-zygote" ;; esac
            case " ${'$'}cmd " in
                *DEXTOP_SOFTWARE_RENDERING=1*)
                    case " ${'$'}cmd " in *" --disable-gpu "*) ;; *) cmd="${'$'}cmd --disable-gpu" ;; esac
                    ;;
                *)
                    if [ "${'$'}GALLIUM_DRIVER" = zink ]; then
                        case "${'$'}cmd" in *--use-angle=*) ;; *) cmd="${'$'}cmd --use-angle=gl" ;; esac
                        case " ${'$'}cmd " in *" --enable-gpu-rasterization "*) ;; *) cmd="${'$'}cmd --enable-gpu-rasterization" ;; esac
                        case " ${'$'}cmd " in *" --ignore-gpu-blocklist "*) ;; *) cmd="${'$'}cmd --ignore-gpu-blocklist" ;; esac
                        case " ${'$'}cmd " in *" --enable-zero-copy "*) ;; *) cmd="${'$'}cmd --enable-zero-copy" ;; esac
                    fi
                    ;;
            esac
            # With no GNOME/KDE session Electron may select basic_text despite a
            # working Secret Service. Select the installed encrypted provider,
            # while preserving an explicitly selected password store.
            if command -v gnome-keyring-daemon >/dev/null 2>&1 &&
                [ -f /usr/share/dbus-1/services/org.freedesktop.secrets.service ]; then
                case "${'$'}cmd" in *--password-store=*) ;; *) cmd="${'$'}cmd --password-store=gnome-libsecret" ;; esac
            fi
            case "${'$'}cmd" in
                *--ozone-platform=*) ;;
                *)
                    if [ -n "${'$'}WAYLAND_DISPLAY" ] && [ -n "${'$'}DEXTOP_GPU_RENDER_NODE" ]; then
                        cmd="${'$'}cmd --ozone-platform=wayland --render-node-override=${'$'}DEXTOP_GPU_RENDER_NODE"
                    else
                        cmd="env -u WAYLAND_DISPLAY MESA_VK_WSI_DEBUG=sw LIBGL_KOPPER_DRI2=true GDK_BACKEND=x11 ELECTRON_OZONE_PLATFORM_HINT=x11 ${'$'}cmd --ozone-platform=x11"
                    fi
                    ;;
            esac
        fi
    """.trimIndent()

    /**
     * The session bus, on an abstract socket because the shell user cannot
     * bind filesystem unix sockets here (X does the same), and the font DPI.
     * Expects D to be the display number.
     */
    private fun sessionSetup(dpi: Int): String = """
        [ ! -f /etc/dextop/session.env ] || . /etc/dextop/session.env
        [ ! -r /etc/profile.d/dextop-path.sh ] || . /etc/profile.d/dextop-path.sh
        if command -v python3 >/dev/null 2>&1 && [ -f /usr/local/bin/dextop-keyring-repair ]; then
            python3 /usr/local/bin/dextop-keyring-repair || echo 'Keyring default recovery failed' >&2
        fi
        $RENDERER_OVERRIDE
        $BROWSER_LAUNCHER_MIGRATION
        $ROOT_TOOLS
        if command -v dbus-daemon >/dev/null; then
            # Images ship "uninitialized" (13 characters), which D-Bus and Chrome reject.
            grep -qE '^[0-9a-f]{32}${'$'}' /etc/machine-id 2>/dev/null || { chmod u+w /etc/machine-id 2>/dev/null; dbus-uuidgen >/etc/machine-id 2>/dev/null; }
            dbus-uuidgen --ensure=/etc/machine-id 2>/dev/null
            mkdir -p /var/lib/dbus && cp -f /etc/machine-id /var/lib/dbus/machine-id 2>/dev/null
            export DBUS_SESSION_BUS_ADDRESS=unix:abstract=/tmp/dextop-dbus-${'$'}D
            # Under PRoot the daemon may see the real uid while clients claim the fake root
            # (or the reverse), so EXTERNAL alone can refuse every client; X is as open (-ac).
            mkdir -p /etc/dextop
            printf '%s\n' '<!DOCTYPE busconfig PUBLIC "-//freedesktop//DTD D-Bus Bus Configuration 1.0//EN"' \
                ' "http://www.freedesktop.org/standards/dbus/1.0/busconfig.dtd">' \
                '<busconfig><include>/usr/share/dbus-1/session.conf</include>' \
                '<auth>EXTERNAL</auth><auth>ANONYMOUS</auth><allow_anonymous/></busconfig>' >/etc/dextop/dbus-session.conf
            # A bus left by an earlier session would keep the address and serve stale settings.
            pkill -f "^dbus-daemon .*--address=${'$'}DBUS_SESSION_BUS_ADDRESS( |${'$'})" 2>/dev/null && sleep 0.3
            dbus-daemon --config-file=/etc/dextop/dbus-session.conf --address=${'$'}DBUS_SESSION_BUS_ADDRESS \
                --fork --print-pid >/tmp/dbus-${'$'}D.log 2>&1
            DBPID=${'$'}(head -1 /tmp/dbus-${'$'}D.log)
            # A system bus too: many applications (software centres, network and disk tools)
            # give up without one. Its own permissive configuration, since the stock one
            # switches to the messagebus user, which PRoot's shell user cannot.
            export DBUS_SYSTEM_BUS_ADDRESS=$SYSTEM_BUS_ADDRESS
            mkdir -p /run/dbus
            $BUS_PROBE
            if ! dextop_bus system; then
            # No bus runs: a socket file left by an earlier session would make its bind fail.
            rm -f /run/dbus/system_bus_socket
            printf '%s\n' '<!DOCTYPE busconfig PUBLIC "-//freedesktop//DTD D-Bus Bus Configuration 1.0//EN"' \
                ' "http://www.freedesktop.org/standards/dbus/1.0/busconfig.dtd">' \
                "<busconfig><type>system</type><listen>${'$'}DBUS_SYSTEM_BUS_ADDRESS</listen><keep_umask/>" \
                '<listen>unix:path=/run/dbus/system_bus_socket</listen><servicedir>/etc/dextop/system-services</servicedir>' \
                '<auth>EXTERNAL</auth><auth>ANONYMOUS</auth><allow_anonymous/>' \
                '<policy context="default"><allow user="*"/><allow own="*"/>' \
                '<allow send_destination="*" eavesdrop="true"/><allow eavesdrop="true"/></policy>' \
                '</busconfig>' >/etc/dextop/dbus-system-${'$'}D.conf
            pkill -f "^dbus-daemon --config-file=/etc/dextop/dbus-system-${'$'}D.conf" 2>/dev/null && sleep 0.3
            dbus-daemon --config-file=/etc/dextop/dbus-system-${'$'}D.conf --fork >/tmp/dbus-system-${'$'}D.log 2>&1
            fi
        fi
        # xterm's default bitmap font is not installed; use the Xft fonts (CJK included).
        command -v xrdb >/dev/null && printf '%s\n' 'Xft.dpi: $dpi' \
            'XTerm*faceName: DejaVu Sans Mono' 'XTerm*faceNameDoublesize: Noto Sans Mono CJK JP' \
            'XTerm*faceSize: 11' 'XTerm*locale: true' \
            'XTerm*VT100.translations: #override \n Ctrl Shift <Key>C: copy-selection(CLIPBOARD) \n Ctrl Shift <Key>V: insert-selection(CLIPBOARD)' \
            | xrdb -nocpp -merge
        [ ! -f /etc/dextop/start-session.sh ] || sh /etc/dextop/start-session.sh
    """.trimIndent()

    /** Where the GPU package installs its Vulkan driver manifest (see [turnipScript]). */
    const val TURNIP_ICD = "/opt/dextop-mesa/share/vulkan/icd.d/freedreno_icd.aarch64.json"
    /** Present when the GPU package also carries Mesa's GL/EGL for Wayland ([LinuxGpuPackage.mesa]). */
    const val TURNIP_WAYLAND_READY = "/opt/dextop-mesa/share/dextop/wayland-ready"
    private const val GPU_PREFIX = "/opt/dextop-mesa"

    /** Whether the GPU package drives [gpuModel] (KGSL's gpu_model, such as "Adreno750v2"). */
    fun turnipSupports(gpuModel: String?) = gpuModel != null &&
        (gpuModel.startsWith("Adreno6") || gpuModel.startsWith("Adreno7") || gpuModel.startsWith("Adreno8"))

    /**
     * A prebuilt GPU package for containers, downloaded during setup and
     * checked by SHA-256; nothing is compiled on the device. [driver] is the
     * Turnip Vulkan driver (KGSL backend) in the zip archive, [notices] its
     * licence files. [mesa], when set, is a directory of the archive laid out
     * like [GPU_PREFIX] (lib/aarch64-linux-gnu, share/glvnd, ...) holding
     * Mesa's GL/EGL built for KGSL, which enables Wayland GPU presentation;
     * without it the distribution's Zink runs OpenGL on the driver.
     */
    internal data class LinuxGpuPackage(
        val name: String,
        val url: String,
        val sha256: String,
        val driver: String,
        val notices: List<String>,
        val mesa: String? = null,
    )

    /**
     * DroidDeck's DD-Turnip: Mesa 26.3-devel with KGSL patches for Adreno
     * 6xx to 8xx, GPL-3.0 with its source published with the release.
     */
    val GPU_PACKAGE = LinuxGpuPackage(
        name = "DD-Turnip-v0.2.0",
        url = "https://github.com/Droid-Deck/Drivers-CI/releases/download/DD-Turnip-v0.2.0/DD-Turnip-v0.2.0.zip",
        sha256 = "fc16658953062cfaabda8a5663c498667d78bb4c9c74c819ff580753270a147d",
        driver = "linux/libvulkan_freedreno.so",
        notices = listOf("NOTICE", "LICENSES", "manifest.json"),
    )

    /**
     * Installs [gpu] into [GPU_PREFIX]: Turnip talks to Android's Adreno
     * kernel driver (/dev/kgsl-3d0) directly, which distribution builds do not.
     */
    fun turnipScript(gpu: LinuxGpuPackage = GPU_PACKAGE): String = """
        set -e
        export DEBIAN_FRONTEND=noninteractive
        case "$(cat /sys/class/kgsl/kgsl-3d0/gpu_model 2>/dev/null)" in
            Adreno6*|Adreno7*|Adreno8*) ;;
            *) echo "No Turnip for this GPU; using VirGL"; exit 0 ;;
        esac
        apt-get install -y --no-install-recommends wget ca-certificates unzip libvulkan1 libgl1-mesa-dri \
            libdrm2 libzstd1 zlib1g libwayland-client0 libx11-xcb1 libxshmfence1 libxcb1 libxcb-dri3-0 \
            libxcb-present0 libxcb-xfixes0 libxcb-sync1 libxcb-randr0 libxcb-shm0 libxcb-keysyms1
        work=/tmp/dextop-gpu-package
        rm -rf "${'$'}work" && mkdir -p "${'$'}work" && cd "${'$'}work"
        wget -q -O package.zip ${gpu.url}
        echo "${gpu.sha256}  package.zip" | sha256sum -c -
        unzip -q package.zip
        rm -rf $GPU_PREFIX
        mkdir -p $GPU_PREFIX/lib $GPU_PREFIX/share/doc/${gpu.name} ${'$'}(dirname $TURNIP_ICD)
        ${gpu.mesa?.let { "cp -a $it/. $GPU_PREFIX/" }.orEmpty()}
        cp ${gpu.driver} $GPU_PREFIX/lib/libvulkan_freedreno.so
        cp -r ${gpu.notices.joinToString(" ")} $GPU_PREFIX/share/doc/${gpu.name}/
        printf '{"file_format_version": "1.0.0", "ICD": {"library_path": "$GPU_PREFIX/lib/libvulkan_freedreno.so", "api_version": "1.4.0"}}\n' > $TURNIP_ICD
        ${if (gpu.mesa != null) "mkdir -p $GPU_PREFIX/share/dextop && touch $TURNIP_WAYLAND_READY" else ""}
        cd / && rm -rf "${'$'}work"
        $MESA_BACKPORTS_SCRIPT
    """.trimIndent() + "\n"

    /**
     * Debian 13's Mesa 25.0 renders through Zink but never presents to an X
     * server without DRI3 (Xvfb): the window stays black. Mesa 26 from the
     * distribution's backports presents through the CPU path, prebuilt.
     */
    private val MESA_BACKPORTS_SCRIPT = """
        . /etc/os-release
        if [ "${'$'}ID" = debian ] && [ -n "${'$'}VERSION_CODENAME" ]; then
            echo "deb http://deb.debian.org/debian ${'$'}VERSION_CODENAME-backports main" > /etc/apt/sources.list.d/dextop-backports.list
            apt-get update
            apt-get install -y --no-install-recommends -t "${'$'}VERSION_CODENAME-backports" \
                mesa-libgallium libgl1-mesa-dri libglx-mesa0 libegl-mesa0 libgbm1 mesa-vulkan-drivers
        fi
    """.trimIndent()

    /**
     * X DPI matching Android's scale: Android lays out 1 dp per px at 160
     * dpi, X toolkits 1 pt per px at 96 dpi.
     */
    private fun xDpi(androidDpi: Int) = (androidDpi * 96 / 160).coerceAtLeast(96)

    /**
     * GTK 4 on Wayland reads the font keys antialiasing, hinting and rgba-order of
     * org.gnome.settings-daemon.plugins.xsettings whenever that schema is installed.
     * gnome-settings-daemon 48 moved them to a ".deprecated" schema, so GTK 4 apps
     * (Transmission, …) abort with "does not contain a key named 'antialiasing'". This
     * compiles the schema with those keys into a directory GLib searches first.
     */
    private val GTK_FONT_SCHEMA_FIX = """
        S=/usr/share/glib-2.0/schemas
        if grep -q 'plugins.xsettings.deprecated' ¤S/org.gnome.settings-daemon.plugins.xsettings.gschema.xml 2>/dev/null &&
            command -v glib-compile-schemas >/dev/null; then
            O=/etc/dextop/gsettings
            if [ ! -f ¤O/gschemas.compiled ] || [ ¤S/org.gnome.settings-daemon.enums.xml -nt ¤O/gschemas.compiled ]; then
                mkdir -p ¤O && cp -f ¤S/org.gnome.settings-daemon.enums.xml ¤O/
                cat >¤O/org.gnome.settings-daemon.plugins.xsettings.gschema.xml <<'DEXTOP_SCHEMA'
        <?xml version="1.0" encoding="UTF-8"?>
        <schemalist>
          <schema gettext-domain="gnome-settings-daemon" id="org.gnome.settings-daemon.plugins.xsettings" path="/org/gnome/settings-daemon/plugins/xsettings/">
            <key name="disabled-gtk-modules" type="as"><default>[]</default></key>
            <key name="enabled-gtk-modules" type="as"><default>[]</default></key>
            <key type="a{sv}" name="overrides"><default>{}</default></key>
            <key name="antialiasing" enum="org.gnome.settings-daemon.GsdFontAntialiasingMode"><default>'grayscale'</default></key>
            <key name="hinting" enum="org.gnome.settings-daemon.GsdFontHinting"><default>'slight'</default></key>
            <key name="rgba-order" enum="org.gnome.settings-daemon.GsdFontRgbaOrder"><default>'rgb'</default></key>
          </schema>
        </schemalist>
        DEXTOP_SCHEMA
                glib-compile-schemas ¤O 2>/dev/null
            fi
            export GSETTINGS_SCHEMA_DIR=¤O
        fi
    """.trimIndent().replace('¤', '$')

    /**
     * Containers set up before the renderer setting existed export
     * LIBGL_ALWAYS_SOFTWARE in session.env, which would hide VirGL.
     */
    private val RENDERER_OVERRIDE =
        "case \"${'$'}GALLIUM_DRIVER\" in virpipe|zink) unset LIBGL_ALWAYS_SOFTWARE ;; esac"

    /**
     * GPU flags for Chromium-based browsers by renderer. On VirGL they draw
     * no text when they rasterize on the GPU (glyph atlases), so only
     * compositing and WebGL use it. Zink uses the ordinary GPU GL path; do not
     * force ANGLE Vulkan, which can stall otherwise working Electron applications.
     */
    val CHROMIUM_VIRGL_FLAGS = "case \"${'$'}GALLIUM_DRIVER\" in " +
        "virpipe) set -- --disable-gpu-rasterization \"${'$'}@\" ;; " +
        "zink) if [ -n \"${'$'}WAYLAND_DISPLAY\" ] && [ -n \"${'$'}DEXTOP_GPU_RENDER_NODE\" ]; then " +
        "set -- --ozone-platform=wayland --render-node-override=\"${'$'}DEXTOP_GPU_RENDER_NODE\" \"${'$'}@\"; " +
        "else export MESA_VK_WSI_DEBUG=sw LIBGL_KOPPER_DRI2=true; set -- --ozone-platform=x11 \"${'$'}@\"; fi; " +
        "set -- --use-angle=gl --in-process-gpu --no-zygote --enable-gpu-rasterization --ignore-gpu-blocklist --enable-zero-copy \"${'$'}@\" ;; esac"

    /** Puts the current [CHROMIUM_VIRGL_FLAGS] into browser launchers written by earlier versions. */
    /**
     * Sessions run as the real uid (see LinuxProot.guest), so package managers
     * and sudo go through fakeroot; every file already belongs to that uid.
     * fakeroot-tcp: Android has no SysV IPC for the default faked.
     */
    private val ROOT_TOOLS: String get() = """
        fakeroot=${'$'}(command -v fakeroot-tcp || command -v fakeroot)
        if [ -n "${'$'}fakeroot" ]; then
            mkdir -p /usr/local/sbin
            for tool in apt apt-get dpkg aptitude pacman; do
                [ -x /usr/bin/${'$'}tool ] || continue
                {
                    echo '#!/bin/sh'
                    # First clear what an interrupted package operation left (see dextop-apt-heal).
                    case "${'$'}tool" in apt|apt-get|aptitude)
                        echo "if [ -x /usr/local/sbin/dextop-apt-heal ]; then if [ \"\${'$'}(id -u)\" = 0 ]; then /usr/local/sbin/dextop-apt-heal; else ${'$'}fakeroot /usr/local/sbin/dextop-apt-heal; fi; fi" ;;
                    esac
                    echo "[ \"\${'$'}(id -u)\" = 0 ] && exec /usr/bin/${'$'}tool \"\${'$'}@\""
                    echo "exec ${'$'}fakeroot /usr/bin/${'$'}tool \"\${'$'}@\""
                } >/usr/local/sbin/${'$'}tool
                chmod 755 /usr/local/sbin/${'$'}tool
            done
            # Real sudo cannot work: Android starts the shell with no_new_privs.
            {
                echo '#!/bin/sh'
                echo 'while [ ${'$'}# -gt 0 ]; do case "${'$'}1" in -u|-g|-C|-D|-h|-p|-r|-t|-U) shift 2 ;; --) shift; break ;; -*) shift ;; *) break ;; esac; done'
                echo '[ "${'$'}(id -u)" = 0 ] && exec "${'$'}@"'
                echo "exec ${'$'}fakeroot \"\${'$'}@\""
            } >/usr/local/bin/sudo
            chmod 755 /usr/local/bin/sudo
        fi
    """.trimIndent()

    private val BROWSER_LAUNCHER_MIGRATION = """
        for launcher in /usr/local/bin/dextop-chrome /usr/local/bin/dextop-chromium; do
            [ -f "${'$'}launcher" ] || continue
            if ! grep -q -- 'DEXTOP_GPU_RENDER_NODE' "${'$'}launcher" || grep -q -- '--use-angle=vulkan' "${'$'}launcher"; then
                sed -i '/disable-gpu-rasterization/d; /DefaultANGLEVulkan/d' "${'$'}launcher"
                sed -i '/^case "${'$'}GALLIUM_DRIVER" in /d' "${'$'}launcher"
                sed -i '/^exec /i ${CHROMIUM_VIRGL_FLAGS.replace("'", "'\\''")}' "${'$'}launcher"
            fi
        done
        if [ -f /usr/local/bin/dextop-firefox ] && ! grep -q MOZ_SHM_NO_SEALS /usr/local/bin/dextop-firefox; then
            sed -i 's/^export MOZ_DISABLE_CONTENT_SANDBOX=1 .*${'$'}/$FIREFOX_SANDBOX_ENVIRONMENT/' /usr/local/bin/dextop-firefox
        fi
        for prefs in /usr/lib/firefox-esr/defaults/pref /usr/lib/firefox/defaults/pref; do
            [ -d "${'$'}prefs" ] && printf '%s\n' $FIREFOX_PREFS >"${'$'}prefs/dextop.js"
        done
    """.trimIndent()

    /**
     * Firefox's seccomp sandboxes crash its child processes under PRoot on
     * newer kernels (Linux 6.12), so every one of them stays off.
     */
    const val FIREFOX_SANDBOX_ENVIRONMENT = "export MOZ_DISABLE_CONTENT_SANDBOX=1 MOZ_DISABLE_RDD_SANDBOX=1 " +
        "MOZ_DISABLE_SOCKET_PROCESS_SANDBOX=1 MOZ_DISABLE_GMP_SANDBOX=1 MOZ_DISABLE_UTILITY_SANDBOX=1 MOZ_DISABLE_GPU_SANDBOX=1 " +
        "MOZ_SHM_NO_SEALS=1"

    /**
     * DroidDeck's Firefox defaults (tools/linuxfs/desktop/firefox-droiddeck.js) as
     * shell-quoted lines, without its devPixelsPerPx: Dextop scales through Xft.dpi.
     */
    // A getter: BROWSER_LAUNCHER_MIGRATION, declared earlier, reads it during initialization.
    private val FIREFOX_PREFS: String get() = listOf(
        """pref("network.manage-offline-status", false);""",
        """pref("browser.offline", false);""",
        """pref("security.sandbox.content.level", 0);""",
        """pref("media.cubeb.sandbox", false);""",
        """pref("security.sandbox.socket.process.level", 0);""",
        """pref("browser.shell.checkDefaultBrowser", false);""",
        """pref("datareporting.policy.dataSubmissionEnabled", false);""",
        """pref("browser.aboutwelcome.enabled", false);""",
    ).joinToString(" ") { "'$it'" }

    /**
     * Toolkits prefer Dextop's Wayland compositor (GPU buffers through
     * dma-buf) and fall back to X11.
     */
    private val WAYLAND_TOOLKIT_ENVIRONMENT = "export WAYLAND_DISPLAY=${LinuxAppSession.WAYLAND_DISPLAY} " +
        "GDK_BACKEND=wayland,x11 GTK_CSD=0 QT_QPA_PLATFORM='wayland;xcb' SDL_VIDEODRIVER=wayland,x11 SDL_VIDEO_DRIVER=wayland,x11 " +
        "MOZ_ENABLE_WAYLAND=1 ELECTRON_OZONE_PLATFORM_HINT=auto\n"

    private val HARDWARE_TOOLKIT_ENVIRONMENT = """
        case "${'$'}GALLIUM_DRIVER" in
            zink|virpipe)
                export GSK_RENDERER=${'$'}{GSK_RENDERER:-gl}
                export MOZ_WEBRENDER=${'$'}{MOZ_WEBRENDER:-1} MOZ_ACCELERATED=${'$'}{MOZ_ACCELERATED:-1}
                export QSG_RHI_BACKEND=${'$'}{QSG_RHI_BACKEND:-opengl}
                export QSG_RHI_PREFER_SOFTWARE_RENDERER=0
                export QT_WIDGETS_RHI=${'$'}{QT_WIDGETS_RHI:-1} QT_WIDGETS_RHI_BACKEND=${'$'}{QT_WIDGETS_RHI_BACKEND:-opengl}
                ;;
        esac
    """.trimIndent()

    private val WAYLAND_ENVIRONMENT = WAYLAND_TOOLKIT_ENVIRONMENT +
        "export DEXTOP_WAYLAND_KGSL=1 MESA_LOADER_DRIVER_OVERRIDE=zink\n" +
        "unset LIBGL_KOPPER_DRI2 MESA_VK_WSI_DEBUG\n" +
        "if [ -f '${LinuxGpuRuntime.PREFIX}/.ready' ]; then\n" +
        "  export __EGL_VENDOR_LIBRARY_FILENAMES=${LinuxGpuRuntime.PREFIX}/share/glvnd/egl_vendor.d/50_mesa.json\n" +
        "  export LIBGL_DRIVERS_PATH=${LinuxGpuRuntime.LIB}/dri\n" +
        "  export GBM_BACKENDS_PATH=${LinuxGpuRuntime.LIB}/gbm\n" +
        "  export LD_PRELOAD=${LinuxGpuRuntime.LIB}/libdextop_kgsl_gbm.so${'$'}{LD_PRELOAD:+:${'$'}LD_PRELOAD}\n" +
        "  for dextop_node in /dev/dri/renderD*; do\n" +
        "    if [ -c \"${'$'}dextop_node\" ]; then export DEXTOP_GPU_RENDER_NODE=\"${'$'}dextop_node\"; break; fi\n" +
        "  done\n" +
        "  export LD_LIBRARY_PATH=${LinuxGpuRuntime.LIB}${'$'}{LD_LIBRARY_PATH:+:${'$'}LD_LIBRARY_PATH}\n" +
        "else\n" +
        "  export __EGL_VENDOR_LIBRARY_FILENAMES=/opt/dextop-mesa/share/glvnd/egl_vendor.d/50_mesa.json\n" +
        "  export LD_LIBRARY_PATH=/opt/dextop-mesa/lib/aarch64-linux-gnu${'$'}{LD_LIBRARY_PATH:+:${'$'}LD_LIBRARY_PATH}\n" +
        "fi"

    private fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
