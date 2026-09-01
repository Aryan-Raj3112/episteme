package com.aryan.reader.shared.ios

import com.aryan.reader.shared.ui.SharedMobileUnifiedLibrarySelectionCapabilities

internal fun iosUnifiedLibrarySelectionCapabilities(): SharedMobileUnifiedLibrarySelectionCapabilities =
    SharedMobileUnifiedLibrarySelectionCapabilities(
        selectionActions = true,
        selectAll = true,
        pin = true,
        addToShelf = true,
        tag = true,
        info = true,
        save = true,
        share = true,
        exportAnnotations = true,
        delete = true,
    )
