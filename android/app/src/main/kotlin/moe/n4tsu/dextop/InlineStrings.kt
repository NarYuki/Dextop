package moe.n4tsu.dextop

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Text written in place as a Japanese/English pair. Other languages look the English up in
 * the tables tool/generate_inline_strings.py builds from lib/l10n/inline/<locale>.json and
 * ships as resources/moe/n4tsu/dextop/l10n/inline_<locale>.json; missing ones stay English.
 */
internal object InlineStrings {
    private val tables = ConcurrentHashMap<String, Map<String, String>>()

    private fun key(locale: Locale) =
        if (locale.language == "pt" && locale.country == "BR") "pt_BR" else locale.language

    private fun table(name: String): Map<String, String> = tables.getOrPut(name) {
        runCatching {
            val text = InlineStrings::class.java.getResourceAsStream("/moe/n4tsu/dextop/l10n/inline_$name.json")
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: return@runCatching emptyMap()
            val json = org.json.JSONObject(text)
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrDefault(emptyMap())
    }

    fun pick(locale: Locale, ja: String, en: String): String =
        if (locale.language == "ja") ja else table(key(locale))[en] ?: en

    /** Fills {0}, {1}… of a text chosen by [pick] (interpolated text is looked up as a template). */
    fun format(template: String, vararg args: Any?): String =
        Regex("""\{(\d+)\}""").replace(template) { m -> args.getOrNull(m.groupValues[1].toInt())?.toString() ?: m.value }
}

/** [ja] or [en] in the device language, translated for the other languages. */
internal fun localText(ja: String, en: String, locale: Locale = Locale.getDefault()) = InlineStrings.pick(locale, ja, en)
