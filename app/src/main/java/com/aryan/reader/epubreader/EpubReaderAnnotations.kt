/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader.epubreader

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.core.text.HtmlCompat
import androidx.core.net.toUri
import com.aryan.reader.R
import com.aryan.reader.copyPlainTextToClipboard
import com.aryan.reader.epub.EpubChapter
import com.aryan.reader.shared.EpubAnnotationSerializer
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.epubHighlightColorFromToken
import com.aryan.reader.shared.ui.SharedMobileEpubAnnotationLabels
import com.aryan.reader.shared.ui.SharedMobileEpubAnnotationSheet
import com.aryan.reader.shared.ui.SharedMobileEpubHighlightColorRow
import com.aryan.reader.shared.ui.SharedMobileEpubHighlightStyleRow
import com.aryan.reader.shared.ui.SharedReaderHighlightPaletteDialog
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.DefaultEpubHighlightPaletteArgb
import com.aryan.reader.shared.sanitizeEpubHighlightPalette
import com.aryan.reader.shared.legacyEpubHighlightColorForArgb
import com.aryan.reader.shared.legacyEpubHighlightColorOrNull
import com.aryan.reader.shared.epubHighlightColorTag
import com.aryan.reader.shared.epubHighlightColorFromToken
import com.aryan.reader.paginatedreader.isReaderExternalHref
import com.aryan.reader.paginatedreader.readerExternalHrefForDisplay
import com.aryan.reader.readerModalMaxHeightDp

private const val BOOKMARK_PREFS_NAME = "epub_reader_bookmarks"

typealias Bookmark = com.aryan.reader.shared.EpubBookmark
typealias HighlightColor = com.aryan.reader.shared.HighlightColor
typealias HighlightStyle = com.aryan.reader.shared.HighlightStyle
typealias UserHighlight = com.aryan.reader.shared.UserHighlight

fun escapeJsString(value: String): String {
    return com.aryan.reader.shared.escapeJsString(value)
}

private val DefaultHighlightPaletteArgb: List<Int>
    get() = DefaultEpubHighlightPaletteArgb

fun saveHighlightPalette(context: Context, palette: List<Int>) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val argbs = sanitizeHighlightPalette(palette).joinToString(",") { it.toString() }
    prefs.edit {
        putString("highlight_palette_argbs", argbs)
        remove("highlight_palette_ids")
    }
}

fun loadHighlightPalette(context: Context): List<Int> {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val savedArgbs = prefs.getString("highlight_palette_argbs", null)
    if (savedArgbs != null) {
        val list = savedArgbs.split(",").mapNotNull { it.toIntOrNull() }
        if (list.size == 4) return list
    }
    val savedIds = prefs.getString("highlight_palette_ids", null)
    if (savedIds != null) {
        val list = savedIds.split(",").mapNotNull { id ->
            HighlightColor.entries.find { it.id == id }?.color?.toArgb()
        }
        if (list.size == 4) return list
    }
    return DefaultHighlightPaletteArgb
}

fun savePreferredHighlightStyle(context: Context, style: HighlightStyle) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putString("preferred_highlight_style", style.id) }
}

fun loadPreferredHighlightStyle(context: Context): HighlightStyle {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return HighlightStyle.fromId(prefs.getString("preferred_highlight_style", null))
}

private fun sanitizeHighlightPalette(palette: List<Int>): List<Int> = sanitizeEpubHighlightPalette(palette)

internal fun legacyHighlightColorForArgb(argb: Int): HighlightColor {
    return legacyEpubHighlightColorForArgb(argb)
}

internal fun legacyHighlightColorOrNull(argb: Int): HighlightColor? {
    return legacyEpubHighlightColorOrNull(argb)
}

internal fun highlightColorTag(argb: Int): String {
    return epubHighlightColorTag(argb)
}

internal fun highlightColorFromToken(token: String): Pair<HighlightColor, Int?> {
    return epubHighlightColorFromToken(token)
}

private fun String.sanitizedAnnotationPrefsKey(): String =
    buildString(length) {
        for (char in this@sanitizedAnnotationPrefsKey) {
            if (char.isLetterOrDigit()) append(char)
        }
    }
// --- Persistence Helpers ---

fun loadBookmarks(context: Context, bookTitle: String, chapters: List<EpubChapter>, bookmarksJson: String?): Set<Bookmark> {
    val stringSetToParse: Collection<String> = if (bookmarksJson != null) {
        return EpubAnnotationSerializer.parseBookmarksJson(bookmarksJson, chapters.map { it.title })
    } else {
        val prefs = context.getSharedPreferences(BOOKMARK_PREFS_NAME, Context.MODE_PRIVATE)
        val key = "bookmarks_cfi_${bookTitle.sanitizedAnnotationPrefsKey()}"
        prefs.getStringSet(key, emptySet()) ?: emptySet()
    }

    return EpubAnnotationSerializer.parseBookmarkEntries(stringSetToParse, chapters.map { it.title })
}

fun saveHighlightsToPrefs(context: Context, bookTitle: String, highlights: List<UserHighlight>) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val sanitizedTitle = bookTitle.sanitizedAnnotationPrefsKey()
    val key = "highlights_data_$sanitizedTitle"
    prefs.edit { putString(key, EpubAnnotationSerializer.highlightsToJson(highlights)) }
}

fun loadHighlightsFromPrefs(context: Context, bookTitle: String): List<UserHighlight> {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val sanitizedTitle = bookTitle.sanitizedAnnotationPrefsKey()
    val key = "highlights_data_$sanitizedTitle"
    val jsonString = prefs.getString(key, "[]") ?: "[]"
    return EpubAnnotationSerializer.parseHighlightsJson(jsonString)
}

fun parseHighlightsJson(jsonString: String?): List<UserHighlight> {
    return EpubAnnotationSerializer.parseHighlightsJson(jsonString)
}

fun highlightsToJson(highlights: List<UserHighlight>): String {
    return EpubAnnotationSerializer.highlightsToJson(highlights)
}

fun bookmarksToJson(bookmarks: Collection<Bookmark>): String {
    return EpubAnnotationSerializer.bookmarksToJson(bookmarks)
}

fun clearHighlightsFromPrefs(context: Context, bookTitle: String) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val sanitizedTitle = bookTitle.sanitizedAnnotationPrefsKey()
    val key = "highlights_data_$sanitizedTitle"
    prefs.edit { remove(key) }
}

// --- Logic Helpers ---

fun processAndAddHighlight(
    newCfi: String,
    newText: String,
    newColor: HighlightColor,
    chapterIndex: Int,
    currentList: MutableList<UserHighlight>,
    locator: ReaderLocator = ReaderLocator.fromLegacy(
        chapterIndex = chapterIndex,
        cfi = newCfi,
        textQuote = newText
    ),
    newColorArgb: Int? = null,
    newStyle: HighlightStyle = HighlightStyle.BACKGROUND
): String {
    return EpubAnnotationSerializer.processAndAddHighlight(
        newCfi = newCfi,
        newText = newText,
        newColor = newColor,
        chapterIndex = chapterIndex,
        currentList = currentList,
        locator = locator,
        newColorArgb = newColorArgb,
        newStyle = newStyle
    )
}

// --- UI Components ---

/**
 * EPUB highlight palette editor.
 *
 * Now a thin adapter over the shared [SharedHighlightPaletteSlotDialog]: this dialog was the
 * original implementation, and the shared one carries the identical layout (all slots visible
 * while one is edited, hex + RGB inputs, reset of the selected slot, Save / Cancel). Android stays
 * the benchmark for behaviour — [initialSelection] and the ARGB list shape are preserved — while
 * the ~200 lines of inline spectrum machinery now live in shared with a single implementation.
 */
@Composable
fun PaletteManagerDialog(
    currentPalette: List<Int>,
    initialSelection: Int = 0,
    onSave: (List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    SharedReaderHighlightPaletteDialog(
        palette = ReaderHighlightPalette(colors = currentPalette),
        onDismiss = onDismiss,
        onSave = { saved -> onSave(saved.sanitized().colors) },
        initialSelection = initialSelection,
        maxDialogHeight = readerModalMaxHeightDp(LocalConfiguration.current.screenHeightDp).dp
    )
}

fun highlightStyleIconRes(style: HighlightStyle): Int {
    return when (style) {
        HighlightStyle.BACKGROUND -> R.drawable.font_background
        HighlightStyle.UNDERLINE -> R.drawable.format_underlined
        HighlightStyle.WAVY_UNDERLINE -> R.drawable.format_underlined_squiggle
        HighlightStyle.STRIKETHROUGH -> R.drawable.strikethrough
    }
}

/**
 * EPUB highlight palette row.
 *
 * Thin adapter over the shared [SharedMobileEpubHighlightColorRow]: this was the original
 * implementation and the shared one carries the identical layout (four ARGB slots, 28dp swatches,
 * 3dp selection border, check glyph, spectrum entry to the palette editor). Android stays the
 * benchmark for behaviour — the ARGB list shape and the selection rule are unchanged — while the
 * row itself now lives in one place for both hosts.
 */
@Composable
fun HighlightColorRow(
    modifier: Modifier = Modifier,
    activeHighlightPalette: List<Int>,
    selectedColorArgb: Int? = null,
    onColorSelect: (Int) -> Unit,
    onOpenPaletteManager: (() -> Unit)? = null,
) {
    SharedMobileEpubHighlightColorRow(
        palette = ReaderHighlightPalette(colors = activeHighlightPalette),
        selectedColorArgb = selectedColorArgb ?: activeHighlightPalette.firstOrNull()
            ?: return,
        onColorSelect = onColorSelect,
        modifier = modifier,
        onOpenPaletteManager = onOpenPaletteManager,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationBottomSheet(
    highlight: UserHighlight,
    effectiveBg: Color,
    effectiveText: Color,
    activeHighlightPalette: List<Int>,
    onColorChange: (Int) -> Unit,
    onStyleChange: (HighlightStyle) -> Unit,
    onOpenPaletteManager: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onDictionary: () -> Unit,
    onTranslate: () -> Unit,
    onSearch: () -> Unit
) {
    // Thin adapter over the shared sheet. This was the original implementation: the layout (style
    // row, palette row, quoted snippet with its accent bar, tool row, note field, delete/save) is
    // Android's, moved to `SharedMobileEpubAnnotationSheet` so both hosts render one surface.
    //
    // What still differs per host is behaviour, not layout: the tool callbacks stay Android's own
    // (AI define / external dictionary packages / clipboard), and the labels come from Android
    // resources rather than a shared lookup, because the Android reader composes without a
    // LocalSharedStringResolver and would otherwise render the English fallbacks.
    SharedMobileEpubAnnotationSheet(
        highlight = highlight,
        palette = ReaderHighlightPalette(colors = activeHighlightPalette),
        effectiveBg = effectiveBg,
        effectiveText = effectiveText,
        labels = SharedMobileEpubAnnotationLabels(
            copy = stringResource(R.string.action_copy),
            speak = stringResource(R.string.label_speak),
            dictionary = stringResource(R.string.label_dict),
            translate = stringResource(R.string.dict_translate),
            search = stringResource(R.string.action_search),
            notePlaceholder = stringResource(R.string.placeholder_add_note),
            delete = stringResource(R.string.action_delete),
            saveNote = stringResource(R.string.action_save_note)
        ),
        onColorChange = onColorChange,
        onStyleChange = onStyleChange,
        onCopy = onCopy,
        onDictionary = onDictionary,
        onTranslate = onTranslate,
        onSearch = onSearch,
        onOpenPaletteManager = onOpenPaletteManager,
        onDismiss = onDismiss,
        onSave = onSave,
        onDelete = onDelete
    )
}

private class MenuActionItem(
    val iconRes: Int? = null,
    val imageVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val label: String,
    val onClick: () -> Unit,
    val isError: Boolean = false
)

@Composable
fun PaginatedTextSelectionMenu(
    onCopy: () -> Unit,
    onSelectAll: (() -> Unit)?,
    onDictionary: () -> Unit,
    onTranslate: () -> Unit,
    onSearch: () -> Unit,
    onHighlight: ((Int, HighlightStyle) -> Unit)?,
    onNote: ((HighlightStyle) -> Unit)? = null,
    onDelete: (() -> Unit)?,
    onTts: (() -> Unit)?,
    @Suppress("unused") isProUser: Boolean,
    @Suppress("unused") isOss: Boolean,
    activeHighlightPalette: List<Int> = emptyList(),
    onOpenPaletteManager: (() -> Unit)? = null,
    existingNote: String? = null,
    selectedColorArgb: Int? = null,
    selectedStyle: HighlightStyle = HighlightStyle.BACKGROUND
) {
    val context = LocalContext.current
    var selectedHighlightStyle by remember(selectedStyle) {
        mutableStateOf(if (selectedStyle == HighlightStyle.BACKGROUND) loadPreferredHighlightStyle(context) else selectedStyle)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.width(IntrinsicSize.Max).widthIn(min = 180.dp)) {
            // 1. Highlight style and colors row
            if (onHighlight != null) {
                SharedMobileEpubHighlightStyleRow(
                    selectedStyle = selectedHighlightStyle,
                    effectiveText = MaterialTheme.colorScheme.onSurface,
                    onStyleSelect = {
                        selectedHighlightStyle = it
                        savePreferredHighlightStyle(context, it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp)
                )
                HighlightColorRow(
                    activeHighlightPalette = activeHighlightPalette,
                    selectedColorArgb = selectedColorArgb,
                    onColorSelect = { onHighlight(it, selectedHighlightStyle) },
                    onOpenPaletteManager = onOpenPaletteManager
                )
                HorizontalDivider()
            }

            // 2. Improved Comment/Note View
            if (!existingNote.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 140.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.label_note),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.label_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = existingNote,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
            }

            val actions = mutableListOf<MenuActionItem>()
            actions.add(MenuActionItem(iconRes = R.drawable.copy, label = stringResource(R.string.action_copy), onClick = onCopy))
            if (onTts != null) {
                actions.add(MenuActionItem(imageVector = Icons.AutoMirrored.Filled.VolumeUp, label = stringResource(R.string.label_speak), onClick = onTts))
            }
            actions.add(MenuActionItem(iconRes = R.drawable.dictionary, label = stringResource(R.string.label_dict), onClick = onDictionary))
            actions.add(MenuActionItem(iconRes = R.drawable.translate, label = stringResource(R.string.dict_translate), onClick = onTranslate))
            actions.add(MenuActionItem(iconRes = R.drawable.search, label = stringResource(R.string.action_search), onClick = onSearch))

            if (onNote != null) {
                val noteLabel = if (existingNote.isNullOrBlank()) stringResource(R.string.label_note) else stringResource(R.string.label_edit)
                actions.add(MenuActionItem(imageVector = Icons.Default.Edit, label = noteLabel, onClick = { onNote(selectedHighlightStyle) }))
            }

            if (onSelectAll != null) {
                actions.add(MenuActionItem(iconRes = R.drawable.select_all, label = stringResource(R.string.select_all), onClick = onSelectAll))
            }
            if (onDelete != null) {
                actions.add(MenuActionItem(imageVector = Icons.Default.Delete, label = stringResource(R.string.action_remove), onClick = onDelete, isError = true))
            }

            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                actions.chunked(3).forEach { rowActions ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowActions.forEach { action ->
                            val tint = if (action.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            Column(
                                modifier = Modifier
                                    .width(56.dp)
                                    .clickable { action.onClick() }
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (action.imageVector != null) {
                                    Icon(imageVector = action.imageVector, contentDescription = action.label, tint = tint, modifier = Modifier.size(22.dp))
                                } else if (action.iconRes != null) {
                                    Icon(painter = painterResource(id = action.iconRes), contentDescription = action.label, tint = tint, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = action.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
                            }
                        }
                        repeat(3 - rowActions.size) {
                            Spacer(modifier = Modifier.width(56.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FootnoteBottomSheet(
    htmlContent: String,
    effectiveBg: Color,
    effectiveText: Color,
    onInternalLinkClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var externalHref by remember { mutableStateOf<String?>(null) }
    val plainText = remember(htmlContent) {
        HtmlCompat.fromHtml(htmlContent, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val maxSheetHeight = configuration.screenHeightDp.dp * 0.5f

    externalHref?.let { urlToShow ->
        AlertDialog(
            onDismissRequest = { externalHref = null },
            title = { Text(stringResource(R.string.dialog_external_link_title)) },
            text = { Text(stringResource(R.string.dialog_external_link_desc, urlToShow)) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        val copied = copyPlainTextToClipboard(
                            context,
                            context.getString(R.string.clip_label_copied_link),
                            urlToShow
                        )
                        if (!copied) {
                            Toast.makeText(context, R.string.error_copy_to_clipboard, Toast.LENGTH_SHORT).show()
                        }
                        externalHref = null
                    }) { Text(stringResource(R.string.action_copy)) }
                    TextButton(onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, urlToShow.toUri()))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, R.string.error_no_browser, Toast.LENGTH_LONG).show()
                        }
                        externalHref = null
                    }) { Text(stringResource(R.string.action_open)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { externalHref = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = effectiveBg,
        contentColor = effectiveText,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.label_note),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    val copied = copyPlainTextToClipboard(
                        context,
                        context.getString(R.string.label_note),
                        plainText
                    )
                    if (!copied) {
                        Toast.makeText(context, R.string.error_copy_to_clipboard, Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(stringResource(R.string.action_copy))
                }
            }

            Surface(
                color = effectiveText.copy(alpha = 0.05f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, effectiveText.copy(alpha = 0.1f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    AndroidView(
                        factory = { context ->
                            TextView(context).apply {
                                setTextColor(effectiveText.toArgb())
                                textSize = 16f
                                setLineSpacing(0f, 1.4f)

                                isVerticalScrollBarEnabled = false
                                movementMethod = LinkMovementMethod.getInstance()
                                linksClickable = true
                            }
                        },
                        update = { textView ->
                            val formatted = HtmlCompat.fromHtml(
                                htmlContent,
                                HtmlCompat.FROM_HTML_MODE_COMPACT
                            )
                            if (formatted is Spannable) {
                                formatted.getSpans(0, formatted.length, URLSpan::class.java).forEach { span ->
                                    val start = formatted.getSpanStart(span)
                                    val end = formatted.getSpanEnd(span)
                                    val flags = formatted.getSpanFlags(span)
                                    formatted.removeSpan(span)
                                    formatted.setSpan(object : ClickableSpan() {
                                        override fun onClick(widget: View) {
                                            val href = span.url.orEmpty()
                                            if (href.isReaderExternalHref()) {
                                                externalHref = href.readerExternalHrefForDisplay()
                                            } else {
                                                onDismiss()
                                                onInternalLinkClick(href)
                                            }
                                        }
                                    }, start, end, flags)
                                }
                            }
                            textView.text = formatted
                        }
                    )
                }
            }
        }
    }
}
