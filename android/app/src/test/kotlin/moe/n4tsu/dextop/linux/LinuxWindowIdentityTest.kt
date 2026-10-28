package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertTrue

class LinuxWindowIdentityTest {
    @Test fun aBrowserPageCannotClaimAnotherApplicationsLaunch() {
        assertTrue(LinuxWindowIdentity.score("firefox-esr", "Blender", "blender.desktop", "Blender") < 2)
        assertTrue(LinuxWindowIdentity.score("firefox-esr", "Claude", "com.anthropic.Claude.desktop", "Claude") < 2)
        assertTrue(LinuxWindowIdentity.score("", "Blender", "blender.desktop", "Blender") < 2)
    }

    @Test fun sharedOfficeBrandDoesNotClaimAnUnrelatedModule() {
        assertTrue(LinuxWindowIdentity.score("libreoffice-writer", "Untitled 1 - LibreOffice Writer",
            "libreoffice-base.desktop", "LibreOffice Base") < 2)
        assertTrue(LinuxWindowIdentity.score("libreoffice-writer", "Untitled 1 - LibreOffice Writer",
            "libreoffice-writer.desktop", "LibreOffice Writer") >= 3)
    }

    @Test fun desktopDeclaredClassWorksWhenTheExecutableHasAnotherName() {
        assertTrue(LinuxWindowIdentity.score("Soffice", "Untitled", "office-suite.desktop", "Office", "Soffice") >= 4)
        assertTrue(LinuxWindowIdentity.score("foreign-app", "Document", "editor.desktop", "Editor") == 0.0)
    }
}
