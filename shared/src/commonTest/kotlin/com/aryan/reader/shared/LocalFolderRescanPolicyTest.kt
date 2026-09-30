package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalFolderRescanPolicyTest {
    private val now = 1_700_000_000_000L

    @Test
    fun `a never-scanned folder is always scanned`() {
        assertTrue(LocalFolderRescanPolicy.shouldScanOnForeground(null, now))
        assertTrue(LocalFolderRescanPolicy.shouldScanOnForeground(0L, now))
    }

    @Test
    fun `a folder scanned moments ago is skipped`() {
        val justScanned = now - 1_000L

        assertFalse(LocalFolderRescanPolicy.shouldScanOnForeground(justScanned, now))
    }

    @Test
    fun `a folder scanned longer ago than the cooldown is scanned again`() {
        val stale = now - LocalFolderRescanPolicy.AUTOMATIC_RESCAN_COOLDOWN_MILLIS - 1L

        assertTrue(LocalFolderRescanPolicy.shouldScanOnForeground(stale, now))
    }

    @Test
    fun `the cooldown boundary is inclusive`() {
        val exact = now - LocalFolderRescanPolicy.AUTOMATIC_RESCAN_COOLDOWN_MILLIS

        assertTrue(LocalFolderRescanPolicy.shouldScanOnForeground(exact, now))
    }

    @Test
    fun `an explicit user refresh always scans`() {
        val justScanned = now - 1L

        assertTrue(
            LocalFolderRescanPolicy.shouldScanOnForeground(justScanned, now, forced = true)
        )
        assertTrue(
            LocalFolderRescanPolicy.shouldScanOnForeground(null, now, forced = true)
        )
    }

    @Test
    fun `a watermark in the future does not block refreshes forever`() {
        // The clock moved backwards, or the device was restored from a backup
        // taken in the future. Treating the folder as never-scanned is the only
        // reading that cannot leave it permanently unrefreshed.
        val future = now + 60_000L

        assertTrue(LocalFolderRescanPolicy.shouldScanOnForeground(future, now))
        assertEquals(0L, LocalFolderRescanPolicy.remainingCooldownMillis(future, now))
    }

    @Test
    fun `remaining cooldown counts down and never goes negative`() {
        val justScanned = now - 10_000L
        val cooldown = LocalFolderRescanPolicy.AUTOMATIC_RESCAN_COOLDOWN_MILLIS

        assertEquals(
            cooldown - 10_000L,
            LocalFolderRescanPolicy.remainingCooldownMillis(justScanned, now),
        )
        val longAgo = now - cooldown * 2
        assertEquals(0L, LocalFolderRescanPolicy.remainingCooldownMillis(longAgo, now))
    }

    @Test
    fun `the cooldown absorbs repeated app switching within one sitting`() {
        // The whole point: scenePhase fires on every app switch. Model a user
        // switching away and back every 30s for ten minutes, with the watermark
        // updated to whenever a scan actually happened. Ungated this would be
        // 20 scans; the cooldown reduces it to one per window.
        var watermark: Long? = null
        var scans = 0
        val ticks = 20
        repeat(ticks) { tick ->
            val at = now + tick * 30_000L
            if (LocalFolderRescanPolicy.shouldScanOnForeground(watermark, at)) {
                scans++
                watermark = at
            }
        }

        val elapsedMillis = ticks * 30_000L
        val cooldown = LocalFolderRescanPolicy.AUTOMATIC_RESCAN_COOLDOWN_MILLIS
        // One scan up front, then at most one per elapsed cooldown window. The
        // point is the reduction, not an exact count: 20 transitions must not
        // become 20 directory walks.
        val upperBound = 1L + elapsedMillis / cooldown
        assertTrue(
            scans.toLong() <= upperBound,
            "$scans scans over ${elapsedMillis}ms exceeds the cooldown bound of $upperBound",
        )
        assertTrue(
            scans.toLong() <= ticks / 4,
            "$ticks foreground transitions produced $scans scans; the cooldown is not doing its job",
        )
        assertTrue(scans >= 1, "the first foreground should always scan")
    }
}
