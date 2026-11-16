package com.wanderwildwood.hitome.ui

import com.wanderwildwood.hitome.Glance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelFitTest {
    private fun section(key: String, lines: Int, heading: String? = "Today") =
        Glance.Section(key, heading, List(lines) { Glance.Line(null, "line $it", false) }, key = key)

    private val order = listOf(Glance.CALENDAR, "memo", "weather")

    @Test fun everythingFits_nothingIsCut() {
        val stacked = listOf(section(Glance.CALENDAR, 3), section("memo", 4))
        assertEquals(listOf(3, 4), shownLines(stacked, order, 0, 400))
    }

    @Test fun aLongTaskListIsCut_notLeftOut() {
        // Calendar 2 lines, weather 1, tasks 5: room for all but two lines.
        val stacked = listOf(section(Glance.CALENDAR, 2), section("memo", 5), section("weather", 1, heading = null))
        val whole = RULE_DP + stacked.sumOf { RULE_DP + PAD_DP + (if (it.heading != null) HEADING_DP else 0) + it.lines.size * LINE_DP }
        assertEquals(listOf(2, 3, 1), shownLines(stacked, order, 0, whole - 2 * LINE_DP))
        assertTrue("memo" in chosen(stacked, emptyList(), order, whole - 2 * LINE_DP))
    }

    @Test fun thePartEarlierInTheOrderKeepsMoreLines() {
        val stacked = listOf(section("memo", 5), section(Glance.CALENDAR, 5))
        val least = RULE_DP + 2 * (RULE_DP + PAD_DP + HEADING_DP + 2 * LINE_DP)
        assertEquals(listOf(2, 4), shownLines(stacked, order, 0, least + 2 * LINE_DP))
    }
}
