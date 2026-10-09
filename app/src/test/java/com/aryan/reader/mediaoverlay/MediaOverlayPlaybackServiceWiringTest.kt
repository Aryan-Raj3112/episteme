package com.aryan.reader.mediaoverlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the parts of the narration service that only the platform can exercise: its notification
 * identity and its media-button participation. Both are cross-file invariants against the other
 * playback services, so a plain source assertion is the cheapest thing that actually fails when one
 * of the three drifts.
 */
class MediaOverlayPlaybackServiceWiringTest {

    @Test
    fun `each playback service posts under its own notification id`() {
        val ids = listOf(
            "TTS" to notificationId(
                "com/aryan/reader/tts/TtsService.kt",
                "TTS_FOREGROUND_NOTIFICATION_ID"
            ),
            "audiobook" to notificationId(
                "com/aryan/reader/audiobook/AudiobookPlayback.kt",
                "AUDIOBOOK_NOTIFICATION_ID"
            ),
            "media overlay" to notificationId(
                "com/aryan/reader/mediaoverlay/MediaOverlayPlaybackService.kt",
                "MEDIA_OVERLAY_NOTIFICATION_ID"
            ),
        )

        // Two live sessions sharing an id means the later post silently replaces the earlier
        // notification, so the user loses a control they were just using.
        assertEquals(
            "playback services share a notification id: $ids",
            ids.size,
            ids.map { it.second }.toSet().size
        )
        // 1001 is Media3's own default id, which an unconfigured provider would reuse by accident.
        assertNotEquals(1001, ids.first { it.first == "media overlay" }.second)
    }

    @Test
    fun `narration service pins its notification and stays the media button target`() {
        val source = sourceFile("com/aryan/reader/mediaoverlay/MediaOverlayPlaybackService.kt").readText()

        assertTrue(source.contains("setMediaNotificationProvider(MediaOverlayNotificationProvider(this))"))
        assertTrue(source.contains("pinPostedPlaybackNotification("))
        assertTrue(source.contains("notificationId = MEDIA_OVERLAY_NOTIFICATION_ID"))
        assertTrue(
            source.contains(
                "MediaButtonRouting.recordPlaybackService(this, MediaOverlayPlaybackService::class.java)"
            )
        )
        // The player listener that records routing has to be detached before release.
        assertTrue(source.contains("player?.removeListener(this)"))
    }

    private fun notificationId(relativePath: String, constantName: String): Int {
        val match = Regex("const val $constantName = (\\d+)")
            .find(sourceFile(relativePath).readText())
            ?: error("Unable to find $constantName in $relativePath")
        return match.groupValues[1].toInt()
    }

    private fun sourceFile(relativePath: String): File {
        val candidates = listOf(
            File("src/main/java/$relativePath"),
            File("app/src/main/java/$relativePath")
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("Unable to locate $relativePath from ${File(".").absolutePath}")
    }
}
