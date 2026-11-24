package com.wanderwildwood.hitome

import com.wanderwildwood.hitome.LockScreenLayout.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A MuditaOS 2.0 lock screen on a 480x800 Kompakt, in round figures from 21 Nov 2026: clock and
 * date down to 272, the padlock from 687 in English, the line under it below that. In German that
 * line wraps to two lines and lifts the padlock by about one line.
 */
class LockScreenLayoutTest {

    private val screen = 800
    private val gap = 12
    private val statusBar = 48

    private fun lockScreen(padlockTop: Int, hintTop: Int, ids: Boolean = true, rows: Boolean = false) = buildList {
        add(Box(0, 800, leaf = false))                                  // the whole lock screen
        add(Box(60, 230, leaf = true, id = "com.android.systemui:id/clock"))
        add(Box(240, 272, leaf = true, id = "com.android.systemui:id/date"))
        if (rows) {
            add(Box(293, 423, leaf = false, id = "com.android.systemui:id/player_widget"))
            add(Box(300, 380, leaf = true))
            add(Box(423, 790, leaf = false, id = "com.android.systemui:id/notification_area"))
            add(Box(430, 470, leaf = true, id = "com.android.systemui:id/missed_call"))
        }
        add(Box(padlockTop - 20, 800, leaf = false))                    // the foot's own frame
        add(Box(padlockTop, padlockTop + 18, leaf = true, id = if (ids) "com.android.systemui:id/lock_icon" else null))
        add(Box(hintTop, 770, leaf = true))                             // the line under it
    }

    private val english = lockScreen(padlockTop = 687, hintTop = 720)
    private val german = lockScreen(padlockTop = 657, hintTop = 690)

    @Test fun englishPadlockIsTheFoot() {
        val foot = LockScreenLayout.footTop(english, screen)
        assertEquals(687, foot)
        assertEquals(272, LockScreenLayout.contentBottom(english, screen, own = true, foot = foot, statusBar = statusBar))
        // As before 0.1.63 (85% of the screen) or a gap above the padlock, whichever is higher.
        assertEquals(675, LockScreenLayout.ownFloor(screen, foot, gap))
    }

    @Test fun germanTwoLineHintLiftsTheFootAndThePanelStaysAboveIt() {
        val foot = LockScreenLayout.footTop(german, screen)
        assertEquals(657, foot)
        // The lifted padlock (bottom 675, above 85% of the screen) is not an item above the panel:
        // taking it for one put the panel under it, over the hint, in one-line mode.
        assertEquals(272, LockScreenLayout.contentBottom(german, screen, own = true, foot = foot, statusBar = statusBar))
        assertEquals(645, LockScreenLayout.ownFloor(screen, foot, gap))
    }

    @Test fun withoutIdsTheFootIsTheHighestSmallItemInTheLowestQuarter() {
        val noIds = lockScreen(padlockTop = 657, hintTop = 690, ids = false)
        assertEquals(657, LockScreenLayout.footTop(noIds, screen))
        // A hint of several lines lifts the padlock further; still found.
        val longHint = lockScreen(padlockTop = 610, hintTop = 640, ids = false)
        assertEquals(610, LockScreenLayout.footTop(longHint, screen))
    }

    @Test fun theIdWinsOverWhatSitsInTheLowestQuarter() {
        val padlockHigh = lockScreen(padlockTop = 560, hintTop = 600)
        assertEquals(560, LockScreenLayout.footTop(padlockHigh, screen))
    }

    @Test fun noFootSeenKeepsTheOldLimit() {
        val bare = listOf(Box(240, 272, leaf = true))
        assertNull(LockScreenLayout.footTop(bare, screen))
        assertEquals(680, LockScreenLayout.ownFloor(screen, null, gap))
        assertEquals(272, LockScreenLayout.contentBottom(bare, screen, own = true, foot = null, statusBar = statusBar))
    }

    @Test fun playerAndNotificationRowsStillCountAboveTheFoot() {
        val crowded = lockScreen(padlockTop = 657, hintTop = 690, rows = true)
        val foot = LockScreenLayout.footTop(crowded, screen)
        assertEquals(657, foot)
        // The missed-call row ends lowest; the notification area's frame reaches past the foot
        // and is left out, as are the whole screen's and the foot's own frames.
        assertEquals(470, LockScreenLayout.contentBottom(crowded, screen, own = true, foot = foot, statusBar = statusBar))
    }

    @Test fun muditaOs1CountsOnlySingleItemsInTheUpperPart() {
        val oneX = listOf(
            Box(0, 800, leaf = false),
            Box(240, 272, leaf = true),
            Box(280, 400, leaf = false),                                 // the music widget's frame
            Box(290, 390, leaf = true),
            Box(687, 705, leaf = true),
        )
        assertEquals(390, LockScreenLayout.contentBottom(oneX, screen, own = false, foot = null, statusBar = statusBar))
    }

    @Test fun nothingReadIsNull() {
        assertNull(LockScreenLayout.contentBottom(emptyList(), screen, own = true, foot = null, statusBar = statusBar))
    }
}
