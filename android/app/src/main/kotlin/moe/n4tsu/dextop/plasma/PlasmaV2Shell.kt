package moe.n4tsu.dextop.plasma

import android.content.Context

/** Desktop shell with wallpaper, panel and desktop window controls. */
internal class PlasmaV2Shell(
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
    applicationOverlay, privateAppDisplays, autoMode = false,
)
