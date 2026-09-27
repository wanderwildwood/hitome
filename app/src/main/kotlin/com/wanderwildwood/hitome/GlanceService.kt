package com.wanderwildwood.hitome

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * The accessibility service, whose only job is the panel on the lock screen. An overlay above
 * the lock screen is a window only an accessibility service may add; it reads nothing but the
 * lock screen itself, to know where its own items end and whether the PIN pad is up.
 */
class GlanceService : AccessibilityService() {

    private var panel: LockscreenPanel? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        panel = LockscreenPanel(this).also { it.start() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        panel?.lockScreenChanged()
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        panel?.stop()
        panel = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        panel?.stop()
        panel = null
        super.onDestroy()
    }
}
