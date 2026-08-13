package com.aryan.reader.paginatedreader

internal fun resolvePaginatedReconfigurationAnchor(
    currentPageLocator: Locator?,
    fallbackLocator: Locator?
): Locator? = com.aryan.reader.shared.reader.resolveSharedPaginatedReconfigurationAnchor(
    currentPageAnchor = currentPageLocator,
    fallbackAnchor = fallbackLocator,
)
