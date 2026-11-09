package moe.n4tsu.cardex

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class CarStringsTest {
    @Test fun companionRecoveryIsTranslatedInEverySupportedLanguage() {
        val expected = mapOf(
            Locale.JAPANESE to "復旧して再試行",
            Locale.ENGLISH to "Recover and retry",
            Locale.KOREAN to "복구 후 다시 시도",
            Locale.CHINESE to "恢复并重试",
            Locale("ru") to "Восстановить и повторить",
            Locale("pt") to "Recuperar e tentar novamente",
            Locale("pt", "BR") to "Recuperar e tentar novamente",
        )
        expected.forEach { (locale, value) ->
            assertEquals(value, CarStrings.text(locale, "uiRecoverAndRetry"))
        }
    }

    @Test fun formatsValuesAfterTranslationAndFallsBackForUnknownLocales() {
        assertEquals("Dextop could not be bound: value {1}",
            CarStrings.text(Locale.FRENCH, "uiDextopCouldNotBeBound0", "value {1}"))
    }
}
