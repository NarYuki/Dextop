package moe.n4tsu.dextop

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** The overlay menu panel and the widgets its pages are built from. */
internal interface OverlayMenu {
    val menu: LinearLayout?
    val menuPrimary: LinearLayout?
    fun actionButton(icon: Int = R.drawable.ic_chevron, label: String, destructive: Boolean = false, action: () -> Unit): TextView
    fun menuTitle(title: String, action: String? = null, actionTag: String? = null, onAction: (() -> Unit)? = null): View
    fun menuHint(textValue: String): View
    fun sectionLabel(label: String): TextView
    fun choiceButton(label: String, selected: Boolean, action: () -> Unit): TextView
    fun animateMenuResize(panel: LinearLayout)
    fun scheduleMenuHeightUpdate()
    fun showMainMenu(panel: LinearLayout)
    fun toggleMenu()
    fun demoExplanation(message: String)
}
