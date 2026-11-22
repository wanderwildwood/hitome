package com.wanderwildwood.hitome

import android.os.Build

/**
 * Whether the phone's own lock screen draws a player for every app and its own notifications.
 *
 * MuditaOS 1.x's lock screen shows the clock, the date and a music widget wired to Mudita's
 * player alone; there Glance lists missed calls and texts, and a music app may draw its own strip
 * near the foot. From MuditaOS 2.0 the lock screen shows whatever app plays, and notifications
 * such as missed calls and texts, as KompaktOS and other Android lock screens do. There Glance
 * repeats none of them and goes under whatever the lock screen draws.
 */
object SystemLockScreen {
    fun drawsItsOwn(): Boolean = drawsItsOwnFor(Build.DISPLAY.orEmpty())

    /** [display] is the build's display id, e.g. "MuditaOS K 1.6.1". */
    internal fun drawsItsOwnFor(display: String): Boolean {
        val major = MUDITA.find(display)?.groupValues?.get(1)?.toIntOrNull() ?: return true
        return major >= 2
    }

    /**
     * Whether Glance's panel on the lock screen stays light whatever dark mode says: on MuditaOS,
     * whose own lock screen stays light (seen on 2.0, 22 Nov 2026), so the panel is not a dark
     * block on a light screen. KompaktOS and other Android lock screens go dark; there it follows.
     */
    fun staysLight(): Boolean = staysLightFor(Build.DISPLAY.orEmpty())

    internal fun staysLightFor(display: String): Boolean = MUDITA.containsMatchIn(display)

    private val MUDITA = Regex("^MuditaOS\\b\\D*(\\d+)\\.")
}
