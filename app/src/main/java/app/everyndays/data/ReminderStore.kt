package app.everyndays.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * All reminders live in one small JSON file in the app's private storage.
 * The list is kept in memory as a StateFlow so the UI updates on every change.
 */
object ReminderStore {
    private const val TAG = "ReminderStore"
    private const val FILE_NAME = "reminders.json"

    private var file: AtomicFile? = null
    private var nextId = 1L
    private val _items = MutableStateFlow<List<Reminder>>(emptyList())
    val items: StateFlow<List<Reminder>> = _items.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val base = File(context.applicationContext.filesDir, FILE_NAME)
        val f = AtomicFile(base)
        file = f
        if (!base.exists()) return
        try {
            decode(String(f.readFully(), Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "Could not read reminders, keeping a copy of the broken file", e)
            base.copyTo(File(base.parentFile, "reminders.broken.json"), overwrite = true)
        }
    }

    fun get(id: Long): Reminder? = _items.value.find { it.id == id }

    /** Adds a new reminder (id = 0) or replaces the one with the same id. */
    @Synchronized
    fun upsert(reminder: Reminder): Reminder {
        val list = _items.value
        val saved = if (reminder.id == 0L) reminder.copy(id = nextId++) else reminder
        if (saved.id >= nextId) nextId = saved.id + 1
        val newList = if (list.any { it.id == saved.id }) {
            list.map { if (it.id == saved.id) saved else it }
        } else {
            list + saved
        }
        commit(newList)
        return saved
    }

    @Synchronized
    fun delete(id: Long) {
        commit(_items.value.filterNot { it.id == id })
    }

    /** Marks a reminder done on [date]; the next one is counted from that day. */
    @Synchronized
    fun markDone(id: Long, date: LocalDate): Reminder? {
        val r = get(id) ?: return null
        val (next, streak) = r.afterDone(date)
        val updated = r.copy(
            history = r.history + Completion(done = date, due = r.nextDue),
            nextDue = next,
            streak = streak,
            notifiedFor = null,
        )
        return upsert(updated)
    }

    @Synchronized
    fun setNotified(notified: Map<Long, LocalDate>) {
        if (notified.isEmpty()) return
        commit(_items.value.map { r -> notified[r.id]?.let { r.copy(notifiedFor = it) } ?: r })
    }

    private fun commit(list: List<Reminder>) {
        _items.value = list
        val f = file ?: return
        val out = try {
            f.startWrite()
        } catch (e: Exception) {
            Log.e(TAG, "Could not open reminders file for writing", e)
            return
        }
        try {
            out.write(encode(list).toByteArray(Charsets.UTF_8))
            f.finishWrite(out)
        } catch (e: Exception) {
            Log.e(TAG, "Could not save reminders", e)
            f.failWrite(out)
        }
    }

    private fun encode(list: List<Reminder>): String {
        val arr = JSONArray()
        for (r in list) {
            val history = JSONArray()
            for (c in r.history) {
                history.put(JSONObject().put("done", c.done.toString()).put("due", c.due.toString()))
            }
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("name", r.name)
                    .put("on", r.onDays)
                    .put("off", r.offDays)
                    .put("streak", r.streak)
                    .put("time", r.time.toString())
                    .put("nextDue", r.nextDue.toString())
                    .put("color", r.color)
                    .put("note", r.note)
                    .put("notifiedFor", r.notifiedFor?.toString() ?: JSONObject.NULL)
                    .put("history", history)
            )
        }
        return JSONObject()
            .put("version", 2)
            .put("nextId", nextId)
            .put("reminders", arr)
            .toString(2)
    }

    private fun decode(text: String) {
        val root = JSONObject(text)
        val arr = root.getJSONArray("reminders")
        val list = ArrayList<Reminder>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val h = o.optJSONArray("history") ?: JSONArray()
            val history = (0 until h.length()).map { j ->
                val c = h.getJSONObject(j)
                Completion(LocalDate.parse(c.getString("done")), LocalDate.parse(c.getString("due")))
            }
            // Version 1 files only had "interval" (= 1 day on, interval-1 days off).
            val on = if (o.has("on")) o.getInt("on") else 1
            val off = if (o.has("off")) o.getInt("off") else o.getInt("interval") - 1
            list += Reminder(
                id = o.getLong("id"),
                name = o.getString("name"),
                onDays = on.coerceAtLeast(1),
                offDays = off.coerceAtLeast(0),
                streak = o.optInt("streak", 0).coerceIn(0, on.coerceAtLeast(1) - 1),
                time = LocalTime.parse(o.getString("time")),
                nextDue = LocalDate.parse(o.getString("nextDue")),
                color = o.optInt("color", 0),
                note = o.optString("note", ""),
                history = history,
                notifiedFor = if (o.isNull("notifiedFor")) null else LocalDate.parse(o.getString("notifiedFor")),
            )
        }
        _items.value = list
        nextId = maxOf(root.optLong("nextId", 1L), (list.maxOfOrNull { it.id } ?: 0L) + 1)
    }
}
