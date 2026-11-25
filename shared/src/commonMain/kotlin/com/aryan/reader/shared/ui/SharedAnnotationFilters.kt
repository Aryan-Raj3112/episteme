package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.CUSTOM_HIGHLIGHT_COLOR_ID_PREFIX
import com.aryan.reader.shared.epubHighlightColorTag

/**
 * Filter selection for a reader's annotation list (EPUB highlights and PDF annotations alike).
 *
 * Both readers used to offer only "All" / "With notes" chips, which cannot answer the question a
 * reader actually asks of a long list — "where are my yellow ones?" — so the list now also filters
 * by the colours the file itself uses. Notes and colour are independent dimensions and combine:
 * "with notes" plus a colour means notes on red highlights.
 *
 * Colour is compared by its RGB identity ([sharedAnnotationColorKey]) rather than the raw ARGB.
 * The PDF highlighter stores its own opacity in the stored ARGB, so the same hue saved at two
 * opacities would otherwise become two swatches that look identical to a reader.
 *
 * [matches] must be applied *during composition*, never inside `remember(highlights, filters)`.
 * Android's EPUB reader hands the drawer a `SnapshotStateList`, whose identity survives every
 * in-place add, recolour and note edit, so a remembered projection never invalidates: the list kept
 * showing the state from before the edit until the book was reopened.
 */
data class SharedAnnotationFilterState(
    val notesOnly: Boolean = false,
    val selectedColorKey: Int? = null,
) {
    val hasActiveFilters: Boolean
        get() = notesOnly || selectedColorKey != null

    fun matches(colorArgb: Int, note: String?): Boolean =
        (!notesOnly || !note.isNullOrBlank()) &&
            (selectedColorKey == null || selectedColorKey == sharedAnnotationColorKey(colorArgb))

    fun withNotesOnly(enabled: Boolean): SharedAnnotationFilterState = copy(notesOnly = enabled)

    /**
     * Select [colorKey], or clear the colour filter when it is already selected.
     *
     * Toggle rather than "select" because the swatch is the only way back to "every colour" besides
     * the All chip, and tapping the selected swatch is the gesture a reader tries first.
     */
    fun withColorToggled(colorKey: Int): SharedAnnotationFilterState = copy(
        selectedColorKey = if (selectedColorKey == colorKey) null else colorKey,
    )

    fun cleared(): SharedAnnotationFilterState = SharedAnnotationFilterState()
}

/** Alpha forced onto a filter swatch: stored highlight opacity is not the colour itself. */
private const val SHARED_ANNOTATION_OPAQUE_ALPHA: Int = -0x1000000 // 0xFF000000

/** RGB identity of an annotation colour; alpha is deliberately dropped. */
fun sharedAnnotationColorKey(argb: Int): Int = argb and 0x00FFFFFF

/** Opaque ARGB to paint a [sharedAnnotationColorKey] swatch with. */
fun sharedAnnotationColorKeyArgb(colorKey: Int): Int = colorKey or SHARED_ANNOTATION_OPAQUE_ALPHA

/**
 * The distinct colours a file's annotations use, most used first.
 *
 * Ordered by count so the swatch a reader wants most often sits closest to the edge of the drawer,
 * ties broken by colour value so the strip never reorders between two equal counts.
 */
fun sharedAnnotationColorFilterOptions(annotationColorArgbs: List<Int>): List<Int> {
    val counts = LinkedHashMap<Int, Int>()
    annotationColorArgbs.forEach { argb ->
        val key = sharedAnnotationColorKey(argb)
        counts[key] = (counts[key] ?: 0) + 1
    }
    return counts.entries
        .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
        .map { it.key }
}

/** Human/TalkBack label for a colour key: the named colour, or `#RRGGBB` for a custom one. */
@Composable
private fun sharedAnnotationColorFilterLabel(colorKey: Int): String {
    val colorId = epubHighlightColorTag(sharedAnnotationColorKeyArgb(colorKey))
    val fallback = if (colorId.startsWith(CUSTOM_HIGHLIGHT_COLOR_ID_PREFIX)) {
        "#${colorId.removePrefix(CUSTOM_HIGHLIGHT_COLOR_ID_PREFIX).uppercase()}"
    } else {
        colorId.replaceFirstChar { it.uppercase() }
    }
    return readerString("color_$colorId", fallback)
}

/**
 * Filter strip for a reader annotation list: All, With Notes, and one swatch per colour the file
 * uses.
 *
 * The swatch row only appears when the file has two or more colours — filtering by the only colour
 * present cannot change the list, so the strip would be pure noise — unless a colour is already
 * selected, in which case it stays so the selection is visible and can be tapped off again.
 *
 * Colours get their own row rather than more chips in the first one because the drawer is
 * 300–360dp wide and the notes chips plus the host's export action already leave no room for a
 * swatch to be visible without scrolling.
 *
 * Hosts keep their own "empty list" state; [SharedAnnotationFilteredEmptyState] covers the case
 * where the list is non-empty but the filters hide all of it, which is a different message.
 */
@Composable
fun SharedAnnotationFilterBar(
    filters: SharedAnnotationFilterState,
    annotationColorArgbs: List<Int>,
    onFiltersChange: (SharedAnnotationFilterState) -> Unit,
    modifier: Modifier = Modifier,
    allLabel: String = readerString("filter_all", "All"),
    withNotesLabel: String = readerString("filter_with_notes", "With Notes"),
    exportLabel: String = readerString("action_export_annotations", "Export annotations"),
    onExport: (() -> Unit)? = null,
) {
    val colorOptions = remember(annotationColorArgbs) {
        sharedAnnotationColorFilterOptions(annotationColorArgbs)
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = !filters.hasActiveFilters,
                // "All" is the reset, not "notes are off": with a colour selected the two readings
                // of the label disagree, and the chip exists precisely to clear both dimensions.
                onClick = { onFiltersChange(filters.cleared()) },
                label = { Text(allLabel) },
            )
            FilterChip(
                selected = filters.notesOnly,
                onClick = { onFiltersChange(filters.withNotesOnly(!filters.notesOnly)) },
                label = { Text(withNotesLabel) },
            )
            Spacer(Modifier.weight(1f))
            if (onExport != null) {
                TextButton(onClick = onExport) { Text(exportLabel) }
            }
        }
        // With a colour selected the strip stays visible even when the rest of the file is one
        // colour, otherwise the selected swatch could vanish and leave no visible filter state.
        if (colorOptions.size > 1 || filters.selectedColorKey != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 10.dp, end = 10.dp, bottom = 4.dp),
                // Swatches keep a 32dp visual with Material's 48dp touch target, so the gap
                // between them is mostly touch padding already.
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                colorOptions.forEach { colorKey ->
                    SharedAnnotationColorFilterSwatch(
                        colorKey = colorKey,
                        selected = filters.selectedColorKey == colorKey,
                        onClick = { onFiltersChange(filters.withColorToggled(colorKey)) },
                    )
                }
            }
        }
    }
}

/**
 * One colour swatch in the filter strip.
 *
 * Selection is a ring around the swatch plus a luminance-aware tick, matching the annotation
 * sheet's palette row (`SharedMobileEpubHighlightColorRow`) so a reader recognises the control.
 * The swatch paints the opaque colour: a PDF highlighter's stored alpha would wash the swatch out
 * against the drawer background and make two colours harder, not easier, to tell apart.
 */
@Composable
fun SharedAnnotationColorFilterSwatch(
    colorKey: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val swatch = Color(sharedAnnotationColorKeyArgb(colorKey))
    val label = sharedAnnotationColorFilterLabel(colorKey)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .testTag("AnnotationColorFilter_${epubHighlightColorTag(sharedAnnotationColorKeyArgb(colorKey))}")
            .size(32.dp)
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                },
                shape = CircleShape,
            )
            .padding(5.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(swatch),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (swatch.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * "The filters hid everything" state, with the way out.
 *
 * Distinct from a genuinely empty book: telling a reader "No highlights yet" while they have a
 * colour chip selected reads as data loss.
 */
@Composable
fun SharedAnnotationFilteredEmptyState(
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
    message: String = readerString(
        "no_annotations_match_filters",
        "No annotations match these filters",
    ),
    clearLabel: String = readerString("action_clear_filters", "Clear filters"),
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClearFilters) { Text(clearLabel) }
    }
}
