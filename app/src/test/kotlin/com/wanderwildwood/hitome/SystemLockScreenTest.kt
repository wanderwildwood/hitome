package com.wanderwildwood.hitome

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemLockScreenTest {
    @Test fun muditaOs1HasNoPlayerForOtherApps() {
        assertFalse(SystemLockScreen.drawsItsOwnFor("MuditaOS K 1.6.1"))
        assertFalse(SystemLockScreen.drawsItsOwnFor("MuditaOS K 1.0"))
    }

    @Test fun muditaOs2AndLaterHaveOne() {
        assertTrue(SystemLockScreen.drawsItsOwnFor("MuditaOS K 2.0.0"))
        assertTrue(SystemLockScreen.drawsItsOwnFor("MuditaOS K 10.1"))
    }

    @Test fun anyOtherAndroidHasOne() {
        assertTrue(SystemLockScreen.drawsItsOwnFor("BP2A.250605.031"))
        assertTrue(SystemLockScreen.drawsItsOwnFor(""))
    }
}
