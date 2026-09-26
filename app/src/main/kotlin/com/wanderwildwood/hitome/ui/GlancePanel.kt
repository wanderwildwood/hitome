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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.hitome.Glance
import com.wanderwildwood.hitome.R

/** Heights the panel is laid out by, in dp; a line of bodySmall with its spacing. */
internal const val HEADING_DP = 20
internal const val LINE_DP = 22
internal const val RULE_DP = 13
/** The 2dp above and below each section and the counts line. */
internal const val PAD_DP = 4

/**
 * The stacked sections, each under a dotted rule like the lock screen's own, then the counts on
 * one line. Pressing a section, or a count, opens the app it came from.
 *
 * [roomDp] is the height there is before a music strip or the padlock; the first section,
 * today's events, gives up lines to fit it and says how many it left out.
 */
@Composable
fun GlancePanel(sections: List<Glance.Section>, roomDp: Int, onOpen: (String) -> Unit) {
    ThemeMMD(colorScheme = monochrome) {
        val stacked = sections.filter { it.packageName in Glance.STACKED }
        val counts = sections.filter { it.packageName in Glance.COUNTS }
        val fitted = fit(stacked, counts.isNotEmpty(), roomDp)
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
            if (counts.isNotEmpty()) {
                Rule()
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    counts.forEachIndexed { i, section ->
                        if (i > 0) TextMMD(text = "  ·  ", style = MaterialTheme.typography.bodySmall)
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
            if (fitted.isNotEmpty() || counts.isNotEmpty()) Rule()
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

/**
 * The first section, today's events, gives up lines from its end until everything fits in
 * [roomDp], and says how many it left out. It always keeps its first line: "+3 more" on its own
 * says there is something today without saying what. Nothing else is cut - the weather is two
 * lines at most and the counts one.
 */
@Composable
private fun fit(stacked: List<Glance.Section>, hasCounts: Boolean, roomDp: Int): List<Glance.Section> {
    val first = stacked.firstOrNull() ?: return stacked
    val rest = stacked.drop(1)
    fun height(firstLines: Int) =
        RULE_DP + PAD_DP + (if (first.heading != null) HEADING_DP else 0) + firstLines * LINE_DP +
            rest.sumOf { RULE_DP + PAD_DP + (if (it.heading != null) HEADING_DP else 0) + it.lines.size * LINE_DP } +
            (if (hasCounts) RULE_DP + PAD_DP + LINE_DP else 0) + RULE_DP
    val all = first.lines.size
    if (height(all) <= roomDp) return stacked
    // Shown lines plus the "+N more" line; at least one real line whatever the room.
    var shown = all - 1
    while (shown > 1 && height(shown + 1) > roomDp) shown--
    val more = stringResource(R.string.panel_more).format(all - shown)
    return listOf(first.copy(lines = first.lines.take(shown) + Glance.Line(null, more, false))) + rest
}
