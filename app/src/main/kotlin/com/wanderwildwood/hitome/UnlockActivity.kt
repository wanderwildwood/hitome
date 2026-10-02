package com.wanderwildwood.hitome

import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle

/**
 * Opens the app whose lines were pressed on the lock screen, once the lock screen is out of
 * the way. It draws nothing: it asks the system to dismiss the lock screen - which a swipe lock
 * does at once, and a PIN asks for - and opens the app only if that succeeds.
 *
 * ⚠ Asked only once its window has focus. Asked in onCreate, while the activity was still
 * coming up over the lock screen, the system cancelled the request a moment after showing the
 * PIN pad: the pad flashed and the lock screen came back.
 */
class UnlockActivity : Activity() {

    private var asked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        if (intent.getStringExtra(EXTRA_PACKAGE) == null) {
            finish()
            return
        }
        // An activity that draws nothing must not outlive a focus that never comes.
        window.decorView.postDelayed({ if (!asked) finish() }, FOCUS_WAIT_MS)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || asked) return
        asked = true
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return finish()
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) {
            launch(pkg)
            return
        }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = launch(pkg)
            override fun onDismissCancelled() = finish()
            override fun onDismissError() = finish()
        })
    }

    private fun launch(pkg: String) {
        packageManager.getLaunchIntentForPackage(pkg)?.let {
            try { startActivity(it) } catch (_: Exception) {}
        }
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
        private const val FOCUS_WAIT_MS = 3_000L
    }
}
