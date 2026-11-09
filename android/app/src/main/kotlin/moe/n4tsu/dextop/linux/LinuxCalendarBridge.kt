package moe.n4tsu.dextop.linux

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/** Android calendar access stays inside the app, using a normal user-approved permission. */
internal object LinuxCalendarBridge {
    private fun prefs(context: Context) = context.getSharedPreferences("linux-calendar", Context.MODE_PRIVATE)
    private fun key(context: Context, id: String) = LinuxExecutionMode.read(context)+"_"+id
    fun granted(context: Context) = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    fun selected(context: Context, id: String): Set<String> = prefs(context).getStringSet(key(context, id), emptySet())!!.toSet()
    fun select(context: Context, id: String, ids: Set<String>) {
        require(ids.size <= 100 && ids.all { it.toLongOrNull()?.let { number -> number >= 0 } == true })
        prefs(context).edit().putStringSet(key(context, id), ids).apply()
        stamps.remove(id)
    }
    fun forget(context: Context, id: String) { prefs(context).edit().remove(key(context, id)).apply(); stamps.remove(id) }
    fun list(context: Context): Map<String, Any> {
        if (!granted(context)) return mapOf("granted" to false, "calendars" to emptyList<Any>())
        val rows = ArrayList<Map<String, Any>>()
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI,
            arrayOf("_id", "calendar_displayName", "account_name", "calendar_color"), "visible=1", null, "calendar_displayName ASC")?.use { cursor ->
            while (cursor.moveToNext() && rows.size < 100) rows += mapOf("id" to cursor.getLong(0).toString(),
                "name" to (cursor.getString(1) ?: moe.n4tsu.dextop.localText("uiCalendar")), "account" to (cursor.getString(2) ?: ""), "color" to cursor.getInt(3))
        }
        return mapOf("granted" to true, "calendars" to rows)
    }

    private var observing = false
    @Volatile private var generation = 0L
    private val stamps = ConcurrentHashMap<String, Pair<Long, Long>>()
    @Synchronized private fun observe(context: Context) {
        if (observing || !granted(context)) return
        context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true,
            object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { generation++ } })
        observing = true
    }

    fun sync(context: Context, service: ILinuxContainerService, id: String, force: Boolean = false) {
        val ids = selected(context, id)
        if (ids.isEmpty()) return
        if (!granted(context)) {
            service.integrationAction(id, JSONObject().put("op", "stop").put("feature", "calendar").toString())
            forget(context, id)
            return
        }
        observe(context.applicationContext)
        val now = System.currentTimeMillis()
        val before = stamps[id]
        if (!force && before != null && before.first == generation && now-before.second < 60000) return
        val settings = JSONObject(service.integrationStatus(id)).getJSONObject("calendar")
        if (!settings.optBoolean("enabled")) return
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now-7*86400000L); ContentUris.appendId(it, now+365*86400000L)
        }.build()
        val events = ArrayList<LinuxCalendarFormat.Event>()
        val arguments = ids.toTypedArray()
        val projection = arrayOf("calendar_id", "event_id", "begin", "end", "allDay", "title", "description", "eventLocation")
        context.contentResolver.query(uri, projection, "calendar_id IN (${arguments.joinToString(",") { "?" }})", arguments, "begin ASC")?.use { cursor ->
            while (cursor.moveToNext() && events.size < 500) events += LinuxCalendarFormat.Event(cursor.getLong(0), cursor.getLong(1),
                cursor.getLong(2), cursor.getLong(3), cursor.getInt(4) != 0, (cursor.getString(5) ?: "").take(160),
                (cursor.getString(6) ?: "").take(256), (cursor.getString(7) ?: "").take(160))
        }
        val whenFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
        fun rows() = JSONArray(events.map { event -> JSONObject().put("title", event.title).put("description", event.description)
            .put("location", event.location).put("when", if (event.allDay) Instant.ofEpochMilli(event.begin).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                else whenFormat.format(Instant.ofEpochMilli(event.begin)) + " – " + whenFormat.format(Instant.ofEpochMilli(event.end))) })
        var ics = LinuxCalendarFormat.encode(events, now)
        var data = rows()
        while (ics.length+data.toString().length > 200000 || ics.toByteArray().size > 240000 || data.toString().toByteArray().size > 128000) {
            events.removeAt(events.lastIndex)
            ics = LinuxCalendarFormat.encode(events, now)
            data = rows()
        }
        service.integrationAction(id, JSONObject().put("op", "calendar-data").put("ics", ics).put("events", data).toString())
        stamps[id] = generation to now
    }

    private val requests = ConcurrentHashMap<String, (Boolean) -> Unit>()
    fun request(context: Context, result: (Boolean) -> Unit) {
        if (granted(context)) { result(true); return }
        val token = java.util.UUID.randomUUID().toString()
        requests[token] = result
        context.startActivity(Intent(context, LinuxCalendarPermissionActivity::class.java).putExtra("token", token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun finishRequest(token: String, granted: Boolean) { requests.remove(token)?.invoke(granted) }
}

class LinuxCalendarPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (LinuxCalendarBridge.granted(this)) finishWith(true)
        else if (savedInstanceState == null) requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 1)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        finishWith(LinuxCalendarBridge.granted(this))
    }
    private fun finishWith(granted: Boolean) {
        LinuxCalendarBridge.finishRequest(intent.getStringExtra("token").orEmpty(), granted)
        finish()
    }
}
