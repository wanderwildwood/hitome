package com.wanderwildwood.hitome

/**
 * Where the lock screen's own items end above the panel, and where its foot begins below it,
 * worked out from the boxes the lock screen reports. Kept apart from [LockscreenPanel] so it
 * can be checked without a phone.
 *
 * The foot is the padlock and the line under it ("Swipe up to unlock" and the like). That line
 * is in the phone's language and wraps to two lines in some (German: "Drücke zum Entsperren den
 * / Netzschalter"), which lifts the padlock. A panel that assumed one line took the lifted
 * padlock for an item above it and sat under it, over the line. So the foot is found, not
 * assumed: by the padlock's id where the lock screen reports ids, else as the highest of the
 * small items in the lowest part of the screen.
 */
internal object LockScreenLayout {

    /** One item the lock screen reports: its box in screen pixels, whether it holds others, its id. */
    data class Box(val top: Int, val bottom: Int, val leaf: Boolean, val id: String? = null)

    /**
     * Where the foot begins, in pixels from the top: the padlock's top edge, or whatever of the
     * foot is highest. Only for lock screens that draw their own player and notifications
     * (MuditaOS 2.0, KompaktOS); null when no foot can be seen, and the old fixed limit holds.
     */
    fun footTop(boxes: List<Box>, screen: Int): Int? {
        boxes.filter { it.id != null && PADLOCK_IDS.any { p -> it.id.endsWith(p) } && it.bottom > it.top }
            .minOfOrNull { it.top }
            ?.let { return it }
        val from = (screen * FOOT_PART).toInt()
        return boxes.filter { it.leaf && it.top >= from && it.bottom > it.top }.minOfOrNull { it.top }
    }

    /**
     * The bottom edge, in pixels, of the lowest item the lock screen draws above the panel, or
     * null when none was seen. [own]: the lock screen draws its own player and notifications;
     * there boxes that hold others count too (the player's frame ends below its buttons), and
     * items count down to the foot ([foot], or [PADLOCK_PART] of the screen where no foot was
     * seen). Elsewhere only single items in the upper part count, as on MuditaOS 1.x.
     *
     * Mudita 2.0 draws its player's frame 18 px below its buttons, and a panel under the buttons
     * alone cut into it; a missed call or a text puts rows under the player, and their boxes end
     * below their text. A box reaching past the limit is one of the lock screen's own frames
     * (the whole screen, the notification area, the foot), and is left out.
     */
    fun contentBottom(boxes: List<Box>, screen: Int, own: Boolean, foot: Int?, statusBar: Int): Int? {
        val upper = (screen * UPPER_PART).toInt()
        val padlock = (screen * PADLOCK_PART).toInt()
        val leafLimit = if (own) minOf(padlock, foot ?: padlock) else upper
        var lowest = -1
        boxes.forEach { b ->
            if (!b.leaf && !own) return@forEach
            if (b.top > statusBar && b.bottom < leafLimit && b.bottom > b.top) lowest = maxOf(lowest, b.bottom)
        }
        return lowest.takeIf { it > 0 }
    }

    /**
     * How far down the panel may reach on a lock screen that draws its own player: a [gap] above
     * the foot, and never lower than [PADLOCK_PART] of the screen, where it reached before.
     */
    fun ownFloor(screen: Int, foot: Int?, gap: Int): Int {
        val padlock = (screen * PADLOCK_PART).toInt()
        return if (foot == null) padlock else minOf(padlock, foot - gap)
    }

    /** The padlock's id on MuditaOS 2.0 ("lock_icon", seen 21 Nov 2026) and in Android 12's own lock screen. */
    private val PADLOCK_IDS = listOf(":id/lock_icon", ":id/lock_icon_view")

    // The lock screen's own items are measured only in the top 60% of the screen on MuditaOS
    // 1.x; below that are the padlock and "Swipe Up".
    const val UPPER_PART = 0.6f
    // Where the lock screen draws its own notifications, they are measured down to here at
    // most: below it are the padlock (from 86% on a Kompakt in English) and the line under it.
    const val PADLOCK_PART = 0.85f
    // Where the foot is looked for when the padlock's id is not reported: the lowest quarter.
    // In English the padlock starts at 86% of the screen; each extra line of the hint lifts it
    // by about 3%, so a hint of four or five lines still starts below here.
    const val FOOT_PART = 0.75f
}
