package moe.n4tsu.dextop

import android.content.Context
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout

/** What [ResolutionMenu] needs from the session beyond the shared interfaces. */
internal interface ResolutionMenuHost : DesktopSession, OverlayMenu {
    fun editButton(action: () -> Unit): ImageButton
    fun effectiveConfig(config: MirrorService.Config): MirrorService.Config
    fun numberField(value: String, label: String): EditText
    fun resizeActiveDisplay(next: MirrorService.Config, reason: String)
    fun resolutionProfiles(): List<ResolutionProfile>
    val resolutionRepository: ResolutionRepository
}

/** Menu pages for choosing, editing and saving desktop resolutions. */
internal class ResolutionMenu(
    private val context: Context,
    private val desk: ResolutionMenuHost,
) {
    fun showResolutionMenu(panel: LinearLayout) {
        desk.animateMenuResize(panel)
        panel.removeAllViews()
        panel.addView(
            desk.menuTitle(
                NativeStrings.text("nativeResolution"),
                NativeStrings.text("nativeReturn")
            ) { desk.showMainMenu(panel) })
        val portrait = desk.targetWidth < desk.targetHeight
        fun oriented(width: Int, height: Int): Pair<Int, Int> =
            if (portrait) height to width else width to height
        desk.resolutionProfiles().forEach { item ->
            val size = oriented(item.width, item.height)
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(
                desk.actionButton(
                if (item.device) R.drawable.ic_smartphone else R.drawable.ic_monitor,
                "${if (item.device) NativeStrings.text("nativeDevice") else "${item.width} × ${item.height}"}   ${item.density} dpi"
            ) {
                if (desk.demoMode) desk.demoExplanation(
                    "${size.first} × ${size.second} / ${item.density} ${
                        NativeStrings.text(
                            "nativeSwitchToResolutionSuffix"
                        )
                    }"
                )
                else {
                    saveSelectedResolution(item.id)
                    desk.resizeActiveDisplay(
                        desk.effectiveConfig(
                            MirrorService.Config(
                                size.first,
                                size.second,
                                item.density,
                                desk.secureDisplay,
                                desk.showSystemDecorations
                            )
                        ),
                        "resolution selected from overlay"
                    )
                }
            }, LinearLayout.LayoutParams(0, desk.dp(50), 1f)
            )
            row.addView(
                desk.editButton { showResolutionEditor(panel, item) },
                LinearLayout.LayoutParams(desk.dp(50), desk.dp(50)).apply { leftMargin = desk.dp(6) })
            panel.addView(row, LinearLayout.LayoutParams(-1, desk.dp(50)).apply { bottomMargin = desk.dp(8) })
        }
        panel.addView(desk.actionButton(R.drawable.ic_add, NativeStrings.text("nativeAddCustomResolution")) {
            showResolutionEditor(panel, null)
        })
    }

    private fun showResolutionEditor(panel: LinearLayout, existing: ResolutionProfile?) {
        desk.animateMenuResize(panel)
        panel.removeAllViews()
        panel.addView(
            desk.menuTitle(
                if (existing == null) NativeStrings.text("nativeAddResolution") else NativeStrings.text(
                    "nativeEditResolution"
                ), NativeStrings.text("nativeReturn")
            ) { showResolutionMenu(panel) })
        val width = desk.numberField(
            (existing?.width ?: desk.targetWidth).toString(),
            NativeStrings.text("nativeWidth")
        ).apply { isEnabled = existing?.device != true }
        val height = desk.numberField(
            (existing?.height ?: desk.targetHeight).toString(),
            NativeStrings.text("nativeHeight")
        ).apply { isEnabled = existing?.device != true }
        val dpi = desk.numberField((existing?.density ?: desk.density).toString(), "dpi")
        val fields = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fields.addView(width, LinearLayout.LayoutParams(0, desk.dp(52), 1f))
        fields.addView(height, LinearLayout.LayoutParams(0, desk.dp(52), 1f).apply { leftMargin = desk.dp(6) })
        fields.addView(dpi, LinearLayout.LayoutParams(0, desk.dp(52), 1f).apply { leftMargin = desk.dp(6) })
        panel.addView(fields, LinearLayout.LayoutParams(-1, desk.dp(52)).apply { bottomMargin = desk.dp(8) })
        panel.addView(
            desk.actionButton(
                R.drawable.ic_chevron,
                if (existing == null) NativeStrings.text("nativeAddAndApply") else NativeStrings.text("nativeSaveAndApply")
            ) {
                val w = width.text.toString().toIntOrNull()
                val h = height.text.toString().toIntOrNull()
                val d = dpi.text.toString().toIntOrNull()
                if (w != null && h != null && d != null && w in 480..7680 && h in 480..7680 && d in 80..640) {
                    val updated = ResolutionProfile(
                        existing?.id ?: "custom_${System.currentTimeMillis()}",
                        w,
                        h,
                        d,
                        existing?.device == true
                    )
                    saveResolutionProfile(updated)
                    saveSelectedResolution(updated.id)
                    val portrait = desk.targetWidth < desk.targetHeight
                    desk.resizeActiveDisplay(
                        desk.effectiveConfig(
                            MirrorService.Config(
                                if (portrait) h else w,
                                if (portrait) w else h,
                                d,
                                desk.secureDisplay,
                                desk.showSystemDecorations
                            )
                        ),
                        "custom resolution applied from overlay"
                    )
                }
            })
        if (existing != null && !existing.device) panel.addView(
            desk.actionButton(
                R.drawable.ic_stop,
                NativeStrings.text("nativeRemoveThisResolution"),
                true
            ) {
                deleteResolutionProfile(existing.id)
                showResolutionMenu(panel)
            })
    }

    private fun saveResolutionProfile(profile: ResolutionProfile) =
        desk.resolutionRepository.save(profile, desk.density)

    private fun deleteResolutionProfile(id: String) =
        desk.resolutionRepository.delete(id, desk.density)

    private fun saveSelectedResolution(id: String) = desk.resolutionRepository.select(id)
}
