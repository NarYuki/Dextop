package moe.n4tsu.dextop

/** Pure ownership checks shared by the relay and its unit tests. */
internal object CardCompanionSessionPolicy {
    fun shouldStopDirectSession(directSessionOwned: Boolean, autoOnlySessionActive: Boolean): Boolean =
        directSessionOwned && autoOnlySessionActive
}
