package app.everyndays.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/**
 * The file format, used both for the app's own storage and for backups.
 * Version 3. Older files (1: "interval"; 2: "on"/"off"/"streak") are still read.
 */
object ReminderJson {
    private const val VERSION = 3

    fun encode(list: List<Reminder>, nextId: Long): String {
        val arr = JSONArray()
        for (r in list) arr.put(encodeReminder(r))
        return JSONObject()
            .put("app", "every-n-days")
            .put("version", VERSION)
            .put("nextId", nextId)
            .put("reminders", arr)
            .toString(2)
    }

    /** Returns the reminders and the stored next id. Throws on anything that isn't a valid file. */
    fun decode(text: String): Pair<List<Reminder>, Long> {
        val root = JSONObject(text)
        val arr = root.getJSONArray("reminders")
        val list = (0 until arr.length()).map { decodeReminder(arr.getJSONObject(it)) }
        return list to root.optLong("nextId", 1L)
    }

    private fun encodeReminder(r: Reminder): JSONObject {
        val s = r.schedule
        val history = JSONArray()
        for (c in r.history) {
            val o = JSONObject().put("done", c.done.toString()).put("due", c.due.toString())
            if (c.skipped) o.put("skipped", true)
            history.put(o)
        }
        return JSONObject()
            .put("id", r.id)
            .put("name", r.name)
            .put("kind", s.kind.name)
            .put("every", s.every)
            .put("fromDone", s.fromDone)
            .put("on", s.onDays)
            .put("off", s.offDays)
            .put("weekdays", JSONArray(s.weekdays.sorted()))
            .put("monthDay", s.monthDay)
            .put("anchor", s.anchor.toString())
            .put("streak", r.streak)
            .put("time", r.time.toString())
            .put("nextDue", r.nextDue.toString())
            .put("color", r.color)
            .put("note", r.note)
            .put("endKind", r.endKind.name)
            .put("endDate", r.endDate?.toString() ?: JSONObject.NULL)
            .put("endCount", r.endCount)
            .put("advanceMinutes", r.advanceMinutes)
            .put("paused", r.paused)
            .put("finished", r.finished)
            .put("notifiedFor", r.notifiedFor?.toString() ?: JSONObject.NULL)
            .put("advanceNotifiedFor", r.advanceNotifiedFor?.toString() ?: JSONObject.NULL)
            .put("history", history)
    }

    private fun decodeReminder(o: JSONObject): Reminder {
        val nextDue = LocalDate.parse(o.getString("nextDue"))
        val schedule = if (o.has("kind")) {
            val wd = o.optJSONArray("weekdays") ?: JSONArray()
            Schedule(
                kind = RepeatKind.valueOf(o.getString("kind")),
                every = o.optInt("every", 1),
                fromDone = o.optBoolean("fromDone", true),
                onDays = o.optInt("on", 2),
                offDays = o.optInt("off", 1),
                weekdays = (0 until wd.length()).map { wd.getInt(it) }.filter { it in 1..7 }.toSet(),
                monthDay = o.optInt("monthDay", 1),
                anchor = date(o, "anchor") ?: nextDue,
            )
        } else {
            // Versions 1 and 2: "every N days" or "N on, M off", always counted from done.
            val on = o.optInt("on", 1).coerceAtLeast(1)
            val off = if (o.has("off")) o.getInt("off") else o.getInt("interval") - 1
            if (on > 1) {
                Schedule(kind = RepeatKind.CYCLE, onDays = on, offDays = off.coerceAtLeast(0), anchor = nextDue)
            } else {
                Schedule(kind = RepeatKind.DAYS, every = (off + 1).coerceAtLeast(1), anchor = nextDue)
            }
        }

        val h = o.optJSONArray("history") ?: JSONArray()
        val history = (0 until h.length()).map { i ->
            val c = h.getJSONObject(i)
            Completion(
                done = LocalDate.parse(c.getString("done")),
                due = LocalDate.parse(c.getString("due")),
                skipped = c.optBoolean("skipped", false),
            )
        }

        return Reminder(
            id = o.getLong("id"),
            name = o.getString("name"),
            schedule = schedule,
            time = LocalTime.parse(o.getString("time")),
            nextDue = nextDue,
            streak = o.optInt("streak", 0).coerceIn(0, (schedule.onDays - 1).coerceAtLeast(0)),
            color = o.optInt("color", 0),
            note = o.optString("note", ""),
            history = history,
            endKind = o.optString("endKind", "").let { k -> EndKind.entries.find { it.name == k } } ?: EndKind.NEVER,
            endDate = date(o, "endDate"),
            endCount = o.optInt("endCount", 0),
            advanceMinutes = o.optInt("advanceMinutes", 0),
            paused = o.optBoolean("paused", false),
            finished = o.optBoolean("finished", false),
            notifiedFor = date(o, "notifiedFor"),
            advanceNotifiedFor = date(o, "advanceNotifiedFor"),
        )
    }

    private fun date(o: JSONObject, key: String): LocalDate? =
        if (!o.has(key) || o.isNull(key)) null else LocalDate.parse(o.getString(key))
}
