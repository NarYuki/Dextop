package moe.n4tsu.dextop.linux

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LinuxSetupPlanTest {
    @Test fun hardwareLaunchEnablesToolkitRenderersWithoutForcingCairo() {
        val script = LinuxCatalog.launchScript(10, "client")
        assertContains(script, "unset WAYLAND_DISPLAY DEXTOP_WAYLAND_KGSL MESA_LOADER_DRIVER_OVERRIDE")
        assertContains(script, "zink|virpipe)")
        assertContains(script, "GSK_RENDERER=\${GSK_RENDERER:-gl}")
        assertContains(script, "MOZ_WEBRENDER=\${MOZ_WEBRENDER:-1}")
        assertContains(script, "MOZ_ACCELERATED=\${MOZ_ACCELERATED:-1}")
        assertContains(script, "QSG_RHI_BACKEND=\${QSG_RHI_BACKEND:-opengl}")
        assertContains(script, "QSG_RHI_PREFER_SOFTWARE_RENDERER=0")
        assertContains(script, "QT_WIDGETS_RHI=\${QT_WIDGETS_RHI:-1}")
        assertFalse("GSK_RENDERER:-cairo" in script)
    }
    @Test fun waylandInstallsXwaylandAndStartsItForDesktopAndApps() {
        assertContains(scripts(debian, LinuxSetupOptions(displayBackend = "wayland")), "xwayland libwayland-client0")
        assertContains(scripts(LinuxCatalog.find("archlinux")!!, LinuxSetupOptions(displayBackend = "wayland")), "xorg-xwayland wayland")
        for (script in listOf(
            LinuxCatalog.appsScript(98, 640, 480, 160, xwayland = true),
            LinuxCatalog.desktopScript(98, 640, 480, 160, "icewm", xwayland = true),
        )) {
            assertContains(script, "WAYLAND_DISPLAY=wayland-x11-98 env -u LD_LIBRARY_PATH -u LIBGL_DRIVERS_PATH")
            assertContains(script, "Xwayland :\$D -nolock")
            assertContains(script, "-geometry 640x480")
            assertContains(script, "-shm -noreset -nolisten tcp -ac")
            assertFalse("Xvfb :" in script)
            assertFalse("xrandr --output screen" in script)
        }
        assertContains(LinuxCatalog.appsScript(98, 640, 480, 160), "Xvfb :")
        assertContains(LinuxCatalog.appsScript(98, 640, 480, 160), "Xvfb :\$D -nolock")
    }

    @Test fun displayBackendDefaultsToXvfbAndRejectsInvalidValues() {
        assertTrue(LinuxSetupOptions.fromJson(null).displayBackend == "xvfb")
        assertFailsWith<IllegalArgumentException> { LinuxSetupOptions(displayBackend = "wayland; id") }
    }

    @Test fun waylandSetupInstallsToolkitBackendsAndKeepsX11Compatibility() {
        for (distro in listOf(debian, LinuxCatalog.find("archlinux")!!)) {
            val script = scripts(distro, LinuxSetupOptions(displayBackend = "wayland"))
            assertContains(script, if (distro.family == LinuxPackageFamily.APT) "qtwayland5 qt6-wayland" else "qt5-wayland qt6-wayland")
            assertContains(script, "wayland >/etc/dextop/display-backend")
            // X11-only clients still use Xvfb, so its Vulkan capability policy remains necessary.
            assertContains(script, "xvfb >${LinuxSessionCompatibility.WSI_CONFIG}")
        }
        val x11 = scripts(debian, LinuxSetupOptions())
        assertContains(x11, "xvfb >/etc/dextop/display-backend")
        assertFalse("qtwayland5" in x11)
    }

    @Test fun waylandWithoutKgslEglDoesNotForceMissingVendorLibrary() {
        val script = LinuxCatalog.launchScript(10, "client", wayland = true, waylandGpu = false)
        assertContains(script, "GDK_BACKEND=wayland,x11")
        assertContains(script, "QT_QPA_PLATFORM='wayland;xcb'")
        assertFalse("export __EGL_VENDOR_LIBRARY_FILENAMES" in script)
        assertFalse("export LD_LIBRARY_PATH=/opt/dextop-mesa/lib/aarch64-linux-gnu" in script)
        val x11 = LinuxCatalog.launchScript(10, "client")
        assertContains(x11, "unset WAYLAND_DISPLAY")
        assertContains(x11, "GDK_BACKEND=x11")
    }

    @Test fun minimalAndSteamInstallersPersistTheSharedXvfbConfiguration() {
        for (options in listOf(LinuxSetupOptions(), LinuxSetupOptions(apps = setOf("steam")))) {
            val script = scripts(debian, options)
            assertContains(script, "cat >/etc/dextop/session.env")
            assertContains(script, "printf '%s\\n' xvfb >${LinuxSessionCompatibility.WSI_CONFIG}")
        }
    }
    @Test fun gpuPackageIsVerifiedAndNeverBuiltOnTheDevice() {
        val script = LinuxCatalog.turnipScript()
        val gpu = LinuxCatalog.GPU_PACKAGE
        assertContains(script, "${gpu.sha256}  package.zip\" | sha256sum -c -")
        assertTrue(script.indexOf("sha256sum -c") < script.indexOf("unzip -q"))
        assertContains(script, LinuxCatalog.TURNIP_ICD)
        assertFalse(script.contains("meson") || script.contains("ninja"))
        // Debian's Mesa 25.0 Zink cannot present to Xvfb; backports' Mesa 26 can.
        assertContains(script, "-t \"\$VERSION_CODENAME-backports\"")
        assertContains(script, "mesa-libgallium libgl1-mesa-dri libglx-mesa0")
        // Without Mesa's KGSL GL/EGL in the package, Wayland GPU presentation stays off.
        assertFalse(script.contains(LinuxCatalog.TURNIP_WAYLAND_READY))
        val withMesa = LinuxCatalog.turnipScript(gpu.copy(mesa = "mesa"))
        assertContains(withMesa, "cp -a mesa/. /opt/dextop-mesa/")
        assertTrue(withMesa.indexOf("touch ${LinuxCatalog.TURNIP_WAYLAND_READY}") > withMesa.indexOf("cp ${gpu.driver}"))
    }

    @Test fun steamComesFromValveWithDroidDecksInstallerAndPreload() {
        val script = scripts(debian, LinuxSetupOptions(apps = setOf("steam")))
        assertContains(script, "steam_client_\${steam_channel}_linuxarm64")
        assertContains(script, "sha256sum -c --quiet")
        assertContains(script, "libdextop_blsession.so")
        assertContains(script, "-no-cef-sandbox -cef-ozone-platform=x11")
        val runtime = LinuxCatalog.find(LinuxCatalog.DROIDDECK_RUNTIME)!!
        assertTrue(runtime.image!!.url.endsWith(".tar.zst"))
        assertContains(scripts(runtime, LinuxSetupOptions(graphics = true)), "libvulkan_freedreno.so")
    }

    @Test fun turnipCoversAdreno6To8() {
        assertTrue(LinuxCatalog.turnipSupports("Adreno750v2"))
        assertTrue(LinuxCatalog.turnipSupports("Adreno840v2"))
        assertTrue(LinuxCatalog.turnipSupports("Adreno619"))
        assertFalse(LinuxCatalog.turnipSupports("Adreno530"))
        assertFalse(LinuxCatalog.turnipSupports(null))
    }
    @Test fun waylandLaunchOverridesLegacySessionAndKeepsX11PresentationAvailable() {
        val wayland = LinuxCatalog.launchScript(10, "wezterm start", wayland = true)
        assertContains(wayland, "export WAYLAND_DISPLAY=wayland-0")
        assertContains(wayland, "unset LIBGL_KOPPER_DRI2 MESA_VK_WSI_DEBUG")
        assertContains(wayland, "export LIBGL_DRIVERS_PATH=${LinuxGpuRuntime.LIB}/dri")
        assertContains(wayland, "__EGL_VENDOR_LIBRARY_FILENAMES=/opt/dextop-mesa/")
        assertContains(wayland, "MESA_LOADER_DRIVER_OVERRIDE=zink")
        assertTrue(wayland.indexOf("export WAYLAND_DISPLAY") > wayland.indexOf(". /etc/dextop/session.env"))
        val x11 = LinuxCatalog.launchScript(10, "xterm")
        assertFalse(x11.contains("export WAYLAND_DISPLAY"))
        assertFalse(x11.contains("unset MESA_VK_WSI_DEBUG"))
    }
    private val debian = LinuxCatalog.find("debian")!!
    private fun scripts(distro: LinuxDistro, options: LinuxSetupOptions) =
        LinuxSetupPlan.steps(distro, options, turnip = true).joinToString("\n") { it.script }

    @Test fun rejectsShellFragmentsAndUnknownOptions() {
        assertFailsWith<IllegalArgumentException> { LinuxSetupOptions(locale = "ja_JP; touch /tmp/injected") }
        assertFailsWith<IllegalArgumentException> { LinuxSetupOptions(ime = "$(id)") }
        assertFailsWith<IllegalArgumentException> { LinuxSetupOptions(apps = setOf("unknown")) }
    }

    @Test fun preventsUnsupportedBrowserPackagesBeforeInstallation() {
        val ubuntu = LinuxCatalog.find("ubuntu")!!
        val arch = LinuxCatalog.find("archlinux")!!
        assertFailsWith<IllegalArgumentException> { LinuxSetupOptions(apps = setOf("firefox")).validate(ubuntu) }
        assertFailsWith<IllegalArgumentException> { LinuxSetupOptions(apps = setOf("chromium")).validate(ubuntu) }
        assertFailsWith<IllegalArgumentException> { LinuxSetupOptions(apps = setOf("chrome")).validate(arch) }
        assertContains(scripts(ubuntu, LinuxSetupOptions(apps = setOf("chrome"))), "google-chrome-stable_current_arm64.deb")
    }

    @Test fun gpusOtherThanAdrenoSkipTurnip() {
        val options = LinuxSetupOptions(graphics = true)
        assertTrue(LinuxSetupPlan.steps(debian, options, turnip = true).any { it.phase == "gpu" })
        assertFalse(LinuxSetupPlan.steps(debian, options, turnip = false).any { it.phase == "gpu" })
    }

    @Test fun minimalSetupDoesNotInstallOptionalDesktopOrBrowserPackages() {
        val plan = LinuxSetupPlan.steps(debian, LinuxSetupOptions(cjkFonts = false), turnip = true)
        assertFalse(plan.any { it.phase in setOf("input", "desktop", "graphics", "applications") })
        val script = plan.joinToString("\n") { it.script }
        assertFalse(script.contains("fonts-noto-cjk"))
        assertContains(script, "fonts-noto-core")
        assertFalse(script.contains("fcitx5 -d"))
    }

    @Test fun japaneseSetupInstallsAndActivatesInputMethodAndSessionLocale() {
        val script = scripts(debian, LinuxSetupOptions(locale = "ja_JP", ime = "mozc", cjkFonts = false, graphics = true))
        assertContains(script, "fcitx5-mozc")
        assertContains(script, "fonts-noto-cjk")
        assertContains(script, "DefaultIM=mozc")
        assertContains(script, "GTK_IM_MODULE=fcitx")
        assertContains(script, "export LANG=ja_JP.UTF-8")
        // The renderer follows the Linux settings (LinuxGraphics), not session.env.
        assertFalse("LIBGL_ALWAYS_SOFTWARE" in script)
        assertContains(script, "profile.before-dextop")
        assertContains(script, "\nDEXTOP_ENV\n")
        assertContains(LinuxCatalog.desktopScript(10, 1280, 720, 160, "icewm"), ". /etc/dextop/session.env")
        assertContains(LinuxCatalog.appsScript(11, 1280, 720, 160), "sh /etc/dextop/start-session.sh")
        // Fake-root clients must reach the session bus (Firefox remoting, xdg-open).
        assertContains(LinuxCatalog.appsScript(11, 1280, 720, 160), "<auth>ANONYMOUS</auth>")
        // Real-uid sessions reach package managers and sudo through fakeroot.
        assertContains(LinuxCatalog.appsScript(11, 1280, 720, 160), "command -v fakeroot-tcp")
        assertContains(LinuxCatalog.appsScript(11, 1280, 720, 160), "'pref(\"browser.shell.checkDefaultBrowser\", false);'")
        // Applications wait for the session bus, or Firefox cannot take over later launches.
        assertTrue(LinuxCatalog.launchScript(11, "firefox").indexOf("@/tmp/dextop-dbus-11") <
            LinuxCatalog.launchScript(11, "firefox").indexOf("sh -c \"\$cmd\""))
        assertContains(LinuxCatalog.launchScript(11, "xterm"), ". /etc/dextop/session.env")
    }

    @Test fun browserLaunchersPreserveArgumentsAndAvoidSnap() {
        val script = scripts(debian, LinuxSetupOptions(apps = setOf("firefox", "chromium", "chrome")))
        assertContains(script, "--no-sandbox --disable-dev-shm-usage \"\$@\"")
        assertContains(script, "MOZ_DISABLE_CONTENT_SANDBOX=1")
        assertContains(script, "libavcodec-extra")
        assertContains(script, "Exec=/usr/local/bin/dextop-chrome %U")
        assertFalse(script.contains("snap install"))
        assertContains(script, "--ozone-platform=x11")
        assertContains(LinuxCatalog.CHROMIUM_VIRGL_FLAGS, "--use-angle=gl")
        assertContains(LinuxCatalog.CHROMIUM_VIRGL_FLAGS, "--enable-gpu-rasterization")
        assertFalse(LinuxCatalog.CHROMIUM_VIRGL_FLAGS.contains("--use-angle=vulkan"))
    }

    @Test fun generatesScriptsForAllSupportedDistributionsAndInputMethods() {
        val directory = System.getProperty("dextop.setup.scriptDirectory")?.let { File(it).apply { mkdirs() } }
        for (distro in LinuxCatalog.distros) for (ime in listOf("none", "mozc", "pinyin", "hangul")) {
            val apps = if (distro.id == LinuxCatalog.DROIDDECK_RUNTIME) setOf("steam")
                else if (distro.id == "ubuntu") setOf("chrome", "files", "editor", "office")
                else if (distro.family == LinuxPackageFamily.PACMAN) setOf("firefox", "chromium", "files", "editor", "office", "steam")
                else LinuxSetupOptions.APPS
            val plan = LinuxSetupPlan.steps(distro, LinuxSetupOptions("complete", "ja_JP", true, ime, true, true, apps), turnip = true)
            assertTrue(plan.last().phase == "integration")
            plan.forEachIndexed { i, step -> directory?.let {
                File(it, "${distro.id}-$ime-$i.sh").writeText(step.script)
            } }
        }
    }
}
