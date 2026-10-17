package moe.n4tsu.dextop.linux

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LinuxSetupPlanTest {
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
        assertContains(wayland, "LIBGL_KOPPER_DRI2=true MESA_VK_WSI_DEBUG=sw")
        assertContains(wayland, "__EGL_VENDOR_LIBRARY_FILENAMES=/opt/dextop-mesa/")
        assertContains(wayland, "MESA_LOADER_DRIVER_OVERRIDE=zink")
        assertTrue(wayland.indexOf("export WAYLAND_DISPLAY") > wayland.indexOf(". /etc/dextop/session.env"))
        val x11 = LinuxCatalog.launchScript(10, "xterm")
        assertFalse(x11.contains("export WAYLAND_DISPLAY"))
        assertFalse(x11.contains("unset MESA_VK_WSI_DEBUG"))
    }
    private val debian = LinuxCatalog.find("debian")!!
    private fun scripts(distro: LinuxDistro, options: LinuxSetupOptions) =
        LinuxSetupPlan.steps(distro, options).joinToString("\n") { it.script }

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

    @Test fun minimalSetupDoesNotInstallOptionalDesktopOrBrowserPackages() {
        val plan = LinuxSetupPlan.steps(debian, LinuxSetupOptions(cjkFonts = false))
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
        assertContains(LinuxCatalog.launchScript(11, "xterm"), ". /etc/dextop/session.env")
    }

    @Test fun browserLaunchersPreserveArgumentsAndAvoidSnap() {
        val script = scripts(debian, LinuxSetupOptions(apps = setOf("firefox", "chromium", "chrome")))
        assertContains(script, "--no-sandbox --disable-dev-shm-usage \"\$@\"")
        assertContains(script, "MOZ_DISABLE_CONTENT_SANDBOX=1")
        assertContains(script, "Exec=/usr/local/bin/dextop-chrome %U")
        assertFalse(script.contains("snap install"))
        assertContains(script, "--ozone-platform=x11 --use-angle=vulkan")
    }

    @Test fun generatesScriptsForAllSupportedDistributionsAndInputMethods() {
        val directory = System.getProperty("dextop.setup.scriptDirectory")?.let { File(it).apply { mkdirs() } }
        for (distro in LinuxCatalog.distros) for (ime in listOf("none", "mozc", "pinyin", "hangul")) {
            val apps = if (distro.id == "ubuntu") setOf("chrome", "files", "editor", "office")
                else if (distro.id == "archlinux") setOf("firefox", "chromium", "files", "editor", "office")
                else LinuxSetupOptions.APPS
            val plan = LinuxSetupPlan.steps(distro, LinuxSetupOptions("complete", "ja_JP", true, ime, true, true, apps))
            assertTrue(plan.last().phase == "integration")
            plan.forEachIndexed { i, step -> directory?.let {
                File(it, "${distro.id}-$ime-$i.sh").writeText(step.script)
            } }
        }
    }
}
