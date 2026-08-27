package com.aryan.reader.shared

enum class SyncedFolderAddDecision {
    ALLOWED,
    INVALID_URI,
    LIMIT_REACHED,
    ALREADY_SYNCED,
}

fun syncedFolderAddDecision(
    folders: Collection<SyncedFolder>,
    uriString: String,
): SyncedFolderAddDecision {
    val normalizedUri = uriString.trim()
    return when {
        normalizedUri.isBlank() -> SyncedFolderAddDecision.INVALID_URI
        folders.count { !it.isAppManaged && !it.isCloudPlaceholder } >= MAX_SYNCED_FOLDER_COUNT -> SyncedFolderAddDecision.LIMIT_REACHED
        folders.any { it.uriString == normalizedUri } -> SyncedFolderAddDecision.ALREADY_SYNCED
        else -> SyncedFolderAddDecision.ALLOWED
    }
}

fun List<SyncedFolder>.withSyncedFolder(folder: SyncedFolder): List<SyncedFolder> {
    return if (syncedFolderAddDecision(this, folder.uriString) == SyncedFolderAddDecision.ALLOWED) {
        this + folder.copy(uriString = folder.uriString.trim())
    } else {
        this
    }
}

fun List<SyncedFolder>.withoutSyncedFolder(uriString: String): List<SyncedFolder> =
    filterNot { it.uriString == uriString }

fun List<SyncedFolder>.withSyncedFolderLocalSync(
    uriString: String,
    enabled: Boolean,
): List<SyncedFolder> = map { folder ->
    if (folder.uriString == uriString) folder.copy(localSyncEnabled = enabled) else folder
}

fun List<SyncedFolder>.withSyncedFolderFileTypes(
    uriString: String,
    requestedFileTypes: Set<FileType>,
    supportedFileTypes: Set<FileType>,
): List<SyncedFolder> {
    val sanitized = requestedFileTypes.filterTo(linkedSetOf()) { it in supportedFileTypes }
    return map { folder ->
        if (folder.uriString == uriString) folder.copy(allowedFileTypes = sanitized) else folder
    }
}
