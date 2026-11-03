package com.wanderwildwood.hitome

/**
 * How often Glance asks the other apps for their lines.
 *
 * Asking an app for its lines starts it if Android had put it away, and keeps it in memory for a
 * while after. Asking every app on every wake kept them all resident and the phone swapped. So a
 * wake reads them again only when the last read is [SOURCES_MS] old, or the apps shown have
 * changed. An app that has new lines says so through a content observer and is read at once
 * regardless.
 */
object ReadPacing {

    const val SOURCES_MS = 15 * 60_000L

    /**
     * Whether a wake at [now] should read the apps again, given the last read at [lastRead]
     * (0 for never) of the sources [lastSources], and the sources now chosen, [sources].
     */
    fun wakeReads(now: Long, lastRead: Long, lastSources: List<String>?, sources: List<String>): Boolean =
        lastRead == 0L || lastSources != sources || now - lastRead >= SOURCES_MS
}
