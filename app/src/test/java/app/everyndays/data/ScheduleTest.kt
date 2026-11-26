package app.everyndays.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ScheduleTest {
    private fun d(s: String) = LocalDate.parse(s)

    private fun reminder(schedule: Schedule, nextDue: String, streak: Int = 0) = Reminder(
        name = "Test",
        schedule = schedule,
        time = LocalTime.of(20, 0),
        nextDue = d(nextDue),
        streak = streak,
    )

    // 2026-11-23 is a Monday.

    @Test fun everyNDaysFromDone() {
        val s = Schedule(RepeatKind.DAYS, every = 3, fromDone = true, anchor = d("2026-11-23"))
        assertEquals(d("2026-11-29"), s.next(due = d("2026-11-23"), done = d("2026-11-26"), streak = 0)!!.first)
        // Done early also counts from the day it was done.
        assertEquals(d("2026-11-25"), s.next(due = d("2026-11-23"), done = d("2026-11-22"), streak = 0)!!.first)
    }

    @Test fun everyNDaysFixedRhythm() {
        val s = Schedule(RepeatKind.DAYS, every = 3, fromDone = false, anchor = d("2026-11-23"))
        // On time: next slot.
        assertEquals(d("2026-11-26"), s.next(d("2026-11-23"), d("2026-11-23"), 0)!!.first)
        // One day late: still the same grid.
        assertEquals(d("2026-11-26"), s.next(d("2026-11-23"), d("2026-11-24"), 0)!!.first)
        // Late past a slot: the slot after that.
        assertEquals(d("2026-11-29"), s.next(d("2026-11-23"), d("2026-11-26"), 0)!!.first)
        // Early: the slot after the due date.
        assertEquals(d("2026-11-26"), s.next(d("2026-11-23"), d("2026-11-21"), 0)!!.first)
    }

    @Test fun cycleTwoOnOneOff() {
        val s = Schedule(RepeatKind.CYCLE, onDays = 2, offDays = 1, anchor = d("2026-11-23"))
        // Day 1 done Monday -> Tuesday is day 2.
        assertEquals(d("2026-11-24") to 1, s.next(d("2026-11-23"), d("2026-11-23"), streak = 0))
        // Day 2 done Tuesday -> skip Wednesday -> Thursday.
        assertEquals(d("2026-11-26") to 0, s.next(d("2026-11-24"), d("2026-11-24"), streak = 1))
    }

    @Test fun weeklyOnWeekdays() {
        val s = Schedule(RepeatKind.WEEKLY, every = 1, weekdays = setOf(1, 3, 5), anchor = d("2026-11-23"))
        assertEquals(d("2026-11-23"), s.firstOnOrAfter(d("2026-11-23"))) // Mon
        assertEquals(d("2026-11-25"), s.next(d("2026-11-23"), d("2026-11-23"), 0)!!.first) // -> Wed
        assertEquals(d("2026-11-30"), s.next(d("2026-11-27"), d("2026-11-27"), 0)!!.first) // Fri -> Mon
        // Done late (Wed's done on Thu): the next is still Fri, not shifted.
        assertEquals(d("2026-11-27"), s.next(d("2026-11-25"), d("2026-11-26"), 0)!!.first)
    }

    @Test fun everyTwoWeeks() {
        val s = Schedule(RepeatKind.WEEKLY, every = 2, weekdays = setOf(2), anchor = d("2026-11-23"))
        assertEquals(d("2026-11-24"), s.firstOnOrAfter(d("2026-11-23")))
        assertEquals(d("2026-12-08"), s.next(d("2026-11-24"), d("2026-11-24"), 0)!!.first)
        assertFalse(s.occursOn(d("2026-12-01")))
    }

    @Test fun monthlyOnDayAndLastDay() {
        val first = Schedule(RepeatKind.MONTHLY, every = 1, monthDay = 1, anchor = d("2026-11-23"))
        assertEquals(d("2026-12-01"), first.firstOnOrAfter(d("2026-11-23")))

        val day31 = Schedule(RepeatKind.MONTHLY, every = 1, monthDay = 31, anchor = d("2027-01-01"))
        assertEquals(d("2027-01-31"), day31.firstOnOrAfter(d("2027-01-01")))
        assertEquals(d("2027-02-28"), day31.next(d("2027-01-31"), d("2027-01-31"), 0)!!.first)

        val last = Schedule(RepeatKind.MONTHLY, every = 1, monthDay = 0, anchor = d("2028-01-01"))
        assertEquals(d("2028-02-29"), last.firstOnOrAfter(d("2028-02-01")))
    }

    @Test fun everyThreeMonths() {
        val s = Schedule(RepeatKind.MONTHLY, every = 3, monthDay = 15, anchor = d("2026-11-01"))
        assertEquals(d("2026-11-15"), s.firstOnOrAfter(d("2026-11-01")))
        assertEquals(d("2027-02-15"), s.next(d("2026-11-15"), d("2026-11-15"), 0)!!.first)
    }

    @Test fun yearlyIncludingLeapDay() {
        val s = Schedule(RepeatKind.YEARLY, anchor = d("2026-02-14"))
        assertEquals(d("2027-02-14"), s.firstOnOrAfter(d("2026-11-26")))
        val leap = Schedule(RepeatKind.YEARLY, anchor = d("2028-02-29"))
        assertEquals(d("2029-02-28"), leap.next(d("2028-02-29"), d("2028-02-29"), 0)!!.first)
    }

    @Test fun onceFinishesWhenDone() {
        val r = reminder(Schedule(RepeatKind.ONCE, anchor = d("2026-11-26")), "2026-11-26")
        assertNull(r.nextIfDoneOn(d("2026-11-26")))
        val done = r.completed(d("2026-11-26"))
        assertTrue(done.finished)
        assertEquals(1, done.history.size)
    }

    @Test fun endsAfterCount() {
        val r = reminder(Schedule(RepeatKind.DAYS, every = 1, anchor = d("2026-11-23")), "2026-11-23")
            .copy(endKind = EndKind.AFTER_COUNT, endCount = 2)
        val once = r.completed(d("2026-11-23"))
        assertFalse(once.finished)
        assertEquals(1, once.timesLeft)
        assertTrue(once.completed(d("2026-11-24")).finished)
    }

    @Test fun endsOnDate() {
        val r = reminder(Schedule(RepeatKind.DAYS, every = 2, anchor = d("2026-11-23")), "2026-11-23")
            .copy(endKind = EndKind.ON_DATE, endDate = d("2026-11-26"))
        val a = r.completed(d("2026-11-23")) // next 25th, before end
        assertFalse(a.finished)
        assertTrue(a.completed(d("2026-11-25")).finished) // next would be 27th, after end
    }

    @Test fun skipKeepsHistoryAndMovesOn() {
        val r = reminder(Schedule(RepeatKind.DAYS, every = 3, anchor = d("2026-11-23")), "2026-11-23")
        val s = r.completed(d("2026-11-23"), skipped = true)
        assertEquals(d("2026-11-26"), s.nextDue)
        assertTrue(s.history.single().skipped)
        assertNull(s.lastDone)
    }

    @Test fun resumeMovesOverdueForward() {
        val days = reminder(Schedule(RepeatKind.DAYS, every = 3, anchor = d("2026-11-01")), "2026-11-10")
            .copy(paused = true)
        assertEquals(d("2026-11-26"), days.resumed(d("2026-11-26")).nextDue)

        val weekly = reminder(
            Schedule(RepeatKind.WEEKLY, weekdays = setOf(5), anchor = d("2026-11-02")), "2026-11-06",
        ).copy(paused = true)
        assertEquals(d("2026-11-27"), weekly.resumed(d("2026-11-26")).nextDue)
    }
}
