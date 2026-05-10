package com.aryan.reader.shared.reader

data class ReaderJumpHistory(
    val locators: List<ReaderLocator> = emptyList(),
    val cursor: Int = -1,
    val maxEntries: Int = 21
) {
    val backLocator: ReaderLocator? get() = locators.getOrNull(cursor - 1)
    val forwardLocator: ReaderLocator? get() = locators.getOrNull(cursor + 1)
    val hasJumpTargets: Boolean get() = backLocator != null || forwardLocator != null

    fun record(
        currentLocator: ReaderLocator?,
        targetLocator: ReaderLocator?,
        chapterCount: Int
    ): ReaderJumpHistory {
        val current = currentLocator?.takeIf { it.isValidJumpLocator(chapterCount) } ?: return this
        val target = targetLocator?.takeIf { it.isValidJumpLocator(chapterCount) } ?: return this
        if (current.hasSameJumpLocation(target)) return this

        val pruned = pruned(chapterCount)
        val nextLocators = pruned.locators.toMutableList()
        var nextCursor = pruned.cursor

        while (nextLocators.lastIndex > nextCursor) {
            nextLocators.removeAt(nextLocators.lastIndex)
        }

        if (nextCursor > 0 && nextLocators.getOrNull(nextCursor - 1)?.hasSameJumpLocation(current) == true) {
            nextLocators[nextCursor] = target
            return copy(
                locators = nextLocators,
                cursor = nextCursor
            ).bounded()
        }

        if (nextCursor == -1 || nextLocators.getOrNull(nextCursor)?.hasSameJumpLocation(current) != true) {
            nextLocators += current
            nextCursor = nextLocators.lastIndex
        }

        if (nextLocators.lastOrNull()?.hasSameJumpLocation(target) != true) {
            nextLocators += target
            nextCursor = nextLocators.lastIndex
        }

        return copy(
            locators = nextLocators,
            cursor = nextCursor
        ).bounded()
    }

    fun pruned(chapterCount: Int): ReaderJumpHistory {
        if (chapterCount <= 0) return clear()
        val nextLocators = locators.toMutableList()
        var nextCursor = cursor
        var index = nextLocators.lastIndex
        while (index >= 0) {
            if (!nextLocators[index].isValidJumpLocator(chapterCount)) {
                nextLocators.removeAt(index)
                if (nextCursor >= index) nextCursor--
            }
            index--
        }
        return copy(
            locators = nextLocators,
            cursor = nextCursor.coerceIn(-1, nextLocators.lastIndex)
        ).bounded()
    }

    fun stepBack(): ReaderJumpHistory {
        return if (backLocator == null) this else copy(cursor = (cursor - 1).coerceAtLeast(0))
    }

    fun stepForward(): ReaderJumpHistory {
        return if (forwardLocator == null) this else copy(cursor = (cursor + 1).coerceAtMost(locators.lastIndex))
    }

    fun clear(): ReaderJumpHistory = copy(locators = emptyList(), cursor = -1)

    private fun bounded(): ReaderJumpHistory {
        val safeMaxEntries = maxEntries.coerceAtLeast(2)
        if (locators.size <= safeMaxEntries) {
            return copy(cursor = cursor.coerceIn(-1, locators.lastIndex))
        }
        val overflow = locators.size - safeMaxEntries
        return copy(
            locators = locators.drop(overflow),
            cursor = (cursor - overflow).coerceIn(-1, locators.size - overflow - 1)
        )
    }
}

fun ReaderLocator.hasSameJumpLocation(other: ReaderLocator): Boolean {
    return jumpLocationKey() == other.jumpLocationKey()
}

private fun ReaderLocator.isValidJumpLocator(chapterCount: Int): Boolean {
    if (chapterCount <= 0) return false
    val chapter = chapterIndex
    return chapter == null || chapter in 0 until chapterCount
}

private fun ReaderLocator.jumpLocationKey(): String {
    val stableCfi = cfi?.takeIf { it.isNotBlank() }
    if (stableCfi != null) {
        return listOf(
            chapterIndex?.toString().orEmpty(),
            chapterId.orEmpty(),
            href.orEmpty(),
            startOffset?.toString().orEmpty(),
            endOffset?.toString().orEmpty(),
            stableCfi
        ).joinToString("|")
    }
    return listOf(
        chapterIndex?.toString().orEmpty(),
        chapterId.orEmpty(),
        href.orEmpty(),
        pageIndex?.toString().orEmpty(),
        startOffset?.toString().orEmpty(),
        endOffset?.toString().orEmpty(),
        cfi.orEmpty()
    ).joinToString("|")
}
