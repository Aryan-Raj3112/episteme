package com.aryan.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ui.SharedExposedDropdownMenuField
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The dropdown's tap layer has to sit on top of the readOnly field without collapsing: these
 * fields live in vertically scrolling containers, where a `fillMaxHeight` overlay resolves to
 * zero height and the field swallows the press, so the menu never opened at all.
 */
@RunWith(AndroidJUnit4::class)
class SharedExposedDropdownMenuFieldTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun fieldOpensTheMenuInsideAVerticallyScrollingColumn() {
        var expanded by mutableStateOf(false)
        var selected by mutableStateOf("none")
        composeTestRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Column {
                        SharedExposedDropdownMenuField(
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                            modifier = Modifier.fillMaxWidth(),
                            field = {
                                OutlinedTextField(
                                    value = selected,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Model") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Option one") },
                                onClick = { selected = "Option one"; expanded = false },
                            )
                        }
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Model").performClick()
        composeTestRule.onNodeWithText("Option one").assertIsDisplayed().performClick()
        composeTestRule.runOnIdle {
            assertThat(selected).isEqualTo("Option one")
            assertThat(expanded).isFalse()
        }
    }
}
