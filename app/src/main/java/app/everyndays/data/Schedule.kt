package app.everyndays.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class RepeatKind { ONCE, DAYS, CYCLE, WEEKLY, MONTHLY, YEARLY }

/**
 * How a reminder repeats.
 *
 * - ONCE: a single date.
 * - DAYS: every [every] days, counted from the day it was done ([fromDone]) or on a fixed rhythm.
 * - CYCLE: [onDays] days in a row, then [offDays] days off, counted from the days it was done.
 * - WEEKLY: on [weekdays], every [every] weeks (the week of [anchor] counts as week 0).
 * - MONTHLY: on [monthDay] (0 = last day), every [every] months (the month of [anchor] counts as month 0).
 * - YEARLY: every year on the month and day of [anchor] (29 Feb falls on 28 Feb in other years).
 *
 * WEEKLY, MONTHLY and YEARLY are tied to the calendar: doing one late never shifts the next one.
 */
data class Schedule(
    val kind: RepeatKind,
    val every: Int = 1,
    val fromDone: Boolean = true,
    val onDays: Int = 2,
    val offDays: Int = 1,
    /** ISO day numbers, 1 = Monday ... 7 = Sunday. */
    val weekdays: Set<Int> = emptySet(),
    /** 1..31, or 0 for the last day of the month. A day past the month's end means its last day. */
    val monthDay: Int = 1,
    val anchor: LocalDate,
) {
    val isRecurring: Boolean get() = kind != RepeatKind.ONCE

    val isCalendar: Boolean
        get() = kind == RepeatKind.WEEKLY || kind == RepeatKind.MONTHLY || kind == RepeatKind.YEARLY

    private val safeEvery: Int get() = every.coerceAtLeast(1)

    private val effectiveWeekdays: Set<Int>
        get() = weekdays.ifEmpty { setOf(anchor.dayOfWeek.value) }

    /** For calendar kinds: whether [d] is one of its dates. Other kinds: always true. */
    fun occursOn(d: LocalDate): Boolean = when (kind) {
        RepeatKind.WEEKLY ->
            d.dayOfWeek.value in effectiveWeekdays &&
                Math.floorMod(weeksBetween(anchor, d), safeEvery.toLong()) == 0L

        RepeatKind.MONTHLY ->
            Math.floorMod(monthsBetween(anchor, d), safeEvery) == 0 &&
                d.dayOfMonth == dayInMonth(YearMonth.from(d))

        RepeatKind.YEARLY ->
            d.month == anchor.month && d.dayOfMonth == minOf(anchor.dayOfMonth, d.lengthOfMonth())

        else -> true
    }

    /** The first date on or after [d] that this schedule falls on. */
    fun firstOnOrAfter(d: LocalDate): LocalDate {
        if (!isCalendar) return d
        var x = d
        // At most ~4 years for any valid schedule; the bound only guards against bad data.
        repeat(5000) {
            if (occursOn(x)) return x
            x = x.plusDays(1)
        }
        return d
    }

    /**
     * The next due date (and the cycle streak) after the occurrence due on [due] was done,
     * or skipped, on [done]. Null when there is no next one (one-time reminders).
     */
    fun next(due: LocalDate, done: LocalDate, streak: Int): Pair<LocalDate, Int>? = when (kind) {
        RepeatKind.ONCE -> null

        RepeatKind.DAYS -> if (fromDone) {
            done.plusDays(safeEvery.toLong()) to 0
        } else {
            // Fixed rhythm: the first slot on the due + k*every grid that is after both dates.
            val k = if (done.isBefore(due)) 1L else ChronoUnit.DAYS.between(due, done) / safeEvery + 1
            due.plusDays(k * safeEvery) to 0
        }

        RepeatKind.CYCLE -> {
            val doneInRun = streak + 1
            if (doneInRun < onDays.coerceAtLeast(1)) {
                done.plusDays(1) to doneInRun
            } else {
                done.plusDays(offDays.coerceAtLeast(0) + 1L) to 0
            }
        }

        RepeatKind.WEEKLY, RepeatKind.MONTHLY, RepeatKind.YEARLY ->
            firstOnOrAfter(maxOf(due, done).plusDays(1)) to 0
    }

    private fun dayInMonth(ym: YearMonth): Int =
        if (monthDay <= 0) ym.lengthOfMonth() else minOf(monthDay, ym.lengthOfMonth())

    companion object {
        fun weeksBetween(a: LocalDate, b: LocalDate): Long {
            val monday = TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
            return ChronoUnit.WEEKS.between(a.with(monday), b.with(monday))
        }

        fun monthsBetween(a: LocalDate, b: LocalDate): Int =
            (b.year * 12 + b.monthValue) - (a.year * 12 + a.monthValue)
    }
}
