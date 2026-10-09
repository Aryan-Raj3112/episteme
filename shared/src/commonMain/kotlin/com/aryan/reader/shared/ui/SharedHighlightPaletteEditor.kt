package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.Dp

/**
 * Editable HSV body of the colour picker: optional [preview], the rectangular spectrum, the
 * brightness slider, the old/new compare pill, and the hex + R/G/B inputs.
 *
 * This is the one implementation of that machinery. [SharedHsvColorPickerDialog] wraps it in a
 * standalone dialog for picking a single colour; [SharedHighlightPaletteSlotDialog] wraps it for
 * editing a multi-slot palette; the Android hosts previously carried two more inline copies of the
 * same body (`EpubReaderAnnotations.PaletteManagerDialog` and
 * `AndroidReaderThemeEditor.HighlightColorPickerDialog`) and now host these two instead.
 *
 * Layout and colours are Android parity, unchanged: 220dp spectrum, 24dp brightness slider, and a
 * 64x36dp compare pill beside the hex and RGB columns.
 */
@Composable
internal fun SharedHsvColorPickerEditor(
    hsv: SharedHsvColor,
    onHsvChange: (SharedHsvColor) -> Unit,
    oldColor: Color,
    modifier: Modifier = Modifier,
    stateKey: Any? = null,
    preview: @Composable (Color) -> Unit = {}
) {
    val color = hsv.toComposeColor()

    preview(color)

    Spacer(Modifier.height(20.dp))

    SharedSpectrumBox(
        hue = hsv.hue,
        saturation = hsv.saturation,
        currentColor = color,
        onHueSatChanged = { hue, saturation ->
            onHsvChange(hsv.copy(hue = hue, saturation = saturation))
        },
        modifier = Modifier.fillMaxWidth().height(220.dp),
        gestureKey = stateKey
    )

    Spacer(Modifier.height(20.dp))

    SharedBrightnessSlider(
        hue = hsv.hue,
        saturation = hsv.saturation,
        value = hsv.value,
        onValueChanged = { onHsvChange(hsv.copy(value = it)) },
        modifier = Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(12.dp)),
        gestureKey = stateKey
    )

    Spacer(Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SharedColorComparePill(
            oldColor = oldColor,
            newColor = color,
            modifier = Modifier.width(64.dp).height(36.dp)
        )

        Column(
            modifier = Modifier.weight(1.6f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                readerString("theme_color_hex", "Hex"),
                color = Color.Gray,
                fontSize = 12.sp,
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            SharedHexInput(color = color, onHexChanged = { onHsvChange(it.toSharedHsvColor()) })
        }

        Row(
            modifier = Modifier.weight(2.4f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SharedRgbInputColumn(
                label = readerString("color_r", "R"),
                value = color.red,
                onValueChange = { onHsvChange(color.copy(red = it).toSharedHsvColor()) },
                modifier = Modifier.weight(1f)
            )
            SharedRgbInputColumn(
                label = readerString("color_g", "G"),
                value = color.green,
                onValueChange = { onHsvChange(color.copy(green = it).toSharedHsvColor()) },
                modifier = Modifier.weight(1f)
            )
            SharedRgbInputColumn(
                label = readerString("color_b", "B"),
                value = color.blue,
                onValueChange = { onHsvChange(color.copy(blue = it).toSharedHsvColor()) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Full-screen palette editor: every slot stays visible while one of them is edited, and the picker
 * for that slot sits right underneath the slot row.
 *
 * Android parity (`EpubReaderAnnotations.PaletteManagerDialog` and
 * `AndroidReaderThemeEditor.HighlightColorPickerDialog`): dark `0xFF2C2C2C` surface, rounded 24dp,
 * pill title, a 48dp circle per slot with a check on the selected one, then the HSV editor, then
 * Reset (restores [defaultSlots] for the selected slot) / Cancel / Save.
 *
 * The caller owns nothing but the save: edits live in a local draft and are only published through
 * [onSave], so Cancel really cancels. Slot-agnostic ([slots] is just colours in order) so the EPUB
 * palette, the PDF palette and any future palette share it.
 *
 * [maxDialogHeight] is a parameter rather than derived from the screen because Compose's common API
 * set has no `LocalConfiguration`. Android hosts pass
 * `readerModalMaxHeightDp(screenHeightDp).dp` — the cap this dialog used before it moved here — so
 * their sizing is unchanged; the default matches the other shared modal.
 */
@Composable
fun SharedHighlightPaletteSlotDialog(
    title: String,
    slots: List<Color>,
    defaultSlots: List<Color> = slots,
    initialSelection: Int = 0,
    onSave: (List<Color>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxDialogHeight: Dp = 600.dp
) {
    // `original` is captured once: it is what the compare pill and Reset measure against, so
    // revisiting a slot does not lose the colour the palette started with.
    val original = remember(slots) { slots }
    var draft by remember(original) { mutableStateOf(original) }
    var selectedIndex by remember {
        mutableStateOf(initialSelection.coerceIn(0, (original.size - 1).coerceAtLeast(0)))
    }
    var hsv by remember(selectedIndex) {
        mutableStateOf(
            draft.getOrNull(selectedIndex).orPaletteDefaultColor(defaultSlots).toSharedHsvColor()
        )
    }

    val currentColor = hsv.toComposeColor()

    // Live-write the edited colour back into the draft, mirroring Android's
    // LaunchedEffect(currentColor) so the slot circle tracks the picker as it moves.
    LaunchedEffect(currentColor, selectedIndex) {
        draft = draft.toMutableList().also {
            if (selectedIndex in it.indices) it[selectedIndex] = currentColor
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF2C2C2C),
            modifier = modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp)
                .heightIn(max = maxDialogHeight)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF3E3E3E), RoundedCornerShape(16.dp))
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(20.dp))

                SharedHsvColorPickerEditor(
                    hsv = hsv,
                    onHsvChange = { hsv = it },
                    oldColor = original.getOrNull(selectedIndex).orPaletteDefaultColor(defaultSlots),
                    stateKey = selectedIndex
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        draft.forEachIndexed { index, slotColor ->
                            val isSelected = selectedIndex == index
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(slotColor)
                                    .clickable { selectedIndex = index }
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) {
                                            Color.White
                                        } else {
                                            Color.Gray
                                        },
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = readerString(
                                            "content_desc_selected",
                                            "Selected"
                                        ),
                                        tint = if (slotColor.luminance() > 0.5f) {
                                            Color.Black
                                        } else {
                                            Color.White
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            hsv = draft.getOrNull(selectedIndex)
                                .orPaletteDefaultColor(defaultSlots)
                                .toSharedHsvColor()
                        }
                    ) {
                        Text(
                            readerString("action_reset", "Reset"),
                            color = Color(0xFFFF5252)
                        )
                    }
                    Row {
                        TextButton(onClick = onDismiss) {
                            Text(
                                readerString("action_cancel", "Cancel"),
                                color = Color.Gray
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                // Read the picker's own colour rather than trusting the draft:
                                // the live-write effect above is scheduled, not synchronous, so a
                                // Save tapped in the same frame as the last drag would otherwise
                                // persist one step behind.
                                onSave(
                                    draft.toMutableList().also {
                                        if (selectedIndex in it.indices) it[selectedIndex] = currentColor
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            )
                        ) {
                            Text(
                                readerString("action_save", "Save"),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reset target for [selectedIndex]: its stock colour, falling back to the palette's first stock
 * colour so a short [defaultSlots] list still has somewhere to reset to.
 */
private fun Color?.orPaletteDefaultColor(defaultSlots: List<Color>): Color =
    this ?: defaultSlots.firstOrNull() ?: Color.White