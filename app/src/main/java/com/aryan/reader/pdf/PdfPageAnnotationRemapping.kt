package com.aryan.reader.pdf

import com.aryan.reader.pdf.data.PdfAnnotation
import com.aryan.reader.pdf.data.PdfTextBox
import com.aryan.reader.pdf.data.VirtualPage
import org.json.JSONArray
import org.json.JSONObject

internal fun remapPdfAnnotationsForLayoutChange(
    currentLayout: List<VirtualPage>,
    updatedLayout: List<VirtualPage>,
    annotations: Map<Int, List<PdfAnnotation>>
): Map<Int, List<PdfAnnotation>> {
    if (annotations.isEmpty()) return emptyMap()

    val mapping = buildPdfPageIndexMapping(
        currentLayout = currentLayout,
        updatedLayout = updatedLayout,
        sourcePageIndices = annotations.keys
    )
    val remapped = linkedMapOf<Int, MutableList<PdfAnnotation>>()

    annotations.toSortedMap().forEach { (sourcePageIndex, pageAnnotations) ->
        val targetPageIndex = mapping[sourcePageIndex] ?: return@forEach
        pageAnnotations.forEach { annotation ->
            remapped.getOrPut(targetPageIndex) { mutableListOf() }
                .add(annotation.copy(pageIndex = targetPageIndex))
        }
    }

    return remapped.mapValues { (_, pageAnnotations) -> pageAnnotations.toList() }
}

internal fun remapPdfTextBoxesForLayoutChange(
    currentLayout: List<VirtualPage>,
    updatedLayout: List<VirtualPage>,
    textBoxes: List<PdfTextBox>
): List<PdfTextBox> {
    if (textBoxes.isEmpty()) return emptyList()

    val mapping = buildPdfPageIndexMapping(
        currentLayout = currentLayout,
        updatedLayout = updatedLayout,
        sourcePageIndices = textBoxes.map { it.pageIndex }
    )

    return textBoxes.mapNotNull { box ->
        mapping[box.pageIndex]?.let { targetPageIndex ->
            box.copy(pageIndex = targetPageIndex)
        }
    }
}

internal fun remapPdfUserHighlightsForLayoutChange(
    currentLayout: List<VirtualPage>,
    updatedLayout: List<VirtualPage>,
    highlights: List<PdfUserHighlight>
): List<PdfUserHighlight> {
    if (highlights.isEmpty()) return emptyList()

    val mapping = buildPdfPageIndexMapping(
        currentLayout = currentLayout,
        updatedLayout = updatedLayout,
        sourcePageIndices = highlights.map { it.pageIndex }
    )

    return highlights.mapNotNull { highlight ->
        mapping[highlight.pageIndex]?.let { targetPageIndex ->
            highlight.copy(pageIndex = targetPageIndex)
        }
    }
}

internal fun remapPdfHistoryActionsForLayoutChange(
    currentLayout: List<VirtualPage>,
    updatedLayout: List<VirtualPage>,
    actions: List<HistoryAction>
): List<HistoryAction> {
    if (actions.isEmpty()) return emptyList()

    return actions.mapNotNull { action ->
        when (action) {
            is HistoryAction.Add -> {
                val mapping = buildPdfPageIndexMapping(
                    currentLayout = currentLayout,
                    updatedLayout = updatedLayout,
                    sourcePageIndices = listOf(action.pageIndex)
                )
                val targetPageIndex = mapping[action.pageIndex] ?: return@mapNotNull null
                action.copy(
                    pageIndex = targetPageIndex,
                    annotation = action.annotation.copy(pageIndex = targetPageIndex)
                )
            }
            is HistoryAction.Remove -> {
                val remappedItems = remapPdfAnnotationsForLayoutChange(
                    currentLayout = currentLayout,
                    updatedLayout = updatedLayout,
                    annotations = action.items
                )
                remappedItems.takeIf { it.isNotEmpty() }?.let(HistoryAction::Remove)
            }
        }
    }
}

internal fun remapPdfBookmarksJsonForLayoutChange(
    currentLayout: List<VirtualPage>,
    updatedLayout: List<VirtualPage>,
    currentBookmarksJson: String
): String {
    if (currentBookmarksJson.isBlank()) return "[]"

    val jsonArray = JSONArray(currentBookmarksJson)
    val sourcePageIndices = buildList {
        for (i in 0 until jsonArray.length()) {
            val pageIndex = jsonArray.optJSONObject(i)?.optInt("pageIndex", Int.MIN_VALUE)
            if (pageIndex != null && pageIndex != Int.MIN_VALUE) add(pageIndex)
        }
    }
    val mapping = buildPdfPageIndexMapping(
        currentLayout = currentLayout,
        updatedLayout = updatedLayout,
        sourcePageIndices = sourcePageIndices
    )
    val newArray = JSONArray()

    for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        val sourcePageIndex = obj.optInt("pageIndex", Int.MIN_VALUE)
        val targetPageIndex = mapping[sourcePageIndex] ?: continue
        val newObj = JSONObject(obj.toString())
        newObj.put("pageIndex", targetPageIndex)
        newObj.put("totalPages", updatedLayout.size)
        newArray.put(newObj)
    }

    return newArray.toString()
}

internal fun buildPdfPageIndexMapping(
    currentLayout: List<VirtualPage>,
    updatedLayout: List<VirtualPage>,
    sourcePageIndices: Iterable<Int>
): Map<Int, Int> {
    val distinctSourcePageIndices = sourcePageIndices.toSet()
    if (distinctSourcePageIndices.isEmpty()) return emptyMap()

    val minimumCurrentPageCount = maxOf(
        currentLayout.size,
        distinctSourcePageIndices.maxOrNull()?.plus(1) ?: 0
    )
    val effectiveCurrentLayout = currentLayout.withDefaultPdfPagesUntil(minimumCurrentPageCount)
    val currentTokens = effectiveCurrentLayout.toOccurrenceTokens()
    val updatedTokenIndices = updatedLayout.toOccurrenceTokens()
        .mapIndexed { index, token -> token to index }
        .toMap()

    return distinctSourcePageIndices.mapNotNull { sourcePageIndex ->
        val token = currentTokens.getOrNull(sourcePageIndex) ?: return@mapNotNull null
        val targetPageIndex = updatedTokenIndices[token] ?: return@mapNotNull null
        sourcePageIndex to targetPageIndex
    }.toMap()
}

private fun List<VirtualPage>.withDefaultPdfPagesUntil(pageCount: Int): List<VirtualPage> {
    if (size >= pageCount) return this
    return this + (size until pageCount).map { VirtualPage.PdfPage(it) }
}

private fun List<VirtualPage>.toOccurrenceTokens(): List<VirtualPageOccurrenceToken> {
    val seen = mutableMapOf<VirtualPageKey, Int>()
    return map { page ->
        val key = page.toVirtualPageKey()
        val occurrence = seen.getOrDefault(key, 0)
        seen[key] = occurrence + 1
        VirtualPageOccurrenceToken(key, occurrence)
    }
}

private fun VirtualPage.toVirtualPageKey(): VirtualPageKey {
    return when (this) {
        is VirtualPage.PdfPage -> VirtualPageKey.Pdf(pdfIndex)
        is VirtualPage.BlankPage -> VirtualPageKey.Blank(id)
    }
}

private data class VirtualPageOccurrenceToken(
    val key: VirtualPageKey,
    val occurrence: Int
)

private sealed interface VirtualPageKey {
    data class Pdf(val pdfIndex: Int) : VirtualPageKey
    data class Blank(val id: String) : VirtualPageKey
}
