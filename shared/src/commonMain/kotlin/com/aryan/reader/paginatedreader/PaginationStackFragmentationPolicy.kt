package com.aryan.reader.paginatedreader

/**
 * Fragmentation plan for a vertically stacked list of blocks that share one height budget
 * (the children of a flex container, or the content of a table cell).
 *
 * The plan is computed from caller-supplied measurements so the Android ContentBlock engine
 * and the shared SemanticBlock engine fragment oversized boxes identically.
 */
sealed interface PaginationStackFragmentationPlan {
    /** Every child fits within the budget; the caller should treat the list as fitting. */
    data object FitsEntirely : PaginationStackFragmentationPlan

    /**
     * The list overflows and can be fragmented.
     *
     * @property headCount number of whole children placed before the boundary.
     * @property splitChildIndex index of the child whose content was itself fragmented across
     *   the boundary, or null when the boundary falls cleanly between children.
     */
    data class Fragmented(val headCount: Int, val splitChildIndex: Int?) : PaginationStackFragmentationPlan

    /** No progress is possible: even child 0 (plus its leading gap) cannot be placed or fragmented. */
    data object NothingFits : PaginationStackFragmentationPlan
}

/**
 * Walks a stacked child list and decides where it fragments under [availableHeightPx].
 *
 * Whole children are placed while they fit. The first overflowing child is offered to
 * [childFragmentFits] (together with the leftover space) so a tall paragraph, nested
 * container, or table row can be split in place; if fragmentation is refused the plan
 * falls back to a clean break before that child. A clean break is only possible after
 * at least one placed child, otherwise the plan is [PaginationStackFragmentationPlan.NothingFits].
 *
 * @param contentHeightsPx rendered height of each child excluding its outer margins.
 * @param collapsedGapsPx collapsed vertical margin between the previous child and this one
 *   (zero-based lists such as flex children simply supply zeros).
 * @param trailingBottomMarginsPx bottom margin of each child; only the last child's value is
 *   significant and mirrors how stacked content height is measured.
 * @param childCanFragment whether the child's content can participate in a mid-split at all.
 * @param childFragmentHeadFits whether the child can yield a head fragment that occupies at
 *   most the supplied leftover height. Called at most once per child.
 */
suspend fun planPaginationStackFragmentation(
    contentHeightsPx: List<Int>,
    collapsedGapsPx: List<Int>,
    trailingBottomMarginsPx: List<Int>,
    availableHeightPx: Int,
    childCanFragment: suspend (index: Int) -> Boolean,
    childFragmentHeadFits: suspend (index: Int, leftoverHeightPx: Int) -> Boolean
): PaginationStackFragmentationPlan {
    if (contentHeightsPx.isEmpty() || availableHeightPx <= 0) {
        return PaginationStackFragmentationPlan.NothingFits
    }
    val lastIndex = contentHeightsPx.lastIndex
    var consumedPx = 0
    for (index in contentHeightsPx.indices) {
        val gapPx = collapsedGapsPx[index].coerceAtLeast(0)
        val neededPx = consumedPx +
            gapPx +
            contentHeightsPx[index].coerceAtLeast(0) +
            if (index == lastIndex) trailingBottomMarginsPx[index].coerceAtLeast(0) else 0
        if (neededPx <= availableHeightPx) {
            consumedPx += gapPx + contentHeightsPx[index].coerceAtLeast(0)
            continue
        }
        val leftoverPx = (availableHeightPx - consumedPx - gapPx).coerceAtLeast(0)
        if (childCanFragment(index) && childFragmentHeadFits(index, leftoverPx)) {
            return PaginationStackFragmentationPlan.Fragmented(
                headCount = index,
                splitChildIndex = index
            )
        }
        if (index == 0) {
            return PaginationStackFragmentationPlan.NothingFits
        }
        return PaginationStackFragmentationPlan.Fragmented(headCount = index, splitChildIndex = null)
    }
    return PaginationStackFragmentationPlan.FitsEntirely
}
