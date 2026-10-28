package moe.n4tsu.dextop.linux

/**
 * Valve's native ARM64 Steam client, set up the way DroidDeck does it:
 * DroidDeck's droiddeck-steam-install (GPL-3.0, Droid-Deck/DroidDeck 26ca374,
 * a resource next to this class) fetches the client from Valve's CDN with
 * every part checked by SHA-256, and the client starts with DroidDeck's
 * session preload and options. Proton and FEX arrive through Steam itself.
 */
internal object LinuxSteam {
    const val LAUNCHER = "/usr/local/bin/dextop-steam"
    private const val INSTALLER = "/usr/local/bin/dextop-steam-install"
    private const val PRELOAD = "${LinuxProot.GUEST_LIBRARY_DIR}/libdextop_blsession.so"

    internal val launcherScript: String = """
        #!/bin/sh
        set -e
        root=${'$'}{XDG_DATA_HOME:-${'$'}HOME/.local/share}/Steam
        if [ ! -x "${'$'}root/steamrtarm64/steam" ]; then
            $INSTALLER
        fi
        exec 9>"${'$'}root/.dextop-launch.lock"
        if ! flock -n 9; then
            echo 'Steam is already starting or running.' >&2
            exit 0
        fi
        # Restore only the exact helper edit made by the earlier Dextop launcher.
        helper="${'$'}root/steamrtarm64/steamwebhelper.sh"
        if [ -f "${'$'}helper" ] && [ "${'$'}(wc -c <"${'$'}helper")" -eq 392 ] &&
            grep -qx 'unset LIBGL_KOPPER_DISABLE' "${'$'}helper" &&
            grep -q '^echo "setting LIBGL_KOPPER_DISABLE=true for steamwebhelper' "${'$'}helper"; then
            sed 's/^unset LIBGL_KOPPER_DISABLE$/export LIBGL_KOPPER_DISABLE=true/' "${'$'}helper" > "${'$'}helper.dextop-restore"
            chmod --reference="${'$'}helper" "${'$'}helper.dextop-restore"
            mv -f "${'$'}helper.dextop-restore" "${'$'}helper"
        fi
        if [ ! -r ${LinuxSessionCompatibility.GUEST_PRELOAD} ] && [ ! -r $PRELOAD ]; then
            echo 'Steam requires the Dextop session compatibility library; update Dextop and restart Linux.' >&2
            exit 1
        fi
        export STEAM_RUNTIME=1 SDL_VIDEO_X11_DGAMOUSE=0
        export GLIBC_TUNABLES=glibc.pthread.rseq=0${'$'}{GLIBC_TUNABLES:+:${'$'}GLIBC_TUNABLES}
        export LD_LIBRARY_PATH=${'$'}root/steamrtarm64:${'$'}root/steamrtarm64/panorama${'$'}{LD_LIBRARY_PATH:+:${'$'}LD_LIBRARY_PATH}
        # Resolve VGUI's legacy GLX entry points through the system GL dispatcher.
        export LD_PRELOAD=libGL.so.1${'$'}{LD_PRELOAD:+:${'$'}LD_PRELOAD}
        if [ "${'$'}{GALLIUM_DRIVER:-}" = zink ]; then
            export MESA_VK_WSI_DEBUG=sw LIBGL_KOPPER_DRI2=true
        fi
        # Older containers without the global preload can still use this launcher.
        [ -r ${LinuxSessionCompatibility.GUEST_PRELOAD} ] || export LD_PRELOAD=$PRELOAD${'$'}{LD_PRELOAD:+:${'$'}LD_PRELOAD}
        # Like Valve's steam.sh, honor the client's update/restart request (42).
        # An exec here exits after the first bootstrap update without opening the client.
        steam_updates=0
        while :; do
            steam_channel=${'$'}(cat "${'$'}root/package/beta" 2>/dev/null || true)
            /usr/local/bin/dextop-steam-state "${'$'}root"
            /usr/local/bin/dextop-proton-arm64 prepare 9>&-
            steam_started=${'$'}(date +%s)
            status=0
            "${'$'}root/steamrtarm64/steam" -nobigpicture -clientbeta "${'$'}steam_channel" -no-cef-sandbox -cef-ozone-platform=x11 -cef-force-gpu -cef-use-gl=angle -cef-use-angle=gl "${'$'}@" 9>&- || status=${'$'}?
            [ "${'$'}status" -eq 42 ] || exit "${'$'}status"
            steam_ran=${'$'}((${'$'}(date +%s) - steam_started))
            [ "${'$'}steam_ran" -lt 60 ] || steam_updates=0
            steam_updates=${'$'}((steam_updates + 1))
            if [ "${'$'}steam_updates" -ge 3 ]; then
                echo 'Steam repeatedly requested an update restart; stopped the loop. See the Steam launch log.' >&2
                exit 1
            fi
            set --
        done
    """.trimIndent() + "\n"

    private val desktopEntry = """
        [Desktop Entry]
        Type=Application
        Name=Steam
        Exec=$LAUNCHER %U
        Icon=steam
        Categories=Game;
        StartupWMClass=steam
        X-Dextop-DisplayBackend=x11
    """.trimIndent() + "\n"

    /** Registers existing ARM64 clients too, including containers installed before Steam setup. */
    fun stage(root: java.io.File) {
        if (!java.io.File(root, "root/.local/share/Steam/steamrtarm64/steam").isFile) return
        stageGaming(root)
        stageIcon(root)
        java.io.File(root, LAUNCHER.removePrefix("/")).apply {
            parentFile?.mkdirs()
            if (!isFile || readText() != launcherScript) writeText(launcherScript)
            check(setExecutable(true, false)) { "Cannot make Steam launcher executable" }
        }
        stageCommandAlias(root)
        java.io.File(root, "root/.local/share/applications/dextop-steam.desktop").apply {
            parentFile?.mkdirs()
            if (!isFile || readText() != desktopEntry) writeText(desktopEntry)
        }
        stageSteamMode(root)
    }

    /** Steam-generated game shortcuts invoke `steam`, while our ARM64 client
     * launcher is named dextop-steam. Preserve any user or distribution command. */
    internal fun stageCommandAlias(root: java.io.File) {
        if (!java.io.File(root, "root/.local/share/Steam/steamrtarm64/steam").isFile) return
        val alias = java.io.File(root, "usr/local/bin/steam").toPath()
        val vendor = java.io.File(root, "usr/bin/steam").toPath()
        if (java.nio.file.Files.exists(alias, java.nio.file.LinkOption.NOFOLLOW_LINKS) ||
            java.nio.file.Files.exists(vendor, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        java.nio.file.Files.createDirectories(alias.parent)
        java.nio.file.Files.createSymbolicLink(alias, java.nio.file.Paths.get("dextop-steam"))
    }

    fun stageIcon(root: java.io.File) {
        val source = java.io.File(root, "root/.local/share/Steam/public/steam_tray.ico")
        val target = java.io.File(root, "root/.local/share/icons/hicolor/96x96/apps/steam.png")
        if (!source.isFile || source.length() !in 1..(2 * 1024 * 1024) || target.isFile) return
        val png = LinuxIconDecoder.png(source) ?: return
        target.parentFile?.mkdirs()
        val temporary = java.io.File(target.parentFile, target.name + ".staged")
        temporary.writeBytes(png)
        java.nio.file.Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    private val gamingResources = mapOf(
        "dextop-gaming" to "usr/local/bin/dextop-gaming",
        "dextop-steam-state" to "usr/local/bin/dextop-steam-state",
        "dextop-deck-gamescope" to "usr/local/bin/dextop-deck-gamescope",
        "dextop-dd-stock-session" to "usr/local/bin/dextop-dd-stock-session",
        "gaming/deck-gamescope.tar.xz" to "usr/local/lib/dextop-gaming/deck-gamescope.tar.xz",
        "gaming/deck-gamescope.json" to "usr/local/lib/dextop-gaming/deck-gamescope.json",
        "gaming/deck-direct-gpu.tar.xz" to "usr/local/lib/dextop-gaming/deck-direct-gpu.tar.xz",
        "gaming/deck-direct-gpu.json" to "usr/local/lib/dextop-gaming/deck-direct-gpu.json",
        "dextop-bluez" to "usr/local/lib/dextop-gaming/dextop-bluez",
        "droiddeck-steam-install" to INSTALLER.removePrefix("/"),
        "gaming/pulse-client.conf" to "usr/local/lib/dextop-gaming/pulse-client.conf",
        "gaming/runtime-check-i386.bin" to "usr/local/lib/dextop-gaming/runtime-check-i386",
        "gaming/runtime-check-x86_64.bin" to "usr/local/lib/dextop-gaming/runtime-check-x86_64",
        "dextop-wine-sync" to "usr/local/lib/dextop-gaming/dextop-wine-sync",
        "dextop-proton-arm64" to "usr/local/bin/dextop-proton-arm64",
        "gaming/libdextop_gamesock-aarch64.bin" to "usr/local/lib/dextop-gaming/preload/aarch64/libdextop_gamesock.so",
        "droiddeck/linuxfs/usr/local/bin/droiddeck-fex" to "usr/local/lib/dextop-gaming/droiddeck-fex",
        "droiddeck/linuxfs/usr/local/bin/steam-compatibility" to "usr/local/lib/dextop-gaming/steam-compatibility",
        // Controllers taken from Android by whichever guest process opens them.
        "gaming/libdextop_padgrab-aarch64.bin" to "usr/local/lib/dextop-gaming/preload/aarch64/libdextop_padgrab.so",
        "gaming/libdextop_padgrab-x86_64.bin" to "usr/local/lib/dextop-gaming/preload/x86_64/libdextop_padgrab.so",
        "gaming/libdextop_padgrab-i386.bin" to "usr/local/lib/dextop-gaming/preload/i386/libdextop_padgrab.so",
        // The Steam mode's physical keyboards and mice.
        "dextop-deck-input" to "usr/local/lib/dextop-gaming/dextop-deck-input",
        // The Steam mode's logind and NetworkManager stand-ins; login1 loads netmanager from its own directory.
        "droiddeck/linuxfs/usr/local/bin/droiddeck-login1" to LOGIN1.removePrefix("/"),
        // SteamOS update probes report unsupported on this Android-hosted guest, as in DroidDeck.
        "droiddeck/linuxfs/usr/bin/steamos-update" to "usr/bin/steamos-update",
        "droiddeck/linuxfs/usr/bin/steamos-select-branch" to "usr/bin/steamos-select-branch",
        "droiddeck/linuxfs/usr/bin/steamos-polkit-helpers/steamos-select-branch" to "usr/bin/steamos-polkit-helpers/steamos-select-branch",
        "droiddeck/linuxfs/usr/bin/steamos-polkit-helpers/steamos-update" to "usr/bin/steamos-polkit-helpers/steamos-update",
        "droiddeck/linuxfs/usr/bin/jupiter-biosupdate" to "usr/bin/jupiter-biosupdate",
        "droiddeck/linuxfs/usr/bin/steamos-polkit-helpers/jupiter-biosupdate" to "usr/bin/steamos-polkit-helpers/jupiter-biosupdate",
        "droiddeck/linuxfs/usr/bin/steamos-polkit-helpers/jupiter-dock-updater" to "usr/bin/steamos-polkit-helpers/jupiter-dock-updater",
        "droiddeck/linuxfs/usr/local/bin/droiddeck-netmanager" to "usr/local/lib/dextop-gaming/droiddeck-netmanager",
        "gaming/libblsession-x86_64.bin" to "usr/local/lib/dextop-gaming/preload/x86_64/libblsession.so",
        "gaming/libdextop_unixsock-x86_64.bin" to "usr/local/lib/dextop-gaming/preload/x86_64/libdextop_unixsock.so",
        "gaming/libblsession-i386.bin" to "usr/local/lib/dextop-gaming/preload/i386/libblsession.so",
        "gaming/libdextop_unixsock-i386.bin" to "usr/local/lib/dextop-gaming/preload/i386/libdextop_unixsock.so",
    )

    private fun gamingResource(name: String) = requireNotNull(LinuxSteam::class.java.getResourceAsStream("/moe/n4tsu/dextop/linux/$name")) {
        "Game runtime resource $name is not packaged"
    }

    /** The x86 compatibility libraries must match the guest, not Android's ARM64 ABI. */
    private fun stageGaming(root: java.io.File) {
        gamingResources.forEach { (resource, relative) ->
            val bytes = gamingResource(resource).use { LinuxScript.resource(it.readBytes()) }
            java.io.File(root, relative).apply {
                parentFile?.mkdirs()
                if (!isFile || !readBytes().contentEquals(bytes)) {
                    // Existing games may still map this library. Replace the inode,
                    // never truncate a library which another process is executing.
                    val staged = java.io.File.createTempFile(name + ".", ".staged", parentFile)
                    try {
                        staged.writeBytes(bytes)
                        check(staged.setExecutable(true, false)) { "Cannot make $relative executable" }
                        java.nio.file.Files.move(staged.toPath(), toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    } finally {
                        staged.delete()
                    }
                }
                check(setExecutable(true, false)) { "Cannot make $relative executable" }
            }
        }
    }

    private val installer: String by lazy {
        requireNotNull(LinuxSteam::class.java.getResourceAsStream("/moe/n4tsu/dextop/linux/droiddeck-steam-install")) {
            "droiddeck-steam-install is not packaged"
        }.bufferedReader().use { LinuxScript.text(it.readText()) }
    }

    /** Libraries the client and its web helper load that minimal images lack. */
    private fun dependencies(apt: Boolean) = if (apt) listOf(
        "curl", "unzip", "python3", "zstd", "ca-certificates", "lsof", "util-linux", "libgtk2.0-0t64", "libibus-1.0-5", "libnm0", "libopenal1",
        "libpipewire-0.3-0t64", "libsdl2-2.0-0", "libnss3", "libxss1", "libgbm1", "libvulkan1",
        // The Steam mode's system bus and the stand-ins' GLib bindings.
        "dbus-daemon", "python3-gi", "gir1.2-glib-2.0", "libxtst6",
    ) else listOf("curl", "unzip", "python", "zstd", "ca-certificates", "lsof", "gtk2", "libibus", "libnm", "openal", "pipewire", "sdl2", "nss", "libxss", "vulkan-icd-loader",
        "dbus", "python-gobject", "gamescope", "libxtst")

    /** Debian 13 carries gamescope only in its backports (contrib); Ubuntu in its archive. */
    private val aptGamescope = """
        . /etc/os-release
        if [ "${'$'}ID" = debian ]; then
            echo "deb http://deb.debian.org/debian ${'$'}VERSION_CODENAME-backports main contrib" > /etc/apt/sources.list.d/dextop-backports.list
            apt-get update
            apt-get install -y --no-install-recommends -t "${'$'}VERSION_CODENAME-backports" gamescope
        else
            apt-get install -y --no-install-recommends gamescope
        fi
    """.trimIndent() + "\n"

    fun installScript(apt: Boolean, steamMode: Boolean = true, install: (List<String>) -> String): String = buildString {
        appendLine("set -e")
        append(install(dependencies(apt)))
        if (apt && steamMode) append(aptGamescope)
        // Own the managed installer without overwriting a runtime's custom DroidDeck copy.
        appendLine("{")
        appendLine("cat >$INSTALLER <<'DEXTOP_STEAM_INSTALL'")
        append(installer.trimEnd('\n')).append('\n')
        appendLine("DEXTOP_STEAM_INSTALL")
        appendLine("chmod 755 $INSTALLER; }")
        appendLine("HOME=/root bash $INSTALLER")
        gamingResources.forEach { (resource, relative) ->
            val encoded = gamingResource(resource).use { java.util.Base64.getEncoder().encodeToString(LinuxScript.resource(it.readBytes())) }
            appendLine("mkdir -p /${relative.substringBeforeLast('/')}")
            // Repair can run while a game still maps a compatibility library.
            // Preserve that inode until readers exit, just like stageGaming().
            appendLine("printf '%s' '$encoded' | base64 -d > /$relative.staged")
            appendLine("chmod 755 /$relative.staged")
            appendLine("mv -f /$relative.staged /$relative")
        }
        appendLine("HOME=/root /usr/local/bin/dextop-gaming install")
        appendLine("HOME=/root /usr/local/bin/dextop-proton-arm64 install")
        appendLine("cat >$LAUNCHER <<'DEXTOP_STEAM'")
        append(launcherScript)
        appendLine("DEXTOP_STEAM")
        appendLine("chmod 755 $LAUNCHER")
        appendLine("mkdir -p /root/.local/share/applications")
        appendLine("cat >/root/.local/share/applications/dextop-steam.desktop <<'DEXTOP_ENTRY'")
        append(desktopEntry)
        appendLine("DEXTOP_ENTRY")
        if (steamMode) append(steamModeScript())
    }

    /** Desktop entry of the Steam mode (Steam's Deck interface in gamescope). */
    const val STEAM_MODE_ENTRY = "dextop-steam-gamemode.desktop"
    private const val STEAM_MODE_LAUNCHER = "/usr/local/bin/dextop-steam-gamemode"
    private const val LOGIN1 = "/usr/local/lib/dextop-gaming/droiddeck-login1"
    /** The Steam mode's log under the guest's /tmp, read through ILinuxContainerService.guestLog. */
    const val STEAM_MODE_LOG = "dextop-steam-gamemode.log"
    /** Controllers kept for Android ("vendor product name"), read by libdextop_padgrab in the guest. */
    const val STEAM_PADS_STATE = "dextop-pads.conf"
    /** "width height" of the Steam Deck Mode window, which sizes gamescope's output and games. */
    const val STEAM_MODE_SIZE = "dextop-steam-size.conf"
    const val STEAM_MODE_RATE = "dextop-steam-rate.conf"
    const val STEAM_MODE_VSYNC = "dextop-steam-vsync.conf"
    /** Ends a running Steam mode: the launcher, the client with its web helpers and gamescope. */
    const val STEAM_MODE_STOP = "pkill -TERM -f '[d]extop-steam-gamemode'; pkill -TERM -f '[s]teamrtarm64/steam'; " +
        "pkill -TERM -f '[g]amescope --backend'; true"

    /**
     * Dextop's Steam mode, after DroidDeck's Steam session: a full-screen gamescope on Dextop's
     * compositor with an exposed Wayland and two Xwaylands, Steam's Deck interface, a system bus
     * with DroidDeck's logind and NetworkManager stand-ins, shutdown and reboot sentinels and a
     * restart on 42. The Steam setup step installs gamescope and the stand-ins' dependencies.
     * DEXTOP_WIDTH, DEXTOP_HEIGHT and DEXTOP_REFRESH come from the window that opens it. The log
     * carries DroidDeck's "== STEP" lines plus "== READY" once the Deck interface renders and
     * "== EXIT <status>" when the mode ends, which Dextop's Steam Deck Mode window shows.
     */
    private val steamModeLauncher = """
        #!/bin/bash
        set -u
        # Explicit experiment marker; retain the regular Debian launch path.
        if [ -f /.dextop-dd-stock-experiment ]; then
            exec /usr/local/bin/dextop-dd-stock-session "${'$'}@"
        fi
        # Debian puts gamescope under /usr/games.
        export HOME=/root USER=root PATH=/usr/local/bin:/usr/bin:/bin:/usr/games LANG=${'$'}{LANG:-C.UTF-8}
        export XDG_DATA_HOME=${'$'}HOME/.local/share XDG_CONFIG_HOME=${'$'}HOME/.config XDG_CACHE_HOME=${'$'}HOME/.cache
        export GLIBC_TUNABLES=glibc.pthread.rseq=0:glibc.malloc.top_pad=16777216 MESA_DISK_CACHE_DATABASE=1
        log=/tmp/$STEAM_MODE_LOG
        exited=/tmp/dextop-steam-gamemode.exited
        step() { echo "== STEP ${'$'}(date -u +%H:%M:%S) ${'$'}*"; }
        steam_root=${'$'}XDG_DATA_HOME/Steam

        if [ -z "${'$'}{DEXTOP_STEAM_INSIDE:-}" ]; then
            # Outside: one full-screen gamescope window on Dextop's compositor.
            exec >>"${'$'}log" 2>&1
            # The parent application display may be Xvfb. Its CPU-only WSI
            # fallback must not leak into gamescope's DRI3-capable Xwayland.
            unset DISPLAY DBUS_SESSION_BUS_ADDRESS MESA_VK_WSI_DEBUG LIBGL_ALWAYS_SOFTWARE
            export XDG_RUNTIME_DIR=/tmp/runtime-root XDG_SESSION_TYPE=wayland WAYLAND_DISPLAY=wayland-0
            mkdir -p "${'$'}XDG_RUNTIME_DIR" && chmod 700 "${'$'}XDG_RUNTIME_DIR"
            if [ -f ${LinuxCatalog.TURNIP_ICD} ]; then
                export VK_ICD_FILENAMES=${LinuxCatalog.TURNIP_ICD} VK_DRIVER_FILES=${LinuxCatalog.TURNIP_ICD}
            fi
            # The size of the Steam Deck Mode window, so gamescope is not resized to it later.
            width=${'$'}{DEXTOP_WIDTH:-1280}
            height=${'$'}{DEXTOP_HEIGHT:-720}
            [ -r /tmp/$STEAM_MODE_SIZE ] && read -r width height < /tmp/$STEAM_MODE_SIZE
            rate=${'$'}{DEXTOP_REFRESH:-60}
            # Only the Auto Deck launcher writes this opt-in cap. Ordinary Linux
            # output timing and non-Auto Deck sessions retain their own rate.
            requested_rate=
            [ -r /tmp/$STEAM_MODE_RATE ] && read -r requested_rate < /tmp/$STEAM_MODE_RATE
            [ "${'$'}requested_rate" = 30 ] && rate=30
            pacing=()
            [ "${'$'}requested_rate" = 30 ] && pacing=(--framerate-limit 30 -o 30)
            requested_vsync=1
            [ -r /tmp/$STEAM_MODE_VSYNC ] && read -r requested_vsync < /tmp/$STEAM_MODE_VSYNC
            if [ "${'$'}requested_vsync" = 0 ]; then
                pacing+=(--immediate-flips)
                export vblank_mode=0 __GL_SYNC_TO_VBLANK=0
            fi
            echo "== ${'$'}(date) Dextop Steam mode ${'$'}{width}x${'$'}{height}@${'$'}rate"
            step "Starting the display"
            echo "Display: ${'$'}{width}x${'$'}{height} @ ${'$'}rate Hz; Wayland + gamescope Xwayland"
            rm -f "${'$'}exited"
            # Match DroidDeck's Steam session, scoped to this process tree.
            export MESA_LOADER_DRIVER_OVERRIDE=zink GALLIUM_DRIVER=zink LIBGL_KOPPER_DRI2=true
            # DroidDeck SessionPrefs defaults. Inherit explicit user overrides;
            # apply only inside this Deck process tree, including launched games.
            export ZINK_DESCRIPTORS=${'$'}{ZINK_DESCRIPTORS:-lazy}
            export ZINK_DEBUG=${'$'}{ZINK_DEBUG:-compact}
            export mesa_glthread=${'$'}{mesa_glthread:-true}
            export MESA_NO_ERROR=${'$'}{MESA_NO_ERROR:-1}
            export TU_DEBUG=${'$'}{TU_DEBUG:-sysmem}
            export FEX_TSOENABLED=${'$'}{FEX_TSOENABLED-1}
            export FEX_VECTORTSOENABLED=${'$'}{FEX_VECTORTSOENABLED-0}
            export FEX_MEMCPYSETTSOENABLED=${'$'}{FEX_MEMCPYSETTSOENABLED-0}
            export FEX_HALFBARRIERTSOENABLED=${'$'}{FEX_HALFBARRIERTSOENABLED-0}
            export FEX_X87REDUCEDPRECISION=${'$'}{FEX_X87REDUCEDPRECISION-1}
            export FEX_MULTIBLOCK=${'$'}{FEX_MULTIBLOCK-1}
            export STEAM_MULTIPLE_XWAYLANDS=1
            audio_port=0
            [ -r /tmp/dextop-deck-audio.conf ] && read -r audio_port < /tmp/dextop-deck-audio.conf
            case "${'$'}audio_port" in ''|*[!0-9]*) audio_port=0 ;; esac
            if [ "${'$'}audio_port" -ge 1024 ] && [ "${'$'}audio_port" -le 65535 ]; then
                dextop_cookie=/tmp/dextop-deck-pulse.cookie
                (umask 077; sed -n '2p' /tmp/dextop-deck-audio.conf | base64 -d > "${'$'}dextop_cookie")
                export PULSE_COOKIE="${'$'}dextop_cookie"
            fi
            if [ "${'$'}audio_port" -ge 1024 ] && [ "${'$'}audio_port" -le 65535 ] &&
                PULSE_SERVER="tcp:127.0.0.1:${'$'}audio_port" timeout 2 pactl info >/dev/null 2>&1; then
                export PULSE_SERVER="tcp:127.0.0.1:${'$'}audio_port"
                echo "Deck audio: DirectAudio"
            else
                export PULSE_COOKIE=/root/.config/pulse/dextop-cookie
                echo "Deck audio: container fallback"
            fi
            export DEXTOP_STEAM_INSIDE=1 GAMESCOPE_FORCE_GENERAL_QUEUE=1 gamescope_cursor_composite=2
            /usr/local/bin/dextop-deck-gamescope --backend wayland --expose-wayland -f -W "${'$'}width" -H "${'$'}height" -w "${'$'}width" -h "${'$'}height" "${'$'}{pacing[@]}" \
                -r "${'$'}rate" -e --xwayland-count 2 --default-touch-mode 4 -- "${'$'}0" "${'$'}@"
            status=${'$'}?
            # gamescope that fails before its session starts still ends the mode visibly.
            [ -e "${'$'}exited" ] || echo "== EXIT ${'$'}status"
            exit "${'$'}status"
        fi

        # Like DroidDeck's session, Steam and its children use gamescope's Xwayland,
        # not the outer Dextop Wayland socket or the parent desktop toolkit policy.
        export WAYLAND_DISPLAY= XDG_SESSION_TYPE=x11
        export GDK_BACKEND=x11 QT_QPA_PLATFORM=xcb SDL_VIDEODRIVER=x11
        export MOZ_ENABLE_WAYLAND=0 ELECTRON_OZONE_PLATFORM_HINT=x11
        unset DEXTOP_WAYLAND_KGSL
        # GBM selects its DRI driver independently of GALLIUM_DRIVER. On KGSL,
        # its fallback exports no KMS handle, which breaks CEF's dma-buf export.
        if [ "${'$'}{GALLIUM_DRIVER:-}" = zink ]; then
            export MESA_LOADER_DRIVER_OVERRIDE=zink
        fi
        helpers=""
        trap 'rc=${'$'}?; [ -n "${'$'}helpers" ] && kill ${'$'}helpers 2>/dev/null; echo "== EXIT ${'$'}rc"; : >"${'$'}exited"' EXIT
        step "Checking the Steam client"
        # Returns at once when the client is installed; installs or repairs it otherwise.
        $INSTALLER || exit ${'$'}?
        export STEAM_RUNTIME=1 SDL_VIDEO_X11_DGAMOUSE=0
        export LD_LIBRARY_PATH=${'$'}steam_root/steamrtarm64:${'$'}steam_root/steamrtarm64/panorama${'$'}{LD_LIBRARY_PATH:+:${'$'}LD_LIBRARY_PATH}
        [ -r ${LinuxSessionCompatibility.GUEST_PRELOAD} ] || export LD_PRELOAD=$PRELOAD${'$'}{LD_PRELOAD:+:${'$'}LD_PRELOAD}
        # Controllers Steam opens are taken from Android, so they do not drive Android Auto too.
        export LD_PRELOAD=/usr/local/lib/dextop-gaming/preload/aarch64/libdextop_padgrab.so${'$'}{LD_PRELOAD:+:${'$'}LD_PRELOAD}
        # The desktop client still running would take the Deck interface's place; end it first.
        pkill -f 'steamrtarm64/steam' 2>/dev/null && sleep 2
        # A client that was killed leaves locks that stop the next one from starting.
        rm -f "${'$'}steam_root/.crash" "${'$'}steam_root/steam.pid" "${'$'}steam_root/config/htmlcache/Singleton"*

        # The Deck interface asks NetworkManager whether it is online and logind for sleep.
        step "Starting system services"
        # A socket marker can survive a crashed server under PRoot's socket bridge.
        # Give each Deck session its own address and pass it explicitly to every client.
        export DBUS_SYSTEM_BUS_ADDRESS="unix:path=/tmp/dextop-deck-system-${'$'}${'$'}.bus"
        mkdir -p /run/dbus /usr/local/share/dextop
        cat >/usr/local/share/dextop/system-bus.conf <<'BUSCONF'
        <!DOCTYPE busconfig PUBLIC "-//freedesktop//DTD D-Bus Bus Configuration 1.0//EN"
         "http://www.freedesktop.org/standards/dbus/1.0/busconfig.dtd">
        <busconfig>
          <type>system</type>
          <listen>unix:tmpdir=/tmp</listen>
          <auth>EXTERNAL</auth>
          <policy context="default">
            <allow user="*"/><allow own="*"/><allow send_destination="*"/><allow receive_sender="*"/>
          </policy>
        </busconfig>
        BUSCONF
        dbus-daemon --config-file=/usr/local/share/dextop/system-bus.conf --address="${'$'}DBUS_SYSTEM_BUS_ADDRESS" --nofork --nopidfile &
        helpers="${'$'}!"
        python3 - <<'DEXTOP_WAIT_BUS' || exit 1
        import time
        from gi.repository import Gio, GLib
        for attempt in range(50):
            try:
                Gio.bus_get_sync(Gio.BusType.SYSTEM, None)
                break
            except GLib.Error:
                time.sleep(0.1)
        else:
            raise SystemExit("Deck system bus did not become available")
        DEXTOP_WAIT_BUS
        export BL_LAUNCH_DIR=/tmp/dextop-steam-launch
        mkdir -p "${'$'}BL_LAUNCH_DIR" && rm -f "${'$'}BL_LAUNCH_DIR/steam-sleep-ready"
        $LOGIN1 --with-netmanager &
        helpers="${'$'}helpers ${'$'}!"
        for _ in ${'$'}(seq 1 50); do [ -f "${'$'}BL_LAUNCH_DIR/steam-sleep-ready" ] && break; sleep 0.1; done
        python3 - <<'DEXTOP_WAIT_NETWORK' || exit 1
        import time
        from gi.repository import Gio, GLib
        bus = Gio.bus_get_sync(Gio.BusType.SYSTEM, None)
        for attempt in range(50):
            try:
                bus.call_sync("org.freedesktop.NetworkManager", "/org/freedesktop/NetworkManager",
                    "org.freedesktop.DBus.ObjectManager", "GetManagedObjects", None,
                    GLib.VariantType.new("(a{oa{sa{sv}}})"), Gio.DBusCallFlags.NONE, 500, None)
                break
            except GLib.Error:
                time.sleep(0.1)
        else:
            raise SystemExit("Android network status service did not become available")
        DEXTOP_WAIT_NETWORK
        step "Connecting the Android Bluetooth service"
        python3 /usr/local/lib/dextop-gaming/dextop-bluez &
        helpers="${'$'}helpers ${'$'}!"
        python3 - <<'DEXTOP_WAIT_BLUETOOTH'
        import time
        from gi.repository import Gio, GLib
        bus = Gio.bus_get_sync(Gio.BusType.SYSTEM, None)
        for attempt in range(50):
            try:
                bus.call_sync("org.bluez", "/", "org.freedesktop.DBus.ObjectManager", "GetManagedObjects", None,
                    GLib.VariantType.new("(a{oa{sa{sv}}})"), Gio.DBusCallFlags.NONE, 500, None)
                break
            except GLib.Error:
                time.sleep(0.1)
        else:
            print("Android Bluetooth bridge is unavailable")
        DEXTOP_WAIT_BLUETOOTH
        # Keyboards and mice plugged into the phone go to gamescope's one X server, where Steam and
        # its games both run; controllers are read from /dev/input by SDL, as no udev runs here.
        step "Connecting keyboards, mice and controllers"
        python3 /usr/local/lib/dextop-gaming/dextop-deck-input &
        helpers="${'$'}helpers ${'$'}!"
        export SDL_JOYSTICK_DISABLE_UDEV=1
        # Android has evdev but no udev database; winebus needs controller metadata too.
        export DEXTOP_EVDEV_UDEV=1

        export STEAM_GAMESCOPE_NIS_SUPPORTED=1 STEAM_GAMESCOPE_FANCY_SCALING_SUPPORT=1
        export STEAMOS_STEAM_SHUTDOWN_SENTINEL=${'$'}HOME/.dextop-steam-shutdown
        export STEAMOS_STEAM_REBOOT_SENTINEL=${'$'}HOME/.dextop-steam-reboot
        # Follow DroidDeck's Steam/ANGLE options and webhelper preload defaults.
        # Chromium backend overrides can disable GPU composition on this CEF build.
        # Keep this policy inside Deck Mode and its child applications.
        export BL_WEBHELPER_ARGS="--no-zygote --in-process-gpu -AudioServiceOutOfProcess"
        # DroidDeck keeps a guest-only CEF inspection endpoint. The session
        # preload rejects connections without the guest socket marker.
        cdp_port=${'$'}((20000 + RANDOM % 30000))
        export BL_CDP_GUARD=${'$'}cdp_port
        printf '%s\n' "${'$'}cdp_port" >/tmp/dextop-steam-cdp-port
        retry=/tmp/dextop-steam-cef-retry
        steam_updates=0
        while :; do
            steam_channel=${'$'}(cat "${'$'}steam_root/package/beta" 2>/dev/null || true)
            /usr/local/bin/dextop-steam-state "${'$'}steam_root"
            rm -f "${'$'}STEAMOS_STEAM_SHUTDOWN_SENTINEL" "${'$'}STEAMOS_STEAM_REBOOT_SENTINEL" "${'$'}retry"
            cef=(-cef-force-gpu -cef-use-gl=angle -cef-use-angle=vulkan)
            [ "${'$'}{DEXTOP_STEAM_CEF_GPU:-}" = 0 ] && cef=(-cef-disable-gpu)
            [ "${'$'}{DEXTOP_STEAM_CEF_GPU:-}" = 1 ] && cef=(-cef-force-gpu -cef-use-gl=angle -cef-use-angle=vulkan)
            /usr/local/bin/dextop-proton-arm64 prepare
            step "Starting the Steam client"
            echo "CEF rendering: ${'$'}{cef[*]}; display: ${'$'}{DISPLAY:-unset}"
            [ -r /etc/droiddeck-network.json ] && python3 -c 'import json; s=json.load(open("/etc/droiddeck-network.json")); print("Android network: %s; connected=%s; validated=%s; strength=%s%%" % (s.get("sourceTransport",s.get("transport","unknown")),s.get("connected"),s.get("validated"),s.get("strength","unknown")))'
            : >"${'$'}steam_root/logs/cef_log.txt" 2>/dev/null
            # The Deck interface is up once its web helper renders a page.
            (
                for _ in ${'$'}(seq 1 900); do
                    if pgrep -f 'steamwebhelper.*--type=renderer' >/dev/null 2>&1; then
                        sleep 3
                        echo "== READY ${'$'}(date -u +%H:%M:%S)"
                        exit 0
                    fi
                    sleep 1
                done
            ) &
            watcher=${'$'}!
            steam_started=${'$'}SECONDS
            status=0
            # Match Deck's network/settings UI. The common runtime protocol bridge
            # unwraps SteamLinuxRuntime/FEX commands without Android mount namespaces.
            "${'$'}steam_root/steamrtarm64/steam" -gamepadui -steamdeck -steamos3 -clientbeta "${'$'}steam_channel" \
                -no-cef-sandbox -cef-ozone-platform=x11 -cef-enable-debugging -devtools-port "${'$'}cdp_port" "${'$'}{cef[@]}" "${'$'}@" || status=${'$'}?
            steam_ran=${'$'}((SECONDS - steam_started))
            kill "${'$'}watcher" 2>/dev/null
            echo "== steam exited ${'$'}status"
            [ -e "${'$'}retry" ] && { sleep 1; continue; }
            # Shut Down ends the mode; Restart and an update (42) start the client again.
            [ -e "${'$'}STEAMOS_STEAM_SHUTDOWN_SENTINEL" ] && exit 0
            [ -e "${'$'}STEAMOS_STEAM_REBOOT_SENTINEL" ] || [ "${'$'}status" -eq 42 ] || exit "${'$'}status"
            if [ "${'$'}status" -eq 42 ]; then
                [ "${'$'}steam_ran" -lt 60 ] || steam_updates=0
                steam_updates=${'$'}((steam_updates + 1))
                if [ "${'$'}steam_updates" -ge 3 ]; then
                    echo 'Steam repeatedly requested an update restart; stopped the loop.' >&2
                    exit 1
                fi
            fi
            set --
        done
    """.trimIndent() + "\n"

    private val steamModeEntry = listOf(
        "[Desktop Entry]", "Type=Application", "Name=Steam Deck Mode", "Exec=$STEAM_MODE_LAUNCHER",
        "Icon=steam", "Categories=Game;",
    ).joinToString("\n", postfix = "\n")

    /** Shell lines that write the Steam mode; part of the Steam setup step. */
    private fun steamModeScript(): String = buildString {
        appendLine("cat >$STEAM_MODE_LAUNCHER <<'DEXTOP_STEAM_MODE'")
        append(steamModeLauncher)
        appendLine("DEXTOP_STEAM_MODE")
        appendLine("chmod 755 $STEAM_MODE_LAUNCHER")
        appendLine("cat >/root/.local/share/applications/$STEAM_MODE_ENTRY <<'DEXTOP_ENTRY'")
        append(steamModeEntry)
        appendLine("DEXTOP_ENTRY")
    }

    /** Keeps the Steam mode current in containers whose Steam setup installed gamescope. */
    private fun stageSteamMode(root: java.io.File) {
        // gamescope may be a symlink that only resolves inside the guest.
        if (listOf("usr/bin/gamescope", "usr/games/gamescope").none {
                java.nio.file.Files.exists(java.io.File(root, it).toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)
            }) return
        java.io.File(root, STEAM_MODE_LAUNCHER.removePrefix("/")).apply {
            parentFile?.mkdirs()
            if (!isFile || readText() != steamModeLauncher) writeText(steamModeLauncher)
            check(setExecutable(true, false)) { "Cannot make the Steam mode launcher executable" }
        }
        java.io.File(root, "root/.local/share/applications/$STEAM_MODE_ENTRY").apply {
            parentFile?.mkdirs()
            if (!isFile || readText() != steamModeEntry) writeText(steamModeEntry)
        }
    }

    /** Desktop entry Plasma launches to open the DroidDeck desktop; hidden from the launcher. */
    const val DROIDDECK_DESKTOP_ENTRY = "dextop-droiddeck-desktop.desktop"
    private const val DROIDDECK_DESKTOP_LAUNCHER = "/usr/local/bin/dextop-droiddeck-desktop"

    /**
     * DroidDeck's desktop mode: SessionService starts `droiddeck-session lxqt`
     * with the guest environment below (addClientEnvironment), and the session
     * execs droiddeck-desktop (Plasma, KWin nested through kwin_wayland_wrapper)
     * on wayland-0, which Dextop's compositor serves. The socket shim's
     * LD_PRELOAD stays, so the environment is not cleared with env -i.
     */
    private val droidDeckDesktopLauncher = """
        #!/bin/sh
        unset DISPLAY DBUS_SESSION_BUS_ADDRESS MESA_LOADER_DRIVER_OVERRIDE GALLIUM_DRIVER
        export HOME=/root USER=root PATH=/usr/local/bin:/usr/bin:/bin TERM=xterm-256color LANG=C.UTF-8
        export XDG_RUNTIME_DIR=/tmp/runtime-root XDG_SESSION_TYPE=wayland WAYLAND_DISPLAY=wayland-0
        export GAMESCOPE_FORCE_GENERAL_QUEUE=1 MESA_LOADER_DRIVER_OVERRIDE=zink GALLIUM_DRIVER=zink LIBGL_KOPPER_DRI2=true
        [ -f ${LinuxCatalog.TURNIP_ICD} ] && export VK_ICD_FILENAMES=${LinuxCatalog.TURNIP_ICD}
        export MESA_DISK_CACHE_DATABASE=1 GLIBC_TUNABLES=glibc.pthread.rseq=0:glibc.malloc.top_pad=16777216
        export BL_WIDTH=${'$'}{DEXTOP_WIDTH:-1280} BL_HEIGHT=${'$'}{DEXTOP_HEIGHT:-720} BL_FPS=0 BL_REFRESH=60
        mkdir -p "${'$'}XDG_RUNTIME_DIR" && chmod 700 "${'$'}XDG_RUNTIME_DIR"
        exec /usr/local/bin/droiddeck-session lxqt
    """.trimIndent() + "\n"

    private val droidDeckDesktopEntry = listOf(
        "[Desktop Entry]", "Type=Application", "Name=DroidDeck Desktop", "Exec=$DROIDDECK_DESKTOP_LAUNCHER",
        "Icon=plasma", "NoDisplay=true",
    ).joinToString("\n", postfix = "\n")

    /** Writes the launcher and its entry from outside the guest; staged before every session. */
    fun writeDroidDeckDesktop(root: java.io.File) {
        java.io.File(root, DROIDDECK_DESKTOP_LAUNCHER.removePrefix("/")).apply {
            parentFile?.mkdirs()
            writeText(droidDeckDesktopLauncher)
            setExecutable(true, false)
        }
        java.io.File(root, "root/.local/share/applications/$DROIDDECK_DESKTOP_ENTRY").apply {
            parentFile?.mkdirs()
            writeText(droidDeckDesktopEntry)
        }
    }

    fun droidDeckDesktopScript(): String = buildString {
        appendLine("set -e")
        appendLine("cat >$DROIDDECK_DESKTOP_LAUNCHER <<'DEXTOP_DESKTOP'")
        append(droidDeckDesktopLauncher)
        appendLine("DEXTOP_DESKTOP")
        appendLine("chmod 755 $DROIDDECK_DESKTOP_LAUNCHER")
        appendLine("mkdir -p /root/.local/share/applications")
        appendLine("cat >/root/.local/share/applications/$DROIDDECK_DESKTOP_ENTRY <<'DEXTOP_ENTRY'")
        append(droidDeckDesktopEntry)
        appendLine("DEXTOP_ENTRY")
    }

    /**
     * DroidDeck's runtime builds Mesa with Turnip for KGSL; Dextop's Turnip
     * manifest points at that driver instead of installing a GPU package.
     */
    fun runtimeTurnipScript(): String = """
        set -e
        for driver in /usr/lib/libvulkan_freedreno.so /usr/lib/aarch64-linux-gnu/libvulkan_freedreno.so; do
            [ -f "${'$'}driver" ] || continue
            mkdir -p ${'$'}(dirname ${LinuxCatalog.TURNIP_ICD})
            printf '{"file_format_version": "1.0.0", "ICD": {"library_path": "%s", "api_version": "1.4.0"}}\n' "${'$'}driver" >${LinuxCatalog.TURNIP_ICD}
            exit 0
        done
        echo "The runtime has no Turnip driver; using VirGL"
    """.trimIndent() + "\n"
}
