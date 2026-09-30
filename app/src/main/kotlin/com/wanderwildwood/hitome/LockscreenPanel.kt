package com.wanderwildwood.hitome

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.wanderwildwood.hitome.ui.GlancePanel
import com.wanderwildwood.hitome.ui.leastDp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The panel on the lock screen: today's events, the weather, and what is unread, each from the
 * app it belongs to (see [Glance]), then how many notifications each chosen app has waiting
 * (see [Notices]).
 *
 * It is an accessibility overlay, the one kind of window the system draws above the lock
 * screen, and only an accessibility service may add one. The way it is added, and when it is
 * taken down, follows Katapult by gezimos (GPL-3.0), whose lock-screen widgets work this way.
 *
 * It shows only while the phone is locked with the screen on, the PIN pad is not up, and no call
 * is ringing or live. It sits under the lowest thing the lock screen itself shows in its upper
 * half - the date, or the charging line, or Mudita's own music player - measured each time
 * rather than assumed, and above the strip a music app may draw near the foot of the screen.
 * Katapult's own lock-screen widgets, when they are drawn, are made room for in the same way.
 */
class LockscreenPanel(private val service: AccessibilityService) :
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private val handler = Handler(Looper.getMainLooper())
    private val sections = mutableStateOf<List<Glance.Section>>(emptyList())
    private val notices = mutableStateOf<List<Notices.Notice>>(emptyList())
    private var scope: CoroutineScope? = null
    private var reading: Job? = null
    private var overlay: ComposeView? = null
    private var overlayTop = -1
    private var overlayRoom = -1

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Read afresh on every wake, so the panel is never older than the last look.
            if (intent.action == Intent.ACTION_SCREEN_ON) readAll()
            // A new look at the lock screen: whether its items can be read is learned afresh.
            if (intent.action == Intent.ACTION_SCREEN_OFF) sawLockScreen = false
            evaluate()
        }
    }

    /** An app that finishes something in the background (the weather) says so here. */
    private val changes = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = readAll()
    }

    /**
     * Events come only from the system UI, and on unlock the last one is the lock screen leaving
     * while it still reports itself locked. Nothing fires after that to take the panel down, so
     * while it is up it looks again on a short tick.
     */
    private val recheck = Runnable { evaluate() }

    fun start() {
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        service.registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        })
        ((Glance.STACKED + Glance.COUNTS).map { Glance.uri(it) } + Today.watched(service)).forEach { uri ->
            try {
                service.contentResolver.registerContentObserver(uri, true, changes)
            } catch (_: Exception) {}
        }
        scope?.launch {
            Notices.now.collect {
                notices.value = it
                evaluate()
            }
        }
        readAll()
    }

    fun stop() {
        handler.removeCallbacks(recheck)
        scope?.cancel()
        scope = null
        remove()
        try { service.unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        try { service.contentResolver.unregisterContentObserver(changes) } catch (_: Exception) {}
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }

    private fun readAll() {
        val s = scope ?: return
        reading?.cancel()
        reading = s.launch {
            sections.value = withContext(Dispatchers.IO) {
                Glance.readAll(service)
            }
            evaluate()
        }
    }

    /**
     * The lock screen said something changed: a swipe began, the PIN pad came up, it is going
     * away. Looked at at once, then often for a moment, because the lock screen reports itself
     * locked for a few frames after an unlock has begun, and a panel still standing then is
     * one the reader sees over their home screen.
     */
    fun lockScreenChanged() {
        quickUntil = SystemClock.uptimeMillis() + QUICK_FOR_MS
        evaluate()
    }

    /** Until when to look again quickly. See [lockScreenChanged]. */
    private var quickUntil = 0L

    /** Until when the panel stays down after the lock screen moved under it. See [place]. */
    private var settleUntil = 0L
    private var settleTop = -1

    /** Whether the lock screen's items have been read since the screen came on. See [place]. */
    private var sawLockScreen = false

    fun evaluate() {
        val keyguard = service.getSystemService(KeyguardManager::class.java)
        val power = service.getSystemService(PowerManager::class.java)
        val locked = keyguard.isKeyguardLocked
        val interactive = power.isInteractive
        val covered = appOverLockScreen()
        val show = locked &&
            interactive &&
            (sections.value.isNotEmpty() || notices.value.isNotEmpty()) &&
            !inCall() &&
            !covered &&
            !pinShowing()
        if (show) {
            place()
        } else {
            if (overlay != null) {
                Log.i(TAG, "hide: locked=$locked interactive=$interactive covered=$covered")
            }
            remove()
        }
        handler.removeCallbacks(recheck)
        // Looked at again while it is up, and while it waits to settle, so that it comes back.
        if (overlay != null || SystemClock.uptimeMillis() < settleUntil + SETTLE_MS) {
            val quick = SystemClock.uptimeMillis() < quickUntil
            handler.postDelayed(recheck, if (quick) QUICK_RECHECK_MS else RECHECK_MS)
        }
    }

    /** Adds the panel, or moves it when what the lock screen shows above it has changed. */
    private fun place() {
        val density = service.resources.displayMetrics.density
        val bottom = lockScreenBottom()
        // ⚠ The lock screen's own items gone, on a lock screen that had them, is the lock screen
        // going: Android still says "locked" for up to 0.6 s after it has visibly gone, and a
        // panel put back then was drawn over the home screen. The fallback place is only for
        // lock screens that could never be read.
        if (bottom == null && sawLockScreen) {
            if (overlay != null) Log.i(TAG, "lock screen's items gone: down")
            remove()
            return
        }
        if (bottom != null) sawLockScreen = true
        val gap = (GAP_DP * density).toInt()
        val screen = service.resources.displayMetrics.heightPixels
        var top = bottom?.let { it + gap } ?: (FALLBACK_TOP_DP * density).toInt()
        val floor = screen - ((MUSIC_STRIP_TOP_FROM_BOTTOM_DP + GAP_DP) * density).toInt()
        // Katapult's widgets are made room for. One in the upper part (its music player) is sat
        // under, like the lock screen's own items. One lower down (its notifications, which can
        // be dragged anywhere) splits what is left in two, and the panel takes the first part
        // it fits in whole. Where it fits in neither, it does not show: a panel over Katapult's
        // is worse than none, and moving Katapult's widget makes room.
        val katapult = katapultBounds()
        katapult.filter { it.top < screen * UPPER_PART }.forEach { top = maxOf(top, it.bottom + gap) }
        val gaps = mutableListOf<Pair<Int, Int>>()
        var from = top
        katapult.filter { it.top >= screen * UPPER_PART }.sortedBy { it.top }.forEach {
            gaps += from to minOf(it.top - gap, floor)
            from = maxOf(from, it.bottom + gap)
        }
        gaps += from to floor
        val least = leastDp(sections.value, notices.value)
        val fits = gaps.firstOrNull { (a, b) -> (b - a) / density >= least }
        if (fits == null) {
            if (overlay != null) Log.i(TAG, "no room beside Katapult's widgets: down")
            remove()
            return
        }
        top = fits.first
        val room = ((fits.second - top) / density).toInt()
        if (overlay != null && top == overlayTop && room == overlayRoom) return
        val now = SystemClock.uptimeMillis()
        // ⚠ Never moved while it is up. The lock screen rearranges itself as it goes away, and
        // a panel that followed it was taken down and put up again as a new window at the
        // moment of unlocking -- a full redraw on e-ink, over the home screen, which is the
        // panel seen to linger. So a change of place takes it down, and it comes back only once
        // the lock screen has held still; if the change was an unlock, it does not come back.
        if (overlay != null) {
            Log.i(TAG, "moved $overlayTop/$overlayRoom -> $top/$room: down until it settles")
            remove()
            settleUntil = now + SETTLE_MS
            settleTop = top
            return
        }
        if (now < settleUntil) {
            // Moved again while waiting: the wait starts over from here.
            if (top != settleTop) {
                settleUntil = now + SETTLE_MS
                settleTop = top
            }
            return
        }
        Log.i(TAG, "show at $top, room $room")
        val view = ComposeView(service).apply {
            setViewTreeLifecycleOwner(this@LockscreenPanel)
            setViewTreeViewModelStoreOwner(this@LockscreenPanel)
            setViewTreeSavedStateRegistryOwner(this@LockscreenPanel)
            setContent {
                GlancePanel(
                    sections = sections.value,
                    notices = notices.value,
                    roomDp = room,
                    onOpen = { pkg -> open(pkg) },
                )
            }
        }
        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            // Told of any touch outside the panel, so that it can go the moment a swipe to
            // unlock begins. See [touchedAway].
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            width = (WIDTH_DP * density).toInt()
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            windowAnimations = R.style.PanelWindow
            y = top
        }
        view.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) touchedAway()
            false
        }
        try {
            service.getSystemService(WindowManager::class.java).addView(view, params)
            overlay = view
            overlayTop = top
            overlayRoom = room
        } catch (_: Exception) {
            overlay = null
        }
    }

    /**
     * A finger went down somewhere else on the lock screen: most likely the start of the swipe
     * that unlocks it. The panel goes now, before the unlock, because the lock screen vanishes
     * the moment that swipe ends -- before Android says anything about unlocking -- and a
     * panel that waited for the announcement was seen over whatever was underneath. If it was
     * not an unlock, the panel comes back once the lock screen has held still for a moment.
     */
    private fun touchedAway() {
        if (overlay == null) return
        Log.i(TAG, "touched outside: down")
        settleTop = overlayTop
        settleUntil = SystemClock.uptimeMillis() + TOUCH_HOLD_MS
        remove()
        evaluate()
    }

    /**
     * Takes the panel down at once.
     *
     * ⚠ `removeView` alone was not at once: the system animated the window out, and the panel
     * stayed on screen for a further 300-500 ms after it was asked to go -- measured on a
     * Kompakt, from the request to the layer leaving the compositor -- which is the panel seen
     * lingering over the home screen. So the window has no animations (`R.style.PanelWindow`),
     * what is drawn is hidden first, and the window goes on the frame after that.
     */
    private fun remove() {
        overlay?.let { view ->
            view.visibility = View.INVISIBLE
            // Removed once the blank frame has been drawn, not before: removed at once, the
            // screen kept the last picture of the panel for as long as the window took to go.
            view.postOnAnimation {
                try { service.getSystemService(WindowManager::class.java).removeViewImmediate(view) } catch (_: Exception) {}
            }
        }
        overlay = null
        overlayTop = -1
        overlayRoom = -1
    }

    /** Unlock, then open the app whose lines were pressed. */
    private fun open(pkg: String) {
        val intent = Intent(service, UnlockActivity::class.java)
            .putExtra(UnlockActivity.EXTRA_PACKAGE, pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        try { service.startActivity(intent) } catch (_: Exception) {}
    }

    /**
     * The bottom edge, in pixels, of the lowest thing the lock screen draws in the upper part
     * of the screen: the date, the charging line, or Mudita's music widget. Null when the lock
     * screen cannot be read, and the panel then takes a fixed place.
     */
    private fun lockScreenBottom(): Int? = try {
        val screen = service.resources.displayMetrics.heightPixels
        val limit = (screen * UPPER_PART).toInt()
        var lowest = -1
        val bounds = Rect()
        service.windows.forEach { window ->
            val root = window.root ?: return@forEach
            if (root.packageName != SYSTEM_UI) return@forEach
            fun walk(node: AccessibilityNodeInfo) {
                if (node.isVisibleToUser && node.childCount == 0) {
                    node.getBoundsInScreen(bounds)
                    // Leaves only, in the upper part, and not the status bar along the top.
                    if (bounds.top > STATUS_BAR_PX && bounds.bottom < limit && bounds.height() > 0) {
                        lowest = maxOf(lowest, bounds.bottom)
                    }
                }
                for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it) }
            }
            walk(root)
        }
        lowest.takeIf { it > 0 }
    } catch (_: Exception) {
        null
    }

    /**
     * Where Katapult's lock-screen widgets are, while it is drawing them. Looked at rather than
     * inferred from its service being on: that one service also locks the phone on a double tap,
     * covers the status-bar clock and runs the screensaver, and its widgets are off by default.
     */
    private fun katapultBounds(): List<Rect> = try {
        service.windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }
            .filter { it.root?.packageName == KATAPULT }
            .map { w -> Rect().also { w.getBoundsInScreen(it) } }
            .filter { it.height() > 0 && it.top > STATUS_BAR_PX }
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * The PIN pad is up when a visible text field holds input focus. Looked at, not waited
     * for: showing the pad again does not always fire a focus event.
     */
    private fun pinShowing(): Boolean = try {
        val focus = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        focus != null && focus.isVisibleToUser && focus.className?.contains("EditText") == true
    } catch (_: Exception) {
        false
    }

    /**
     * An app has put something up over the lock screen: the call screen, an alarm. [inCall] is
     * not enough on its own. A phone set to vibrate never puts its audio into ringing mode -- a
     * Kompakt's log goes straight from normal to in-call when the call is answered -- so while it
     * rang, a panel could sit over Accept and Decline. Whatever an app shows over the lock screen
     * is what the person is there to look at, so the panel gives way to all of it.
     */
    private fun appOverLockScreen(): Boolean = try {
        service.windows.any { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
    } catch (_: Exception) {
        false
    }

    /** A ringing or live call draws its own screen over the lock screen. */
    private fun inCall(): Boolean = try {
        when (service.getSystemService(AudioManager::class.java).mode) {
            AudioManager.MODE_RINGTONE,
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION -> true
            else -> false
        }
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val SYSTEM_UI = "com.android.systemui"
        const val KATAPULT = "com.gezimos.katapult"
        // The width of the lock screen's own dotted rules, so the panel lines up under them.
        const val WIDTH_DP = com.wanderwildwood.hitome.ui.PANEL_WIDTH_DP.toFloat()
        const val GAP_DP = 8f
        // Where the panel goes if the lock screen cannot be read: under the date and a
        // charging line, which is where Mudita's own widget ends.
        const val FALLBACK_TOP_DP = 292f
        // A music app's lock-screen strip: 96dp up from the foot and about 52dp tall.
        const val MUSIC_STRIP_TOP_FROM_BOTTOM_DP = 148f
        // The lock screen's own items are measured only in the top 60% of the screen; below
        // that are the padlock and "Swipe Up".
        const val UPPER_PART = 0.6f
        const val STATUS_BAR_PX = 48
        // How often the panel looks again while it is up: the music strips' interval. Faster
        // for most of a second after the lock screen reports a change, which is when an
        // unlock is under way. See [lockScreenChanged].
        const val TAG = "GlancePanel"
        const val RECHECK_MS = 120L
        const val QUICK_RECHECK_MS = 40L
        const val QUICK_FOR_MS = 800L
        // How long the lock screen must hold still under the panel before it comes back.
        const val SETTLE_MS = 300L
        // How long the panel stays down after a touch elsewhere. Long enough to outlast a swipe
        // to unlock: only the finger going down is reported, not it lifting, and a second was
        // measured to run out mid-swipe on a Kompakt, bringing the panel back just as the lock
        // screen went.
        const val TOUCH_HOLD_MS = 3_000L
    }
}
