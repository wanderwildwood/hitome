package com.wanderwildwood.hitome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class QuietScheduleTest {

    private val zone = ZoneId.of("America/New_York")
    private val everyDay = DayOfWeek.values().toSet()
    private val night = QuietSchedule(LocalTime.of(22, 0), LocalTime.of(7, 0), everyDay)
    private val afternoon = QuietSchedule(LocalTime.of(13, 0), LocalTime.of(15, 30), everyDay)

    /** 2026-11-09 is a Monday. */
    private fun at(day: Int, hour: Int, minute: Int = 0, month: Int = 11): ZonedDateTime =
        LocalDateTime.of(2026, month, day, hour, minute).atZone(zone)

    @Test
    fun aDaytimeWindowIsQuietOnlyBetweenItsTimes() {
        assertFalse(afternoon.inside(at(9, 12, 59)))
        assertTrue(afternoon.inside(at(9, 13, 0)))
        assertTrue(afternoon.inside(at(9, 15, 29)))
        assertFalse(afternoon.inside(at(9, 15, 30)))
    }

    @Test
    fun aNightCrossesMidnight() {
        assertFalse(night.inside(at(9, 21, 59)))
        assertTrue(night.inside(at(9, 22, 0)))
        assertTrue(night.inside(at(9, 23, 59)))
        assertTrue(night.inside(at(10, 0, 0)))
        assertTrue(night.inside(at(10, 6, 59)))
        assertFalse(night.inside(at(10, 7, 0)))
        assertFalse(night.inside(at(10, 12, 0)))
    }

    @Test
    fun aNightBelongsToTheDayItStarts() {
        val fridays = night.copy(days = setOf(FRIDAY))
        // Friday the 13th, 11 PM, and the Saturday morning after: Friday's night.
        assertTrue(fridays.inside(at(13, 23)))
        assertTrue(fridays.inside(at(14, 3)))
        // Saturday night is not chosen, nor the small hours of Friday (Thursday's night).
        assertFalse(fridays.inside(at(14, 23)))
        assertFalse(fridays.inside(at(15, 3)))
        assertFalse(fridays.inside(at(13, 3)))
    }

    @Test
    fun weekendNightsOnly() {
        val weekend = night.copy(days = setOf(SATURDAY, SUNDAY))
        assertFalse(weekend.inside(at(13, 23))) // Friday night
        assertTrue(weekend.inside(at(14, 23))) // Saturday night
        assertTrue(weekend.inside(at(16, 6))) // Monday morning, Sunday's night
        assertFalse(weekend.inside(at(16, 23))) // Monday night
    }

    @Test
    fun noDaysIsNeverQuietAndNeverChanges() {
        val none = night.copy(days = emptySet())
        assertFalse(none.inside(at(9, 23)))
        assertNull(none.nextChange(at(9, 23)))
    }

    @Test
    fun theNextChangeIsTheStartOutsideAndTheEndInside() {
        assertEquals(at(9, 22), night.nextChange(at(9, 12)))
        assertEquals(at(10, 7), night.nextChange(at(9, 22)))
        assertEquals(at(10, 7), night.nextChange(at(10, 2)))
        assertEquals(at(10, 22), night.nextChange(at(10, 7)))
    }

    @Test
    fun theNextChangeSkipsDaysNotChosen() {
        val mondays = night.copy(days = setOf(MONDAY))
        // Tuesday morning after Monday's night: next is the following Monday at 10 PM.
        assertEquals(at(16, 22), mondays.nextChange(at(10, 7)))
    }

    @Test
    fun nightsBackToBackAllDayNeverChange() {
        val always = QuietSchedule(LocalTime.of(8, 0), LocalTime.of(8, 0), everyDay)
        assertTrue(always.inside(at(9, 3)))
        assertTrue(always.inside(at(9, 8)))
        assertNull(always.nextChange(at(9, 3)))
    }

    @Test
    fun anEndEqualToTheStartIsAWholeDay() {
        val mondayAllDay = QuietSchedule(LocalTime.of(8, 0), LocalTime.of(8, 0), setOf(MONDAY))
        assertTrue(mondayAllDay.inside(at(9, 8)))
        assertTrue(mondayAllDay.inside(at(10, 7, 59)))
        assertFalse(mondayAllDay.inside(at(10, 8)))
        assertEquals(at(10, 8), mondayAllDay.nextChange(at(9, 12)))
    }

    @Test
    fun theSpringNightIsAnHourShorter() {
        // 2026-03-08: clocks go from 2:00 to 3:00 in New York. Saturday's night still ends at 7.
        val start = at(7, 22, month = 3)
        val end = night.nextChange(start)!!
        assertEquals(at(8, 7, month = 3), end)
        assertEquals(Duration.ofHours(8), Duration.between(start, end))
        assertTrue(night.inside(at(8, 3, 30, month = 3)))
        assertFalse(night.inside(at(8, 7, 0, month = 3)))
    }

    @Test
    fun theAutumnNightIsAnHourLonger() {
        // 2026-11-01: clocks go from 2:00 back to 1:00.
        val start = at(31, 22, month = 10)
        val end = night.nextChange(start)!!
        assertEquals(at(1, 7), end)
        assertEquals(Duration.ofHours(10), Duration.between(start, end))
        assertTrue(night.inside(at(1, 1, 30)))
    }

    @Test
    fun aStartInTheSkippedHourComesJustAfterIt() {
        val small = QuietSchedule(LocalTime.of(2, 30), LocalTime.of(5, 0), everyDay)
        val saturdayNoon = at(7, 12, month = 3)
        // 2:30 does not exist on 8 March; it begins at 3:30, the same instant as 2:30 EST.
        assertEquals(LocalDateTime.of(2026, 3, 8, 3, 30).atZone(zone), small.nextChange(saturdayNoon))
        assertFalse(small.inside(at(8, 1, 59, month = 3)))
        assertTrue(small.inside(at(8, 3, 31, month = 3)))
    }

    @Test
    fun daysRoundTripThroughTheirStoredBits() {
        val some = setOf(MONDAY, FRIDAY, SUNDAY)
        assertEquals(some, QuietSchedule.daysOf(QuietSchedule.bits(some)))
        assertEquals(everyDay, QuietSchedule.daysOf(0b111_1111))
        assertEquals(LocalTime.of(22, 0), QuietSchedule.minutes(22 * 60))
    }
}
