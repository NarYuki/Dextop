package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxCalendarFormatTest {
    @Test fun encodesAllDayExclusiveEndAndUtcTimes() {
        val event = LinuxCalendarFormat.Event(3, 7, 0, 86400000, true, "Calendar")
        val data = LinuxCalendarFormat.encode(listOf(event), 0)
        assertContains(data, "DTSTART;VALUE=DATE:19700101\r\n")
        assertContains(data, "DTEND;VALUE=DATE:19700102\r\n")
        assertContains(data, "UID:3-7-0@dextop\r\n")
        assertContains(LinuxCalendarFormat.encode(listOf(event.copy(allDay = false)), 0), "DTSTART:19700101T000000Z\r\n")
    }
    @Test fun providerTextCannotInjectEventsAndFoldsWithoutSplittingUtf8() {
        assertEquals("a\\,b\\;c\\nd\\\\e", LinuxCalendarFormat.escape("a,b;c\r\nd\\e"))
        val title = "予定🎂".repeat(60)+"\nEND:VEVENT"
        val data = LinuxCalendarFormat.encode(listOf(LinuxCalendarFormat.Event(1, 2, 0, 1, false, title)), 0)
        assertFalse(data.contains("\r\nEND:VEVENT\r\nEND:VEVENT"))
        for (line in data.split("\r\n")) assertTrue(line.toByteArray(Charsets.UTF_8).size <= 75)
        assertContains(data.replace("\r\n ", ""), "SUMMARY:${LinuxCalendarFormat.escape(title)}")
    }
}
