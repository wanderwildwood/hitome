package com.wanderwildwood.hitome

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * The accessibility service, whose only job is the panel on the lock screen. An overlay above
 * the lock screen is a window only an accessibility service may add; it reads nothing but the
 * lock screen itself, to know where its own items end and whether the PIN pad is up.
 * Being always running, it also listens for power pressed twice, for the torch ([Torch]).
 */
class GlanceService : AccessibilityService() {

    private var panel: LockscreenPanel? = null
    private var torch: Torch? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Where the lock screen draws its own player (MuditaOS 2.0, KompaktOS), the player's
        // box is a plain frame Android leaves out of what this service sees; the panel has to
        // see it to sit clear of it. Only there: on MuditaOS 1.x the panel measures as before.
        if (SystemLockScreen.drawsItsOwn()) {
            serviceInfo = serviceInfo?.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            }
        }
        // Connected again without an unbind in between: the old ones go first, or two torches
        // would each answer the same two presses and cancel out.
        panel?.stop()
        torch?.stop()
        panel = LockscreenPanel(this).also { it.start() }
        torch = Torch(this).also { it.start() }
        // Started inside quiet hours (after an update, say): on at once, not at the next alarm.
        if (QuietHours.enabled(this)) QuietHours.sync(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        panel?.lockScreenChanged()
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        panel?.stop()
        panel = null
        torch?.stop()
        torch = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        panel?.stop()
        panel = null
        torch?.stop()
        torch = null
        super.onDestroy()
    }
}
