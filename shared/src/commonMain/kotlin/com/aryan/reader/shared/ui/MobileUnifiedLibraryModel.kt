package com.aryan.reader.shared.ui

/**
 * Shared state contract for the unified mobile library chrome.
 *
 * The Android and iOS hosts can keep their own persistence and side effects,
 * while the visible section/filter/search/list behavior is described by this
 * platform-neutral model.
 */
data class MobileUnifiedLibraryViewState(
    val section: MobileUnifiedLibrarySection = MobileUnifiedLibrarySection.HOME,
    val selectedShelfId: String? = null,
    val filter: MobileUnifiedLibraryFilter = MobileUnifiedLibraryFilter.ALL,
    val query: String = "",
    val searchActive: Boolean = false,
    val useListView: Boolean = false,
) {
    val showContinueReading: Boolean
        get() = section == MobileUnifiedLibrarySection.HOME &&
            filter == MobileUnifiedLibraryFilter.ALL &&
            query.isBlank() &&
            !searchActive

    val showSearchResults: Boolean
        get() = section == MobileUnifiedLibrarySection.HOME && searchActive
}

/** Generic book/shelf-independent adapter consumed by the shared Android-style UI. */
data class MobileUnifiedLibraryModel<T>(
    val viewState: MobileUnifiedLibraryViewState,
    val visibleBooks: List<T>,
    val continueReading: T?,
) {
    val showContinueReading: Boolean get() = viewState.showContinueReading
    val showSearchResults: Boolean get() = viewState.showSearchResults
}

fun <T> mobileUnifiedLibraryModel(
    viewState: MobileUnifiedLibraryViewState,
    visibleBooks: List<T>,
    continueReading: T?,
): MobileUnifiedLibraryModel<T> = MobileUnifiedLibraryModel(
    viewState = viewState,
    visibleBooks = visibleBooks,
    continueReading = continueReading,
)
