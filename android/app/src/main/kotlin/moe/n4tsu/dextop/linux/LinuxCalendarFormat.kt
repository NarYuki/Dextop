package moe.n4tsu.dextop.linux

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Independent iCalendar encoder: UTF-8 folding and escaping apply to every provider value. */
internal object LinuxCalendarFormat {
    data class Event(val calendar: Long, val id: Long, val begin: Long, val end: Long, val allDay: Boolean,
                     val title: String, val description: String = "", val location: String = "")
    private val utc = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val date = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)
    fun escape(text: String) = text.replace("\\", "\\\\").replace("\r\n", "\n").replace("\r", "\n")
        .replace("\n", "\\n").replace(";", "\\;").replace(",", "\\,")
    fun fold(line: String): String {
        val output = StringBuilder()
        var octets = 0
        line.codePoints().forEach { point ->
            val text = String(Character.toChars(point))
            val size = text.toByteArray(Charsets.UTF_8).size
            if (octets + size > 75) { output.append("\r\n "); octets = 1 }
            output.append(text)
            octets += size
        }
        return output.toString()
    }
    fun encode(events: List<Event>, now: Long = System.currentTimeMillis()): String {
        val lines = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Dextop//Android Calendar//EN", "CALSCALE:GREGORIAN", "METHOD:PUBLISH")
        for (event in events) {
            lines += "BEGIN:VEVENT"
            lines += "UID:${event.calendar}-${event.id}-${event.begin}@dextop"
            lines += "DTSTAMP:${utc.format(Instant.ofEpochMilli(now))}"
            if (event.allDay) {
                lines += "DTSTART;VALUE=DATE:${date.format(Instant.ofEpochMilli(event.begin))}"
                lines += "DTEND;VALUE=DATE:${date.format(Instant.ofEpochMilli(event.end))}"
            } else {
                lines += "DTSTART:${utc.format(Instant.ofEpochMilli(event.begin))}"
                lines += "DTEND:${utc.format(Instant.ofEpochMilli(event.end))}"
            }
            lines += "SUMMARY:${escape(event.title)}"
            if (event.description.isNotEmpty()) lines += "DESCRIPTION:${escape(event.description)}"
            if (event.location.isNotEmpty()) lines += "LOCATION:${escape(event.location)}"
            lines += "END:VEVENT"
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n", postfix = "\r\n", transform = ::fold)
    }
}
