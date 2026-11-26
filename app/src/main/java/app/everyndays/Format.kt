package app.everyndays

import android.content.Context
import android.text.format.DateFormat
import app.everyndays.data.Completion
import app.everyndays.data.Reminder
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
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

    fun daysBetween(from: LocalDate, to: LocalDate): Int = ChronoUnit.DAYS.between(from, to).toInt()

    fun days(n: Int) = if (n == 1) "1 day" else "$n days"

    fun every(n: Int) = if (n == 1) "Every day" else "Every $n days"

    /** "Every 3 days" or "2 days on, 1 day off" */
    fun schedule(r: Reminder): String =
        if (r.isCycle) "${r.onDays} days on, ${days(r.offDays)} off" else every(r.offDays + 1)

    /** Compact form for list rows: "Every 3 days" or "2 on, 1 off · day 1 of 2" */
    fun scheduleShort(r: Reminder): String =
        if (r.isCycle) "${r.onDays} on, ${r.offDays} off · ${cycleDay(r)}" else every(r.offDays + 1)

    /** "day 2 of 2" */
    fun cycleDay(r: Reminder): String = "day ${r.cycleDay} of ${r.onDays}"

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

    /** "Due today · every 7 days", "1 day late · every 2 days" or "Due today · day 2 of 2" */
    fun notificationText(r: Reminder, today: LocalDate): String {
        val n = daysBetween(today, r.nextDue)
        val status = when {
            n < 0 -> "${days(-n)} late"
            n == 0 -> "Due today"
            else -> "Due ${dayLabel(r.nextDue, today).lowercase(EN)}"
        }
        val detail = if (r.isCycle) cycleDay(r) else every(r.offDays + 1).lowercase(EN)
        return "$status · $detail"
    }

    /** "on time", "2 days late", "1 day early" */
    fun completionLabel(c: Completion): String {
        val n = daysBetween(c.due, c.done)
        return when {
            n == 0 -> "on time"
            n > 0 -> "${days(n)} late"
            else -> "${days(-n)} early"
        }
    }
}
