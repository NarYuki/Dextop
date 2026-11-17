package moe.n4tsu.dextop.linux

import android.content.Context
import org.json.JSONObject

/** App-owned preferences also apply when Linux reconnects or changes execution mode. */
internal object LinuxExperimentalFeatures {
    private const val ENABLED = "flutter.experimental_linux_landlock_fast_path"
    private const val MODE = "flutter.experimental_linux_fast_mode_kind"
    private fun preferences(context: Context) = context
        .getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)

    fun fastModeEnabled(context: Context): Boolean = preferences(context).getBoolean(ENABLED, false)
    fun fastMode(context: Context): LinuxFastMode = LinuxFastMode.from(preferences(context).getString(MODE, null))

    fun publish(context: Context, service: ILinuxContainerService) {
        service.setLinuxSettings(JSONObject()
            .put("landlockFastPath", fastModeEnabled(context))
            .put("fastPathMode", fastMode(context).id).toString())
    }
}
