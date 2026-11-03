package com.wanderwildwood.hitome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Apps that keep a `<package>.glance` provider are found without a list naming them. */
class DiscoveryTest {

    private val self = "com.wanderwildwood.hitome"
    private val tuner = "com.wanderwildwood.onsa"
    private val clock = "com.wanderwildwood.tokei"

    /** Each app's provider owner, as the package manager would say: only these keep one. */
    private val owners = mapOf(
        tuner to tuner,
        clock to clock,
        Glance.SKY to Glance.SKY,
        Glance.MUSIC_K to Glance.MUSIC_K,
        self to self,
        // An app whose name is taken by a provider of another's.
        "com.example.borrowed" to "com.example.elsewhere",
    )

    private fun find(vararg launchable: String) = Glance.discovered(launchable.toList(), self) { owners[it] }

    @Test fun appsWithTheProviderAreFound() {
        assertEquals(listOf(tuner, clock), find(clock, tuner, "com.example.plain"))
    }

    @Test fun knownAppsGlanceItselfAndMusicBoxKAreNotFoundAgain() {
        assertEquals(emptyList<String>(), find(Glance.SKY, Glance.MUSIC_K, self))
    }

    @Test fun aProviderMustBeTheAppsOwn() {
        assertEquals(emptyList<String>(), find("com.example.borrowed"))
    }

    @Test fun foundOnceEvenWithSeveralLauncherEntries() {
        assertEquals(listOf(tuner), find(tuner, tuner))
    }

    @Test fun foundAppsAreOffUntilSwitchedOn() {
        assertTrue(Glance.isOptIn(tuner))
        assertTrue(Glance.isOptIn(Glance.BITCOIN))
        assertFalse(Glance.isOptIn(Glance.SKY))
        assertFalse(Glance.isOptIn(Glance.UNREAD))
        assertFalse(Glance.isOptIn("com.wanderwildwood.kotozute"))
    }

    @Test fun withNothingFoundTheOrderIsAsBefore() {
        assertEquals(Glance.DEFAULT_ORDER, Glance.order(null, null, emptyList()))
        val saved = (listOf(Glance.UNREAD) + Glance.STACKED).joinToString(",")
        assertEquals(listOf(Glance.UNREAD) + Glance.STACKED, Glance.order(saved, null, emptyList()))
        assertEquals(listOf(Glance.SKY) + (Glance.DEFAULT_ORDER - Glance.SKY), Glance.order(null, "WEATHER", emptyList()))
    }

    @Test fun aFoundAppTakesItsPlaceBeforeTheCounts() {
        assertEquals(Glance.STACKED + tuner + Glance.UNREAD, Glance.order(null, null, listOf(tuner)))
        val saved = Glance.DEFAULT_ORDER.joinToString(",")
        assertEquals(Glance.STACKED + tuner + Glance.UNREAD, Glance.order(saved, null, listOf(tuner)))
    }

    @Test fun aMovedFoundAppKeepsItsPlace() {
        val saved = (listOf(tuner) + Glance.DEFAULT_ORDER).joinToString(",")
        assertEquals(listOf(tuner) + Glance.DEFAULT_ORDER, Glance.order(saved, null, listOf(tuner)))
    }

    @Test fun anAppGoneFromThePhoneDropsOut() {
        val saved = (listOf(tuner) + Glance.DEFAULT_ORDER).joinToString(",")
        assertEquals(Glance.DEFAULT_ORDER, Glance.order(saved, null, emptyList()))
    }

    @Test fun everyFoundAppIsReadInTheOrder() {
        val order = Glance.order(null, null, listOf(tuner, clock))
        assertTrue(Glance.sources(order).containsAll(listOf(tuner, clock)))
    }
}
