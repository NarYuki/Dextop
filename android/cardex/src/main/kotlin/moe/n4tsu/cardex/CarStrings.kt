package moe.n4tsu.cardex

import android.content.Context
import java.util.Properties
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Shared translated text for the phone UI, Parking and Driving. */
internal object CarStrings {
    private val tables = ConcurrentHashMap<String, Map<String, String>>()

    private fun table(locale: String): Map<String, String> = tables.getOrPut(locale) {
        val properties = Properties()
        CarStrings::class.java.getResourceAsStream("/moe/n4tsu/dextop/l10n/inline_$locale.properties")
            ?.bufferedReader(Charsets.UTF_8)?.use { properties.load(it) }
        properties.stringPropertyNames().associateWith { properties.getProperty(it) }
    }

    fun text(context: Context, key: String, vararg args: Any?): String {
        return text(context.resources.configuration.locales[0], key, *args)
    }

    fun text(locale: Locale, key: String, vararg args: Any?): String {
        val regional = if (locale.country.isBlank()) locale.language else "${locale.language}_${locale.country}"
        val template = table(regional)[key] ?: table(locale.language)[key] ?: table("en")[key] ?: key
        return Regex("\\{(\\d+)\\}").replace(template) { match ->
            args.getOrNull(match.groupValues[1].toInt())?.toString() ?: match.value
        }
    }
}
