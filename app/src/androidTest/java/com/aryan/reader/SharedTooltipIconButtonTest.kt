package com.aryan.reader

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ui.SharedTooltipIconButton
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedTooltipIconButtonTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun enabledStatePreservesToolbarClickPolicy() {
        var enabledClicks = 0
        var disabledClicks = 0
        composeTestRule.setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Row {
                    SharedTooltipIconButton("Enabled", { enabledClicks++ }) {
                        Icon(Icons.Default.Info, contentDescription = "enabled tool")
                    }
                    SharedTooltipIconButton("Disabled", { disabledClicks++ }, enabled = false) {
                        Icon(Icons.Default.Info, contentDescription = "disabled tool")
                    }
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("enabled tool").performClick()
        composeTestRule.onNodeWithContentDescription("disabled tool").performClick()
        composeTestRule.runOnIdle {
            assertThat(enabledClicks).isEqualTo(1)
            assertThat(disabledClicks).isEqualTo(0)
        }
    }
}
