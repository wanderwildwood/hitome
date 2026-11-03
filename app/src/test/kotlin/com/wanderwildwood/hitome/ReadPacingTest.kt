package com.wanderwildwood.hitome

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadPacingTest {

    private val sources = listOf("+com.example.mail", "+com.example.weather")
    private val minute = 60_000L

    @Test
    fun theFirstWakeAlwaysReads() {
        assertTrue(ReadPacing.wakeReads(now = 5 * minute, lastRead = 0L, lastSources = null, sources = sources))
    }

    @Test
    fun aWakeSoonAfterAReadDoesNotAskTheAppsAgain() {
        assertFalse(ReadPacing.wakeReads(now = 14 * minute, lastRead = 1, lastSources = sources, sources = sources))
    }

    @Test
    fun aWakeFifteenMinutesOnReadsAgain() {
        assertTrue(ReadPacing.wakeReads(now = 15 * minute + 1, lastRead = 1, lastSources = sources, sources = sources))
    }

    @Test
    fun choosingDifferentAppsReadsAtOnce() {
        val more = sources + "+com.example.music"
        assertTrue(ReadPacing.wakeReads(now = 2 * minute, lastRead = 1, lastSources = sources, sources = more))
    }

    @Test
    fun switchingAnAppOffReadsAtOnce() {
        val off = listOf("+com.example.mail", "-com.example.weather")
        assertTrue(ReadPacing.wakeReads(now = 2 * minute, lastRead = 1, lastSources = sources, sources = off))
    }
}
