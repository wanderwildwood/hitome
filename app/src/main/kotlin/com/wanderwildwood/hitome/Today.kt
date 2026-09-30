package com.wanderwildwood.hitome

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Today's events for a phone without Calendar (koyomi), which otherwise hands Glance its own.
 * Off until the owner turns it on in Glance.
 *
 * Two places are read. Mudita's own Calendar keeps its events to itself but answers any app
 * that asks, so it needs no permission; it has no repeating events, one row per event. Every
 * other calendar on the phone - DAVx5, Etar, anything that syncs - is Android's shared
 * calendar, which needs calendar access, asked for only when this is switched on.
 *
 * The lines are laid out as Calendar lays out its own: those running all day first, then the
 * rest not yet over, each with its time.
 */
object Today {

    const val MUDITA_CALENDAR = "com.mudita.calendar"
    private val MUDITA_EVENTS: Uri = Uri.parse("content://$MUDITA_CALENDAR/events")

    private const val PREFS = "today"
    private const val KEY_ON = "on"
    // Glance trims to the room it has; more than this would never fit.
    private const val MAX = 6

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply()
    }

    fun canReadCalendars(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun muditaCalendarInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(MUDITA_CALENDAR) != null

    /** The URIs to watch for changes, where watching is allowed. */
    fun watched(context: Context): List<Uri> =
        listOf(MUDITA_EVENTS) + if (canReadCalendars(context)) listOf(CalendarContract.CONTENT_URI) else emptyList()

    private data class Event(val title: String, val start: LocalDateTime, val finish: LocalDateTime, val allDay: Boolean)

    /** Null when switched off, or when today has nothing left. */
    fun read(context: Context): Glance.Section? {
        if (!enabled(context)) return null
        val today = LocalDate.now()
        val now = LocalDateTime.now()
        val events = mudita(context, today) + shared(context, today)
        val allDay = events.filter { it.allDay }.sortedBy { it.title.lowercase() }
        val timed = events.filter { !it.allDay && it.finish.isAfter(now) }.sortedBy { it.start }
        val time = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm")
        val lines = allDay.map { Glance.Line(context.getString(R.string.today_all_day), it.title, false) } +
            timed.map {
                // An event under way since before today says so rather than giving yesterday's time.
                val lead = if (it.start.toLocalDate().isBefore(today)) context.getString(R.string.today_until, it.finish.format(time))
                else it.start.format(time)
                Glance.Line(lead, it.title, false)
            }
        if (lines.isEmpty()) return null
        return Glance.Section(opener(context), context.getString(R.string.today_heading), lines.take(MAX))
    }

    /**
     * Mudita's Calendar: `startDateTime` and `endDateTime` are milliseconds since the epoch.
     * Asked only for what touches today; a Calendar that changes its columns gives nothing.
     */
    private fun mudita(context: Context, today: LocalDate): List<Event> = try {
        val zone = ZoneId.systemDefault()
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        context.contentResolver.query(
            MUDITA_EVENTS,
            null,
            "endDateTime > ? AND startDateTime < ?",
            arrayOf(from.toString(), to.toString()),
            "startDateTime",
        )?.use { c ->
            val title = c.getColumnIndex("title")
            val start = c.getColumnIndex("startDateTime")
            val end = c.getColumnIndex("endDateTime")
            val allDay = c.getColumnIndex("isAllDayEvent")
            if (title < 0 || start < 0 || end < 0) return emptyList()
            buildList {
                while (c.moveToNext()) {
                    add(
                        Event(
                            title = c.getString(title)?.takeIf { it.isNotBlank() } ?: context.getString(R.string.today_untitled),
                            start = LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(start)), zone),
                            finish = LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(end)), zone),
                            allDay = allDay >= 0 && c.getInt(allDay) == 1,
                        ),
                    )
                }
            }
        } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    /** Android's shared calendar, visible calendars only, as Calendar reads it. */
    private fun shared(context: Context, today: LocalDate): List<Event> {
        if (!canReadCalendars(context)) return emptyList()
        return try {
            val zone = ZoneId.systemDefault()
            // Widened by a day either side: an all-day event is stored in UTC, so near midnight
            // its millis fall on the neighbouring local day. The date check below trims it back.
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli().toString())
                .appendPath(today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli().toString())
                .build()
            context.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                ),
                "${CalendarContract.Instances.VISIBLE} = 1 AND (${CalendarContract.Instances.STATUS} IS NULL OR " +
                    "${CalendarContract.Instances.STATUS} != ${CalendarContract.Events.STATUS_CANCELED})",
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val allDay = c.getInt(3) == 1
                        fun local(ms: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), if (allDay) ZoneOffset.UTC else zone)
                        val e = Event(
                            title = c.getString(0)?.takeIf { it.isNotBlank() } ?: context.getString(R.string.today_untitled),
                            start = local(c.getLong(1)),
                            finish = local(c.getLong(2)),
                            allDay = allDay,
                        )
                        if (e.start.toLocalDate().isAfter(today) || lastDay(e).isBefore(today)) continue
                        add(e)
                    }
                }
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** An all-day end is the midnight after, and so is a timed event's ending at midnight. */
    private fun lastDay(e: Event): LocalDate {
        val d = e.finish.toLocalDate()
        return if ((e.allDay || e.finish.toLocalTime() == LocalTime.MIDNIGHT) && d.isAfter(e.start.toLocalDate())) d.minusDays(1) else d
    }

    /** What pressing the events opens: Mudita's Calendar, or whatever app is the calendar. */
    private fun opener(context: Context): String {
        if (muditaCalendarInstalled(context)) return MUDITA_CALENDAR
        val calendar = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
        return context.packageManager.resolveActivity(calendar, 0)?.activityInfo?.packageName ?: MUDITA_CALENDAR
    }
}
