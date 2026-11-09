package com.wanderwildwood.hitome

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlumbingTest {
    @Test
    fun thePhonesOwnPartsAreLeftOut() {
        assertTrue(Glance.isPlumbing("com.android.webview", system = true))
        assertTrue(Glance.isPlumbing("com.mediatek.gnss.nonframeworklbs", system = true))
        assertTrue(Glance.isPlumbing("com.android.settings", system = true))
    }

    @Test
    fun appsPeopleUseStay() {
        assertFalse(Glance.isPlumbing("com.mudita.alarm", system = true))
        assertFalse(Glance.isPlumbing("com.android.deskclock", system = true))
        assertFalse(Glance.isPlumbing("com.wanderwildwood.soramoyo", system = false))
    }

    @Test
    fun anInstalledAppIsAlwaysListed() {
        assertFalse(Glance.isPlumbing("com.android.webview", system = false))
    }
}
