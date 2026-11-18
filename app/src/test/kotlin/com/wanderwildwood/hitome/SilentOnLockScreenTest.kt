package com.wanderwildwood.hitome

import android.app.NotificationManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** With the phone set to hide silent notifications on the lock screen, Glance leaves them out too. */
class SilentOnLockScreenTest {

    @Test
    fun silentOnesAreLeftOutWhenThePhoneHidesThem() {
        assertTrue(Notices.hiddenAsSilent(false, false, NotificationManager.IMPORTANCE_LOW))
        assertTrue(Notices.hiddenAsSilent(false, false, NotificationManager.IMPORTANCE_MIN))
        assertTrue(Notices.hiddenAsSilent(false, true, NotificationManager.IMPORTANCE_DEFAULT))
    }

    @Test
    fun onesThatSoundStay() {
        assertFalse(Notices.hiddenAsSilent(false, false, NotificationManager.IMPORTANCE_DEFAULT))
        assertFalse(Notices.hiddenAsSilent(false, false, NotificationManager.IMPORTANCE_HIGH))
    }

    @Test
    fun everythingStaysWhenThePhoneShowsSilentOnes() {
        assertFalse(Notices.hiddenAsSilent(true, true, NotificationManager.IMPORTANCE_MIN))
        assertFalse(Notices.hiddenAsSilent(true, false, NotificationManager.IMPORTANCE_LOW))
    }
}
