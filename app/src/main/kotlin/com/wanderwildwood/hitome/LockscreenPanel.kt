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
import android.provider.Settings
import android.view.Gravity
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The panel on the lock screen: today's events, the weather, and what is unread, each from the
 * app it belongs to (see [Glance]).
 *
 * It is an accessibility overlay, the one kind of window the system draws above the lock
 * screen, and only an accessibility service may add one. The way it is added, and when it is
 * taken down, follows Katapult by gezimos (GPL-3.0), whose lock-screen widgets work this way.
 *
 * It shows only while the phone is locked with the screen on, the PIN pad is not up, no call is
 * ringing or live, and Katapult is not drawing lock-screen widgets of its own. It sits under the
 * lowest thing the lock screen itself shows in its upper half - the date, or the charging line,
 * or Mudita's own music player - measured each time rather than assumed, and above the strip a
 * music app may draw near the foot of the screen.
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
    private var scope: CoroutineScope? = null
    private var reading: Job? = null
    private var overlay: ComposeView? = null
    private var overlayTop = -1

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Read afresh on every wake, so the panel is never older than the last look.
            if (intent.action == Intent.ACTION_SCREEN_ON) readAll()
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
        (Glance.STACKED + Glance.COUNTS).forEach { pkg ->
            try {
                service.contentResolver.registerContentObserver(Glance.uri(pkg), true, changes)
            } catch (_: Exception) {}
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
                (Glance.STACKED + Glance.COUNTS).mapNotNull { Glance.read(service, it) }
            }
            evaluate()
        }
    }

    fun evaluate() {
        val keyguard = service.getSystemService(KeyguardManager::class.java)
        val power = service.getSystemService(PowerManager::class.java)
        val show = keyguard.isKeyguardLocked &&
            power.isInteractive &&
            sections.value.isNotEmpty() &&
            !katapultDrawing() &&
            !inCall() &&
            !appOverLockScreen() &&
            !pinShowing()
        if (show) place() else remove()
        handler.removeCallbacks(recheck)
        if (overlay != null) handler.postDelayed(recheck, RECHECK_MS)
    }

    /** Adds the panel, or moves it when what the lock screen shows above it has changed. */
    private fun place() {
        val density = service.resources.displayMetrics.density
        val top = lockScreenBottom()?.let { it + (GAP_DP * density).toInt() }
            ?: (FALLBACK_TOP_DP * density).toInt()
        if (overlay != null && top == overlayTop) return
        remove()
        val screen = service.resources.displayMetrics.heightPixels
        val floor = screen - ((MUSIC_STRIP_TOP_FROM_BOTTOM_DP + GAP_DP) * density).toInt()
        val room = ((floor - top) / density).toInt().coerceAtLeast(0)
        val view = ComposeView(service).apply {
            setViewTreeLifecycleOwner(this@LockscreenPanel)
            setViewTreeViewModelStoreOwner(this@LockscreenPanel)
            setViewTreeSavedStateRegistryOwner(this@LockscreenPanel)
            setContent {
                GlancePanel(
                    sections = sections.value,
                    roomDp = room,
                    onOpen = { pkg -> open(pkg) },
                )
            }
        }
        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            width = (WIDTH_DP * density).toInt()
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = top
        }
        try {
            service.getSystemService(WindowManager::class.java).addView(view, params)
            overlay = view
            overlayTop = top
        } catch (_: Exception) {
            overlay = null
        }
    }

    private fun remove() {
        overlay?.let {
            try { service.getSystemService(WindowManager::class.java).removeView(it) } catch (_: Exception) {}
        }
        overlay = null
        overlayTop = -1
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

    /** Katapult draws lock-screen widgets of its own; two panels would stack. */
    private fun katapultDrawing(): Boolean {
        val enabled = Settings.Secure.getString(
            service.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { it.startsWith("$KATAPULT/") }
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
        const val WIDTH_DP = 240f
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
        const val RECHECK_MS = 250L
    }
}
