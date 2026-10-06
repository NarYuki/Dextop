package moe.n4tsu.dextop

import android.content.Context

/** Restores overrides written by releases that used to pin the phone panel to 120 Hz. */
internal class InternalRefreshRateController(
    context: Context,
    private val sessionJournal: SessionJournal
) {
    fun applyIfEnabled() {
        // The setting now controls VirtualDisplayConfig.requestedRefreshRate.
        // Do not modify Android's min/peak refresh-rate settings.
    }

    fun restore() {
        sessionJournal.restoreSystemKeys(KEYS)
    }

    fun keepCurrentValue() {
        Unit
    }

    fun isEnabledAndSupported(): Boolean =
        false

    companion object {
        private val KEYS = listOf("min_refresh_rate", "peak_refresh_rate")
    }
}
