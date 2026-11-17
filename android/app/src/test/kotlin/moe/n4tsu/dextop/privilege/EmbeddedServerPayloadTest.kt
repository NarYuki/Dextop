package moe.n4tsu.dextop.privilege

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class EmbeddedServerPayloadTest {
    @Test fun launchUsesImmutableLibraryAndReportsTheStartedPid() {
        val command = EmbeddedServerPayload.command("/data/app/base.apk", "moe.n4tsu.dextop",
            "/data/app/lib/arm64/libdextop_input.so")
        // The key arrives on stdin and passes through a builtin: it is in no process's argv.
        assertContains(command, "IFS= read -r key; echo \"\$key\" | CLASSPATH=")
        assertContains(command, "'/data/app/lib/arm64/libdextop_input.so'")
        assertContains(command, "echo DEXTOP_RUNTIME_PID=$!")
        assertFalse(command.contains("unzip"))
    }
}
