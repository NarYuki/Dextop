package moe.n4tsu.dextop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationTaskLocatorTest {
    @Test
    fun findsNotificationTaskOnPhoneDisplay() {
        val dump = """
            Display #0 (activities from top to bottom):
              * Hist #0: ActivityRecord{abc u0 com.example.mail/.MessageActivity t451}
                launchedFromUid=1000 launchedFromPackage=com.android.systemui launchedFromFeature=null userId=0
            Display #7 (activities from top to bottom):
              * Hist #0: ActivityRecord{def u0 com.example.other/.MainActivity t88}
                launchedFromUid=10234 launchedFromPackage=com.example.other userId=0
        """.trimIndent()

        assertEquals(451, NotificationTaskLocator.findSystemUiLaunchedTask(dump, "com.example.mail", 7))
    }

    @Test
    fun ignoresTaskAlreadyOnDesktopDisplay() {
        val dump = """
            Display: mDisplayId=7 (organized)
              * Hist #0: ActivityRecord{abc u0 com.example.mail/.MessageActivity t451}
                launchedFromUid=1000 launchedFromPackage=com.android.systemui launchedFromFeature=null userId=0
        """.trimIndent()

        assertNull(NotificationTaskLocator.findSystemUiLaunchedTask(dump, "com.example.mail", 7))
    }

    @Test
    fun ignoresNonSystemUiLaunches() {
        val dump = """
            Display: mDisplayId=0 (organized)
              * Hist #0: ActivityRecord{abc u0 com.example.mail/.MessageActivity t451}
                launchedFromUid=10234 launchedFromPackage=com.example.launcher userId=0
        """.trimIndent()

        assertNull(NotificationTaskLocator.findSystemUiLaunchedTask(dump, "com.example.mail", 7))
    }
}
