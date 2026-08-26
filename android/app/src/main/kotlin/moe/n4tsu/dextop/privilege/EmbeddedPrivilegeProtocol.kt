package moe.n4tsu.dextop.privilege

internal object EmbeddedPrivilegeProtocol {
    const val descriptor = "moe.n4tsu.dextop.privilege.RUNTIME"
    const val transactService = 0x44585001
    const val executeCommand = 0x44585002
    /** Forwards Dextop's private privileged-input AIDL service through the bundled runtime. */
    const val transactInputService = 0x44585003
    const val providerMethod = "publishRuntime"
    const val providerBinder = "runtimeBinder"
    const val providerLifecycleBinder = "providerLifecycleBinder"
}
