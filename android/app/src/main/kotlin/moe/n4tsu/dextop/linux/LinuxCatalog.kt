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
                xvfb x11-xserver-utils xauth icewm xterm dbus-x11 pulseaudio pulseaudio-utils \
                fonts-dejavu-core xdg-utils ca-certificates
        """.trimIndent() + "\n"
        LinuxPackageFamily.PACMAN -> """
            set -e
            pacman -S --noconfirm --needed \
                xorg-server-xvfb xorg-xrdb xorg-xauth icewm xterm dbus pulseaudio \
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
    fun launchScript(display: Int, command: String, wayland: Boolean = false): String = """
        export DISPLAY=:$display XDG_RUNTIME_DIR=/tmp/runtime-root
        export DBUS_SESSION_BUS_ADDRESS=unix:abstract=/tmp/dextop-dbus-$display
        [ ! -f /etc/dextop/session.env ] || . /etc/dextop/session.env
        $RENDERER_OVERRIDE
        ${if (wayland) WAYLAND_ENVIRONMENT else ""}
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
     * Containers set up before the renderer setting existed export
     * LIBGL_ALWAYS_SOFTWARE in session.env, which would hide VirGL.
     */
    private val RENDERER_OVERRIDE =
        "case \"${'$'}GALLIUM_DRIVER\" in virpipe|zink) unset LIBGL_ALWAYS_SOFTWARE ;; esac"

    /**
     * GPU flags for Chromium-based browsers by renderer. On VirGL they draw
     * no text when they rasterize on the GPU (glyph atlases), so only
     * compositing and WebGL use it. On Turnip, ANGLE drives Vulkan directly:
     * through Zink the GPU process fails (calibrated timestamps).
     */
    val CHROMIUM_VIRGL_FLAGS = "case \"${'$'}GALLIUM_DRIVER\" in " +
        "virpipe) set -- --disable-gpu-rasterization \"${'$'}@\" ;; " +
        "zink) set -- --ozone-platform=x11 --use-angle=vulkan --enable-features=Vulkan,DefaultANGLEVulkan,VulkanFromANGLE \"${'$'}@\" ;; esac"

    /** Puts the current [CHROMIUM_VIRGL_FLAGS] into browser launchers written by earlier versions. */
    private val BROWSER_LAUNCHER_MIGRATION = """
        for launcher in /usr/local/bin/dextop-chrome /usr/local/bin/dextop-chromium; do
            [ -f "${'$'}launcher" ] && ! grep -q -- '--ozone-platform=x11' "${'$'}launcher" && {
                sed -i '/disable-gpu-rasterization/d; /DefaultANGLEVulkan/d' "${'$'}launcher"
                sed -i '/^exec /i ${CHROMIUM_VIRGL_FLAGS.replace("'", "'\\''")}' "${'$'}launcher"
            }
        done
        if [ -f /usr/local/bin/dextop-firefox ] && ! grep -q MOZ_DISABLE_GPU_SANDBOX /usr/local/bin/dextop-firefox; then
            sed -i 's/^export MOZ_DISABLE_CONTENT_SANDBOX=1 MOZ_DISABLE_RDD_SANDBOX=1${'$'}/$FIREFOX_SANDBOX_ENVIRONMENT/' /usr/local/bin/dextop-firefox
        fi
    """.trimIndent()

    /**
     * Firefox's seccomp sandboxes crash its child processes under PRoot on
     * newer kernels (Linux 6.12), so every one of them stays off.
     */
    const val FIREFOX_SANDBOX_ENVIRONMENT = "export MOZ_DISABLE_CONTENT_SANDBOX=1 MOZ_DISABLE_RDD_SANDBOX=1 " +
        "MOZ_DISABLE_SOCKET_PROCESS_SANDBOX=1 MOZ_DISABLE_GMP_SANDBOX=1 MOZ_DISABLE_UTILITY_SANDBOX=1 MOZ_DISABLE_GPU_SANDBOX=1"

    /**
     * Toolkits prefer Dextop's Wayland compositor (GPU buffers through
     * dma-buf) and fall back to X11.
     */
    private val WAYLAND_ENVIRONMENT = "export WAYLAND_DISPLAY=${LinuxAppSession.WAYLAND_DISPLAY} " +
        "GDK_BACKEND=wayland,x11 GTK_CSD=0 QT_QPA_PLATFORM='wayland;xcb' SDL_VIDEODRIVER=wayland,x11 " +
        "MOZ_ENABLE_WAYLAND=1 ELECTRON_OZONE_PLATFORM_HINT=auto\n" +
        "export DEXTOP_WAYLAND_KGSL=1 MESA_LOADER_DRIVER_OVERRIDE=zink\n" +
        // X11 GLX clients launched from a Wayland terminal still need XPutImage presentation.
        "export LIBGL_KOPPER_DRI2=true MESA_VK_WSI_DEBUG=sw\n" +
        "export __EGL_VENDOR_LIBRARY_FILENAMES=/opt/dextop-mesa/share/glvnd/egl_vendor.d/50_mesa.json\n" +
        "export LD_LIBRARY_PATH=/opt/dextop-mesa/lib/aarch64-linux-gnu${'$'}{LD_LIBRARY_PATH:+:${'$'}LD_LIBRARY_PATH}"

    private fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
