package moe.n4tsu.dextop.linux

import android.content.Context
import org.json.JSONObject

/** App-owned preferences also apply when Linux reconnects or changes execution mode. */
internal object LinuxExperimentalFeatures {
    private const val ENABLED = "flutter.experimental_linux_landlock_fast_path"
    private const val MODE = "flutter.experimental_linux_fast_mode_kind"
    private fun preferences(context: Context) = context
        .getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)

    fun fastModeEnabled(context: Context): Boolean = preferences(context).getBoolean(ENABLED, true)
    /** Unset means unprotected; an opt-in saved before the modes existed stays protected. */
    fun fastMode(context: Context): LinuxFastMode = preferences(context).let {
        val kind = it.getString(MODE, null)
        if (kind == null && it.contains(ENABLED)) LinuxFastMode.PROTECTED else LinuxFastMode.from(kind)
    }

    fun publish(context: Context, service: ILinuxContainerService) {
        service.setLinuxSettings(JSONObject()
            .put("landlockFastPath", fastModeEnabled(context))
            .put("fastPathMode", fastMode(context).id).toString())
    }
}
