package com.wanderwildwood.hitome

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A text counted on Messaging's line is not counted again under the phone's own texting app. */
class RepeatsMessagingTest {

    private val messaging = "com.wanderwildwood.kotozute"
    private val mudita = "com.mudita.messages"

    @Test
    fun otherTextingAppsAreLeftOutWhileMessagingIsTheTextingApp() {
        Notices.OTHER_TEXTING.forEach { assertTrue(it, Notices.repeatsMessaging(it, messaging)) }
    }

    @Test
    fun theyCountWhenAnotherAppIsTheTextingApp() {
        assertFalse(Notices.repeatsMessaging(mudita, mudita))
        assertFalse(Notices.repeatsMessaging(mudita, "com.google.android.apps.messaging"))
    }

    @Test
    fun theyCountWhenTheTextingAppIsUnknown() {
        assertFalse(Notices.repeatsMessaging(mudita, null))
    }

    @Test
    fun otherChosenAppsAlwaysCount() {
        assertFalse(Notices.repeatsMessaging("org.thoughtcrime.securesms", messaging))
        assertFalse(Notices.repeatsMessaging("com.wanderwildwood.tokei", messaging))
    }
}
