package com.wanderwildwood.hitome.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.hitome.Glance
import com.wanderwildwood.hitome.Notices
import com.wanderwildwood.hitome.R

/** Heights the panel is laid out by, in dp; a line of bodySmall with its spacing. */
internal const val HEADING_DP = 20
internal const val LINE_DP = 22
internal const val RULE_DP = 13
/** The 2dp above and below each section and the counts line. */
internal const val PAD_DP = 4
/** Between the rows when each section is one line, so they read apart. */
internal const val ONE_LINE_GAP_DP = 6
/** The panel's width, the lock screen's own dotted rules. */
internal const val PANEL_WIDTH_DP = 240

/**
 * The parts in the person's [order], each under a dotted rule like the lock screen's own: the
 * stacked sections, and the counts with other apps' notifications as names and numbers, which
 * are one part. Pressing a section, a count or a name opens the app it came from.
 *
 * [roomDp] is the height there is before a music strip or the padlock. When not everything fits,
 * the parts earlier in the order win ([chosen]), and a part with a list gives up lines to fit and says
 * how many it left out.
 */
@Composable
fun GlancePanel(
    sections: List<Glance.Section>,
    notices: List<Notices.Notice>,
    order: List<String>,
    roomDp: Int,
    onOpen: (String) -> Unit,
    scale: Float = 1f,
    /** Each section already in one line ([Glance.oneLine]): one rule above them all, leads aligned. */
    oneLine: Boolean = false,
) {
    // A larger text size draws the whole panel larger, type, rules and spacing together, so it
    // is laid out exactly as at Normal: [roomDp] and every height here are in the panel's own
    // units, which are the phone's dp times [scale].
    val outer = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(outer.density * scale, outer.fontScale)) {
        ThemeMMD(colorScheme = monochrome, typography = houseTypography) { DarkSelection {
            // With the words shown, each app has a line of its own; without, the names share lines.
            val withText = notices.any { it.text != null }
            // Without words, Messaging's and Email's entries have nothing to add to their counts.
            val allNotices = if (withText) notices else notices.filter { it.counted }
            val kept = chosen(sections, allNotices, order, roomDp, oneLine)
            val keptSections = sections.filter { it.key in kept }
            @Suppress("NAME_SHADOWING")
            val notices = if (Glance.UNREAD in kept) allNotices else emptyList()
            val counts = keptSections.filter { it.key == Glance.UNREAD }
            val noticeRows = if (withText) emptyList() else packNotices(notices)
            val textLines = if (withText) textLines(notices) else emptyList()
            val noticeLines = noticeRows.size + textLines.size
            val footDp = if (counts.isEmpty() && noticeLines == 0) 0
                else RULE_DP + PAD_DP + (if (counts.isNotEmpty()) LINE_DP else 0) + noticeLines * LINE_DP
            val fitted = fit(keptSections.filter { it.key != Glance.UNREAD }, order, footDp, roomDp)
            val sharedLead = if (oneLine) leadWidth(fitted.flatMap { it.lines }) else null
            var ruled = false
            var first = true
            Column(Modifier.fillMaxWidth().background(Color.White)) {
                order.forEach { key ->
                    if (key == Glance.UNREAD) {
                        if (counts.isNotEmpty() || noticeLines > 0) Foot(counts, textLines, noticeRows, onOpen)
                        return@forEach
                    }
                    fitted.filter { it.key == key }.forEach { section ->
                        if (!oneLine || !ruled) Rule()
                        ruled = true
                        // One line a section: the rows close up, with the pad once above them all.
                        val pad = if (!oneLine) Modifier.padding(vertical = 2.dp)
                            else Modifier.padding(top = if (first) PAD_DP.dp else ONE_LINE_GAP_DP.dp)
                        first = false
                        Column(Modifier.fillMaxWidth().clickable { onOpen(section.packageName) }.then(pad)) {
                            section.heading?.let {
                                TextMMD(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                            }
                            val leadWidth = sharedLead ?: leadWidth(section.lines)
                            section.lines.forEach { line -> LineRow(line, leadWidth) }
                        }
                    }
                }
                // One line a section is for a crowded lock screen: the rule under it only where
                // there is room left for it.
                val usedDp = baseDp(oneLine) + fitted.size * (LINE_DP + ONE_LINE_GAP_DP) + footDp
                val ruleFits = !oneLine || usedDp + RULE_DP <= roomDp
                if (ruleFits && (fitted.isNotEmpty() || counts.isNotEmpty() || noticeLines > 0)) Rule()
            }
        } }
    }
}

/** The counts on one line, then other apps' notifications, under one rule. */
@Composable
private fun Foot(
    counts: List<Glance.Section>,
    textLines: List<TextLine>,
    noticeRows: List<List<NoticeItem>>,
    onOpen: (String) -> Unit,
) {
    Rule()
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        if (counts.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                counts.forEachIndexed { i, section ->
                    if (i > 0) TextMMD(text = SEPARATOR, style = MaterialTheme.typography.bodySmall)
                    TextMMD(
                        text = section.lines.joinToString(" · ") { it.text },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onOpen(section.packageName) },
                    )
                }
            }
        }
        textLines.forEach { line ->
            Row(
                Modifier.fillMaxWidth().then(
                    if (line.packageName != null) Modifier.clickable { onOpen(line.packageName) } else Modifier,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextMMD(
                    text = line.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                if (line.text != null) {
                    TextMMD(
                        text = "  " + line.text,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        noticeRows.forEach { row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                row.forEachIndexed { i, item ->
                    if (i > 0) TextMMD(text = SEPARATOR, style = MaterialTheme.typography.bodySmall)
                    TextMMD(
                        text = item.text,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (item.packageName != null) {
                            Modifier.clickable { onOpen(item.packageName) }
                        } else Modifier,
                    )
                }
            }
        }
    }
}

/**
 * The lead column, as wide as the widest lead in the section and never narrower than 64 dp, so
 * a section's texts start in one line down the panel. "12:30 PM" and "until 12:30 PM" did not
 * fit the old fixed 64. Measured in the bold it is drawn in; a lead that would take more than
 * half the panel is cut instead of pushing its text off the edge.
 */
@Composable
private fun leadWidth(lines: List<Glance.Line>): Dp {
    val leads = lines.mapNotNull { it.lead }.filterNot(::isMark)
    if (leads.isEmpty()) return MIN_LEAD
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
    val widest = leads.maxOf { measurer.measure(it, style, maxLines = 1).size.width }
    val gap = with(LocalDensity.current) { (widest.toDp() + LEAD_GAP) }
    return gap.coerceIn(MIN_LEAD, (PANEL_WIDTH_DP / 2).dp)
}

private val MIN_LEAD = 64.dp
private val LEAD_GAP = 8.dp

/**
 * A lead that is only a mark, as Memo's "•" before a pinned note's items, not a time: it is set
 * in under the section's lines without one and its text follows close after it, rather than
 * taking the time column (GitHub #1).
 */
private fun isMark(lead: String) = lead.length == 1 && !lead[0].isLetterOrDigit()

private val MARK_INDENT = 10.dp
private val MARK_GAP = 5.dp

@Composable
private fun LineRow(line: Glance.Line, leadWidth: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (line.lead != null && isMark(line.lead)) {
            Spacer(Modifier.width(MARK_INDENT))
            TextMMD(
                text = line.lead,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Spacer(Modifier.width(MARK_GAP))
        } else if (line.lead != null) {
            TextMMD(
                text = line.lead,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(leadWidth),
            )
        }
        TextMMD(
            text = line.text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (line.bold) FontWeight.Bold else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A dotted rule, drawn as the lock screen draws its own above and below the charging line. */
@Composable
private fun Rule() {
    Spacer(Modifier.height(6.dp))
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = Color.Black,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
        )
    }
    Spacer(Modifier.height(6.dp))
}

private const val SEPARATOR = "  ·  "

/** Most lines other apps' notifications take; past that, the last says how many apps more. */
private const val NOTICE_ROWS = 2

/** One pressable piece of a notices line: "Signal 2", or "+3" with nothing to open. */
private data class NoticeItem(val text: String, val packageName: String?)

/**
 * Other apps' notifications as "Name n", as many to a line as fit the panel's width, on at most
 * [NOTICE_ROWS] lines. Measured with the type it is drawn in, not guessed from a letter count.
 */
@Composable
private fun packNotices(notices: List<Notices.Notice>): List<List<NoticeItem>> {
    if (notices.isEmpty()) return emptyList()
    val measurer = rememberTextMeasurer()
    val style: TextStyle = MaterialTheme.typography.bodySmall
    val width = with(LocalDensity.current) { PANEL_WIDTH_DP.dp.toPx() }
    fun w(text: String) = measurer.measure(text, style, maxLines = 1).size.width
    val sep = w(SEPARATOR)
    val items = notices.map { NoticeItem("${it.label} ${it.count}", it.packageName) }
    val rows = mutableListOf(mutableListOf<NoticeItem>())
    var used = 0
    for ((i, item) in items.withIndex()) {
        val need = w(item.text) + if (rows.last().isEmpty()) 0 else sep
        if (rows.last().isNotEmpty() && used + need > width) {
            if (rows.size == NOTICE_ROWS) {
                // No room for the rest: the last line ends by saying how many apps are left,
                // giving up its own last names until that fits.
                val row = rows.last()
                var left = items.size - i
                fun more() = NoticeItem("+$left", null)
                while (row.size > 1 && used + sep + w(more().text) > width) {
                    val gone = row.removeAt(row.lastIndex)
                    used -= w(gone.text) + sep
                    left++
                }
                row += more()
                return rows
            }
            rows += mutableListOf<NoticeItem>()
            used = 0
            rows.last() += item
            used = w(item.text)
        } else {
            rows.last() += item
            used += need
        }
    }
    return rows
}

/** Most apps given a line of their own when what they say is shown; the rest are counted. */
private const val TEXT_ROWS = 3

/** A line of an app's words: "Signal 2" in bold, then what the newest one says. */
private data class TextLine(val name: String, val text: String?, val packageName: String?)

/**
 * One line per app, newest first, up to [TEXT_ROWS]; past that, the last line says how many
 * apps more. An app whose words are not to be shown keeps its line with only its name.
 */
@Composable
private fun textLines(notices: List<Notices.Notice>): List<TextLine> {
    // Messaging and Email say how many on their own line above, so here only their name.
    val lines = notices.map { TextLine(if (it.counted) "${it.label} ${it.count}" else it.label, it.text, it.packageName) }
    if (lines.size <= TEXT_ROWS) return lines
    val more = stringResource(R.string.panel_more_apps).format(lines.size - (TEXT_ROWS - 1))
    return lines.take(TEXT_ROWS - 1) + TextLine(more, null, null)
}

/**
 * A part with more lines than fit gives up lines from its end and says how many it left out,
 * whichever app it comes from: today's events, the tasks due today, anything with a list. Each
 * always keeps its first line: "+3 more" on its own says there is something without saying
 * what. The room goes to the parts in the person's [order], each already sure of its least
 * height ([leastLines]); a part that does not fit even so was left out whole by [chosen].
 */
@Composable
private fun fit(stacked: List<Glance.Section>, order: List<String>, footDp: Int, roomDp: Int): List<Glance.Section> {
    val shown = shownLines(stacked, order, footDp, roomDp)
    return stacked.mapIndexed { i, section ->
        val n = shown[i]
        if (n >= section.lines.size) section
        else {
            val more = stringResource(R.string.panel_more).format(leftOut(section.lines.drop(n - 1)))
            section.copy(lines = section.lines.take(n - 1) + Glance.Line(null, more, false))
        }
    }
}

/**
 * How many things the lines a part gives up stand for: one each, except the app's own "and n
 * more" line, which stands for its n (marked by the app, so this holds in any language).
 */
internal fun leftOut(cut: List<Glance.Line>): Int = cut.sumOf { if (it.more > 0) it.more else 1 }

/** The fewest lines a part is drawn in: all of one or two, else its first and "+N more". */
private fun leastLines(section: Glance.Section) = minOf(section.lines.size, 2)

/**
 * How many lines each of [stacked] is drawn in, the "+N more" line counted, in [roomDp]: every
 * part its least, then what room is left line by line to the parts in [order].
 */
internal fun shownLines(stacked: List<Glance.Section>, order: List<String>, footDp: Int, roomDp: Int): List<Int> {
    val shown = stacked.map { leastLines(it) }.toMutableList()
    var spare = (roomDp - footDp - RULE_DP - stacked.indices.sumOf { sectionDp(stacked[it], shown[it]) }) / LINE_DP
    stacked.indices.sortedBy { i -> order.indexOf(stacked[i].key).let { if (it < 0) Int.MAX_VALUE else it } }.forEach { i ->
        val give = minOf(stacked[i].lines.size - shown[i], maxOf(spare, 0))
        shown[i] += give
        spare -= give
    }
    return shown
}

private fun sectionDp(section: Glance.Section, lines: Int) =
    RULE_DP + PAD_DP + (if (section.heading != null) HEADING_DP else 0) + lines * LINE_DP

/**
 * Each part of [order] that has something to show, with the least height it can be drawn in, in
 * dp: a part with a list cut to its first line and "+N more", as [fit] can leave it. Other apps' notifications are counted at the most lines they can take, since how
 * they pack is known only once measured.
 */
private fun parts(
    sections: List<Glance.Section>,
    notices: List<Notices.Notice>,
    order: List<String>,
    oneLine: Boolean = false,
): List<Pair<String, Int>> = order.mapNotNull { key ->
    if (key == Glance.UNREAD) {
        val counts = sections.any { it.key == Glance.UNREAD }
        val withText = notices.any { it.text != null }
        val shown = if (withText) notices.size else notices.count { it.counted }
        val noticeLines = minOf(shown, if (withText) TEXT_ROWS else NOTICE_ROWS)
        if (!counts && noticeLines == 0) null
        else key to RULE_DP + PAD_DP + (if (counts) LINE_DP else 0) + noticeLines * LINE_DP
    } else {
        val section = sections.firstOrNull { it.key == key } ?: return@mapNotNull null
        key to if (oneLine) LINE_DP + ONE_LINE_GAP_DP else sectionDp(section, leastLines(section))
    }
}

/** The least height, in dp, the whole panel can be drawn in. See [parts]. */
internal fun leastDp(sections: List<Glance.Section>, notices: List<Notices.Notice>, order: List<String>, oneLine: Boolean = false): Int =
    parts(sections, notices, order, oneLine).sumOf { it.second } + baseDp(oneLine)

/** Beside the parts: the rule under them all, or with one line a section the rule and pad over them. */
private fun baseDp(oneLine: Boolean) = if (oneLine) RULE_DP + PAD_DP - ONE_LINE_GAP_DP else RULE_DP

/** The least height of the first part in [order] that has something to show; 0 for none. */
internal fun firstDp(sections: List<Glance.Section>, notices: List<Notices.Notice>, order: List<String>, oneLine: Boolean = false): Int =
    parts(sections, notices, order, oneLine).firstOrNull()?.let { it.second + baseDp(oneLine) } ?: 0

/**
 * The parts drawn in [roomDp]: the first in [order] always, then each after it that still fits,
 * so the person's order decides what wins. A part too tall for what is left is passed over for
 * a smaller one after it.
 */
internal fun chosen(
    sections: List<Glance.Section>,
    notices: List<Notices.Notice>,
    order: List<String>,
    roomDp: Int,
    oneLine: Boolean = false,
): Set<String> {
    val kept = mutableSetOf<String>()
    var used = baseDp(oneLine)
    parts(sections, notices, order, oneLine).forEach { (key, dp) ->
        if (kept.isEmpty() || used + dp <= roomDp) {
            kept += key
            used += dp
        }
    }
    return kept
}
