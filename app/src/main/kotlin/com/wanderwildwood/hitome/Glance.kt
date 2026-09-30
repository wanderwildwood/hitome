package com.wanderwildwood.hitome

import android.content.Context
import android.net.Uri

/**
 * What an app has to say on the lock screen, asked of the app itself.
 *
 * Each app that takes part keeps a small read-only provider at `<package>.glance`, answers only
 * this app, and says nothing when its own "show on the lock screen" setting is off. So the app
 * decides what its lines are and whether there are any, and Glance only draws them in a fixed
 * order and opens the app that wrote them when they are pressed.
 *
 * Columns, one row per line: `heading` (on the first row, optional), `lead` (a short left
 * column such as a time or a temperature, optional), `text`, and `bold` (1 or 0).
 */
object Glance {

    data class Line(val lead: String?, val text: String, val bold: Boolean)

    data class Section(val packageName: String, val heading: String?, val lines: List<Line>)

    /** Stacked sections, top to bottom: today's events, then the weather. */
    val STACKED = listOf(
        "com.wanderwildwood.koyomi",
        "com.wanderwildwood.soramoyo",
    )

    /** Counts that share one line at the foot: unread messages, then unread mail. */
    val COUNTS = listOf(
        "com.wanderwildwood.kotozute",
        "com.wanderwildwood.tayori",
    )

    /**
     * Every app that hands Glance its own lines. None of them is among the apps whose
     * notifications are counted (see [Notices]): each already has its place on the panel.
     */
    val SOURCES = STACKED + COUNTS

    fun uri(packageName: String): Uri = Uri.parse("content://$packageName.glance/lines")

    /** Calendar (koyomi), whose own lines take the place of Glance's reading of today. */
    const val CALENDAR = "com.wanderwildwood.koyomi"

    /**
     * Everything the panel draws but other apps' notifications, in its order. Today's events
     * are Calendar's own while it is installed, and otherwise Glance's reading ([Today]).
     */
    fun readAll(context: Context): List<Section> {
        val calendarInstalled = context.packageManager.getLaunchIntentForPackage(CALENDAR) != null
        return (STACKED + COUNTS).mapNotNull { pkg ->
            if (pkg == CALENDAR && !calendarInstalled) Today.read(context) else read(context, pkg)
        }
    }

    /** Null when the app is not installed, has nothing to say, or has its switch off. */
    fun read(context: Context, packageName: String): Section? = try {
        context.contentResolver.query(uri(packageName), COLUMNS, null, null, null)?.use { c ->
            val heading = c.getColumnIndex("heading")
            val lead = c.getColumnIndex("lead")
            val text = c.getColumnIndex("text")
            val bold = c.getColumnIndex("bold")
            if (text < 0) return null
            var title: String? = null
            val lines = mutableListOf<Line>()
            while (c.moveToNext()) {
                if (title == null && heading >= 0) title = c.getString(heading)?.takeIf { it.isNotBlank() }
                val t = c.getString(text)?.takeIf { it.isNotBlank() } ?: continue
                lines += Line(
                    lead = if (lead >= 0) c.getString(lead)?.takeIf { it.isNotBlank() } else null,
                    text = t,
                    bold = bold >= 0 && c.getInt(bold) == 1,
                )
            }
            if (lines.isEmpty()) null else Section(packageName, title, lines)
        }
    } catch (_: Exception) {
        // Not installed, too old to have a provider, or it refused: in every case, nothing.
        null
    }

    private val COLUMNS = arrayOf("heading", "lead", "text", "bold")
}
