package com.wanderwildwood.hitome

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.CancellationSignal
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * The panel's lines for a home screen: inkOS:wndr (com.wanderwildwood.iori), which draws them
 * under its apps. `content://com.wanderwildwood.hitome.panel/lines`, read-only.
 *
 * It hands over what the lock screen would show from the apps that take part - the same
 * sources, in the order chosen here, with the same switches here - and, for each source, why it
 * shows or does not. Other apps' notifications are never part of it.
 *
 * Columns, one row per line: `pkg` (the source, from [Glance.SOURCES]), `heading` (on a
 * source's first row, optional), `lead` (optional), `text`, `bold` (1 or 0), and `status`. A
 * source with no lines has one row of its own, with no text, to carry its status:
 *
 * - [ON]: its lines follow. Today's events read by Glance itself, on a phone without Calendar,
 *   are Calendar's, and may be on with nothing left today.
 * - [OFF_APP]: the app answered with nothing - its own lock-screen switch is off, or it has
 *   nothing to say just now. The apps answer both the same way.
 * - [OFF_GLANCE]: one that stays off until switched on in Glance too, and is not.
 * - [NOT_INSTALLED].
 * - [NEEDS_UPDATE]: installed, but too old to hand Glance its lines (no provider, or one that
 *   answers without a `text` column).
 * - [NO_ANSWER]: it refused, failed, took longer than [WAIT_MS], or is still busy with the last
 *   time it was asked.
 *
 * It answers only iori, known by its package name and the certificate its releases are signed
 * with ([IORI_CERT_SHA256]), so an app that took the same name, or a debug build, gets nothing.
 * A debug build of Glance also answers a debug build of iori ([IORI_DEBUG], [DEBUG_CERTS_SHA256]),
 * so the two debug builds can be tested together; a release of Glance never does.
 * The apps are read from here, as Glance, so their own providers keep answering Glance alone.
 *
 * A source app that hangs costs at most one thread here: it is not asked again until its last
 * answer has come back, and each ask goes through an unstable client with a [CancellationSignal]
 * that is cancelled at the deadline, so a source dying under it costs only that ask.
 */
class PanelForHome : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = query(uri, projection, null as Bundle?, null)

    /** The form a caller's read arrives in, with the caller's [CancellationSignal]. */
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        queryArgs: Bundle?,
        cancellationSignal: CancellationSignal?,
    ): Cursor? {
        val context = context ?: return null
        if (uri.path != "/lines") return null
        if (!allowed(context, Binder.getCallingUid())) return null
        cancellationSignal?.throwIfCanceled()
        val identity = Binder.clearCallingIdentity()
        return try {
            MatrixCursor(COLUMNS).apply { rows(context, cancellationSignal).forEach { addRow(it) } }
        } catch (_: Exception) {
            null
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    /** One source being asked: its answer to come, and the signal that calls the ask off. */
    private class Ask(val future: Future<Answer>?, val signal: CancellationSignal) {
        fun cancel() {
            signal.cancel()
            future?.cancel(true)
        }
    }

    /**
     * Every source in the order chosen here, each with its status, as [query]'s rows. When the
     * caller gives up, so does the wait: every ask still out is called off and answers
     * [NO_ANSWER].
     */
    private fun rows(context: Context, caller: CancellationSignal?): List<Array<Any?>> {
        val calendarInstalled = Glance.calendarInstalled(context)
        val sources = Glance.sources(Glance.order(context))
        // Each app is asked on its own thread, all at once, so one that hangs costs only itself.
        val asked = sources.associateWith { pkg -> ask(context, pkg, calendarInstalled) }
        caller?.setOnCancelListener { asked.values.forEach { it.cancel() } }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MS)
        return try {
            sources.flatMap { pkg -> rowsFor(pkg, waitFor(asked.getValue(pkg), deadline)) }
        } finally {
            caller?.setOnCancelListener(null)
        }
    }

    /**
     * Asks [pkg] on a thread of its own, unless it has not yet answered the last ask: a thread
     * stuck in a hung app's provider cannot be interrupted, so it is never joined by another.
     */
    private fun ask(context: Context, pkg: String, calendarInstalled: Boolean): Ask {
        val signal = CancellationSignal()
        if (busy.putIfAbsent(pkg, true) != null) return Ask(null, signal)
        val future = try {
            pool.submit<Answer> {
                try {
                    answer(context, pkg, calendarInstalled, signal)
                } finally {
                    busy.remove(pkg)
                }
            }
        } catch (_: Exception) {
            busy.remove(pkg)
            null
        }
        return Ask(future, signal)
    }

    private fun waitFor(ask: Ask, deadline: Long): Answer {
        val future = ask.future ?: return Answer(NO_ANSWER)
        return try {
            future.get((deadline - System.nanoTime()).coerceAtLeast(0), TimeUnit.NANOSECONDS)
        } catch (_: Exception) {
            ask.cancel()
            Answer(NO_ANSWER)
        }
    }

    /** What [pkg] has to say, as [Glance.readAll] would read it, and why. */
    private fun answer(context: Context, pkg: String, calendarInstalled: Boolean, signal: CancellationSignal): Answer {
        val from = Glance.providers(pkg)
        if (from.size == 1) return answerOne(context, pkg, calendarInstalled, signal)
        return chooseAnswer(from.map { answerOne(context, it, calendarInstalled, signal) })
    }

    private fun answerOne(context: Context, pkg: String, calendarInstalled: Boolean, signal: CancellationSignal): Answer {
        val pm = context.packageManager
        if (pkg == Glance.CALENDAR && !calendarInstalled) {
            // Glance's own reading of today takes Calendar's place, as on the lock screen.
            if (!Today.enabled(context)) return Answer(NOT_INSTALLED)
            return Answer(ON, Today.read(context))
        }
        val installed = try {
            pm.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        if (!installed) return Answer(NOT_INSTALLED)
        if (pkg in Glance.OPT_IN && !Glance.switchedOn(context, pkg)) return Answer(OFF_GLANCE)
        if (pm.resolveContentProvider("$pkg.glance", 0) == null) return Answer(NEEDS_UPDATE)
        val uri = Glance.uri(pkg)
        // Unstable, so a source that dies while it is being read costs this ask, not Glance.
        val client = try {
            context.contentResolver.acquireUnstableContentProviderClient(uri)
        } catch (_: Exception) {
            null
        } ?: return Answer(NO_ANSWER)
        return try {
            client.query(uri, Glance.COLUMNS, null, null, null, signal)?.use { c ->
                // An answer with no text column is from a provider older than the lines.
                if (c.getColumnIndex("text") < 0) return@use Answer(NEEDS_UPDATE)
                val section = Glance.section(c, pkg)
                if (section == null) Answer(OFF_APP) else Answer(ON, section)
            } ?: Answer(NO_ANSWER)
        } catch (_: Exception) {
            Answer(NO_ANSWER)
        } finally {
            client.close()
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    /** A source's [status], and its lines when it has any. */
    data class Answer(val status: String, val section: Glance.Section? = null)

    companion object {
        const val AUTHORITY = "com.wanderwildwood.hitome.panel"

        val URI: Uri by lazy { Uri.parse("content://$AUTHORITY/lines") }

        /** The one app answered. */
        const val IORI = "com.wanderwildwood.iori"

        /** iori's debug build, answered only by a debug build of Glance. */
        const val IORI_DEBUG = "com.wanderwildwood.iori.debug"

        /**
         * The SHA-256 of the certificate iori's releases are signed with, lower-case hex with no
         * colons (`keytool -list -v` on its release keystore). Debug builds are not answered.
         */
        const val IORI_CERT_SHA256 = "fb06881da0a2c7b0a98fc6c730098a106636974db877d63e18670b53b0890512"

        /**
         * The debug certificates iori's debug builds come out signed with: the build box's and
         * the desk machine's own debug keys. Looked at only when Glance itself is a debug build.
         */
        val DEBUG_CERTS_SHA256 = setOf(
            "1e7e9cc0ea37a725d0eaf622187792c425dc0351a3e228ef462f8eaacbb9db06",
            "9ae26e87553000817b4185b15d9047845e87871d8f5204dac77b82183fee248b",
        )

        val COLUMNS = arrayOf("pkg", "heading", "lead", "text", "bold", "status")

        const val ON = "on"
        const val OFF_APP = "off_app"
        const val OFF_GLANCE = "off_glance"
        const val NOT_INSTALLED = "not_installed"
        const val NEEDS_UPDATE = "needs_update"
        const val NO_ANSWER = "no_answer"

        /** How long all the apps together may take; home asks again on its next look. */
        const val WAIT_MS = 1000L

        /**
         * One thread per source at most: [busy] keeps a source from being asked again while it
         * has not answered, so the pool never needs more.
         */
        private val pool: ExecutorService = Executors.newFixedThreadPool(Glance.SOURCES.size.coerceIn(1, 12)) { r ->
            Thread(r, "panel-for-home").apply { isDaemon = true }
        }

        /** The sources asked and not yet answered. */
        private val busy = ConcurrentHashMap<String, Boolean>()

        /**
         * Tells a home screen reading the panel that what it would read has changed: the order,
         * a switch here, or today's events.
         */
        fun changed(context: Context) {
            try {
                context.contentResolver.notifyChange(URI, null)
            } catch (_: Exception) {
            }
        }

        /** [pkg]'s rows: one per line, or one with no text that carries only its status. */
        /** Of several apps' answers for one place: the one playing, else one with lines, else the most telling status. */
        fun chooseAnswer(answers: List<Answer>): Answer {
            val section = Glance.pickPlaying(answers.map { it.section })
            if (section != null) return answers.first { it.section === section }
            val order = listOf(OFF_APP, OFF_GLANCE, NEEDS_UPDATE, NO_ANSWER, NOT_INSTALLED)
            return answers.minByOrNull { order.indexOf(it.status).let { i -> if (i < 0) order.size else i } } ?: Answer(NOT_INSTALLED)
        }

        fun rowsFor(pkg: String, answer: Answer): List<Array<Any?>> {
            val section = answer.section
            if (section == null || section.lines.isEmpty()) return listOf(arrayOf(pkg, null, null, null, 0, answer.status))
            return section.lines.mapIndexed { i, line ->
                arrayOf(pkg, if (i == 0) section.heading else null, line.lead, line.text, if (line.bold) 1 else 0, answer.status)
            }
        }

        /**
         * Whether [uid] is iori, signed by its release certificate and nothing else; or, in a
         * debug build of Glance only, iori's debug build signed by one of [DEBUG_CERTS_SHA256].
         */
        private fun allowed(context: Context, uid: Int): Boolean {
            val pm = context.packageManager
            val packages = pm.getPackagesForUid(uid) ?: return false
            return packages.any { name ->
                val certs = when {
                    name == IORI -> setOf(IORI_CERT_SHA256)
                    BuildConfig.DEBUG && name == IORI_DEBUG -> DEBUG_CERTS_SHA256
                    else -> return@any false
                }
                val signers = runCatching {
                    pm.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
                }.getOrNull()
                !signers.isNullOrEmpty() && signers.all { sha256(it.toByteArray()) in certs }
            }
        }

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
