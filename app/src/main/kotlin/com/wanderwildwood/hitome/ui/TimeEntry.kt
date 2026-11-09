package com.wanderwildwood.hitome.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.time.TimeInputMMD
import com.mudita.mmd.components.time.TimeInputStateMMD
import com.mudita.mmd.components.time.TimePickerSelectionMode
import com.wanderwildwood.hitome.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Setting a time, as Datebook (koyomi ui/TimeEntry.kt) sets one: Mudita's time input, the hour
 * and the minute typed as the Kompakt's own clock sets them, on whichever clock the phone uses.
 * Read from there and kept to what Glance needs; the AM/PM choice is the house choice row.
 */

/**
 * The hour on a 12-hour clock face and back. Twelve is the awkward hour, since 12 AM is the
 * first hour of the day and 12 PM the thirteenth.
 */
object Clock12 {
    /** 0–23 to the hour a 12-hour clock shows, 1–12. */
    fun faceHour(hour24: Int): Int {
        require(hour24 in 0..23)
        return (hour24 % 12).let { if (it == 0) 12 else it }
    }

    fun isPm(hour24: Int): Boolean = hour24 >= 12

    /** A clock-face hour, 1–12, with its half of the day, to 0–23. */
    fun toHour24(face: Int, pm: Boolean): Int {
        require(face in 1..12)
        return face % 12 + if (pm) 12 else 0
    }

    /**
     * What a typed hour means on the 12-hour clock: 1–12 in the half of the day showing; 0 is
     * that half's twelve; 13–23 is plainly the 24-hour clock, taken as it stands.
     */
    fun typed(value: Int, pm: Boolean): Int = when (value) {
        0 -> if (pm) 12 else 0
        in 1..12 -> toHour24(value, pm)
        in 13..23 -> value
        else -> throw IllegalArgumentException("hour $value")
    }

    /** The half of the day moved, the clock face kept: 9 AM becomes 9 PM, 12 AM becomes 12 PM. */
    fun withHalf(hour24: Int, pm: Boolean): Int = if (isPm(hour24) == pm) hour24 else (hour24 + 12) % 24
}

/**
 * What [TimeInputMMD] reads and writes. MMD 1.0.2's 12-hour mode sets a typed hour as it
 * stands (a "6" with PM showing came out as six in the morning), so MMD is always told this is
 * the 24-hour clock and, on a 12-hour phone, handed the clock-face hour; the half of the day is
 * chosen under it. [hour24] is the time itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
class ClockState(hour24: Int, minute: Int, val twelveHour: Boolean) : TimeInputStateMMD {
    var hour24 by mutableIntStateOf(hour24)
        private set

    override var minute by mutableIntStateOf(minute)

    override var is24hour: Boolean
        get() = true
        set(_) {}

    override var selection by mutableStateOf(TimePickerSelectionMode.Hour)

    override var isAfternoon: Boolean
        get() = Clock12.isPm(hour24)
        set(pm) { hour24 = Clock12.withHalf(hour24, pm) }

    override var hour: Int
        get() = if (twelveHour) Clock12.faceHour(hour24) else hour24
        set(value) { hour24 = if (twelveHour) Clock12.typed(value, isAfternoon) else value }

    companion object {
        fun saver(twelveHour: Boolean) = Saver<ClockState, List<Int>>(
            save = { listOf(it.hour24, it.minute) },
            restore = { ClockState(it[0], it[1], twelveHour) },
        )
    }
}

/** A time to set, under [title]; on a 12-hour phone with AM and PM under the two numbers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(title: String, initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val twelveHour = !DateFormat.is24HourFormat(LocalContext.current)
    val state = rememberSaveable(saver = ClockState.saver(twelveHour)) {
        ClockState(initial.hour, initial.minute, twelveHour)
    }
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        TimeInputMMD(state = state, modifier = Modifier.align(Alignment.CenterHorizontally))
        if (twelveHour) {
            HouseChoiceRow(
                options = listOf(halfOfDay(false), halfOfDay(true)),
                selected = if (state.isAfternoon) 1 else 0,
                onSelect = { state.isAfternoon = it == 1 },
            )
            Spacer(Modifier.height(16.dp))
        } else {
            Spacer(Modifier.height(8.dp))
        }
        DialogButtons(
            cancel = stringResource(R.string.cancel),
            confirm = stringResource(R.string.ok),
            onCancel = onDismiss,
            onConfirm = {
                onPick(LocalTime.of(state.hour24, state.minute))
                onDismiss()
            },
            confirmOn = true,
        )
    }
}

/** "AM" or "PM" in the phone's own words for its language. */
private fun halfOfDay(pm: Boolean): String =
    DateTimeFormatter.ofPattern("a", Locale.getDefault()).format(LocalTime.of(if (pm) 12 else 0, 0))
