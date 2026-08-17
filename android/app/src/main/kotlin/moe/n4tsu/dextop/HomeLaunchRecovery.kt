package moe.n4tsu.dextop

import android.content.Context
import android.os.Build

/**
 * Read side of the Samsung HOME-launch workaround that [MirrorService]
 * persists per firmware build.
 *
 * A session start records the firmware that needed system decorations and
 * drops the record once the firmware changes, so the original configuration
 * gets a clean attempt again. Diagnostics must never trigger that cleanup:
 * everything here only reads.
 */
internal object HomeLaunchRecovery {
    // Mirrors the preference name, key, and firmware identity that
    // MirrorService writes. Kept here rather than shared from MirrorService so
    // this stays additive and that file is not touched; unifying them is a
    // one-line follow-up if preferred.
    const val PREFERENCES = "dextop_home_launch_recovery"
    const val FIRMWARE_KEY = "firmware_fingerprint"

    enum class DecorationWorkaround(
        val report: String,
        /** Whether a session start is forced to system decorations by the record. */
        val forcesSystemDecorations: Boolean
    ) {
        /** The environment never applies the workaround. */
        NOT_APPLICABLE("not_applicable", false),

        /** No HOME launch has needed system decorations on this install. */
        NONE("none", false),

        /** Recorded for the running firmware, so sessions start decorated. */
        APPLIED("applied", true),

        /**
         * Recorded for an earlier firmware. The next session start discards it
         * and retries the undecorated configuration.
         */
        STALE_PENDING_RETRY("stale_pending_retry", false)
    }

    fun firmwareIdentity(): String = Build.FINGERPRINT.ifBlank {
        listOf(Build.DISPLAY, Build.VERSION.INCREMENTAL, Build.VERSION.SECURITY_PATCH)
            .joinToString("/")
    }

    fun classify(
        stored: String?,
        firmware: String,
        platformManaged: Boolean
    ): DecorationWorkaround = when {
        !platformManaged -> DecorationWorkaround.NOT_APPLICABLE
        stored.isNullOrBlank() -> DecorationWorkaround.NONE
        stored == firmware -> DecorationWorkaround.APPLIED
        else -> DecorationWorkaround.STALE_PENDING_RETRY
    }

    /** Never clears the stored value, unlike a session start. */
    fun classify(context: Context, platformManaged: Boolean): DecorationWorkaround = classify(
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(FIRMWARE_KEY, null),
        firmwareIdentity(),
        platformManaged
    )
}
