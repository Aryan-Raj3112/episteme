package com.aryan.reader.pdf

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legere.pdfiumandroid.api.Bookmark
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import org.json.JSONArray
import timber.log.Timber

private const val MAX_FIXED_RECURSION = 128

internal data class PdfBookmark(val pageIndex: Int, val title: String, val totalPages: Int)

internal data class TocEntry(val title: String, val pageIndex: Int, val nestLevel: Int)

/**
 * Patches the library bug where siblings are truncated due to depth-state leakage.
 */
suspend fun PdfDocumentKt.getFixedTableOfContents(): List<Bookmark> {
    val tag = "PdfTocFix"
    Timber.tag(tag).i("Starting Pure Reflection Traversal...")

    return try {
        // 1. Get the 'document' field (PdfDocumentU) from PdfDocumentKt
        val documentField = PdfDocumentKt::class.java.getDeclaredField("document").apply { isAccessible = true }
        val docUInstance = documentField.get(this) ?: return getTableOfContents()

        // 2. Get the 'nativeDocument' field from PdfDocumentU
        val nativeDocField = docUInstance.javaClass.getDeclaredField("nativeDocument").apply { isAccessible = true }
        val nativeDocInstance = nativeDocField.get(docUInstance) ?: return getTableOfContents()

        // 3. Get the native pointer (long) from PdfDocumentU
        val ptrField = docUInstance.javaClass.getDeclaredField("mNativeDocPtr").apply { isAccessible = true }
        val mNativeDocPtr = ptrField.get(docUInstance) as Long

        // 4. Look up native methods using primitive 'long' types (mandatory for JNI)
        val nClass = nativeDocInstance.javaClass
        val lp = Long::class.javaPrimitiveType!! // Shorthand for 'long'

        val getTitleM = nClass.getMethod("getBookmarkTitle", lp)
        val getDestIdxM = nClass.getMethod("getBookmarkDestIndex", lp, lp)
        val getFirstChildM = nClass.getMethod("getFirstChildBookmark", lp, lp)
        val getSiblingM = nClass.getMethod("getSiblingBookmark", lp, lp)

        val topLevel = mutableListOf<Bookmark>()
        val visited = mutableSetOf<Long>()

        /**
         * Corrected traversal: Iterative for siblings, recursive for children.
         */
        fun walk(parentList: MutableList<Bookmark>, startPtr: Long, level: Int) {
            var currentPtr = startPtr
            var itemIndex = 0

            while (currentPtr != 0L) {
                if (visited.contains(currentPtr)) break
                visited.add(currentPtr)

                val title = getTitleM.invoke(nativeDocInstance, currentPtr) as? String ?: "Untitled"
                val pageIdx = getDestIdxM.invoke(nativeDocInstance, mNativeDocPtr, currentPtr) as Long

                Timber.tag(tag).v("Lvl $level | Item $itemIndex | Ptr: 0x${java.lang.Long.toHexString(currentPtr)} | $title")

                val bookmark = Bookmark().apply {
                    this.mNativePtr = currentPtr
                    this.title = title
                    this.pageIdx = pageIdx
                }
                parentList.add(bookmark)

                // Recursive dive into children
                val firstChild = getFirstChildM.invoke(nativeDocInstance, mNativeDocPtr, currentPtr) as Long
                if (firstChild != 0L && level < MAX_FIXED_RECURSION) {
                    walk(bookmark.children, firstChild, level + 1)
                }

                // Iterative move to next sibling
                currentPtr = getSiblingM.invoke(nativeDocInstance, mNativeDocPtr, currentPtr) as Long
                itemIndex++
            }
        }

        // 5. Start from the root (Pass 0L as primitive long)
        val firstRoot = getFirstChildM.invoke(nativeDocInstance, mNativeDocPtr, 0L) as Long
        if (firstRoot != 0L) {
            walk(topLevel, firstRoot, 0)
        }

        if (topLevel.isEmpty()) {
            Timber.tag(tag).w("No items found, falling back to library.")
            getTableOfContents()
        } else {
            Timber.tag(tag).i("TOC Successfully Patched! Nodes: ${visited.size}")
            topLevel
        }
    } catch (e: Exception) {
        Timber.tag(tag).e(e, "Reflection traversal critical error.")
        this.getTableOfContents()
    }
}

internal fun flattenToc(bookmarks: List<Bookmark>, level: Int = 0): List<TocEntry> {
    Timber.tag("PdfTocDebug").d("Processing level $level with ${bookmarks.size} items")
    val entries = mutableListOf<TocEntry>()
    for ((index, bookmark) in bookmarks.withIndex()) {
        val title = bookmark.title ?: "Untitled Chapter"
        val childCount = bookmark.children.size

        Timber.tag("PdfTocDebug").d(
            "Lvl $level | Item $index: \"$title\" (Page: ${bookmark.pageIdx}) | Children: $childCount"
        )

        entries.add(
            TocEntry(
                title = title,
                pageIndex = bookmark.pageIdx.toInt(),
                nestLevel = level
            )
        )

        if (childCount > 0) {
            Timber.tag("PdfTocDebug").v("Entering children of \"$title\"")
            entries.addAll(flattenToc(bookmark.children, level + 1))
            Timber.tag("PdfTocDebug").v("Returned to Lvl $level from \"$title\"")
        }
    }
    return entries
}

internal fun loadPdfBookmarksFromJson(bookmarksJson: String?): Set<PdfBookmark> {
    if (bookmarksJson.isNullOrBlank()) return emptySet()
    return try {
        val jsonArray = JSONArray(bookmarksJson)
        (0 until jsonArray.length()).mapNotNull { i ->
            try {
                val json = jsonArray.getJSONObject(i)
                PdfBookmark(
                    pageIndex = json.getInt("pageIndex"),
                    title = json.getString("title"),
                    totalPages = json.getInt("totalPages")
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse bookmark from JSON object")
                null
            }
        }.toSet()
    } catch (e: Exception) {
        Timber.e(e, "Failed to parse bookmarks from JSON string: $bookmarksJson")
        emptySet()
    }
}

@Composable
internal fun PdfTocTreeItem(
    label: String,
    nestLevel: Int,
    isExpanded: Boolean,
    hasChildren: Boolean,
    isCurrent: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else Color.Transparent,
        label = "TocItemBackground"
    )

    val contentColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width((16 * nestLevel).dp))

        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable(enabled = hasChildren, onClick = onToggleExpand),
            contentAlignment = Alignment.Center
        ) {
            if (hasChildren) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = label,
            style = if (nestLevel == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else if (nestLevel == 0) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 16.dp)
        )
    }
}