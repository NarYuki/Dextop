package moe.n4tsu.dextop.input

import android.content.Context
import android.graphics.PointF
import moe.n4tsu.dextop.R
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.Normalizer
import java.util.Locale
import java.util.PriorityQueue
import java.util.zip.GZIPInputStream
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Layout-independent offline geometric swipe decoder.
 *
 * Its normalized trace/template pipeline is derived from the geometric design
 * documented by CleverKeys (GPL-3.0). Dextop keeps this compact adapter separate
 * from the IME and accessibility boundaries used to commit a selected candidate.
 */
internal class LaptopSwipeDecoder(context: Context) {
    private val appContext = context.applicationContext
    private val resources = context.applicationContext.resources
    private val learning = appContext.getSharedPreferences("swipe_decoder_learning", Context.MODE_PRIVATE)
    private val assetLexicons = mutableMapOf<Language, List<Pair<String, String>>>()
    private val lexicons = mutableMapOf<Language, List<Lexeme>>()
    private val preparedTemplates = object : LinkedHashMap<TemplateKey, List<PreparedLexeme>>(4, .75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<TemplateKey, List<PreparedLexeme>>?
        ): Boolean = size > MAX_TEMPLATE_LAYOUTS
    }
    enum class Language(val id: String) {
        EN("en"), FR("fr"), DE("de"), ES("es"), IT("it"), PT("pt"),
        RU("ru"), UK("uk"), KO("ko"), JA("ja"), ZH_PINYIN("zh-pinyin");

        companion object {
            fun parse(raw: String?): Language = entries.firstOrNull { it.id == raw } ?: EN
        }
    }

    data class Key(val symbol: Char, val center: PointF)
    data class Candidate(val value: String, val score: Float)
    private data class Lexeme(val trace: String, val value: String, val rank: Int)
    private data class TemplateKey(val language: Language, val layoutSignature: Int)
    private data class PreparedLexeme(
        val value: String,
        val rank: Int,
        val shape: FloatArray,
        val pathLength: Float,
    )

    /** Builds the expensive dictionary geometry before the first swipe. */
    fun prewarm(keys: List<Key>, language: Language) {
        preparedLexicon(language, keys)
    }

    fun decode(
        points: List<PointF>,
        keys: List<Key>,
        language: Language,
        previousWord: String? = null,
        maxResults: Int = 5,
    ): List<Candidate> {
        // Gesture and templates must share the keyboard coordinate system.
        // Normalizing every gesture to its own bounding box discards the
        // actual start/end keys and made unrelated shapes rank together
        // (for example "fuck" as "application" and "my" as "mine").
        val normalizedTrace = resample(
            normalizeToKeyboard(points, keys.map { it.center }),
            SAMPLE_COUNT,
        ).toCoordinates()
        if (normalizedTrace.size < 6) return emptyList()
        val traceLength = pathLength(normalizedTrace)
        val best = PriorityQueue<Candidate>(maxResults + 1, compareByDescending<Candidate> { it.score })
        preparedLexicon(language, keys).forEach { lexeme ->
            val shape = lexeme.shape
            val endpoint = coordinateDistance(normalizedTrace, 0, shape, 0) +
                    coordinateDistance(normalizedTrace, normalizedTrace.size - 2, shape, shape.size - 2)
            // With both paths in keyboard coordinates, candidates whose
            // first/last keys are materially different can be rejected early.
            // The previous threshold was effectively permissive enough to
            // accept words ending several keys away.
            if (endpoint > .42f) return@forEach
            var path = 0f
            var index = 0
            while (index < normalizedTrace.size) {
                path += coordinateDistance(normalizedTrace, index, shape, index)
                index += 2
            }
            path /= SAMPLE_COUNT
            val length = abs(traceLength - lexeme.pathLength)
            val learned = learning.getInt("${language.id}:${lexeme.value.lowercase(Locale.ROOT)}", 0)
            val contextBoost = contextualBoost(language, previousWord, lexeme.value)
            val candidate = Candidate(
                lexeme.value,
                path + endpoint * .33f + length * .08f -
                        ln(lexeme.rank + 1f) * .012f -
                        ln(learned + 1f) * .045f - contextBoost,
            )
            best += candidate
            if (best.size > maxResults) best.poll()
        }
        return best.toList().sortedWith(compareBy<Candidate> { it.score }.thenBy { it.value })
            .distinctBy { it.value }
    }

    fun learn(language: Language, value: String) {
        val key = "${language.id}:${value.lowercase(Locale.ROOT)}"
        learning.edit().putInt(key, (learning.getInt(key, 0) + 1).coerceAtMost(10_000)).apply()
    }

    private fun contextualBoost(language: Language, previousWord: String?, value: String): Float {
        if (language != Language.EN) return 0f
        val previous = previousWord?.lowercase(Locale.ENGLISH)?.trim().orEmpty()
        val next = value.lowercase(Locale.ENGLISH)
        if (previous.isEmpty()) return if (next == "i") .025f else 0f
        val expected = ENGLISH_BIGRAMS[previous] ?: return 0f
        return if (next in expected) .075f else 0f
    }

    private fun preparedLexicon(language: Language, keys: List<Key>): List<PreparedLexeme> {
        val normalizedKeys = normalize(keys.map { it.center })
        if (normalizedKeys.size != keys.size) return emptyList()
        val signature = layoutSignature(keys, normalizedKeys)
        val cacheKey = TemplateKey(language, signature)
        synchronized(preparedTemplates) {
            preparedTemplates[cacheKey]?.let { return it }
        }
        val keyMap = keys.indices.associate { fold(keys[it].symbol) to normalizedKeys[it] }
        val prepared = lexicon(language).mapNotNull { lexeme ->
            val rawShape = lexeme.trace.map { keyMap[fold(it)] ?: return@mapNotNull null }
                .deduplicate()
            // keyMap is already normalized against the keyboard bounds. Do
            // not normalize each word independently or its key locations are
            // lost before comparison with the user's gesture.
            val sampled = resample(rawShape, SAMPLE_COUNT).toCoordinates()
            if (sampled.size < 6) return@mapNotNull null
            PreparedLexeme(lexeme.value, lexeme.rank, sampled, pathLength(sampled))
        }
        synchronized(preparedTemplates) {
            preparedTemplates[cacheKey] = prepared
        }
        return prepared
    }

    private fun layoutSignature(keys: List<Key>, normalized: List<PointF>): Int {
        var result = 1
        keys.indices.sortedBy { fold(keys[it].symbol) }.forEach { index ->
            result = 31 * result + fold(keys[index].symbol).code
            result = 31 * result + (normalized[index].x * 1000f).roundToInt()
            result = 31 * result + (normalized[index].y * 1000f).roundToInt()
        }
        return result
    }

    private fun lexicon(language: Language): List<Lexeme> = synchronized(lexicons) {
        lexicons.getOrPut(language) {
        val pairs: List<Pair<String, String>> = when (language) {
            Language.EN -> ENGLISH_CONTRACTIONS + assetLexicon(language, R.raw.swipe_en)
            Language.FR -> assetLexicon(language, R.raw.swipe_fr)
            Language.DE -> assetLexicon(language, R.raw.swipe_de)
            Language.ES -> assetLexicon(language, R.raw.swipe_es)
            Language.IT -> assetLexicon(language, R.raw.swipe_it)
            Language.PT -> assetLexicon(language, R.raw.swipe_pt)
            Language.RU -> assetLexicon(language, R.raw.swipe_ru)
            Language.UK -> assetLexicon(language, R.raw.swipe_uk)
            Language.KO -> assetLexicon(language, R.raw.swipe_ko)
            Language.JA -> assetLexicon(language, R.raw.swipe_ja)
            Language.ZH_PINYIN -> assetLexicon(language, R.raw.swipe_zh_pinyin)
        }
        pairs.mapIndexed { index, pair -> Lexeme(pair.first, pair.second, pairs.size - index) }
        }
    }

    private fun assetLexicon(language: Language, rawResource: Int): List<Pair<String, String>> =
        synchronized(assetLexicons) {
            assetLexicons.getOrPut(language) {
                resources.openRawResource(rawResource).use { raw ->
                    GZIPInputStream(raw).use { gzip ->
                        BufferedReader(InputStreamReader(gzip, Charsets.UTF_8)).useLines { lines ->
                            lines.mapNotNull { line ->
                                val separator = line.indexOf('\t')
                                if (separator <= 0 || separator == line.lastIndex) null
                                else line.substring(0, separator) to line.substring(separator + 1)
                            }.toList()
                        }
                    }
                }
            }
        }

    private fun normalize(points: List<PointF>): List<PointF> {
        if (points.size < 2) return emptyList()
        val minX = points.minOf { it.x }; val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }; val maxY = points.maxOf { it.y }
        val scale = max(maxX - minX, maxY - minY).coerceAtLeast(1f)
        return points.map { PointF((it.x - minX) / scale, (it.y - minY) / scale) }
    }

    private fun normalizeToKeyboard(
        points: List<PointF>,
        keyboardKeys: List<PointF>,
    ): List<PointF> {
        if (points.size < 2 || keyboardKeys.size < 2) return emptyList()
        val minX = keyboardKeys.minOf { it.x }
        val maxX = keyboardKeys.maxOf { it.x }
        val minY = keyboardKeys.minOf { it.y }
        val maxY = keyboardKeys.maxOf { it.y }
        val scale = max(maxX - minX, maxY - minY).coerceAtLeast(1f)
        return points.map { point ->
            PointF((point.x - minX) / scale, (point.y - minY) / scale)
        }
    }

    private fun resample(points: List<PointF>, count: Int): List<PointF> {
        if (points.size < 2) return emptyList()
        val total = pathLength(points)
        if (total <= .0001f) return emptyList()
        val step = total / (count - 1)
        val output = arrayListOf(PointF(points.first().x, points.first().y))
        var carried = 0f; var previous = points.first(); var index = 1
        while (index < points.size && output.size < count - 1) {
            val current = points[index]
            val segment = distance(previous, current)
            if (segment > 0f && carried + segment >= step) {
                val ratio = (step - carried) / segment
                previous = PointF(
                    previous.x + (current.x - previous.x) * ratio,
                    previous.y + (current.y - previous.y) * ratio,
                )
                output += previous; carried = 0f
            } else {
                carried += segment; previous = current; index++
            }
        }
        while (output.size < count) output += PointF(points.last().x, points.last().y)
        return output
    }

    private fun List<PointF>.deduplicate(): List<PointF> = filterIndexed { index, point ->
        index == 0 || distance(point, this[index - 1]) > .001f
    }

    private fun pathLength(points: List<PointF>): Float {
        var value = 0f
        for (index in 1 until points.size) value += distance(points[index - 1], points[index])
        return value
    }

    private fun List<PointF>.toCoordinates(): FloatArray = FloatArray(size * 2).also { values ->
        forEachIndexed { index, point ->
            values[index * 2] = point.x
            values[index * 2 + 1] = point.y
        }
    }

    private fun pathLength(points: FloatArray): Float {
        var value = 0f
        var index = 2
        while (index < points.size) {
            value += coordinateDistance(points, index - 2, points, index)
            index += 2
        }
        return value
    }

    private fun coordinateDistance(a: FloatArray, ai: Int, b: FloatArray, bi: Int): Float =
        hypot(a[ai] - b[bi], a[ai + 1] - b[bi + 1])

    private fun distance(a: PointF, b: PointF): Float = hypot(a.x - b.x, a.y - b.y)
    private fun fold(value: Char): Char = foldText(value.toString()).firstOrNull() ?: value.lowercaseChar()
    private fun foldText(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    private fun words(value: String): List<String> = value.split(' ').filter(String::isNotBlank)

    companion object {
        private const val SAMPLE_COUNT = 32
        private const val MAX_TEMPLATE_LAYOUTS = 3
        /** Apostrophes are output forms; their traces use only physical letter keys. */
        private val ENGLISH_CONTRACTIONS = listOf(
            "id" to "I'd", "ill" to "I'll", "im" to "I'm", "ive" to "I've",
            "isnt" to "isn't", "arent" to "aren't", "wasnt" to "wasn't",
            "werent" to "weren't", "dont" to "don't", "doesnt" to "doesn't",
            "didnt" to "didn't", "cant" to "can't", "couldnt" to "couldn't",
            "wont" to "won't", "wouldnt" to "wouldn't", "shouldnt" to "shouldn't",
            "youre" to "you're", "youve" to "you've", "youll" to "you'll",
            "theyre" to "they're", "theyve" to "they've", "theyll" to "they'll",
            "weve" to "we've",
            "hes" to "he's", "shes" to "she's", "thats" to "that's",
            "theres" to "there's", "whats" to "what's", "lets" to "let's",
        )
        private val ENGLISH_BIGRAMS = mapOf(
            "i" to setOf("am", "have", "will", "would", "can", "don't", "think", "know"),
            "you" to setOf("are", "have", "will", "can", "know", "want"),
            "we" to setOf("are", "have", "will", "can", "need"),
            "they" to setOf("are", "have", "will", "can"),
            "the" to setOf("same", "first", "new", "other", "best", "next"),
            "to" to setOf("be", "the", "a", "do", "make", "use", "get"),
            "this" to setOf("is", "will", "can"),
            "it" to setOf("is", "was", "will", "can"),
        )
        private const val ENGLISH = "the be to of and a in that have it for not on with as you do at this but by from they we say her she or an will my one all would there their what so up out if about who get which go me when make can like time no just know take people into year your good some could them see other than then now look only come over think also back after use two how our work first well way even new want because these give day most"
        private const val FRENCH = "de la le et les des en un une du que pour dans qui sur pas plus par je avec ce ne nous vous il elle au aux comme mais tout faire son sa ses être avoir très sans bien où si leur aussi entre quand après avant même autre encore temps monde jour"
        private const val GERMAN = "der die das und in zu den von mit sich des auf für ist im dem nicht ein eine als auch es an werden aus er hat dass sie nach wird bei einer um am sind noch wie einem über einen so zum war haben nur oder aber vor zur bis mehr"
        private const val SPANISH = "de la que el en y a los del se las por un para con no una su al lo como más pero sus le ya o este sí porque esta entre cuando muy sin sobre también me hasta hay donde quien desde todo nos durante todos uno"
        private const val ITALIAN = "di e il la che a per in un è una sono del non con da come più si al lo ha ma le se dei delle anche questo nella essere tra o gli alla mi quando fare tutto molto senza dopo prima ancora bene dove"
        private const val PORTUGUESE = "de a o que e do da em um para é com não uma os no se na por mais as dos como mas ao ele das à seu sua ou quando muito nos já eu também só pelo pela até isso ela entre era depois sem mesmo"
        private const val RUSSIAN = "и в не на я быть он с что а по это она этот к но они мы как из у который то за свой весь год от так о для ты же все тот мочь вы человек такой его сказать только или ещё бы себя один уже до время если сам когда"
        private const val UKRAINIAN = "і в не на я бути він з що а по це вона цей до але вони ми як із у який те за свій весь рік від так для ти же все той могти ви людина такий його сказати тільки або ще себе один вже час якщо сам коли"
    }
}
