package com.wanderwildwood.hitome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountSwitchTest {

    private val messaging = "com.wanderwildwood.kotozute"
    private val mailbox = "com.wanderwildwood.tayori"

    @Test
    fun countsAreOnUntilSwitchedOffHere() {
        assertTrue(Glance.shownHere(messaging, on = emptySet(), off = emptySet()))
        assertTrue(Glance.shownHere(mailbox, on = emptySet(), off = emptySet()))
        assertFalse(Glance.shownHere(messaging, on = emptySet(), off = setOf(messaging)))
        // One switched off leaves the other alone.
        assertTrue(Glance.shownHere(mailbox, on = emptySet(), off = setOf(messaging)))
    }

    @Test
    fun optInSourcesStillWaitForTheirSwitch() {
        assertFalse(Glance.shownHere(Glance.BITCOIN, on = emptySet(), off = emptySet()))
        assertTrue(Glance.shownHere(Glance.BITCOIN, on = setOf(Glance.BITCOIN), off = emptySet()))
        // The off set is only for the counts.
        assertFalse(Glance.shownHere(Glance.BITCOIN, on = emptySet(), off = setOf(Glance.BITCOIN)))
    }

    @Test
    fun otherSourcesAreOnUntilSwitchedOffHere() {
        assertTrue(Glance.shownHere(Glance.SKY, on = emptySet(), off = emptySet()))
        assertFalse(Glance.shownHere(Glance.SKY, on = emptySet(), off = setOf(Glance.SKY)))
    }

    @Test
    fun eachCountHasADoorToItsOwnSwitch() {
        assertEquals(Glance.COUNTS.toSet(), Glance.OWN_SWITCH_ACTIONS.keys)
        Glance.OWN_SWITCH_ACTIONS.forEach { (pkg, action) -> assertTrue(action.startsWith("$pkg.action.")) }
    }
}
