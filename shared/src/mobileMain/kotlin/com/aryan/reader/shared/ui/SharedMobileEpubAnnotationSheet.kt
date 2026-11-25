package com.aryan.reader.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.CUSTOM_HIGHLIGHT_COLOR_ID_PREFIX
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.generated.resources.Res
import com.aryan.reader.shared.generated.resources.copy
import com.aryan.reader.shared.generated.resources.dictionary
import com.aryan.reader.shared.generated.resources.font_background
import com.aryan.reader.shared.generated.resources.format_underlined
import com.aryan.reader.shared.generated.resources.format_underlined_squiggle
import com.aryan.reader.shared.generated.resources.search
import com.aryan.reader.shared.generated.resources.strikethrough
import com.aryan.reader.shared.generated.resources.translate
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Localised labels the annotation sheet renders.
 *
 * The sheet itself never calls `readerString` for these, because the Android reader composes shared
 * surfaces without a [LocalSharedStringResolver] — the only place Android provides one is the
 * settings screen — so a shared lookup would silently fall back to English in the reader while the
 * rest of the app is translated. Each host supplies its own table instead: Android passes
 * `stringResource(...)` values from its own resources, iOS relies on
 * [sharedMobileEpubAnnotationLabels] and the resolver its root provides.
 */
data class SharedMobileEpubAnnotationLabels(
    val copy: String,
    val speak: String,
    val dictionary: String,
    val translate: String,
    val search: String,
    val notePlaceholder: String,
    val delete: String,
    val saveNote: String,
)

/** Resolver-backed labels, for hosts that provide one (iOS, desktop). */
@Composable
fun sharedMobileEpubAnnotationLabels(): SharedMobileEpubAnnotationLabels {
    return SharedMobileEpubAnnotationLabels(
        copy = readerString("action_copy", "Copy"),
        speak = readerString("label_speak", "Speak"),
        dictionary = readerString("label_dict", "Dict"),
        translate = readerString("dict_translate", "Translate"),
        search = readerString("action_search", "Search"),
        notePlaceholder = readerString("placeholder_add_note", "Add a note…"),
        delete = readerString("action_delete", "Delete"),
        saveNote = readerString("action_save_note", "Save Note"),
    )
}

private fun highlightStyleIcon(style: HighlightStyle): DrawableResource {
    return when (style) {
        HighlightStyle.BACKGROUND -> Res.drawable.font_background
        HighlightStyle.UNDERLINE -> Res.drawable.format_underlined
        HighlightStyle.WAVY_UNDERLINE -> Res.drawable.format_underlined_squiggle
        HighlightStyle.STRIKETHROUGH -> Res.drawable.strikethrough
    }
}

/**
 * Highlight-style picker row.
 *
 * Android benchmark (`epubreader/EpubReaderAnnotations.kt` `HighlightStyleRow`): all four styles are
 * always visible in one centred row, each a 34dp rounded chip tinted from the surface's foreground,
 * with the selected one carrying the stronger background and border. This was private to that file
 * and shared with the selection menu, so it is public here for both hosts.
 */
@Composable
fun SharedMobileEpubHighlightStyleRow(
    selectedStyle: HighlightStyle,
    effectiveText: Color,
    onStyleSelect: (HighlightStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HighlightStyle.entries.forEach { style ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selectedStyle == style) effectiveText.copy(alpha = 0.16f)
                        else effectiveText.copy(alpha = 0.06f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (selectedStyle == style) {
                            effectiveText.copy(alpha = 0.55f)
                        } else {
                            effectiveText.copy(alpha = 0.24f)
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onStyleSelect(style) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(highlightStyleIcon(style)),
                    contentDescription = style.id,
                    tint = effectiveText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Highlight-palette row: the four ARGB slots, plus the spectrum entry to the palette editor.
 *
 * Android benchmark (`epubreader/EpubReaderAnnotations.kt` `HighlightColorRow`). The palette is
 * iterated by *slot*, not by element, so a host that persisted fewer than four colours still shows
 * the stock four rather than a short row.
 */
@Composable
fun SharedMobileEpubHighlightColorRow(
    palette: ReaderHighlightPalette,
    /**
     * The colour to mark as current, or null when nothing is current.
     *
     * Null is a real state, not a missing argument: the row is also the *chooser* shown when text is
     * selected, and there the host has no colour yet. Marking the first slot in that case claimed a
     * colour the user never picked — and it read as "this text is already yellow" over text that was
     * not highlighted at all.
     */
    selectedColorArgb: Int?,
    onColorSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onOpenPaletteManager: (() -> Unit)? = null,
    selectedColorDescription: String = readerString("content_desc_selected_color", "Selected color"),
) {
    val sanitizedPalette = palette.sanitized()
    Row(
        modifier = modifier.padding(vertical = 8.dp, horizontal = 10.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        sanitizedPalette.colors.indices.forEach { slot ->
            val slotArgb = sanitizedPalette.argbAt(slot)
            val selected = selectedColorArgb == slotArgb
            val swatchColor = Color(slotArgb)
            // Swatches are pure colour, so they were unlabelled: TalkBack announced an unlabelled
            // button and tests had no stable handle to select one by.
            val colorId = sanitizedPalette.colorIdAt(slot)
            val colorLabel = readerString(
                "color_$colorId",
                if (colorId.startsWith(CUSTOM_HIGHLIGHT_COLOR_ID_PREFIX)) {
                    "#${colorId.removePrefix(CUSTOM_HIGHLIGHT_COLOR_ID_PREFIX).uppercase()}"
                } else {
                    colorId.replaceFirstChar { it.uppercase() }
                }
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(28.dp)
                    .testTag("HighlightColor_$colorId")
                    .clip(CircleShape)
                    .background(swatchColor)
                    .clickable { onColorSelect(slotArgb) }
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        },
                        shape = CircleShape
                    )
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = selectedColorDescription,
                        tint = if (swatchColor.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        onOpenPaletteManager?.let { openPaletteManager ->
            Spacer(modifier = Modifier.width(6.dp))
            SharedReaderHighlightPaletteSpectrumButton(
                onClick = openPaletteManager,
                size = 28.dp
            )
        }
    }
}

/**
 * Annotation editor sheet for an existing EPUB highlight.
 *
 * Android benchmark (`epubreader/EpubReaderAnnotations.kt` `AnnotationBottomSheet`), moved to shared
 * so both hosts render one surface instead of two that have already drifted once. The layout is
 * Android's: style row, palette row, the quoted snippet with its coloured accent bar, the tool row,
 * the note field, then delete/save.
 *
 * What the host still owns, deliberately:
 * - **the tool actions.** Copy, dictionary, translate and search are handed in as callbacks because
 *   each host routes them differently (Android to its AI define / external dictionary packages, iOS
 *   to its lookup service). This sheet only supplies the icons and the labels.
 * - **delete confirmation.** The sheet calls [onDelete] and nothing else; Android deletes
 *   immediately, iOS keeps its confirmation dialog around the same callback. Wrapping the
 *   confirmation into the sheet would take an affordance away from one host or give one to the
 *   other, so it stays host policy rather than becoming a platform branch in the sheet.
 * - **preferred style persistence.** Same reasoning as Android's own call site: the sheet reports
 *   the choice, the host remembers it.
 *
 * The note itself is the one thing the sheet commits on its own: [onSave] is "persist this text",
 * not "persist and close", and the sheet reports the draft through it well before the user presses
 * Save (see [SharedReaderNoteAutosave]). Dismissing the sheet after either path is the sheet's job,
 * through [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobileEpubAnnotationSheet(
    highlight: UserHighlight,
    palette: ReaderHighlightPalette,
    effectiveBg: Color,
    effectiveText: Color,
    labels: SharedMobileEpubAnnotationLabels = sharedMobileEpubAnnotationLabels(),
    onColorChange: (Int) -> Unit,
    onStyleChange: (HighlightStyle) -> Unit,
    onCopy: () -> Unit,
    onDictionary: () -> Unit,
    onTranslate: () -> Unit,
    onSearch: () -> Unit,
    onOpenPaletteManager: (() -> Unit)? = null,
    onSpeak: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var noteText by remember(highlight.id) { mutableStateOf(highlight.note.orEmpty()) }

    // The draft is committed by the sheet, not only by the Save button. Before this, every
    // dismissal path took the note with it: a scrim tap, a back press, and — because a body with
    // nothing to scroll hands the drag to the sheet — the swipe-down gesture the reporter hit by
    // accident. `rememberUpdatedState` keeps the autosave pointed at the host's current callback
    // even though the instance itself is keyed on the highlight id.
    val scope = rememberCoroutineScope()
    val commitNote by rememberUpdatedState(onSave)
    val autosave = remember(highlight.id) {
        SharedReaderNoteAutosave(
            scope = scope,
            initialText = highlight.note.orEmpty(),
            commit = { commitNote(it) }
        )
    }
    val dismissWithFlush: () -> Unit = {
        autosave.flush(noteText)
        onDismiss()
    }
    // What the keyboard may take away is deliberately narrow: only the Delete/Save row, see
    // `sharedMobileAnnotationSheetLayout`. The styling rows, the snippet, the tools, the drag handle
    // and the note field itself keep their resting layout — collapsing the editor to the field alone
    // is what made the input cover the whole sheet.
    //
    // `WindowInsets.isImeVisible` is Android-only in this Compose version, so visibility comes from a
    // non-zero IME inset instead: equivalent for open/closed, and gracefully false where IME insets
    // are unsupported.
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val annotationLayout = sharedMobileAnnotationSheetLayout(isImeVisible)

    ModalBottomSheet(
        onDismissRequest = dismissWithFlush,
        sheetState = sheetState,
        containerColor = effectiveBg, // Matches user theme
        contentColor = effectiveText,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Safety net rather than the mechanism: Material3's sheet already pads its own
                // window for the IME and consumes that inset, so this is a no-op there. It is what
                // keeps the editor honest on a host whose sheet does not, and if a Material3
                // change ever drops it.
                .imePadding()
        ) {
            // The styling/tools header scrolls, the note field does not: a Column measures its
            // children with the height that is left, so keeping everything in one body is exactly
            // what let the keyboard squeeze the field and then the Save button to nothing. The
            // header stays while typing — the policy says the keyboard takes nothing but the action
            // row — and gives the field room by scrolling.
            if (annotationLayout.editorChromeVisible) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                ) {
                    // Top: Highlight Style + Colors
                    SharedMobileEpubHighlightStyleRow(
                        selectedStyle = highlight.style,
                        effectiveText = effectiveText,
                        onStyleSelect = onStyleChange,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    SharedMobileEpubHighlightColorRow(
                        palette = palette,
                        selectedColorArgb = highlight.effectiveArgb,
                        onColorSelect = onColorChange,
                        onOpenPaletteManager = onOpenPaletteManager,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Middle: Elegant Highlight Snippet Card
                    Surface(
                        color = highlight.effectiveColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, highlight.effectiveColor.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                            // Left colored accent bar
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .fillMaxHeight()
                                    .background(highlight.effectiveColor)
                            )
                            Text(
                                text = "\"${highlight.text}\"",
                                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                color = effectiveText.copy(alpha = 0.9f),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Action Tools Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SharedMobileEpubAnnotationToolButton(
                            icon = Res.drawable.copy,
                            label = labels.copy,
                            onClick = onCopy,
                            effectiveText = effectiveText
                        )
                        // Android's benchmark row has exactly four actions and no read-aloud, because its
                        // speak entry lives in the selection menu. iOS keeps the affordance here, so it is a
                        // slot the host may leave null — on Android it does, and the row is unchanged.
                        onSpeak?.let { speak ->
                            SharedMobileEpubAnnotationToolButton(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                label = labels.speak,
                                onClick = speak,
                                effectiveText = effectiveText
                            )
                        }
                        SharedMobileEpubAnnotationToolButton(
                            icon = Res.drawable.dictionary,
                            label = labels.dictionary,
                            onClick = onDictionary,
                            effectiveText = effectiveText
                        )
                        SharedMobileEpubAnnotationToolButton(
                            icon = Res.drawable.translate,
                            label = labels.translate,
                            onClick = onTranslate,
                            effectiveText = effectiveText
                        )
                        SharedMobileEpubAnnotationToolButton(
                            icon = Res.drawable.search,
                            label = labels.search,
                            onClick = onSearch,
                            effectiveText = effectiveText
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }

            // Note field. Outside the scrolling header so it can never be the row that gets measured
            // away, and the same height with or without a keyboard: growing it past these five lines
            // is what let the input cover the rest of the editor.
            OutlinedTextField(
                value = noteText,
                onValueChange = {
                    noteText = it
                    autosave.onTextChanged(it)
                },
                placeholder = { Text(labels.notePlaceholder, color = effectiveText.copy(alpha = 0.5f)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .heightIn(min = 100.dp),
                maxLines = annotationLayout.noteFieldMaxLines,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = effectiveText.copy(alpha = 0.3f),
                    focusedTextColor = effectiveText,
                    unfocusedTextColor = effectiveText
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Bottom Actions. Outside the scrolling region on purpose: with the keyboard up, Save
            // sitting at the bottom of a scrolling body is a Save the user has to scroll to. While
            // the keyboard is actually open the row is hidden outright — the one thing the keyboard
            // takes away (see `sharedMobileAnnotationSheetLayout`).
            if (annotationLayout.bottomActionsVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            // Drop the draft first: a pending commit would write the note back onto
                            // an id this button just deleted.
                            autosave.discard()
                            onDelete()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(labels.delete)
                    }
                    Button(
                        onClick = dismissWithFlush,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(labels.saveNote)
                    }
                }
            } else {
                // The action row stepped aside, but the field keeps the same gap below it instead of
                // sitting flush against the keyboard.
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SharedMobileEpubAnnotationToolButton(
    label: String,
    onClick: () -> Unit,
    effectiveText: Color,
    icon: DrawableResource? = null,
    imageVector: ImageVector? = null,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = effectiveText.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
        } else if (imageVector != null) {
            Icon(
                imageVector = imageVector,
                contentDescription = label,
                tint = effectiveText.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = effectiveText.copy(alpha = 0.8f)
        )
    }
}

/**
 * "Delete this highlight?" confirmation.
 *
 * Extracted from the drawer list, where it was inline, because the sheet path needs the same dialog
 * and a third copy is what this migration is for. The *policy* is what actually differs per host —
 * Android deletes from the sheet without asking, iOS asks — so the dialog is reachable from both and
 * the hosts keep their own decision, rather than the sheet quietly gaining or losing a guardrail.
 */
@Composable
fun SharedMobileEpubHighlightDeleteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(readerString("dialog_delete_highlight", "Delete Highlight?")) },
        text = {
            Text(
                readerString(
                    "dialog_delete_highlight_desc",
                    "Are you sure you want to permanently delete this highlight?"
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(readerString("action_delete", "Delete"), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(readerString("action_cancel", "Cancel")) }
        }
    )
}
