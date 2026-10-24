package moe.n4tsu.dextop.plasma

import android.content.Context

/** Car shell with its own home, dock and split workspace; no desktop chrome. */
internal class DextopAutoShell(
    service: Context,
    displayId: Int,
    displayWidth: Int,
    displayHeight: Int,
    densityDpi: Int,
    host: PlasmaShell.Host,
    fullscreenAppDisplays: Boolean = false,
    passiveShellFocus: Boolean = false,
    maxRateAppDisplays: Boolean = false,
    applicationOverlay: Boolean = false,
    privateAppDisplays: Boolean = false,
) : CompositedWindowShell(
    service, displayId, displayWidth, displayHeight, densityDpi, host,
    fullscreenAppDisplays, passiveShellFocus, maxRateAppDisplays,
    applicationOverlay, privateAppDisplays, autoMode = true,
)
