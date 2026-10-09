package com.aryan.reader.shared

/**
 * When a foreground / resume event is allowed to trigger a local-folder scan.
 *
 * Both platforms reach this from a lifecycle callback, and a lifecycle callback
 * fires far more often than a folder's contents change: every app switch,
 * every Control Centre pull, every notification dismissal. Scanning on each of
 * those turns a cheap directory walk into the app's dominant foreground cost.
 *
 * An explicit user-initiated refresh is never gated. The distinction that
 * matters is "did the user ask", not "how long has it been".
 */
object LocalFolderRescanPolicy {
    /**
     * Minimum gap between automatic (lifecycle-triggered) scans of one folder.
     *
     * Long enough that backgrounding and returning a few times in a row does
     * not re-walk the tree each time, short enough that a folder edited outside
     * the app is picked up within a normal session.
     */
    const val AUTOMATIC_RESCAN_COOLDOWN_MILLIS: Long = 5 * 60 * 1000L

    /**
     * A folder that has never been scanned has no watermark, so a null
     * [lastScanTimeMillis] always allows the scan. Only a folder that has
     * already been seen in this install is subject to the cooldown.
     *
     * [forced] is set by an explicit user action and bypasses the cooldown.
     */
    fun shouldScanOnForeground(
        lastScanTimeMillis: Long?,
        nowMillis: Long,
        forced: Boolean = false,
    ): Boolean {
        if (forced) return true
        val last = lastScanTimeMillis ?: return true
        if (last <= 0L) return true
        // A watermark in the future means the clock moved backwards; treat the
        // folder as unscanned rather than blocking refreshes indefinitely.
        if (last > nowMillis) return true
        return nowMillis - last >= AUTOMATIC_RESCAN_COOLDOWN_MILLIS
    }

    /**
     * Milliseconds remaining before an automatic scan is allowed, or 0 when one
     * is allowed now. Used for diagnostics so a skipped refresh is visible
     * rather than silent.
     */
    fun remainingCooldownMillis(
        lastScanTimeMillis: Long?,
        nowMillis: Long,
    ): Long {
        val last = lastScanTimeMillis ?: return 0L
        if (last <= 0L || last > nowMillis) return 0L
        val remaining = AUTOMATIC_RESCAN_COOLDOWN_MILLIS - (nowMillis - last)
        return remaining.coerceAtLeast(0L)
    }
}
