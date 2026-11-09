package moe.n4tsu.dextop

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class InlineStringsTest {
    @Test fun catalogsLoadWithoutAndroidJsonAndSelectRegionalPortuguese() {
        val expected = mapOf(
            Locale.JAPANESE to "表示倍率",
            Locale.ENGLISH to "Display scale",
            Locale.KOREAN to "표시 배율",
            Locale.CHINESE to "显示缩放",
            Locale("ru") to "Масштаб интерфейса",
            Locale("pt") to "Escala do ecrã",
            Locale("pt", "BR") to "Escala da tela",
        )
        expected.forEach { (locale, text) ->
            assertEquals(text, InlineStrings.pick(locale, "uiDisplayScale"))
        }
    }

    @Test fun unsupportedLocaleFallsBackToEnglish() {
        assertEquals("Display scale", InlineStrings.pick(Locale.FRENCH, "uiDisplayScale"))
    }

    @Test fun formattingPreservesArgumentBracesAndPropertiesNewlines() {
        assertEquals("value {1} / next", InlineStrings.format("{0} / {1}", "value {1}", "next"))
        val message = InlineStrings.pick(Locale.ENGLISH, "uiReason0Diagnostics1Action2")
        assertEquals("Reason: why\nDiagnostics: data\nAction: retry", InlineStrings.format(message, "why", "data", "retry"))
    }
}
