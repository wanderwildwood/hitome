package com.wanderwildwood.hitome

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * DuraSpeed, MediaTek's background manager on the Kompakt, closes apps a few minutes after the
 * screen goes dark, and the lock-screen counts stop changing. Settings has no way into it, and
 * its own screen will not open for another app, but its App info page will, and that page has
 * an Open button. The same way in as Messaging's.
 */
object DuraSpeed {

    private const val PACKAGE = "com.mediatek.duraspeed"

    /** Whether DuraSpeed is on this phone at all (listed in the manifest's queries). */
    fun present(context: Context): Boolean = runCatching {
        context.packageManager.getApplicationInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

    /** DuraSpeed's App info page, or Settings where that will not open. */
    fun open(context: Context) {
        val appInfo = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(appInfo) }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
