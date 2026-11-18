package com.wanderwildwood.hitome

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Handler
import android.os.Build
import android.os.Looper
import com.wanderwildwood.hitome.ui.DarkMode
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Home's dark switch, for the lock-screen panel.
 *
 * [DarkMode] asks Home what its switch says only while one of the app's screens is in front.
 * The panel is a window of the accessibility service, with no screen behind it, so after a
 * press on Home's switch it kept the old look until Glance's own settings were next opened.
 * Here the service asks Home itself: whenever the screen comes on, as the panel comes up,
 * and whenever Home says its switch was pressed, for as long as the service runs.
 *
 * The answer is taken as [DarkMode] takes its own: stored under its names, then followed
 * unless the app's own switch holds. DarkMode is the same file in every app and keeps its
 * asking to itself, so the stored names are repeated here and must stay as they are there,
 * and so is its test of Home: only Home's release (by name and certificate) is heard, and with
 * no such Home, Home's last word is forgotten, as DarkMode does.
 */
object HomeDark {

    // DarkMode's own stored state (the same file, the same names).
    private const val PREFS = "dark_mode"
    private const val KEY_STAMP = "home_stamp"
    private const val KEY_HOME = "home_dark"
    private const val KEY_OWN = "own"

    private val main = Handler(Looper.getMainLooper())
    private val asker = Executors.newSingleThreadExecutor()
    private var app: Context? = null

    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            app?.let { ask(it) }
        }
    }

    /** Listens for presses on Home's switch while [on]; asks once at the start. */
    fun watch(context: Context, on: Boolean) {
        val resolver = context.applicationContext.contentResolver
        if (on) {
            app = context.applicationContext
            runCatching { resolver.registerContentObserver(DarkMode.HOME, false, observer) }
            ask(context)
        } else {
            runCatching { resolver.unregisterContentObserver(observer) }
            app = null
        }
    }

    /** Asks Home what its switch says, and turns the app's windows if that changed anything. */
    fun ask(context: Context) {
        val ctx = context.applicationContext
        asker.execute {
            runCatching {
                val cursor = if (isHome(ctx)) ctx.contentResolver.query(DarkMode.HOME, null, null, null, null) else null
                if (cursor == null) {
                    val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    if (p.getBoolean(KEY_HOME, false)) {
                        p.edit().putBoolean(KEY_HOME, false).commit()
                        main.post { follow(ctx) }
                    }
                } else cursor.use { c ->
                    if (!c.moveToFirst()) return@use
                    val dark = c.getInt(c.getColumnIndexOrThrow("dark")) != 0
                    val stamp = c.getLong(c.getColumnIndexOrThrow("stamp"))
                    val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    if (stamp == p.getLong(KEY_STAMP, 0L) && dark == p.getBoolean(KEY_HOME, false)) return@use
                    p.edit().putLong(KEY_STAMP, stamp).putBoolean(KEY_HOME, dark).commit()
                    main.post { follow(ctx) }
                }
            }
        }
    }

    /** DarkMode's test of Home, repeated: Home's name, signed with Home's release certificate only. */
    private fun isHome(context: Context): Boolean {
        val pm = context.packageManager
        val owner = DarkMode.HOME.authority?.let { pm.resolveContentProvider(it, 0) }?.packageName ?: return false
        if (owner != DarkMode.HOME_PACKAGE) return false
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val certs = if (debuggable) DarkMode.DEBUG_CERTS_SHA256 + DarkMode.HOME_CERT_SHA256 else setOf(DarkMode.HOME_CERT_SHA256)
        val signers = signers(pm, owner)
        return signers.isNotEmpty() && signers.all { it in certs }
    }

    @Suppress("DEPRECATION")
    private fun signers(pm: PackageManager, pkg: String): List<String> = runCatching {
        val raw = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        } else {
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
        }
        raw.orEmpty().map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }.getOrDefault(emptyList())

    /**
     * What DarkMode does once it has heard from Home: unless the app's own switch holds, it
     * takes what the phone and Home ask for. Handed to [DarkMode.set] as exactly that, its own
     * switch is left not holding, as it was.
     */
    private fun follow(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getBoolean(KEY_OWN, false)) return
        val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val wanted = systemDark || p.getBoolean(KEY_HOME, false)
        if (wanted != DarkMode.on) DarkMode.set(context, wanted)
    }
}
