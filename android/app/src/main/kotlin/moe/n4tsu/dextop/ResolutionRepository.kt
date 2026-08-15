package moe.n4tsu.dextop

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

internal data class ResolutionProfile(
    val id: String,
    val width: Int,
    val height: Int,
    val density: Int,
    val device: Boolean = false
)

internal class ResolutionRepository(
    private val context: Context,
    private val tag: String
) {
    private val preferences
        get() = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)

    fun profiles(fallbackDensity: Int): List<ResolutionProfile> {
        val mode = context.getSystemService(DisplayManager::class.java).getDisplay(0)?.mode
        val physicalWidth = mode?.physicalWidth ?: 2340
        val physicalHeight = mode?.physicalHeight ?: 1080
        val deviceDensity = fallbackDensity
        return buildList {
            add(
                ResolutionProfile(
                    "device",
                    maxOf(physicalWidth, physicalHeight),
                    minOf(physicalWidth, physicalHeight),
                    deviceDensity,
                    true
                )
            )
            runCatching {
                val array = JSONArray(preferences.getString("flutter.custom_resolution_profiles", "[]"))
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        ResolutionProfile(
                            item.getString("id"),
                            item.getInt("width"),
                            item.getInt("height"),
                            item.getInt("density")
                        )
                    )
                }
            }.onFailure { Log.e(tag, "unable to read display profiles", it) }
        }
    }

    fun save(profile: ResolutionProfile, fallbackDensity: Int) {
        if (profile.device) {
            preferences.edit().remove("flutter.device_resolution_dpi").apply()
            return
        }
        val items = profiles(fallbackDensity).filterNot { it.device }.toMutableList()
        val index = items.indexOfFirst { it.id == profile.id }
        if (index >= 0) items[index] = profile else items += profile
        writeCustom(items)
    }

    fun delete(id: String, fallbackDensity: Int) {
        writeCustom(profiles(fallbackDensity).filter { !it.device && it.id != id })
        if (preferences.getString("flutter.selected_resolution_id", "device") == id) {
            preferences.edit().putString("flutter.selected_resolution_id", "device").apply()
        }
    }

    fun select(id: String) {
        preferences.edit().putString("flutter.selected_resolution_id", id).apply()
    }

    private fun writeCustom(items: List<ResolutionProfile>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("width", item.width)
                put("height", item.height)
                put("density", item.density)
            })
        }
        preferences.edit().putString("flutter.custom_resolution_profiles", array.toString()).apply()
    }
}
