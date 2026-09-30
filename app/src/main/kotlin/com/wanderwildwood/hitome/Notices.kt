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
 * its notifications are up. Only apps on the list the phone's owner chose in Glance are
 * counted, and with none chosen nothing is.
 *
 * What the newest one says is shown too only when the owner turns that on, and even then not
 * where Android itself would hide it on the lock screen (see [NoticeListener.words]).
 */
object Notices {

    /** [text] is the newest notification's words, or null when they are not to be shown. */
    data class Notice(val packageName: String, val label: String, val count: Int, val text: String? = null)

    private val _now = MutableStateFlow<List<Notice>>(emptyList())
    val now: StateFlow<List<Notice>> get() = _now

    private const val PREFS = "notices"
    private const val KEY_CHOSEN = "chosen"
    private const val KEY_SHOW_TEXT = "show_text"

    fun chosen(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_CHOSEN, null)?.toSet()
            ?: emptySet()

    fun setChosen(context: Context, packageName: String, on: Boolean) {
        val next = chosen(context).let { if (on) it + packageName else it - packageName }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY_CHOSEN, next).apply()
        NoticeListener.instance?.recount()
    }

    /** Whether the panel shows what the newest notification says. Off unless turned on. */
    fun showText(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SHOW_TEXT, false)

    fun setShowText(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SHOW_TEXT, on).apply()
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
 * Android's notification access. Each notification from a chosen app adds one to that app's
 * count. Its title and text are read only when the owner has turned on showing them.
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
        val newest = mutableMapOf<String, android.service.notification.StatusBarNotification>()
        val counts = mutableMapOf<String, Int>()
        active.forEach { sbn ->
            if (sbn.packageName !in chosen || sbn.packageName in Glance.SOURCES) return@forEach
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
            if (sbn.postTime >= (latest[sbn.packageName] ?: 0L)) newest[sbn.packageName] = sbn
            latest[sbn.packageName] = maxOf(latest[sbn.packageName] ?: 0L, sbn.postTime)
        }
        val showText = Notices.showText(this)
        Notices.publish(
            counts.entries
                .sortedByDescending { latest[it.key] ?: 0L }
                .map { (pkg, count) ->
                    val text = if (showText) newest[pkg]?.let { words(it, ranking, rank) } else null
                    Notices.Notice(pkg, label(pkg), count, text)
                },
        )
    }

    /**
     * What a notification says, as one line: "title: text". Null where Android's own lock
     * screen would hide it: the phone set not to show private content on the lock screen, and
     * the notification or its channel marked private. Then its public version is used if the
     * app gave one ("2 new messages"), and otherwise only the name and count show.
     */
    private fun words(
        sbn: android.service.notification.StatusBarNotification,
        ranking: RankingMap?,
        rank: Ranking,
    ): String? {
        val allowPrivate = Settings.Secure.getInt(contentResolver, "lock_screen_allow_private_notifications", 1) != 0
        val override = if (ranking != null && ranking.getRanking(sbn.key, rank)) rank.lockscreenVisibilityOverride
            else NotificationManagerVisibilityNone
        val private = override == Notification.VISIBILITY_PRIVATE ||
            rank.channel?.lockscreenVisibility == Notification.VISIBILITY_PRIVATE ||
            (override == NotificationManagerVisibilityNone && sbn.notification.visibility == Notification.VISIBILITY_PRIVATE)
        val n = if (private && !allowPrivate) sbn.notification.publicVersion ?: return null else sbn.notification
        val extras = n.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT) ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
            ?.toString()?.trim().orEmpty()
        val line = listOf(title, text).filter { it.isNotEmpty() }.joinToString(": ")
            .replace(Regex("\\s+"), " ")
        return line.ifEmpty { null }
    }

    private fun label(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    companion object {
        /** `NotificationManager.VISIBILITY_NO_OVERRIDE`: neither the owner nor the channel set one. */
        private const val NotificationManagerVisibilityNone = -1000

        @Volatile
        var instance: NoticeListener? = null
            private set
    }
}
