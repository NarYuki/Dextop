package moe.n4tsu.dextop

import java.io.File

internal object CpuTemperature {
    private val cpuTypeMarkers = listOf("cpu", "soc", "ap", "cluster", "big", "little", "gold", "silver")

    fun readCelsius(): Double? {
        val zones = File("/sys/class/thermal").listFiles { file ->
            file.name.startsWith("thermal_zone")
        }.orEmpty()
        val candidates = zones.mapNotNull { zone ->
            val type = runCatching { File(zone, "type").readText().trim().lowercase() }.getOrNull()
                ?: return@mapNotNull null
            if (cpuTypeMarkers.none(type::contains)) return@mapNotNull null
            val raw = runCatching { File(zone, "temp").readText().trim().toDouble() }.getOrNull()
                ?: return@mapNotNull null
            val celsius = if (raw > 1000) raw / 1000.0 else raw
            celsius.takeIf { it in -20.0..150.0 }
        }
        return candidates.maxOrNull()
    }

    fun formatted(): String = readCelsius()?.let { "%.1f °C".format(it) } ?: "-- °C"
}
