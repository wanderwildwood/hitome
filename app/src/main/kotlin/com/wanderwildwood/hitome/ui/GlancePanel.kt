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
/** The panel's width, the lock screen's own dotted rules. */
internal const val PANEL_WIDTH_DP = 240

/**
 * The stacked sections, each under a dotted rule like the lock screen's own, then the counts on
 * one line, then other apps' notifications as names and numbers. Pressing a section, a count or
 * a name opens the app it came from.
 *
 * [roomDp] is the height there is before a music strip or the padlock; the first section,
 * today's events, gives up lines to fit it and says how many it left out.
 */
@Composable
fun GlancePanel(
    sections: List<Glance.Section>,
    notices: List<Notices.Notice>,
    roomDp: Int,
    onOpen: (String) -> Unit,
) {
    ThemeMMD(colorScheme = monochrome) {
        val stacked = sections.filter { it.packageName in Glance.STACKED }
        val counts = sections.filter { it.packageName in Glance.COUNTS }
        // With the words shown, each app has a line of its own; without, the names share lines.
        val withText = notices.any { it.text != null }
        val noticeRows = if (withText) emptyList() else packNotices(notices)
        val textLines = if (withText) textLines(notices) else emptyList()
        val noticeLines = noticeRows.size + textLines.size
        val footDp = if (counts.isEmpty() && noticeLines == 0) 0
            else RULE_DP + PAD_DP + (if (counts.isNotEmpty()) LINE_DP else 0) + noticeLines * LINE_DP
        val fitted = fit(stacked, footDp, roomDp)
        Column(Modifier.fillMaxWidth().background(Color.White)) {
            fitted.forEach { section ->
                Rule()
                Column(Modifier.fillMaxWidth().clickable { onOpen(section.packageName) }.padding(vertical = 2.dp)) {
                    section.heading?.let {
                        TextMMD(
                            text = it.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                    section.lines.forEach { line -> LineRow(line) }
                }
            }
            if (counts.isNotEmpty() || noticeLines > 0) {
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
            if (fitted.isNotEmpty() || counts.isNotEmpty() || noticeLines > 0) Rule()
        }
    }
}

@Composable
private fun LineRow(line: Glance.Line) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (line.lead != null) {
            TextMMD(
                text = line.lead,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.width(64.dp),
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
    val lines = notices.map { TextLine("${it.label} ${it.count}", it.text, it.packageName) }
    if (lines.size <= TEXT_ROWS) return lines
    val more = stringResource(R.string.panel_more_apps).format(lines.size - (TEXT_ROWS - 1))
    return lines.take(TEXT_ROWS - 1) + TextLine(more, null, null)
}

/**
 * The first section, today's events, gives up lines from its end until everything fits in
 * [roomDp], and says how many it left out. It always keeps its first line: "+3 more" on its own
 * says there is something today without saying what. Nothing else is cut - the weather is two
 * lines at most, the counts one, and other apps' notifications three.
 */
@Composable
private fun fit(stacked: List<Glance.Section>, footDp: Int, roomDp: Int): List<Glance.Section> {
    val first = stacked.firstOrNull() ?: return stacked
    val rest = stacked.drop(1)
    fun height(firstLines: Int) =
        RULE_DP + PAD_DP + (if (first.heading != null) HEADING_DP else 0) + firstLines * LINE_DP +
            rest.sumOf { RULE_DP + PAD_DP + (if (it.heading != null) HEADING_DP else 0) + it.lines.size * LINE_DP } +
            footDp + RULE_DP
    val all = first.lines.size
    if (height(all) <= roomDp) return stacked
    // Shown lines plus the "+N more" line; at least one real line whatever the room.
    var shown = all - 1
    while (shown > 1 && height(shown + 1) > roomDp) shown--
    val more = stringResource(R.string.panel_more).format(all - shown)
    return listOf(first.copy(lines = first.lines.take(shown) + Glance.Line(null, more, false))) + rest
}
