package com.wanderwildwood.hitome

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What other apps have waiting, as the panel shows it: each chosen app's name and how many of
 * its notifications are up. Never what they say. Only apps on the list the phone's owner chose
 * in Glance are counted, and with none chosen nothing is.
 */
object Notices {

    data class Notice(val packageName: String, val label: String, val count: Int)

    private val _now = MutableStateFlow<List<Notice>>(emptyList())
    val now: StateFlow<List<Notice>> get() = _now

    private const val PREFS = "notices"
    private const val KEY_CHOSEN = "chosen"

    fun chosen(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_CHOSEN, null)?.toSet()
            ?: emptySet()

    fun setChosen(context: Context, packageName: String, on: Boolean) {
        val next = chosen(context).let { if (on) it + packageName else it - packageName }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY_CHOSEN, next).apply()
        NoticeListener.instance?.recount()
    }

    /** Whether Glance has been given notification access in Android's settings. */
    fun accessGranted(context: Context): Boolean {
        val mine = ComponentName(context, NoticeListener::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }

    internal fun publish(list: List<Notice>) {
        _now.value = list
    }
}

/**
 * Android's notification access, used for counting only. Each notification from a chosen app
 * adds one to that app's count; its title and text are never read.
 *
 * Left out: anything ongoing (a player, a download, a running service), a group's summary,
 * which stands for notifications already counted, and anything the app or the owner has marked
 * not to be shown on the lock screen at all.
 */
class NoticeListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        recount()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        Notices.publish(emptyList())
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) = recount()

    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) = recount()

    override fun onDestroy() {
        if (instance === this) instance = null
        Notices.publish(emptyList())
        super.onDestroy()
    }

    fun recount() {
        val chosen = Notices.chosen(this)
        if (chosen.isEmpty()) {
            Notices.publish(emptyList())
            return
        }
        val active = try { activeNotifications?.toList() } catch (_: Exception) { null } ?: emptyList()
        val ranking = try { currentRanking } catch (_: Exception) { null }
        val rank = Ranking()
        // Newest first, so the app that just spoke leads the line.
        val latest = mutableMapOf<String, Long>()
        val counts = mutableMapOf<String, Int>()
        active.forEach { sbn ->
            if (sbn.packageName !in chosen || sbn.packageName in Glance.COUNTS) return@forEach
            val n = sbn.notification
            if (sbn.isOngoing) return@forEach
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return@forEach
            if (n.visibility == Notification.VISIBILITY_SECRET) return@forEach
            if (ranking != null && ranking.getRanking(sbn.key, rank)) {
                if (rank.lockscreenVisibilityOverride == Notification.VISIBILITY_SECRET) return@forEach
                if (rank.channel?.lockscreenVisibility == Notification.VISIBILITY_SECRET) return@forEach
                if (rank.isSuspended) return@forEach
            }
            counts[sbn.packageName] = (counts[sbn.packageName] ?: 0) + 1
            latest[sbn.packageName] = maxOf(latest[sbn.packageName] ?: 0L, sbn.postTime)
        }
        Notices.publish(
            counts.entries
                .sortedByDescending { latest[it.key] ?: 0L }
                .map { (pkg, count) -> Notices.Notice(pkg, label(pkg), count) },
        )
    }

    private fun label(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    companion object {
        @Volatile
        var instance: NoticeListener? = null
            private set
    }
}
