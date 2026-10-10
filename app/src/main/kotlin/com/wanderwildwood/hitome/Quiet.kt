package com.wanderwildwood.hitome

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.service.notification.Condition
import android.service.notification.ZenPolicy

/**
 * Ring for calls only: calls and alarms make their sound, every other notification arrives
 * silent and still shows, on the lock screen and here.
 *
 * It is a Do Not Disturb rule of Glance's own, so the phone's own Do Not Disturb and its
 * settings are left as they are. Notification access is what lets Glance set it.
 */
object Quiet {

    private const val PREFS = "quiet"
    private const val KEY_RULE = "rule"
    private val CONDITION: Uri = Uri.parse("condition://com.wanderwildwood.hitome/calls_only")

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    /** Whether the phone lets Glance set Do Not Disturb, which comes with notification access. */
    fun allowed(context: Context): Boolean = manager(context).isNotificationPolicyAccessGranted

    fun on(context: Context): Boolean {
        val id = ruleId(context) ?: return false
        val nm = manager(context)
        val rule = try { nm.getAutomaticZenRule(id) } catch (_: Exception) { null } ?: return false
        // Turned off from the phone's own Do Not Disturb switch, the rule is still there but
        // sleeps; it reads as off, and turning it on makes it anew.
        return rule.isEnabled && nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }

    /** Returns whether it took. */
    fun set(context: Context, on: Boolean): Boolean {
        val nm = manager(context)
        if (!nm.isNotificationPolicyAccessGranted) return false
        return try {
            if (on) {
                val rule = AutomaticZenRule(
                    context.getString(R.string.quiet_rule_name),
                    null,
                    ComponentName(context, MainActivity::class.java),
                    CONDITION,
                    policy(),
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                    true,
                )
                // Made anew each time: a rule the phone's own Do Not Disturb switch turned off
                // stays snoozed, and would not come back on by being told so again.
                ruleId(context)?.let { nm.removeAutomaticZenRule(it) }
                val id = nm.addAutomaticZenRule(rule)
                setRuleId(context, id)
                nm.setAutomaticZenRuleState(id, Condition(CONDITION, "", Condition.STATE_TRUE))
            } else {
                ruleId(context)?.let { nm.removeAutomaticZenRule(it) }
                setRuleId(context, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Calls from anyone, a second call within minutes, alarms and media; nothing hidden. */
    private fun policy(): ZenPolicy = ZenPolicy.Builder()
        .disallowAllSounds()
        .allowCalls(ZenPolicy.PEOPLE_TYPE_ANYONE)
        .allowRepeatCallers(true)
        .allowAlarms(true)
        .allowMedia(true)
        .showAllVisualEffects()
        .build()

    private fun ruleId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_RULE, null)

    private fun setRuleId(context: Context, id: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_RULE, id).apply()
    }
}
