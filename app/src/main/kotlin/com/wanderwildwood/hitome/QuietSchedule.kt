package com.wanderwildwood.hitome

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * When quiet hours are, worked out with no phone in sight so it can be tested.
 *
 * A night is named by the day it starts: quiet hours from 10 PM to 7 AM on Friday run from
 * Friday 10 PM to Saturday 7 AM. An end at or before the start is the next morning, and an end
 * equal to the start is a whole day. Each start and end is read on the wall clock of the day it
 * falls on, so on the night the clocks change the night is an hour shorter or longer, and a
 * start in the hour skipped in spring comes at the first minute after it.
 */
data class QuietSchedule(
    val start: LocalTime,
    val end: LocalTime,
    val days: Set<DayOfWeek>,
) {
    /** Whether [now] falls in quiet hours: a night that started today, or one that started yesterday and has not ended. */
    fun inside(now: ZonedDateTime): Boolean {
        val today = now.toLocalDate()
        return listOf(today.minusDays(1), today).any { day ->
            val (from, to) = window(day, now)
            day.dayOfWeek in days && !now.isBefore(from) && now.isBefore(to)
        }
    }

    /**
     * The next moment after [now] when quiet hours begin or end, or null when they never do
     * (no day chosen, or every day all day). A start that only joins on to the night before is
     * not a change, and is passed over.
     */
    fun nextChange(now: ZonedDateTime): ZonedDateTime? {
        if (days.isEmpty()) return null
        val quietNow = inside(now)
        val today = now.toLocalDate()
        return (-1L..8L)
            .map { today.plusDays(it) }
            .filter { it.dayOfWeek in days }
            .flatMap { window(it, now).toList() }
            .filter { it.isAfter(now) }
            .sorted()
            .firstOrNull { inside(it) != quietNow }
    }

    /** The night that starts on [day], in [now]'s time zone. */
    private fun window(day: LocalDate, now: ZonedDateTime): Pair<ZonedDateTime, ZonedDateTime> {
        val zone = now.zone
        val from = day.atTime(start).atZone(zone)
        val endDay = if (end.isAfter(start)) day else day.plusDays(1)
        return from to endDay.atTime(end).atZone(zone)
    }

    /** How the settings keep it: times as minutes after midnight, days as bits. */
    companion object {
        fun minutes(m: Int): LocalTime = LocalTime.of((m / 60).coerceIn(0, 23), (m % 60).coerceIn(0, 59))

        /** Monday is bit 0, Sunday bit 6. */
        fun bits(days: Set<DayOfWeek>): Int = days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }
        fun daysOf(bits: Int): Set<DayOfWeek> = DayOfWeek.values().filter { bits and (1 shl (it.value - 1)) != 0 }.toSet()
    }
}
