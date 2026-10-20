package moe.n4tsu.splitui

import android.app.Activity
import android.app.AlertDialog
import org.json.JSONObject

class SplitPlacementDialog(private val activity: Activity, private val action: (String) -> Unit) {
    private var dialog: AlertDialog? = null
    private var previous = ""
    fun update(json: String) {
        if (json == previous) return
        previous = json
        dialog?.dismiss()
        dialog = null
        if (json.isBlank() || activity.isFinishing) return
        val state = JSONObject(json)
        val labels = mutableListOf<String>()
        val actions = mutableListOf<String>()
        val icons = mutableListOf<String>()
        if (state.optString("side").isEmpty()) {
            labels += "左 / Left"; actions += "left"
            labels += "右 / Right"; actions += "right"
        } else {
            val apps = state.getJSONArray("apps")
            for (i in 0 until apps.length()) {
                val app = apps.getJSONObject(i)
                labels += app.getString("label")
                actions += "app:" + app.getString("package")
                icons += app.optString("icon")
            }
        }
        val themedContext = android.view.ContextThemeWrapper(activity, android.R.style.Theme_Material_Dialog_Alert)
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
        val builder = AlertDialog.Builder(themedContext)
            .setTitle(if (state.optString("side").isEmpty()) "配置先を選択 / Place app"
                else "反対側のアプリ / Other app ${state.optInt("page") + 1}/${state.optInt("pages")}")
            .setAdapter(object : android.widget.ArrayAdapter<String>(themedContext, android.R.layout.simple_list_item_1, labels) {
                override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                    val view = super.getView(position, convertView, parent) as android.widget.TextView
                    val size = (32 * activity.resources.displayMetrics.density).toInt()
                    val drawable = runCatching {
                        val bytes = android.util.Base64.decode(icons.getOrElse(position) { "" }, android.util.Base64.DEFAULT)
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let {
                            android.graphics.drawable.BitmapDrawable(activity.resources, it).apply { setBounds(0, 0, size, size) }
                        }
                    }.getOrNull()
                    view.setCompoundDrawables(drawable, null, null, null)
                    view.compoundDrawablePadding = (12 * activity.resources.displayMetrics.density).toInt()
                    view.textSize = 16f
                    view.setTextColor(android.graphics.Color.rgb(240, 240, 245))
                    view.setPadding(dp(24), dp(12), dp(24), dp(12))
                    view.minimumHeight = dp(56)
                    return view
                }
            }) { _, index -> action(actions[index]) }
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ -> action("cancel") }
        if (state.optString("side").isNotEmpty()) {
            if (state.optInt("page") > 0) builder.setNeutralButton("▲ Previous") { _, _ -> action("previous") }
            if (state.optInt("page") + 1 < state.optInt("pages")) builder.setPositiveButton("▼ Next") { _, _ -> action("next") }
        }
        if (state.optString("error").isNotEmpty()) builder.setMessage(state.getString("error"))
        dialog = builder.show()
        dialog?.window?.setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.rgb(32, 33, 39))
            cornerRadius = dp(28).toFloat()
        })
        dialog?.listView?.apply {
            divider = null
            setPadding(0, dp(8), 0, dp(8))
        }
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL).forEach { button ->
            dialog?.getButton(button)?.apply {
                isAllCaps = false
                setTextColor(android.graphics.Color.rgb(199, 184, 255))
            }
        }
    }
    fun close() { dialog?.dismiss(); dialog = null; previous = "" }
}

