package moe.n4tsu.dextop.privilege

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class EmbeddedServerPayloadTest {
    @Test fun launchUsesImmutableLibraryAndReportsTheStartedPid() {
        val command = EmbeddedServerPayload.command("/data/app/base.apk", "moe.n4tsu.dextop",
            "/data/app/lib/arm64/libdextop_input.so", "secret")
        assertContains(command, "printf '%s\\n' 'secret' | CLASSPATH=")
        assertContains(command, "'/data/app/lib/arm64/libdextop_input.so'")
        assertContains(command, "echo DEXTOP_RUNTIME_PID=$!")
        assertFalse(command.substringAfter("app_process").contains("secret"))
        assertFalse(command.contains("unzip"))
    }
}
