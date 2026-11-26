package app.everyndays

import android.content.Context
import android.text.format.DateFormat
import app.everyndays.data.Completion
import app.everyndays.data.EndKind
import app.everyndays.data.Reminder
import app.everyndays.data.RepeatKind
import app.everyndays.data.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** All user-facing date and text formatting, in English regardless of phone language. */
object Format {
    private val EN = Locale.ENGLISH
    private val long = DateTimeFormatter.ofPattern("EEEE, d MMMM", EN)
    private val longYear = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", EN)
    private val short = DateTimeFormatter.ofPattern("EEE d MMM", EN)
    private val shortYear = DateTimeFormatter.ofPattern("EEE d MMM yyyy", EN)
    private val comma = DateTimeFormatter.ofPattern("EEE, d MMM", EN)
    private val commaYear = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", EN)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", EN)

    fun daysBetween(from: LocalDate, to: LocalDate): Int = ChronoUnit.DAYS.between(from, to).toInt()

    fun days(n: Int) = if (n == 1) "1 day" else "$n days"
    fun times(n: Int) = if (n == 1) "1 time" else "$n times"

    fun every(n: Int) = if (n == 1) "Every day" else "Every $n days"

    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }

    fun weekdayShort(iso: Int): String = DayOfWeek.of(iso).getDisplayName(TextStyle.SHORT, EN)

    fun monthDayText(monthDay: Int): String = if (monthDay <= 0) "the last day" else "the ${ordinal(monthDay)}"

    /** "Once", "Every 3 days", "2 days on, 1 day off", "Mon, Wed, Fri", "Monthly on the 1st", ... */
    fun schedule(s: Schedule): String = when (s.kind) {
        RepeatKind.ONCE -> "Once"
        RepeatKind.DAYS -> every(s.every) + if (s.fromDone) "" else " (fixed)"
        RepeatKind.CYCLE -> "${s.onDays} days on, ${days(s.offDays)} off"
        RepeatKind.WEEKLY -> {
            val names = s.weekdays.sorted().joinToString(", ") { weekdayShort(it) }.ifEmpty { "Weekly" }
            when {
                s.weekdays.size == 7 && s.every == 1 -> "Every day"
                s.every == 1 -> names
                else -> "Every ${s.every} weeks: $names"
            }
        }
        RepeatKind.MONTHLY ->
            if (s.every == 1) "Monthly on ${monthDayText(s.monthDay)}"
            else "Every ${s.every} months on ${monthDayText(s.monthDay)}"
        RepeatKind.YEARLY -> "Yearly on ${s.anchor.format(dayMonth)}"
    }

    fun schedule(r: Reminder): String = schedule(r.schedule)

    /** List-row subtitle: the schedule, plus the cycle day or status where useful. */
    fun subtitle(r: Reminder, today: LocalDate): String = when {
        r.finished -> {
            val on = r.finishedOn
            if (on != null) "Done ${commaDate(on, today)}" else "Finished"
        }
        r.paused -> "Paused · ${schedule(r)}"
        r.isCycle -> "${r.schedule.onDays} on, ${r.schedule.offDays} off · ${cycleDay(r)}"
        else -> schedule(r)
    }

    /** "day 2 of 2" */
    fun cycleDay(r: Reminder): String = "day ${r.cycleDay} of ${r.schedule.onDays}"

    /** "Ends after 10 times · 3 left" or "Ends on Fri, 4 Dec" */
    fun ending(r: Reminder, today: LocalDate): String? = when (r.endKind) {
        EndKind.NEVER -> null
        EndKind.ON_DATE -> r.endDate?.let { "Ends on ${commaDate(it, today)}" }
        EndKind.AFTER_COUNT -> "Ends after ${times(r.endCount)} · ${r.timesLeft ?: 0} left"
    }

    /** "Wednesday, 25 November" */
    fun longDate(d: LocalDate, today: LocalDate): String =
        d.format(if (d.year == today.year) long else longYear)

    /** "Fri 27 Nov" */
    fun shortDate(d: LocalDate, today: LocalDate): String =
        d.format(if (d.year == today.year) short else shortYear)

    /** "Fri, 27 Nov" */
    fun commaDate(d: LocalDate, today: LocalDate): String =
        d.format(if (d.year == today.year) comma else commaYear)

    /** "Today", "Tomorrow", "Yesterday" or "Fri 27 Nov" */
    fun dayLabel(d: LocalDate, today: LocalDate): String = when (daysBetween(today, d)) {
        0 -> "Today"
        1 -> "Tomorrow"
        -1 -> "Yesterday"
        else -> shortDate(d, today)
    }

    /** "2 days late", "Today", "Tomorrow", "in 3 days" */
    fun relative(due: LocalDate, today: LocalDate): String {
        val n = daysBetween(today, due)
        return when {
            n < 0 -> "${days(-n)} late"
            n == 0 -> "Today"
            n == 1 -> "Tomorrow"
            else -> "in ${days(n)}"
        }
    }

    fun time(context: Context, t: LocalTime): String {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        return t.format(DateTimeFormatter.ofPattern(pattern, EN))
    }

    /** "Due today · every 7 days", "1 day late · Mon, Wed" or "Due today · day 2 of 2" */
    fun notificationText(r: Reminder, today: LocalDate): String {
        val n = daysBetween(today, r.nextDue)
        val status = when {
            n < 0 -> "${days(-n)} late"
            n == 0 -> "Due today"
            else -> "Due ${dayLabel(r.nextDue, today).lowercase(EN)}"
        }
        val detail = when (r.schedule.kind) {
            RepeatKind.ONCE -> null
            RepeatKind.CYCLE -> cycleDay(r)
            else -> schedule(r).replaceFirstChar { it.lowercase(EN) }
        }
        return if (detail == null) status else "$status · $detail"
    }

    /** Heads-up text: "Tomorrow at 14:00" or "Today at 14:00" or "Fri 27 Nov at 14:00" */
    fun advanceText(context: Context, r: Reminder, today: LocalDate): String =
        "${dayLabel(r.nextDue, today)} at ${time(context, r.time)}"

    /** Choices for the heads-up before a reminder, in minutes. */
    val advanceChoices = listOf(0, 15, 60, 180, 1440, 2880, 10080)

    fun advanceLabel(minutes: Int): String = when {
        minutes <= 0 -> "None"
        minutes < 60 -> "$minutes min"
        minutes < 1440 -> if (minutes == 60) "1 hour" else "${minutes / 60} hours"
        minutes == 10080 -> "1 week"
        else -> days(minutes / 1440)
    }

    /** "on time", "2 days late", "1 day early", "skipped" */
    fun completionLabel(c: Completion): String {
        if (c.skipped) return "skipped"
        val n = daysBetween(c.due, c.done)
        return when {
            n == 0 -> "on time"
            n > 0 -> "${days(n)} late"
            else -> "${days(-n)} early"
        }
    }
}
