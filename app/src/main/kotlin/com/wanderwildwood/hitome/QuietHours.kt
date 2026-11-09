package com.wanderwildwood.hitome

import android.app.AlarmManager
import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import com.wanderwildwood.hitome.QuietSchedule.Companion.bits
import com.wanderwildwood.hitome.QuietSchedule.Companion.daysOf
import com.wanderwildwood.hitome.QuietSchedule.Companion.minutes
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Quiet hours: on the chosen nights, from the start to the end, nothing rings but alarms, and a
 * second call from the same number within 15 minutes if "Let repeat callers ring" is on.
 * Media still plays when it is played. Notifications still arrive, silent, and still show.
 *
 * It is a Do Not Disturb rule of Glance's own, apart from Ring for calls only ([Quiet]), and
 * like that one it is what notification access allows. Android's own schedules are hidden on
 * the Kompakt, so Glance turns the rule on and off itself: an alarm at each start and end, and
 * a look at the clock whenever the screen comes on, the app is opened, the phone starts, or its
 * clock or time zone changes. Turned off from the phone's own Do Not Disturb switch, it stays
 * off for the rest of that night and comes back the next.
 */
object QuietHours {

    private const val PREFS = "quiet_hours"
    private const val KEY_ON = "on"
    private const val KEY_START = "start"
    private const val KEY_END = "end"
    private const val KEY_DAYS = "days"
    private const val KEY_REPEAT = "repeat"
    private const val KEY_RULE = "rule"
    private val CONDITION: Uri = Uri.parse("condition://com.wanderwildwood.hitome/quiet_hours")

    /** 10 PM to 7 AM, every night. */
    private const val DEFAULT_START = 22 * 60
    private const val DEFAULT_END = 7 * 60
    private const val ALL_DAYS = 0b111_1111

    /** How late an alarm may come when exact alarms are not allowed. */
    private const val WINDOW_MS = 10 * 60_000L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ON, false)
    fun start(context: Context): LocalTime = minutes(prefs(context).getInt(KEY_START, DEFAULT_START))
    fun end(context: Context): LocalTime = minutes(prefs(context).getInt(KEY_END, DEFAULT_END))
    fun days(context: Context): Set<DayOfWeek> = daysOf(prefs(context).getInt(KEY_DAYS, ALL_DAYS))
    fun repeatCallers(context: Context): Boolean = prefs(context).getBoolean(KEY_REPEAT, true)

    fun schedule(context: Context) = QuietSchedule(start(context), end(context), days(context))

    /** Each setter puts the rule right at once: on or off for now, and the next alarm. */
    fun setEnabled(context: Context, on: Boolean) = save(context) { putBoolean(KEY_ON, on) }
    fun setStart(context: Context, time: LocalTime) = save(context) { putInt(KEY_START, time.hour * 60 + time.minute) }
    fun setEnd(context: Context, time: LocalTime) = save(context) { putInt(KEY_END, time.hour * 60 + time.minute) }
    fun setDays(context: Context, days: Set<DayOfWeek>) = save(context) { putInt(KEY_DAYS, bits(days)) }
    fun setRepeatCallers(context: Context, on: Boolean) {
        save(context) { putBoolean(KEY_REPEAT, on) }
        // The rule's policy carries this, so the rule is told.
        val id = ruleId(context) ?: return
        val nm = manager(context)
        try {
            nm.getAutomaticZenRule(id)?.let { it.zenPolicy = policy(on); nm.updateAutomaticZenRule(id, it) }
        } catch (_: Exception) {}
    }

    private fun save(context: Context, edit: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs(context).edit().apply(edit).apply()
        sync(context)
    }

    /**
     * Puts the phone where the schedule says it should be now, and sets the alarm for the next
     * change. Safe to call at any time and as often as wanted.
     */
    fun sync(context: Context) {
        val app = context.applicationContext
        if (!enabled(app)) {
            removeRule(app)
            cancelAlarm(app)
            return
        }
        val schedule = schedule(app)
        val now = ZonedDateTime.now()
        // Without notification access the rule cannot be set; the alarm stays, so it is set at
        // the next change once access is back.
        if (manager(app).isNotificationPolicyAccessGranted) setState(app, schedule.inside(now))
        val next = schedule.nextChange(now)
        if (next == null) cancelAlarm(app) else setAlarm(app, next.toInstant().toEpochMilli())
    }

    /** Whether quiet hours are on the phone right now. */
    fun activeNow(context: Context): Boolean {
        val id = ruleId(context) ?: return false
        val nm = manager(context)
        val rule = try { nm.getAutomaticZenRule(id) } catch (_: Exception) { null } ?: return false
        return rule.isEnabled && schedule(context).inside(ZonedDateTime.now()) &&
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }

    private fun setState(context: Context, quiet: Boolean) {
        val nm = manager(context)
        try {
            val id = ruleId(context)?.takeIf { nm.getAutomaticZenRule(it) != null } ?: run {
                val rule = AutomaticZenRule(
                    context.getString(R.string.quiet_hours_rule_name),
                    null,
                    ComponentName(context, MainActivity::class.java),
                    CONDITION,
                    policy(repeatCallers(context)),
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                    true,
                )
                nm.addAutomaticZenRule(rule).also { setRuleId(context, it) }
            }
            // Told true while already true, a rule the phone's own switch turned off stays off;
            // told false at the end of the night, it is ready for the next.
            val state = if (quiet) Condition.STATE_TRUE else Condition.STATE_FALSE
            nm.setAutomaticZenRuleState(id, Condition(CONDITION, "", state))
        } catch (_: Exception) {}
    }

    private fun removeRule(context: Context) {
        val id = ruleId(context) ?: return
        try { manager(context).removeAutomaticZenRule(id) } catch (_: Exception) {}
        setRuleId(context, null)
    }

    /** Alarms ring, media plays when played, and a repeat caller if [repeat]; nothing hidden. */
    private fun policy(repeat: Boolean): ZenPolicy = ZenPolicy.Builder()
        .disallowAllSounds()
        .allowCalls(ZenPolicy.PEOPLE_TYPE_NONE)
        .allowRepeatCallers(repeat)
        .allowAlarms(true)
        .allowMedia(true)
        .showAllVisualEffects()
        .build()

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, QuietHoursReceiver::class.java).setAction(ACTION_CHANGE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * On time to the minute, even with the phone asleep, while the phone allows exact alarms
     * (Android 12 allows them unless switched off in Alarms & reminders). Otherwise within ten
     * minutes, or later while the phone sleeps; the screen coming on puts it right.
     */
    private fun setAlarm(context: Context, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(context)
        try {
            if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, pi)
        } catch (_: SecurityException) {
            am.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, pi)
        }
    }

    private fun cancelAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
    }

    private fun ruleId(context: Context): String? = prefs(context).getString(KEY_RULE, null)
    private fun setRuleId(context: Context, id: String?) = prefs(context).edit().putString(KEY_RULE, id).apply()

    const val ACTION_CHANGE = "com.wanderwildwood.hitome.QUIET_HOURS_CHANGE"
}

/** The alarm at each start and end, and the phone starting or its clock changing: look again. */
class QuietHoursReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            QuietHours.ACTION_CHANGE,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> QuietHours.sync(context)
        }
    }
}
