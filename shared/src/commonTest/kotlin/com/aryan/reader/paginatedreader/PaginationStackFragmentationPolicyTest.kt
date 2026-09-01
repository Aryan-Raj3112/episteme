package com.aryan.reader.paginatedreader

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PaginationStackFragmentationPolicyTest {

    private suspend fun plan(
        contentHeights: List<Int>,
        collapsedGaps: List<Int> = List(contentHeights.size) { 0 },
        trailingBottomMargins: List<Int> = List(contentHeights.size) { 0 },
        availableHeight: Int,
        fragmentableIndices: Set<Int> = emptySet(),
        fragmentableLeftovers: MutableMap<Int, Int> = mutableMapOf(),
        fragmentableResult: Boolean = true
    ): PaginationStackFragmentationPlan = planPaginationStackFragmentation(
        contentHeightsPx = contentHeights,
        collapsedGapsPx = collapsedGaps,
        trailingBottomMarginsPx = trailingBottomMargins,
        availableHeightPx = availableHeight,
        childCanFragment = { it in fragmentableIndices },
        childFragmentHeadFits = { index, leftoverPx ->
            fragmentableLeftovers[index] = leftoverPx
            fragmentableResult
        }
    )

    @Test
    fun `fitsEntirelyWhenEveryChildIncludingTrailingBottomMarginFits`() = runTest {
        val plan = plan(
            contentHeights = listOf(60, 30),
            collapsedGaps = listOf(0, 5),
            trailingBottomMargins = listOf(10, 5),
            availableHeight = 100
        )
        assertEquals(PaginationStackFragmentationPlan.FitsEntirely, plan)
    }

    @Test
    fun `fragmentsFirstChildWhenItAloneOverflowsAndIsFragmentable`() = runTest {
        val leftovers = mutableMapOf<Int, Int>()
        val plan = plan(
            contentHeights = listOf(400, 100),
            availableHeight = 300,
            fragmentableIndices = setOf(0, 1),
            fragmentableLeftovers = leftovers
        )
        assertEquals(PaginationStackFragmentationPlan.Fragmented(headCount = 0, splitChildIndex = 0), plan)
        assertEquals(300, leftovers[0])
    }

    @Test
    fun `fragmentsOverflowingChildBeforeFallingBackToCleanBreak`() = runTest {
        val leftovers = mutableMapOf<Int, Int>()
        val plan = plan(
            contentHeights = listOf(100, 300, 50),
            availableHeight = 200,
            fragmentableIndices = setOf(1),
            fragmentableLeftovers = leftovers
        )
        assertEquals(PaginationStackFragmentationPlan.Fragmented(headCount = 1, splitChildIndex = 1), plan)
        assertEquals(100, leftovers[1])
    }

    @Test
    fun `fallsBackToCleanBreakBetweenChildrenWhenOverflowingChildCannotFragment`() = runTest {
        val plan = plan(
            contentHeights = listOf(100, 300, 50),
            availableHeight = 200
        )
        assertEquals(PaginationStackFragmentationPlan.Fragmented(headCount = 1, splitChildIndex = null), plan)
    }

    @Test
    fun `nothingFitsWhenFirstChildCannotBePlacedOrFragmented`() = runTest {
        val plan = plan(
            contentHeights = listOf(400, 100),
            availableHeight = 300,
            fragmentableIndices = setOf(1)
        )
        assertEquals(PaginationStackFragmentationPlan.NothingFits, plan)
    }

    @Test
    fun `fragmentFitsRefusalFallsBackToCleanBreakOrNothingFits`() = runTest {
        val refused = plan(
            contentHeights = listOf(100, 300),
            availableHeight = 200,
            fragmentableIndices = setOf(1),
            fragmentableResult = false
        )
        assertEquals(PaginationStackFragmentationPlan.Fragmented(headCount = 1, splitChildIndex = null), refused)

        val refusedAtFirst = plan(
            contentHeights = listOf(400),
            availableHeight = 200,
            fragmentableIndices = setOf(0),
            fragmentableResult = false
        )
        assertEquals(PaginationStackFragmentationPlan.NothingFits, refusedAtFirst)
    }

    @Test
    fun `collapsedGapBeforeOverflowingChildIsDeductedFromLeftover`() = runTest {
        val leftovers = mutableMapOf<Int, Int>()
        plan(
            contentHeights = listOf(40, 100),
            collapsedGaps = listOf(0, 20),
            availableHeight = 100,
            fragmentableIndices = setOf(1),
            fragmentableLeftovers = leftovers
        )
        assertEquals(40, leftovers[1])
    }

    @Test
    fun `trailingBottomMarginOfLastChildCountsAgainstBudget`() = runTest {
        val fits = plan(
            contentHeights = listOf(60),
            trailingBottomMargins = listOf(40),
            availableHeight = 100
        )
        assertEquals(PaginationStackFragmentationPlan.FitsEntirely, fits)

        val overflows = plan(
            contentHeights = listOf(60),
            trailingBottomMargins = listOf(41),
            availableHeight = 100,
            fragmentableIndices = setOf(0),
            fragmentableResult = false
        )
        assertEquals(PaginationStackFragmentationPlan.NothingFits, overflows)
    }

    @Test
    fun `emptyListOrNonPositiveBudgetNeverFits`() = runTest {
        assertEquals(
            PaginationStackFragmentationPlan.NothingFits,
            plan(contentHeights = emptyList(), availableHeight = 100)
        )
        assertEquals(
            PaginationStackFragmentationPlan.NothingFits,
            plan(contentHeights = listOf(10), availableHeight = 0)
        )
    }
}
