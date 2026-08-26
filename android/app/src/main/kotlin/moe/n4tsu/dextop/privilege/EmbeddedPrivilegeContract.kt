package moe.n4tsu.dextop.privilege

import android.content.Context

/**
 * Distribution boundary for privilege bootstrapping.
 *
 * The bundled implementation is isolated behind this contract while external
 * Shizuku-compatible providers remain available as an alternative.
 */
interface EmbeddedPrivilegeContract {
    val included: Boolean

    suspend fun pair(context: Context, code: String): PairingResult

    suspend fun connectAndStart(context: Context): StartResult

    data class PairingResult(
        val paired: Boolean,
        val endpoint: String? = null,
        val detail: String? = null,
    )

    data class StartResult(
        val started: Boolean,
        val endpoint: String? = null,
        val detail: String? = null,
    )
}
