package moe.n4tsu.dextop

import java.util.Locale
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves catalog keys from generated resources in the requested locale.
 * All supported locales, including Japanese and English, live in translation files.
 */
internal object InlineStrings {
    private val tables = ConcurrentHashMap<String, Map<String, String>>()

    private fun localeKey(locale: Locale) =
        if (locale.language == "pt" && locale.country == "BR") "pt_BR" else locale.language

    private fun table(name: String): Map<String, String> = tables.getOrPut(name) {
        runCatching {
            val stream = InlineStrings::class.java.getResourceAsStream("/moe/n4tsu/dextop/l10n/inline_$name.properties")
                ?: return@runCatching emptyMap()
            val properties = Properties()
            stream.bufferedReader(Charsets.UTF_8).use { properties.load(it) }
            properties.stringPropertyNames().associateWith { properties.getProperty(it) }
        }.getOrDefault(emptyMap())
    }

    fun pick(locale: Locale, key: String): String =
        table(localeKey(locale))[key] ?: table("en")[key] ?: key

    /** Fills {0}, {1}… of a text chosen by [pick] (interpolated text is looked up as a template). */
    fun format(template: String, vararg args: Any?): String =
        Regex("""\{(\d+)\}""").replace(template) { m -> args.getOrNull(m.groupValues[1].toInt())?.toString() ?: m.value }
}

/** Resolves [key] in the device language. */
internal fun localText(key: String, locale: Locale = Locale.getDefault()) = InlineStrings.pick(locale, key)
