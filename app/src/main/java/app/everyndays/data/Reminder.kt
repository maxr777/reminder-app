package app.everyndays.data

import java.time.LocalDate
import java.time.LocalTime

/** One time the reminder was marked done, and the date it was due at that point. */
data class Completion(val done: LocalDate, val due: LocalDate)

data class Reminder(
    val id: Long = 0,
    val name: String,
    val intervalDays: Int,
    val time: LocalTime,
    val nextDue: LocalDate,
    val color: Int = 0,
    val note: String = "",
    val history: List<Completion> = emptyList(),
    /** The due date we already posted a notification for, so we don't post it twice. */
    val notifiedFor: LocalDate? = null,
) {
    val lastDone: LocalDate? get() = history.maxOfOrNull { it.done }
}
