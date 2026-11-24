package com.aryan.reader

import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.shared.ReaderTabKind
import com.aryan.reader.shared.readerTabKind
import com.aryan.reader.shared.ui.SharedOpenTab
import kotlin.math.roundToInt

/**
 * The library tab switcher's list: text tabs first, then fixed-layout tabs, each in tab order.
 *
 * The two lists come from the library projection, which already resolves every open tab id
 * against the library, so this only adds presentation state. The readers keep separate active
 * tabs, so each group can have its own highlighted row.
 */
internal fun ReaderScreenState.homeOpenTabs(
    usePdfFileNameAsDisplayName: Boolean = this.usePdfFileNameAsDisplayName,
): List<SharedOpenTab> =
    textOpenTabs.toSharedOpenTabs(
        activeBookId = tabState.text.activeBookId,
        usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
    ) + openTabs.toSharedOpenTabs(
        activeBookId = tabState.fixedLayout.activeBookId,
        usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
    )

private fun List<RecentFileItem>.toSharedOpenTabs(
    activeBookId: String?,
    usePdfFileNameAsDisplayName: Boolean,
): List<SharedOpenTab> = mapNotNull { item ->
    val kind = item.type.readerTabKind() ?: return@mapNotNull null
    SharedOpenTab(
        bookId = item.bookId,
        title = item.cardTitle(usePdfFileNameAsDisplayName),
        kind = kind,
        isActive = item.bookId == activeBookId,
        chapterNumber = if (kind == ReaderTabKind.TEXT) {
            item.lastChapterIndex?.plus(1)?.takeIf { it > 0 }
        } else {
            null
        },
        pageNumber = if (kind == ReaderTabKind.FIXED_LAYOUT) {
            item.lastPage?.plus(1)?.takeIf { it > 0 }
        } else {
            null
        },
        progressPercent = item.progressPercentage
            ?.coerceIn(0f, 100f)
            ?.roundToInt()
            ?.takeIf { it > 0 },
    )
}
