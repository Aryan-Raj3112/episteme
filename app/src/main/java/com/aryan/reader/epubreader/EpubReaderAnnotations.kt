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
import timber.log.Timber
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.aryan.reader.R
import com.aryan.reader.epub.EpubChapter
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.min

private const val BOOKMARK_PREFS_NAME = "epub_reader_bookmarks"

data class Bookmark(
    val cfi: String,
    val chapterTitle: String,
    val label: String? = null,
    val snippet: String,
    val pageInChapter: Int?,
    val totalPagesInChapter: Int?,
    val chapterIndex: Int
)

enum class HighlightColor(val id: String, val color: Color, val cssClass: String) {
    YELLOW("yellow", Color(0xFFFBC02D), "user-highlight-yellow"),
    GREEN("green", Color(0xFF388E3C), "user-highlight-green"),
    BLUE("blue", Color(0xFF1976D2), "user-highlight-blue"),
    RED("red", Color(0xFFD32F2F), "user-highlight-red"),
    PURPLE("purple", Color(0xFF7B1FA2), "user-highlight-purple"),
    ORANGE("orange", Color(0xFFF57C00), "user-highlight-orange"),
    CYAN("cyan", Color(0xFF0097A7), "user-highlight-cyan"),
    MAGENTA("magenta", Color(0xFFC2185B), "user-highlight-magenta"),
    LIME("lime", Color(0xFFAFB42B), "user-highlight-lime"),
    PINK("pink", Color(0xFFE91E63), "user-highlight-pink"),
    TEAL("teal", Color(0xFF00796B), "user-highlight-teal"),
    INDIGO("indigo", Color(0xFF303F9F), "user-highlight-indigo"),
    BLACK("black", Color(0xFF424242), "user-highlight-black"),
    WHITE("white", Color(0xFFF5F5F5), "user-highlight-white");
}

data class UserHighlight(
    val id: String = UUID.randomUUID().toString(),
    val cfi: String,
    val text: String,
    val color: HighlightColor,
    val chapterIndex: Int,
    val note: String? = null
)

fun escapeJsString(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
        .replace("\u2028", "\\u2028")
        .replace("\u2029", "\\u2029")
}

fun saveHighlightPalette(context: Context, palette: List<HighlightColor>) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val ids = palette.joinToString(",") { it.id }
    prefs.edit { putString("highlight_palette_ids", ids) }
}

fun loadHighlightPalette(context: Context): List<HighlightColor> {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val savedIds = prefs.getString("highlight_palette_ids", null)
    if (savedIds != null) {
        val list = savedIds.split(",").mapNotNull { id ->
            HighlightColor.entries.find { it.id == id }
        }
        if (list.size == 4) return list
    }
    return listOf(HighlightColor.YELLOW, HighlightColor.GREEN, HighlightColor.BLUE, HighlightColor.RED)
}

// --- Persistence Helpers ---

fun loadBookmarks(context: Context, bookTitle: String, chapters: List<EpubChapter>, bookmarksJson: String?): Set<Bookmark> {
    val stringSetToParse: Collection<String> = if (bookmarksJson != null) {
        try {
            val jsonArray = JSONArray(bookmarksJson)
            (0 until jsonArray.length()).map { jsonArray.getString(it) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse bookmarks from ViewModel")
            emptyList()
        }
    } else {
        val prefs = context.getSharedPreferences(BOOKMARK_PREFS_NAME, Context.MODE_PRIVATE)
        val key = "bookmarks_cfi_${bookTitle.replace("[^a-zA-Z0-9]".toRegex(), "")}"
        prefs.getStringSet(key, emptySet()) ?: emptySet()
    }

    return stringSetToParse.mapNotNull { jsonString ->
        try {
            val json = JSONObject(jsonString)
            val chapterIndex = if (json.has("chapterIndex")) {
                json.getInt("chapterIndex")
            } else {
                val chapterTitle = json.getString("chapterTitle")
                chapters.indexOfFirst { it.title == chapterTitle }.coerceAtLeast(0)
            }
            Bookmark(
                cfi = json.getString("cfi"),
                chapterTitle = json.getString("chapterTitle"),
                label = if (json.has("label")) json.getString("label") else null,
                snippet = json.getString("snippet"),
                pageInChapter = if (json.has("pageInChapter")) json.optInt("pageInChapter") else null,
                totalPagesInChapter = if (json.has("totalPagesInChapter")) json.optInt("totalPagesInChapter") else null,
                chapterIndex = chapterIndex
            )
        } catch (_: Exception) {
            null
        }
    }.toSet()
}

fun saveHighlightsToPrefs(context: Context, bookTitle: String, highlights: List<UserHighlight>) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val sanitizedTitle = bookTitle.replace("[^a-zA-Z0-9]".toRegex(), "")
    val key = "highlights_data_$sanitizedTitle"
    val jsonArray = JSONArray()
    highlights.forEach { h ->
        val obj = JSONObject().apply {
            put("id", h.id)
            put("cfi", h.cfi)
            put("text", h.text)
            put("colorId", h.color.id)
            put("chapterIndex", h.chapterIndex)
            put("note", h.note ?: "")
        }
        jsonArray.put(obj)
    }
    prefs.edit { putString(key, jsonArray.toString()) }
}

fun loadHighlightsFromPrefs(context: Context, bookTitle: String): List<UserHighlight> {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val sanitizedTitle = bookTitle.replace("[^a-zA-Z0-9]".toRegex(), "")
    val key = "highlights_data_$sanitizedTitle"
    val jsonString = prefs.getString(key, "[]") ?: "[]"
    val list = mutableListOf<UserHighlight>()
    try {
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val colorId = obj.getString("colorId")
            val color = HighlightColor.entries.find { it.id == colorId } ?: HighlightColor.YELLOW
            val noteStr = obj.optString("note", "")
            list.add(
                UserHighlight(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    cfi = obj.getString("cfi"),
                    text = obj.getString("text"),
                    color = color,
                    chapterIndex = obj.getInt("chapterIndex"),
                    note = noteStr.takeIf { it.isNotBlank() }
                )
            )
        }
    } catch (e: Exception) {
        Timber.e(e, "Error loading highlights")
    }
    return list
}

fun parseHighlightsJson(jsonString: String?): List<UserHighlight> {
    if (jsonString.isNullOrBlank()) return emptyList()
    val list = mutableListOf<UserHighlight>()
    try {
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val colorId = obj.getString("colorId")
            val color = HighlightColor.entries.find { it.id == colorId } ?: HighlightColor.YELLOW
            val noteStr = obj.optString("note", "")
            list.add(
                UserHighlight(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    cfi = obj.getString("cfi"),
                    text = obj.getString("text"),
                    color = color,
                    chapterIndex = obj.getInt("chapterIndex"),
                    note = noteStr.takeIf { it.isNotBlank() }
                )
            )
        }
    } catch (e: Exception) {
        Timber.e(e, "Error parsing highlights JSON")
    }
    return list
}

fun highlightsToJson(highlights: List<UserHighlight>): String {
    val jsonArray = JSONArray()
    highlights.forEach { h ->
        val obj = JSONObject().apply {
            put("id", h.id)
            put("cfi", h.cfi)
            put("text", h.text)
            put("colorId", h.color.id)
            put("chapterIndex", h.chapterIndex)
            put("note", h.note ?: "")
        }
        jsonArray.put(obj)
    }
    return jsonArray.toString()
}

fun clearHighlightsFromPrefs(context: Context, bookTitle: String) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val sanitizedTitle = bookTitle.replace("[^a-zA-Z0-9]".toRegex(), "")
    val key = "highlights_data_$sanitizedTitle"
    prefs.edit { remove(key) }
}

// --- Logic Helpers ---

fun processAndAddHighlight(
    newCfi: String,
    newText: String,
    newColor: HighlightColor,
    chapterIndex: Int,
    currentList: MutableList<UserHighlight>
): String {
    val newParts = newCfi.split('|')
    val newStartFull = newParts.first()
    val newEndFull = newParts.last()
    val newStartPath = newStartFull.split(':').first()
    val newStartOffset = newStartFull.substringAfter(':', "0").toInt()
    val newEndPath = newEndFull.split(':').first()
    val newEndOffset = newEndFull.substringAfter(':', "0").toInt()

    val iterator = currentList.iterator()
    var finalStartPath = newStartPath
    var finalStartOffset = newStartOffset
    var finalEndPath = newEndPath
    var finalEndOffset = newEndOffset
    var finalText = newText
    var finalNote: String? = null

    while (iterator.hasNext()) {
        val existing = iterator.next()
        if (existing.chapterIndex != chapterIndex) continue

        val exParts = existing.cfi.split('|')
        val exStartFull = exParts.first()
        val exEndFull = exParts.last()
        val exStartPath = exStartFull.split(':').first()
        val exStartOffset = exStartFull.substringAfter(':', "0").toInt()
        val exEndPath = exEndFull.split(':').first()
        val exEndOffset = exEndFull.substringAfter(':', "0").toInt()

        fun comparePaths(p1: String, p2: String): Int {
            val parts1 = p1.split('/').filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
            val parts2 = p2.split('/').filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
            val len = min(parts1.size, parts2.size)
            for (i in 0 until len) {
                if (parts1[i] != parts2[i]) return parts1[i] - parts2[i]
            }
            return parts1.size - parts2.size
        }

        val startCmp = comparePaths(exStartPath, newEndPath)
        val endCmp = comparePaths(exEndPath, newStartPath)

        val isDisjoint = (startCmp > 0) || (startCmp == 0 && exStartOffset > newEndOffset) ||
                (endCmp < 0) || (endCmp == 0 && exEndOffset < newStartOffset)

        if (!isDisjoint) {
            iterator.remove()
            if (existing.note != null && finalNote == null) finalNote = existing.note
            val unionStartCmp = comparePaths(finalStartPath, exStartPath)
            if (unionStartCmp > 0 || (unionStartCmp == 0 && finalStartOffset > exStartOffset)) {
                finalStartPath = exStartPath
                finalStartOffset = exStartOffset
            }
            val unionEndCmp = comparePaths(finalEndPath, exEndPath)
            if (unionEndCmp < 0 || (unionEndCmp == 0 && finalEndOffset < exEndOffset)) {
                finalEndPath = exEndPath
                finalEndOffset = exEndOffset
            }
            if (existing.text.length > finalText.length) finalText = existing.text
        }
    }

    val finalCfi = "$finalStartPath:$finalStartOffset|$finalEndPath:$finalEndOffset"
    currentList.add(UserHighlight(
        cfi = finalCfi,
        text = finalText,
        color = newColor,
        chapterIndex = chapterIndex,
        note = finalNote
    ))
    return finalCfi
}

// --- UI Components ---

@Composable
fun BookmarkButton(
    isBookmarked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(48.dp)
            .height(48.dp)
            .clip(RectangleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = isBookmarked,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Icon(
                painter = painterResource(id = R.drawable.bookmark),
                contentDescription = stringResource(R.string.content_desc_bookmark_icon),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun SpectrumButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 32.dp
) {
    val rainbowColors = listOf(
        Color.Red, Color(0xFFFF7F00), Color.Yellow, Color.Green,
        Color.Blue, Color(0xFF4B0082), Color(0xFF8B00FF)
    )

    Box(
        modifier = modifier
            .size(size)
            .background(
                brush = Brush.sweepGradient(rainbowColors),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

@Composable
fun PaletteManagerDialog(
    currentPalette: List<HighlightColor>,
    onSave: (List<HighlightColor>) -> Unit,
    onDismiss: () -> Unit
) {
    var tempPalette by remember { mutableStateOf(currentPalette.toMutableList()) }
    var selectedSlotIndex by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_customize_palette), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.palette_tap_slot_to_edit), style = MaterialTheme.typography.bodySmall)
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tempPalette.forEachIndexed { index, colorEnum ->
                        val isSelected = index == selectedSlotIndex
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .background(colorEnum.color, CircleShape)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clip(CircleShape)
                                .clickable { selectedSlotIndex = index }
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(R.string.content_desc_selected_slot),
                                    tint = if (colorEnum == HighlightColor.WHITE) Color.Black else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // 2. Bottom Grid: Available Colors
                Text(stringResource(R.string.palette_select_color_for_slot), style = MaterialTheme.typography.bodySmall)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.height(200.dp)
                ) {
                    items(HighlightColor.entries) { colorOption ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(colorOption.color, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), CircleShape)
                                .clickable {
                                    val newList = tempPalette.toMutableList()
                                    newList[selectedSlotIndex] = colorOption
                                    tempPalette = newList
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(tempPalette) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationBottomSheet(
    highlight: UserHighlight,
    effectiveBg: Color,
    effectiveText: Color,
    activeHighlightPalette: List<HighlightColor>,
    onColorChange: (HighlightColor) -> Unit,
    onOpenPaletteManager: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onDictionary: () -> Unit,
    onTranslate: () -> Unit,
    onSearch: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var noteText by remember { mutableStateOf(highlight.note ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = effectiveBg, // Matches user theme
        contentColor = effectiveText,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            // Top: Highlight Colors
            HighlightColorRow(
                activeHighlightPalette = activeHighlightPalette,
                selectedColor = highlight.color,
                onColorSelect = onColorChange,
                onOpenPaletteManager = onOpenPaletteManager,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Middle: Elegant Highlight Snippet Card
            Surface(
                color = highlight.color.color.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, highlight.color.color.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    // Left colored accent bar
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .fillMaxHeight()
                            .background(highlight.color.color)
                    )
                    Text(
                        text = "\"${highlight.text}\"",
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                        maxLines = 4,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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
                BottomSheetToolButton(icon = R.drawable.copy, label = "Copy", onClick = onCopy, effectiveText = effectiveText)
                BottomSheetToolButton(icon = R.drawable.dictionary, label = "Dict", onClick = onDictionary, effectiveText = effectiveText)
                BottomSheetToolButton(icon = R.drawable.translate, label = "Translate", onClick = onTranslate, effectiveText = effectiveText)
                BottomSheetToolButton(icon = R.drawable.search, label = "Search", onClick = onSearch, effectiveText = effectiveText)
            }

            Spacer(Modifier.height(16.dp))

            // Note TextField
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                placeholder = { Text("Add a note...", color = effectiveText.copy(alpha = 0.5f)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                maxLines = 5,
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

            Spacer(Modifier.height(24.dp))

            // Bottom Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete")
                }
                Button(
                    onClick = { onSave(noteText) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Save Note")
                }
            }
        }
    }
}

@Composable
private fun BottomSheetToolButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    effectiveText: Color
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = label,
            tint = effectiveText.copy(alpha = 0.8f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = effectiveText.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun PaginatedTextSelectionMenu(
    onCopy: () -> Unit,
    onSelectAll: (() -> Unit)?,
    onDictionary: () -> Unit,
    onTranslate: () -> Unit,
    onSearch: () -> Unit,
    onHighlight: ((HighlightColor) -> Unit)?,
    onNote: (() -> Unit)? = null,
    onDelete: (() -> Unit)?,
    onTts: (() -> Unit)?,
    @Suppress("unused") isProUser: Boolean,
    @Suppress("unused") isOss: Boolean,
    activeHighlightPalette: List<HighlightColor> = emptyList(),
    onOpenPaletteManager: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.width(IntrinsicSize.Max).widthIn(min = 200.dp)) {
            if (onHighlight != null) {
                HighlightColorRow(
                    activeHighlightPalette = activeHighlightPalette,
                    selectedColor = null,
                    onColorSelect = onHighlight,
                    onOpenPaletteManager = onOpenPaletteManager
                )
                HorizontalDivider()
            }

            val actions = mutableListOf<MenuActionItem>()
            actions.add(MenuActionItem(iconRes = R.drawable.copy, label = "Copy", onClick = onCopy))
            if (onTts != null) {
                actions.add(MenuActionItem(imageVector = Icons.AutoMirrored.Filled.VolumeUp, label = "Speak", onClick = onTts))
            }
            actions.add(MenuActionItem(iconRes = R.drawable.dictionary, label = "Dict", onClick = onDictionary))
            actions.add(MenuActionItem(iconRes = R.drawable.translate, label = "Translate", onClick = onTranslate))
            actions.add(MenuActionItem(iconRes = R.drawable.search, label = "Search", onClick = onSearch))

            if (onNote != null) {
                actions.add(MenuActionItem(imageVector = Icons.Default.Edit, label = "Note", onClick = onNote))
            }

            if (onSelectAll != null) {
                actions.add(MenuActionItem(iconRes = R.drawable.select_all, label = "Select All", onClick = onSelectAll))
            }
            if (onDelete != null) {
                actions.add(MenuActionItem(imageVector = Icons.Default.Delete, label = "Remove", onClick = onDelete, isError = true))
            }

            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                actions.chunked(3).forEach { rowActions ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowActions.forEach { action ->
                            val tint = if (action.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            Column(
                                modifier = Modifier
                                    .width(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { action.onClick() }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (action.imageVector != null) {
                                    Icon(imageVector = action.imageVector, contentDescription = action.label, tint = tint, modifier = Modifier.size(24.dp))
                                } else if (action.iconRes != null) {
                                    Icon(painter = painterResource(id = action.iconRes), contentDescription = action.label, tint = tint, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = action.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
                            }
                        }
                        repeat(3 - rowActions.size) {
                            Spacer(modifier = Modifier.width(64.dp))
                        }
                    }
                }
            }
        }
    }
}

private class MenuActionItem(
    val iconRes: Int? = null,
    val imageVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val label: String,
    val onClick: () -> Unit,
    val isError: Boolean = false
)

@Composable
fun HighlightColorRow(
    modifier: Modifier = Modifier,
    activeHighlightPalette: List<HighlightColor>,
    selectedColor: HighlightColor? = null,
    onColorSelect: (HighlightColor) -> Unit,
    onOpenPaletteManager: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .padding(vertical = 12.dp, horizontal = 12.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        activeHighlightPalette.forEach { colorEnum ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(32.dp)
                    .clip(CircleShape) // 1. Clip shape for ripple
                    .background(colorEnum.color) // 2. Apply background
                    .clickable {
                        Timber.d("HighlightColorRow: Color clicked -> ${colorEnum.name}")
                        onColorSelect(colorEnum)
                    } // 3. Add clickable (ripple)
                    .border( // 4. Add border on top
                        width = if (selectedColor == colorEnum) 3.dp else 1.dp,
                        color = if (selectedColor == colorEnum) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha=0.3f),
                        shape = CircleShape
                    )
            ) {
                if (selectedColor == colorEnum) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = if (colorEnum == HighlightColor.WHITE || colorEnum == HighlightColor.YELLOW) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (onOpenPaletteManager != null) {
            Spacer(modifier = Modifier.width(8.dp))
            SpectrumButton(
                onClick = onOpenPaletteManager,
                size = 32.dp
            )
        }
    }
}

@Composable
fun PaginatedTextSelectionMenu(
    onCopy: () -> Unit,
    onSelectAll: (() -> Unit)?,
    onDictionary: () -> Unit,
    onTranslate: () -> Unit,
    onSearch: () -> Unit,
    onHighlight: ((HighlightColor) -> Unit)?,
    onNote: (() -> Unit)? = null,
    onDelete: (() -> Unit)?,
    onTts: (() -> Unit)?,
    @Suppress("unused") isProUser: Boolean,
    @Suppress("unused") isOss: Boolean,
    activeHighlightPalette: List<HighlightColor> = emptyList(),
    onOpenPaletteManager: (() -> Unit)? = null,
    existingNote: String? = null,
    selectedColor: HighlightColor? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.width(IntrinsicSize.Max).widthIn(min = 200.dp)) {
            // 1. Colors Row
            if (onHighlight != null) {
                HighlightColorRow(
                    activeHighlightPalette = activeHighlightPalette,
                    selectedColor = selectedColor,
                    onColorSelect = onHighlight,
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
                                contentDescription = "Note",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Note",
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
            actions.add(MenuActionItem(iconRes = R.drawable.copy, label = "Copy", onClick = onCopy))
            if (onTts != null) {
                actions.add(MenuActionItem(imageVector = Icons.AutoMirrored.Filled.VolumeUp, label = "Speak", onClick = onTts))
            }
            actions.add(MenuActionItem(iconRes = R.drawable.dictionary, label = "Dict", onClick = onDictionary))
            actions.add(MenuActionItem(iconRes = R.drawable.translate, label = "Translate", onClick = onTranslate))
            actions.add(MenuActionItem(iconRes = R.drawable.search, label = "Search", onClick = onSearch))

            if (onNote != null) {
                val noteLabel = if (existingNote.isNullOrBlank()) "Note" else "Edit"
                actions.add(MenuActionItem(imageVector = Icons.Default.Edit, label = noteLabel, onClick = onNote))
            }

            if (onSelectAll != null) {
                actions.add(MenuActionItem(iconRes = R.drawable.select_all, label = "Select All", onClick = onSelectAll))
            }
            if (onDelete != null) {
                actions.add(MenuActionItem(imageVector = Icons.Default.Delete, label = "Remove", onClick = onDelete, isError = true))
            }

            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                actions.chunked(3).forEach { rowActions ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowActions.forEach { action ->
                            val tint = if (action.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            Column(
                                modifier = Modifier
                                    .width(64.dp)
                                    .clickable { action.onClick() }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (action.imageVector != null) {
                                    Icon(imageVector = action.imageVector, contentDescription = action.label, tint = tint, modifier = Modifier.size(24.dp))
                                } else if (action.iconRes != null) {
                                    Icon(painter = painterResource(id = action.iconRes), contentDescription = action.label, tint = tint, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = action.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
                            }
                        }
                        repeat(3 - rowActions.size) {
                            Spacer(modifier = Modifier.width(64.dp))
                        }
                    }
                }
            }
        }
    }
}