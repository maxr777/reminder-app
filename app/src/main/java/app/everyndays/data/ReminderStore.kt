package app.everyndays.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.LocalDate

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
            val (list, id) = ReminderJson.decode(String(f.readFully(), Charsets.UTF_8))
            _items.value = list
            nextId = maxOf(id, (list.maxOfOrNull { it.id } ?: 0L) + 1)
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

    /** Marks the current occurrence done (or skipped) on [date]. */
    @Synchronized
    fun complete(id: Long, date: LocalDate, skipped: Boolean = false): Reminder? {
        val r = get(id) ?: return null
        if (r.finished) return r
        return upsert(r.completed(date, skipped))
    }

    @Synchronized
    fun setPaused(id: Long, paused: Boolean, today: LocalDate): Reminder? {
        val r = get(id) ?: return null
        val updated = if (paused) r.copy(paused = true) else r.resumed(today)
        return upsert(updated)
    }

    @Synchronized
    fun setNotified(main: Map<Long, LocalDate>, advance: Map<Long, LocalDate>) {
        if (main.isEmpty() && advance.isEmpty()) return
        commit(
            _items.value.map { r ->
                var x = r
                main[r.id]?.let { x = x.copy(notifiedFor = it) }
                advance[r.id]?.let { x = x.copy(advanceNotifiedFor = it) }
                x
            }
        )
    }

    /** The whole list as JSON, for a backup file. */
    fun exportJson(): String = synchronized(this) { ReminderJson.encode(_items.value, nextId) }

    /** Replaces everything with the reminders in a backup file. Returns how many were loaded. */
    @Synchronized
    fun importJson(text: String): Int {
        val (list, id) = ReminderJson.decode(text) // throws on a bad file, before anything changes
        // Notifications are re-posted for whatever is due after a restore.
        val cleaned = list.map { it.copy(notifiedFor = null, advanceNotifiedFor = null) }
        nextId = maxOf(id, (cleaned.maxOfOrNull { it.id } ?: 0L) + 1)
        commit(cleaned)
        return cleaned.size
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
            out.write(ReminderJson.encode(list, nextId).toByteArray(Charsets.UTF_8))
            f.finishWrite(out)
        } catch (e: Exception) {
            Log.e(TAG, "Could not save reminders", e)
            f.failWrite(out)
        }
    }
}
