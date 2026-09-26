package com.wanderwildwood.hitome

import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle

/**
 * Opens the app whose lines were pressed on the lock screen, once the lock screen is out of
 * the way. It draws nothing: it asks the system to dismiss the lock screen - which a swipe lock
 * does at once, and a PIN asks for - and opens the app only if that succeeds.
 */
class UnlockActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        if (pkg == null) {
            finish()
            return
        }
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
    }
}
