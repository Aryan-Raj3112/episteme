package com.aryan.reader

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ui.SharedSearchField
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the Material 3 search field the library bar, the settings hub and iOS search share.
 *
 * The field is `SearchBarDefaults.InputField` rendered in its collapsed form, so it is worth
 * locking down that it still behaves like a text field: this is the part a component swap can
 * silently break (an input field that only reports clicks as an "expand" gesture would pass every
 * existing test that only asserts the placeholder).
 */
@RunWith(AndroidJUnit4::class)
class SharedSearchFieldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val query = mutableStateOf("")
    private val submitted = mutableListOf<String>()

    private fun setField() {
        composeTestRule.setContent {
            MaterialTheme {
                SharedSearchField(
                    query = query.value,
                    onQueryChange = { query.value = it },
                    placeholder = "Search books",
                    modifier = Modifier.fillMaxWidth().testTag("search"),
                    onSearch = { submitted += it },
                    trailingIcon = {
                        if (query.value.isNotEmpty()) {
                            IconButton(onClick = { query.value = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                )
            }
        }
    }

    @Test
    fun typingReportsEachEditToTheHost() {
        setField()

        composeTestRule.onNodeWithTag("search").performTextInput("dune")

        composeTestRule.runOnIdle {
            assertThat(query.value).isEqualTo("dune")
        }
    }

    @Test
    fun trailingClearIconEmptiesTheQuery() {
        query.value = "dune"
        setField()

        composeTestRule.onNodeWithContentDescription("Clear search").performClick()

        composeTestRule.runOnIdle {
            assertThat(query.value).isEmpty()
        }
    }

    @Test
    fun imeSearchSubmitsTheCurrentQuery() {
        query.value = "dune"
        setField()

        composeTestRule.onNodeWithTag("search").performImeAction()

        composeTestRule.runOnIdle {
            assertThat(submitted).containsExactly("dune")
        }
    }
}
