@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val activeSharedTooltipState = mutableStateOf<TooltipState?>(null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedTooltipIconButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    val tooltipState = rememberTooltipState(isPersistent = true)

    LaunchedEffect(tooltipState.isVisible) {
        if (tooltipState.isVisible) {
            val previous = activeSharedTooltipState.value
            if (previous != null && previous !== tooltipState) {
                previous.dismiss()
            }
            activeSharedTooltipState.value = tooltipState
        } else if (activeSharedTooltipState.value === tooltipState) {
            activeSharedTooltipState.value = null
        }
    }

    TooltipBox(
        positionProvider = if (description != null) {
            TooltipDefaults.rememberRichTooltipPositionProvider()
        } else {
            TooltipDefaults.rememberPlainTooltipPositionProvider()
        },
        tooltip = {
            if (description != null) {
                RichTooltip(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            content()
                            Text(
                                text = text,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    },
                    colors = TooltipDefaults.richTooltipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(text = description, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                PlainTooltip {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        content()
                        Text(text)
                    }
                }
            }
        },
        state = tooltipState,
    ) {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            content()
        }
    }
}
