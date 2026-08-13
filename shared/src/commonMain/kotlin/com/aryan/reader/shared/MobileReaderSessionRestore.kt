package com.aryan.reader.shared

enum class MobileReaderSessionRestoreAction {
    NONE,
    CLEAR_PERSISTED_SESSION,
    RESTORE,
}

data class MobileReaderSessionRestoreCandidate(
    val bookId: String,
    val fileType: FileType,
    val isAvailable: Boolean,
    val hasReadableLocation: Boolean,
)

/**
 * Validates persisted mobile reader identity before either host starts native
 * loading. Android's ordering is canonical: an absent book id means there is
 * no restore request; every invalid request with an id is cleared.
 */
fun mobileReaderSessionRestoreAction(
    persistedBookId: String?,
    persistedFileTypeName: String?,
    pendingRemovalBookIds: Set<String> = emptySet(),
    candidate: MobileReaderSessionRestoreCandidate?,
): MobileReaderSessionRestoreAction {
    val bookId = persistedBookId?.takeIf(String::isNotBlank)
        ?: return MobileReaderSessionRestoreAction.NONE
    if (bookId in pendingRemovalBookIds) {
        return MobileReaderSessionRestoreAction.CLEAR_PERSISTED_SESSION
    }
    val fileType = persistedFileTypeName
        ?.let { name -> runCatching { FileType.valueOf(name) }.getOrNull() }
        ?: return MobileReaderSessionRestoreAction.CLEAR_PERSISTED_SESSION
    return if (
        candidate != null &&
        candidate.bookId == bookId &&
        candidate.fileType == fileType &&
        candidate.isAvailable &&
        candidate.hasReadableLocation
    ) {
        MobileReaderSessionRestoreAction.RESTORE
    } else {
        MobileReaderSessionRestoreAction.CLEAR_PERSISTED_SESSION
    }
}
