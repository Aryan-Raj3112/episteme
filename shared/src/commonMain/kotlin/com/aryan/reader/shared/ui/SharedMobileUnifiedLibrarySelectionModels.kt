package com.aryan.reader.shared.ui

/**
 * Capabilities exposed by the contextual book-selection bar in Library Beta.
 *
 * The selection bar is shared UI, while a platform decides which actions are safe to
 * expose. Keeping the capability set explicit prevents a host from accidentally
 * rendering an action without wiring its native side effect.
 */
data class SharedMobileUnifiedLibrarySelectionCapabilities(
    val selectionActions: Boolean = false,
    val selectAll: Boolean = false,
    val pin: Boolean = false,
    val addToShelf: Boolean = false,
    val tag: Boolean = false,
    val info: Boolean = false,
    val save: Boolean = false,
    val share: Boolean = false,
    val exportAnnotations: Boolean = false,
    val delete: Boolean = false,
) {
    val enabledActions: Set<SharedMobileUnifiedLibrarySelectionAction>
        get() = if (!selectionActions) {
            emptySet()
        } else {
            buildSet {
                if (selectAll) add(SharedMobileUnifiedLibrarySelectionAction.SELECT_ALL)
                if (pin) add(SharedMobileUnifiedLibrarySelectionAction.PIN)
                if (addToShelf) add(SharedMobileUnifiedLibrarySelectionAction.ADD_TO_SHELF)
                if (tag) add(SharedMobileUnifiedLibrarySelectionAction.TAG)
                if (info) add(SharedMobileUnifiedLibrarySelectionAction.INFO)
                if (save) add(SharedMobileUnifiedLibrarySelectionAction.SAVE)
                if (share) add(SharedMobileUnifiedLibrarySelectionAction.SHARE)
                if (exportAnnotations) add(SharedMobileUnifiedLibrarySelectionAction.EXPORT_ANNOTATIONS)
                if (delete) add(SharedMobileUnifiedLibrarySelectionAction.DELETE)
            }
        }
}

enum class SharedMobileUnifiedLibrarySelectionAction {
    SELECT_ALL,
    PIN,
    ADD_TO_SHELF,
    TAG,
    INFO,
    SAVE,
    SHARE,
    EXPORT_ANNOTATIONS,
    DELETE,
}
