package app.everyndays.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** One time the reminder was marked done (or skipped), and the date it was due then. */
data class Completion(val done: LocalDate, val due: LocalDate, val skipped: Boolean = false)

enum class EndKind { NEVER, ON_DATE, AFTER_COUNT }

data class Reminder(
    val id: Long = 0,
    val name: String,
    val schedule: Schedule,
    val time: LocalTime,
    val nextDue: LocalDate,
    /** CYCLE only: how many on-days of the current run are already done. */
    val streak: Int = 0,
    val color: Int = 0,
    val note: String = "",
    val history: List<Completion> = emptyList(),
    val endKind: EndKind = EndKind.NEVER,
    val endDate: LocalDate? = null,
    val endCount: Int = 0,
    /** Extra heads-up this many minutes before the due time; 0 = none. */
    val advanceMinutes: Int = 0,
    val paused: Boolean = false,
    /** One-time reminder done, or a repeating one that reached its end. */
    val finished: Boolean = false,
    /** The due date we already posted the main notification for. */
    val notifiedFor: LocalDate? = null,
    /** The due date we already posted the heads-up notification for. */
    val advanceNotifiedFor: LocalDate? = null,
) {
    val isActive: Boolean get() = !paused && !finished
    val isCycle: Boolean get() = schedule.kind == RepeatKind.CYCLE
    val cycleDay: Int get() = streak + 1
    val dueAt: LocalDateTime get() = nextDue.atTime(time)
    val advanceAt: LocalDateTime? get() = if (advanceMinutes > 0) dueAt.minusMinutes(advanceMinutes.toLong()) else null

    val lastDone: LocalDate? get() = history.filterNot { it.skipped }.maxOfOrNull { it.done }
    val finishedOn: LocalDate? get() = history.maxOfOrNull { it.done }

    /** For "after N times": how many are left, counting the next one. */
    val timesLeft: Int? get() = if (endKind == EndKind.AFTER_COUNT) maxOf(0, endCount - history.size) else null

    /** The reminder after the current one is marked done, or skipped, on [date]. */
    fun completed(date: LocalDate, skipped: Boolean = false): Reminder {
        val newHistory = history + Completion(done = date, due = nextDue, skipped = skipped)
        val next = schedule.next(nextDue, date, streak)
        val ended = next == null ||
            (endKind == EndKind.AFTER_COUNT && newHistory.size >= endCount) ||
            (endKind == EndKind.ON_DATE && endDate != null && next.first.isAfter(endDate))
        return if (ended || next == null) {
            copy(history = newHistory, finished = true, notifiedFor = null, advanceNotifiedFor = null)
        } else {
            copy(
                history = newHistory,
                nextDue = next.first,
                streak = next.second,
                notifiedFor = null,
                advanceNotifiedFor = null,
            )
        }
    }

    /** Where the next one would land if marked done on [date]; null if that would be the last. */
    fun nextIfDoneOn(date: LocalDate): LocalDate? {
        val after = completed(date)
        return if (after.finished) null else after.nextDue
    }

    fun resumed(today: LocalDate): Reminder {
        val due = when {
            !nextDue.isBefore(today) -> nextDue
            schedule.isCalendar -> schedule.firstOnOrAfter(today)
            else -> today
        }
        return copy(paused = false, nextDue = due, notifiedFor = null, advanceNotifiedFor = null)
    }
}
