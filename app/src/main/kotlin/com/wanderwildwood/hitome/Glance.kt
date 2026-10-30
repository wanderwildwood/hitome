package com.wanderwildwood.hitome

import android.content.ComponentName
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Settings

/**
 * What an app has to say on the lock screen, asked of the app itself.
 *
 * Each app that takes part keeps a small read-only provider at `<package>.glance`, answers only
 * this app, and says nothing when its own "show on the lock screen" setting is off. So the app
 * decides what its lines are and whether there are any, and Glance only draws them in the order
 * the person chose ([order]) and opens the app that wrote them when they are pressed.
 *
 * Columns, one row per line: `heading` (on the first row, optional), `lead` (a short left
 * column such as a time or a temperature, optional), `text`, and `bold` (1 or 0).
 */
object Glance {

    data class Line(val lead: String?, val text: String, val bold: Boolean)

    /**
     * [key] is the section's place in [order]: its source, or [UNREAD] for the counts. Today's
     * events read by Glance itself open another calendar app but keep Calendar's place.
     */
    data class Section(
        val packageName: String,
        val heading: String?,
        val lines: List<Line>,
        val key: String = if (packageName in COUNTS) UNREAD else packageName,
        /** For a player: whether it is playing now, when it says (null: it did not say). */
        val playing: Boolean? = null,
    )

    /** Music Box, whose place on the panel is also taken by Music Box K when that is the one playing. */
    const val MUSIC = "com.wanderwildwood.jimeikin"
    const val MUSIC_K = "com.wanderwildwood.jimeikin.k"

    /** The apps that can fill [pkg]'s place: itself, and for the music place both Music Boxes. */
    fun providers(pkg: String): List<String> = if (pkg == MUSIC) listOf(MUSIC, MUSIC_K) else listOf(pkg)

    /**
     * Of the answers from [providers], the one to draw: the one that is playing, else the first
     * that has lines. Null when none has. Music Box and Music Box K can both be installed and
     * both hold a song, so a paused one never hides the one that is sounding.
     */
    fun pickPlaying(sections: List<Section?>): Section? {
        val have = sections.filterNotNull()
        return have.firstOrNull { it.playing == true } ?: have.firstOrNull()
    }

    /** Whether the app for [pkg]'s place is on the phone; for the music place, either Music Box. */
    fun installed(pm: android.content.pm.PackageManager, pkg: String): Boolean =
        providers(pkg).any { pm.getLaunchIntentForPackage(it) != null }

    /**
     * Stacked sections, top to bottom until the order is changed: the emergency card, today's
     * events, the next dose, the weather, pinned notes, today's tickets, then what is playing -
     * a song, then a book - and last the bitcoin price.
     */
    val STACKED = listOf(
        "com.wanderwildwood.zatsuno",
        "com.wanderwildwood.koyomi",
        "com.wanderwildwood.fukuyaku",
        "com.wanderwildwood.soramoyo",
        "com.wanderwildwood.oboegaki",
        "com.wanderwildwood.satsuire",
        "com.wanderwildwood.jimeikin",
        "com.wanderwildwood.mimidoku",
        "com.wanderwildwood.zeni",
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

    /**
     * Sources that stay off the panel until switched on here too, as well as in their own app:
     * the emergency card, the next dose, today's tickets and the bitcoin price. Nothing new
     * appears on the lock screen just because an app was installed.
     */
    val OPT_IN = setOf(
        "com.wanderwildwood.zatsuno",
        "com.wanderwildwood.fukuyaku",
        "com.wanderwildwood.satsuire",
        BITCOIN,
    )

    private const val KEY_ON = "on"

    /** Whether [packageName], one of [OPT_IN], has been switched on in Glance. */
    fun switchedOn(context: Context, packageName: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_ON, emptySet())!!.contains(packageName)

    fun setSwitchedOn(context: Context, packageName: String, on: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = prefs.getStringSet(KEY_ON, emptySet())!!.toMutableSet()
        if (on) now += packageName else now -= packageName
        prefs.edit().putStringSet(KEY_ON, now).apply()
        PanelForHome.changed(context)
    }

    /** The apps that say what is playing, each of which can draw its own controls on the lock screen. */
    val PLAYERS = setOf("com.wanderwildwood.jimeikin", "com.wanderwildwood.mimidoku")

    /**
     * Whether [packageName] has its own lock-screen controls switched on, an accessibility service
     * of its own. They already name what is playing, so the panel leaves that line out.
     */
    fun ownControlsOn(context: Context, packageName: String): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val wanted = providers(packageName)
        return enabled.split(':').any { ComponentName.unflattenFromString(it)?.packageName in wanted }
    }

    fun uri(packageName: String): Uri = Uri.parse("content://$packageName.glance/lines")

    /** Calendar (koyomi), whose own lines take the place of Glance's reading of today. */
    const val CALENDAR = "com.wanderwildwood.koyomi"

    const val SKY = "com.wanderwildwood.soramoyo"

    const val BITCOIN = "com.wanderwildwood.zeni"

    /** Apps that fetch their lines from elsewhere, and are asked to bring them up to date on each wake. */
    val REFRESHED = listOf(SKY, BITCOIN)

    /**
     * The counts as one part of the order: Messaging's and Email's unread, and other apps'
     * notifications under them. They share the foot of the panel, so they move together.
     */
    const val UNREAD = "unread"

    /** The order as it first is: the stacked sections, then the counts at the foot. */
    val DEFAULT_ORDER = STACKED + UNREAD

    private const val PREFS = "panel"
    private const val KEY_ORDER = "order"

    /** The 0.1.7 to 0.1.11 setting, "when there is not room for all of it", read once. */
    private const val KEY_KEEP = "keep"

    /**
     * The parts top to bottom, which is also which win when there is not room for all of them:
     * the first part always shows, and each one after it if there is room left. Saved as the
     * person left it; a part added in a later version goes at the end.
     */
    fun order(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_ORDER, null)
        val start = if (saved != null) {
            saved.split(',').filter { it in DEFAULT_ORDER }.distinct()
        } else {
            // Whoever chose to keep the weather or the counts before there was an order
            // still has them win: they go first.
            when (prefs.getString(KEY_KEEP, null)) {
                "WEATHER" -> listOf(SKY) + (DEFAULT_ORDER - SKY)
                "COUNTS" -> listOf(UNREAD) + (DEFAULT_ORDER - UNREAD)
                else -> DEFAULT_ORDER
            }
        }
        return start + DEFAULT_ORDER.filter { it !in start }
    }

    private const val KEY_TEXT_SIZE = "text_size"

    /** The panel's sizes, as multiples of its first one: Normal, Large, Larger. */
    val TEXT_SCALES = floatArrayOf(1f, 1.15f, 1.3f)

    /** Which of [TEXT_SCALES] the panel is drawn at; Normal until another is chosen. */
    fun textSize(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_TEXT_SIZE, 0).coerceIn(0, TEXT_SCALES.lastIndex)

    fun setTextSize(context: Context, size: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_TEXT_SIZE, size).apply()
    }

    fun textScale(context: Context): Float = TEXT_SCALES[textSize(context)]

    fun setOrder(context: Context, order: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ORDER, order.joinToString(",")).apply()
        PanelForHome.changed(context)
    }

    /** [order] with the part at [from] moved to [to]. */
    fun moved(order: List<String>, from: Int, to: Int): List<String> {
        if (from !in order.indices || to !in order.indices || from == to) return order
        return order.toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * Everything the panel draws but other apps' notifications, in the person's [order]. Today's
     * events are Calendar's own while it is installed, and otherwise Glance's reading ([Today]).
     */
    fun readAll(context: Context): List<Section> {
        val calendarInstalled = calendarInstalled(context)
        return sources(order(context)).mapNotNull { pkg ->
            when {
                pkg in OPT_IN && !switchedOn(context, pkg) -> null
                pkg in PLAYERS && ownControlsOn(context, pkg) -> null
                pkg == CALENDAR && !calendarInstalled -> Today.read(context)?.copy(key = CALENDAR)
                else -> read(context, pkg)
            }
        }
    }

    /**
     * Whether a place in [order] is shown in Settings: only apps that are on this phone. Calendar's
     * place stays, since Today's events stands in for it; the counts stay while Messaging or
     * Email is installed, or notification access is on.
     */
    fun listed(key: String, installed: Map<String, Boolean>, access: Boolean): Boolean = when (key) {
        CALENDAR -> true
        UNREAD -> access || COUNTS.any { installed[it] == true }
        else -> installed[key] == true
    }

    /** The sources one by one in [order], the counts' place standing for both of them. */
    fun sources(order: List<String>): List<String> = order.flatMap { if (it == UNREAD) COUNTS else listOf(it) }

    fun calendarInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(CALENDAR) != null

    /** Null when the app is not installed, has nothing to say, or has its switch off. */
    fun read(context: Context, packageName: String): Section? {
        val from = providers(packageName)
        return if (from.size == 1) readOne(context, packageName) else pickPlaying(from.map { readOne(context, it) })
    }

    private fun readOne(context: Context, packageName: String): Section? = try {
        context.contentResolver.query(uri(packageName), COLUMNS, null, null, null)?.use { c -> section(c, packageName) }
    } catch (_: Exception) {
        // Not installed, too old to have a provider, or it refused: in every case, nothing.
        null
    }

    /** The section an app's answer makes; null when it has no lines, or no `text` column. */
    fun section(c: Cursor, packageName: String): Section? {
        val heading = c.getColumnIndex("heading")
        val lead = c.getColumnIndex("lead")
        val text = c.getColumnIndex("text")
        val bold = c.getColumnIndex("bold")
        val playingAt = c.getColumnIndex("playing")
        if (text < 0) return null
        var playing: Boolean? = null
        var title: String? = null
        val lines = mutableListOf<Line>()
        while (c.moveToNext()) {
            if (title == null && heading >= 0) title = c.getString(heading)?.takeIf { it.isNotBlank() }
            val t = c.getString(text)?.takeIf { it.isNotBlank() } ?: continue
            if (playingAt >= 0 && playing == null && !c.isNull(playingAt)) playing = c.getInt(playingAt) == 1
            lines += Line(
                lead = if (lead >= 0) c.getString(lead)?.takeIf { it.isNotBlank() } else null,
                text = t,
                bold = bold >= 0 && c.getInt(bold) == 1,
            )
        }
        return if (lines.isEmpty()) null else Section(packageName, title, lines, playing = playing)
    }

    val COLUMNS = arrayOf("heading", "lead", "text", "bold", "playing")
}
