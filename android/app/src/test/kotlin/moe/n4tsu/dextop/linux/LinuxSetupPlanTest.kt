package moe.n4tsu.dextop.linux

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LinuxSetupPlanTest {
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
        assertContains(script, "LIBGL_ALWAYS_SOFTWARE=1")
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
