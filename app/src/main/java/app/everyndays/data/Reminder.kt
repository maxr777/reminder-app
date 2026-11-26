package app.everyndays.data

import java.time.LocalDate
import java.time.LocalTime

/** One time the reminder was marked done, and the date it was due at that point. */
data class Completion(val done: LocalDate, val due: LocalDate)

/**
 * A repeating reminder. The schedule is a cycle of [onDays] days in a row followed by
 * [offDays] days off. "Every N days" is simply 1 day on, N-1 days off.
 * Every step is counted from the day it was actually marked done.
 */
data class Reminder(
    val id: Long = 0,
    val name: String,
    val onDays: Int = 1,
    val offDays: Int,
    /** How many on-days of the current run are already done (0 until onDays-1). */
    val streak: Int = 0,
    val time: LocalTime,
    val nextDue: LocalDate,
    val color: Int = 0,
    val note: String = "",
    val history: List<Completion> = emptyList(),
    /** The due date we already posted a notification for, so we don't post it twice. */
    val notifiedFor: LocalDate? = null,
) {
    val lastDone: LocalDate? get() = history.maxOfOrNull { it.done }

    /** True for "N days on, M days off"; false for plain "every N days". */
    val isCycle: Boolean get() = onDays > 1

    /** Which on-day the next time is, 1-based ("day 2 of 2"). */
    val cycleDay: Int get() = streak + 1

    /** Where the next date lands if marked done on [date], and the streak after that. */
    fun afterDone(date: LocalDate): Pair<LocalDate, Int> {
        val done = streak + 1
        return if (done < onDays) {
            date.plusDays(1) to done
        } else {
            date.plusDays(offDays + 1L) to 0
        }
    }
}
