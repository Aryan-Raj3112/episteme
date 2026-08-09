package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.SharedAudiobook
import com.aryan.reader.shared.SharedAudiobookPlaybackState
import com.aryan.reader.shared.SharedBookTtsListenState
import com.aryan.reader.shared.ui.SharedMobileAudiobooksSection
import com.aryan.reader.shared.ui.SharedMobileAudiobookMiniPlayerFrame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedMobileAudiobooksSectionTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun activeContinueItemIsNotDuplicatedInRegularList() {
        val audiobook = SharedAudiobook(
            bookId = "active",
            filePath = "/books/active.m4b",
            format = "M4B",
            title = "Active book",
            durationMs = 60_000,
            positionMs = 10_000,
            addedAt = 1,
            lastListenedAt = 2,
        )
        composeTestRule.setContent {
            MaterialTheme {
                SharedMobileAudiobooksSection(
                    audiobooks = listOf(audiobook),
                    playback = SharedAudiobookPlaybackState(bookId = "active"),
                    ttsItems = emptyList(),
                    ttsPlayback = SharedBookTtsListenState(),
                    onOpenPlayer = {},
                    onOpenTtsPlayer = { _, _ -> },
                    onAddAudiobook = {},
                )
            }
        }

        composeTestRule.onAllNodesWithTag("AudiobookContinue").assertCountEquals(1)
        composeTestRule.onAllNodesWithTag("ListenLibraryRow-active").assertCountEquals(0)
    }

    @Test
    fun sharedMiniPlayerFrameExpandsFromTheAndroidBenchmarkSurface() {
        var expanded = false
        composeTestRule.setContent {
            MaterialTheme {
                SharedMobileAudiobookMiniPlayerFrame(
                    title = "Book",
                    subtitle = "Chapter 2",
                    progress = 0.4f,
                    isPlaying = false,
                    onTogglePlayback = {},
                    onExpand = { expanded = true },
                    onStopPlayback = {},
                    cover = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("AudiobookMiniPlayer").performClick()
        assertTrue(expanded)
    }
}
