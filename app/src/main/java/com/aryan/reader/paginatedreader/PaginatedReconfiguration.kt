package com.aryan.reader.paginatedreader

internal fun resolvePaginatedReconfigurationAnchor(
    currentPageLocator: Locator?,
    fallbackLocator: Locator?
): Locator? = currentPageLocator ?: fallbackLocator
