package com.aryan.reader.pdf

import com.aryan.reader.pdf.data.PdfAnnotation
import com.aryan.reader.pdf.data.PdfTextBox
import com.aryan.reader.pdf.data.VirtualPage
import org.json.JSONArray
import org.json.JSONObject

internal fun shouldAutoPrunePdfBlankPage(
    lastPage: VirtualPage?,
    currentLastIndex: Int,
    highestRequiredTextPageIndex: Int,
    hasText: Boolean,
    hasAnnotations: Boolean,
    hasTextBoxes: Boolean,
    hasHighlights: Boolean,
    hasBookmark: Boolean,
): Boolean {
    return lastPage is VirtualPage.BlankPage &&
        !lastPage.wasManuallyAdded &&
        currentLastIndex > highestRequiredTextPageIndex &&
        !hasText &&
        !hasAnnotations &&
        !hasTextBoxes &&
        !hasHighlights &&
        !hasBookmark
}

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

    val jsonArray = runCatching { JSONArray(currentBookmarksJson) }.getOrNull()
        ?: return currentBookmarksJson
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
        val obj = jsonArray.optJSONObject(i) ?: continue
        val sourcePageIndex = obj.optInt("pageIndex", Int.MIN_VALUE)
        if (sourcePageIndex == Int.MIN_VALUE || sourcePageIndex < 0) continue

        // A page can legitimately disappear when a generated blank page is
        // contracted. Keep the bookmark instead of silently dropping it; the
        // nearest remaining display page is the only lossless representation
        // available in the legacy page-index schema.
        val targetPageIndex = mapping[sourcePageIndex]
            ?: sourcePageIndex.takeIf { updatedLayout.isNotEmpty() }
                ?.coerceAtMost(updatedLayout.lastIndex)
            ?: continue
        val newObj = JSONObject(obj.toString())
        newObj.put("pageIndex", targetPageIndex)
        if (updatedLayout.isNotEmpty()) {
            newObj.put("totalPages", updatedLayout.size)
        }
        newArray.put(newObj)
    }

    return newArray.toString()
}

internal fun buildPdfPageIndexMapping(
    currentLayout: List<VirtualPage>,
    updatedLayout: List<VirtualPage>,
    sourcePageIndices: Iterable<Int>
): Map<Int, Int> {
    return buildSharedPdfPageIndexMapping(
        currentLayout = currentLayout.map(VirtualPage::toSharedPageIdentity),
        updatedLayout = updatedLayout.map(VirtualPage::toSharedPageIdentity),
        sourcePageIndices = sourcePageIndices,
    )
}

private fun VirtualPage.toSharedPageIdentity(): PdfPageIdentity = when (this) {
    is VirtualPage.PdfPage -> PdfPageIdentity.Pdf(pdfIndex)
    is VirtualPage.BlankPage -> PdfPageIdentity.Blank(id)
}
