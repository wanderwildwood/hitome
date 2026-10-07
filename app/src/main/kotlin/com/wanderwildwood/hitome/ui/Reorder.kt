package com.wanderwildwood.hitome.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Hold a row to pick it up, move the finger to carry it, let go to put it down. A row held and
 * let go without moving asks for the menu instead ([onMenu]), which has Move up and Move down
 * for when dragging is fiddly.
 *
 * Read before the row's own presses ([PointerEventPass.Initial]): a hold is taken whole, so the
 * switch under the finger is not also flipped and the list does not scroll. A quick press or a
 * scroll before the hold is left alone. [onMove] is given where the finger is, in the window,
 * so the list can move the row past the one the finger has crossed the middle of; the row jumps
 * one place at a time rather than gliding, which is kinder to e-ink.
 *
 * The gesture outlives recompositions (a row that moves keeps its [key]), so it always calls
 * the newest of the functions it was given.
 */
@Composable
fun Modifier.holdToMove(
    key: Any,
    toWindow: (Offset) -> Offset,
    onPickUp: () -> Unit,
    onMove: (windowY: Float) -> Unit,
    onDrop: () -> Unit,
    onMenu: () -> Unit,
): Modifier {
    val window by rememberUpdatedState(toWindow)
    val pickUp by rememberUpdatedState(onPickUp)
    val move by rememberUpdatedState(onMove)
    val drop by rememberUpdatedState(onDrop)
    val menu by rememberUpdatedState(onMenu)
    return pointerInput(key) { awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes
                    .firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                if (!change.pressed || change.isConsumed) return@withTimeoutOrNull false
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    return@withTimeoutOrNull false
                }
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } == null
        if (!held) return@awaitEachGesture
        pickUp()
        val start = window(down.position).y
        var moved = false
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes
                .firstOrNull { it.id == down.id } ?: break
            change.consume()
            if (!change.pressed) break
            val y = window(change.position).y
            if (!moved && kotlin.math.abs(y - start) > viewConfiguration.touchSlop) moved = true
            if (moved) move(y)
        }
        drop()
        if (!moved) menu()
    } }
}
