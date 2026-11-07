package com.aryan.reader.epubreader

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.R
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the Android adapter over the shared auto-scroll surface.
 *
 * The layout, the speed maths and the animation now live once in
 * `SharedMobileAutoScrollControls`; what is left in Android is the wiring — the
 * localized label table (the shared surface cannot resolve Android strings) and
 * the call-site callback names. That wiring is what this pins, because the two
 * Android readers (EPUB and PDF) drive this one surface and a swapped or
 * dropped callback would silently stop controlling auto-scroll.
 */
@RunWith(AndroidJUnit4::class)
class AutoScrollControlsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun text(@StringRes id: Int) = context.getString(id)

    private val collapsed = mutableStateOf(true)
    private val playing = mutableStateOf(false)
    private val speed = mutableFloatStateOf(2f)
    private val localMode = mutableStateOf(false)

    private val playPauseToggles = mutableListOf<Boolean>()
    private val reportedSpeeds = mutableListOf<Float>()
    private val localModeChoices = mutableListOf<Boolean>()
    private val collapseChanges = mutableListOf<Boolean>()
    private var closeClicks = 0
    private var musicianModeClicks = 0
    private var inputModeClicks = 0
    private var scrollToTopClicks = 0

    @Test
    fun collapsedSurfaceExpandsThroughTheHostCallback() {
        showControls()

        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_expand)).performClick()

        composeTestRule.runOnIdle {
            assertThat(collapseChanges).containsExactly(false)
        }
    }

    @Test
    fun expandedSurfaceRoutesEveryToolThroughTheHostCallbacks() {
        collapsed.value = false
        showControls(withScrollToTop = true)

        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_start_playback))
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_enable_musician_mode))
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_swap_controls))
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(text(R.string.action_scroll_to_top))
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(text(R.string.action_close))
            .assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_start_playback)).performClick()
        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_enable_musician_mode)).performClick()
        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_swap_controls)).performClick()
        composeTestRule.onNodeWithContentDescription(text(R.string.action_scroll_to_top)).performClick()
        composeTestRule.onNodeWithContentDescription(text(R.string.action_close)).performClick()
        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_collapse)).performClick()

        composeTestRule.runOnIdle {
            assertThat(playPauseToggles).containsExactly(true)
            assertThat(musicianModeClicks).isEqualTo(1)
            assertThat(inputModeClicks).isEqualTo(1)
            assertThat(scrollToTopClicks).isEqualTo(1)
            assertThat(closeClicks).isEqualTo(1)
            assertThat(collapseChanges).containsExactly(true)
        }
    }

    @Test
    fun speedReadoutAndStepperFollowTheAndroidOneDecimalFormat() {
        collapsed.value = false
        showControls()

        composeTestRule.onNodeWithText("2.0x").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_faster)).performClick()
        composeTestRule.runOnIdle { speed.floatValue = 2.1f }
        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_faster)).performClick()

        composeTestRule.runOnIdle {
            // 2.1f + 0.1f is 2.1999998 in binary; the shared boundary snaps it,
            // which is what keeps the stored value and the readout agreeing.
            assertThat(reportedSpeeds).containsExactly(2.1f, 2.2f)
        }
    }

    @Test
    fun modeMenuReportsTheLocalSpeedChoiceThroughTheHostCallback() {
        collapsed.value = false
        showControls()

        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_select_mode)).performClick()
        composeTestRule.onNodeWithText(text(R.string.auto_scroll_local_speed)).performClick()

        composeTestRule.runOnIdle {
            assertThat(localModeChoices).containsExactly(true)
        }
    }

    private fun showControls(withScrollToTop: Boolean = false) {
        composeTestRule.setContent {
            MaterialTheme {
                AutoScrollControls(
                    isPlaying = playing.value,
                    onPlayPauseToggle = { playPauseToggles += !playing.value },
                    speed = speed.floatValue,
                    minSpeed = 0.5f,
                    maxSpeed = 5f,
                    onSpeedChange = { reportedSpeeds += it },
                    onMinSpeedChange = {},
                    onMaxSpeedChange = {},
                    onClose = { closeClicks += 1 },
                    isCollapsed = collapsed.value,
                    onCollapseChange = { collapseChanges += it },
                    isMusicianMode = false,
                    onMusicianModeToggle = { musicianModeClicks += 1 },
                    useSlider = false,
                    onInputModeToggle = { inputModeClicks += 1 },
                    isLocalMode = localMode.value,
                    onLocalModeToggle = { localModeChoices += it },
                    onScrollToTop = ({ scrollToTopClicks += 1 }).takeIf { withScrollToTop },
                )
            }
        }
    }
}
