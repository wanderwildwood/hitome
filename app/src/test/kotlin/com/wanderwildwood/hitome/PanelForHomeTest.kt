package com.wanderwildwood.hitome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PanelForHomeTest {

    @Test
    fun countsTakeTheirOwnPackagesNotTheirPlace() {
        val sources = Glance.sources(listOf(Glance.UNREAD, Glance.SKY))
        assertEquals(Glance.COUNTS + Glance.SKY, sources)
        assertEquals(Glance.SOURCES.toSet(), Glance.sources(Glance.DEFAULT_ORDER).toSet())
    }

    @Test
    fun aSourceWithNoLinesHasOneRowForItsStatus() {
        val rows = PanelForHome.rowsFor(Glance.SKY, PanelForHome.Answer(PanelForHome.OFF_APP))
        assertEquals(1, rows.size)
        assertEquals(listOf(Glance.SKY, null, null, null, 0, PanelForHome.OFF_APP), rows[0].toList())
    }

    @Test
    fun theHeadingIsOnTheFirstRowOnly() {
        val section = Glance.Section(
            "com.wanderwildwood.kotozute",
            "Messages",
            listOf(Glance.Line("3", "unread", true), Glance.Line(null, "second", false)),
        )
        val rows = PanelForHome.rowsFor("com.wanderwildwood.kotozute", PanelForHome.Answer(PanelForHome.ON, section))
        assertEquals(2, rows.size)
        assertEquals(listOf("com.wanderwildwood.kotozute", "Messages", "3", "unread", 1, PanelForHome.ON), rows[0].toList())
        assertNull(rows[1][1])
        assertEquals("com.wanderwildwood.kotozute", rows[1][0])
    }

    @Test
    fun onlyInstalledAppsAreListed() {
        val installed = mapOf(Glance.SKY to true, "com.wanderwildwood.fukuyaku" to false)
        assertEquals(true, Glance.listed(Glance.SKY, installed, false))
        assertEquals(false, Glance.listed("com.wanderwildwood.fukuyaku", installed, false))
        assertEquals(false, Glance.listed("com.wanderwildwood.jimeikin", installed, false))
        // Calendar's place is Today's events when Calendar itself is missing.
        assertEquals(true, Glance.listed(Glance.CALENDAR, installed, false))
    }

    @Test
    fun theCountsStayWhileMessagingOrEmailIsHereOrAccessIsOn() {
        assertEquals(false, Glance.listed(Glance.UNREAD, emptyMap(), false))
        assertEquals(true, Glance.listed(Glance.UNREAD, emptyMap(), true))
        assertEquals(true, Glance.listed(Glance.UNREAD, mapOf("com.wanderwildwood.tayori" to true), false))
    }

    private fun music(pkg: String, playing: Boolean?) =
        Glance.Section(pkg, null, listOf(Glance.Line("Playing", "Song", false)), key = Glance.MUSIC, playing = playing)

    @Test
    fun musicBoxAndMusicBoxKShareOnePlace() {
        assertEquals(listOf(Glance.MUSIC, Glance.MUSIC_K), Glance.providers(Glance.MUSIC))
        assertEquals(listOf(Glance.SKY), Glance.providers(Glance.SKY))
        assertEquals("com.wanderwildwood.jimeikin.k", Glance.MUSIC_K)
    }

    @Test
    fun theOnePlayingIsShownWhenBothAreInstalled() {
        val full = music(Glance.MUSIC, false)
        val k = music(Glance.MUSIC_K, true)
        assertEquals(Glance.MUSIC_K, Glance.pickPlaying(listOf(full, k))?.packageName)
        assertEquals(Glance.MUSIC, Glance.pickPlaying(listOf(music(Glance.MUSIC, true), k))?.packageName)
        // Neither says; the first with lines wins. One alone is shown whether or not it says.
        assertEquals(Glance.MUSIC, Glance.pickPlaying(listOf(music(Glance.MUSIC, null), music(Glance.MUSIC_K, null)))?.packageName)
        assertEquals(Glance.MUSIC_K, Glance.pickPlaying(listOf(null, music(Glance.MUSIC_K, null)))?.packageName)
        assertNull(Glance.pickPlaying(listOf(null, null)))
    }

    @Test
    fun theAnswerForTheMusicPlaceIsTheOneWithTheSong() {
        val none = PanelForHome.Answer(PanelForHome.OFF_APP)
        val missing = PanelForHome.Answer(PanelForHome.NOT_INSTALLED)
        val kPlaying = PanelForHome.Answer(PanelForHome.ON, music(Glance.MUSIC_K, true))
        assertEquals(kPlaying, PanelForHome.chooseAnswer(listOf(PanelForHome.Answer(PanelForHome.ON, music(Glance.MUSIC, false)), kPlaying)))
        assertEquals(kPlaying, PanelForHome.chooseAnswer(listOf(missing, kPlaying)))
        // Nothing playing: the more telling status, not "not installed" while one of them is.
        assertEquals(none, PanelForHome.chooseAnswer(listOf(none, missing)))
        assertEquals(none, PanelForHome.chooseAnswer(listOf(missing, none)))
        assertEquals(missing, PanelForHome.chooseAnswer(listOf(missing, missing)))
    }
}
