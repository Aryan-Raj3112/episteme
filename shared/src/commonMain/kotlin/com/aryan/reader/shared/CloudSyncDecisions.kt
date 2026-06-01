package com.aryan.reader.shared

enum class SharedCloudBookMetadataWinner {
    LOCAL,
    REMOTE,
    SAME
}

fun sharedCloudBookReadingMetadataWinner(
    localModifiedTimestamp: Long?,
    remoteModifiedTimestamp: Long
): SharedCloudBookMetadataWinner {
    val localTimestamp = localModifiedTimestamp ?: Long.MIN_VALUE
    return when {
        localTimestamp > remoteModifiedTimestamp -> SharedCloudBookMetadataWinner.LOCAL
        remoteModifiedTimestamp > localTimestamp -> SharedCloudBookMetadataWinner.REMOTE
        else -> SharedCloudBookMetadataWinner.SAME
    }
}

fun sharedCloudBookMetadataWinner(
    localModifiedTimestamp: Long?,
    remoteModifiedTimestamp: Long,
    localSidecarModifiedTimestamp: Long = 0L
): SharedCloudBookMetadataWinner {
    val localTimestamp = maxOf(localModifiedTimestamp ?: Long.MIN_VALUE, localSidecarModifiedTimestamp)
    return when {
        localTimestamp > remoteModifiedTimestamp -> SharedCloudBookMetadataWinner.LOCAL
        remoteModifiedTimestamp > localTimestamp -> SharedCloudBookMetadataWinner.REMOTE
        else -> SharedCloudBookMetadataWinner.SAME
    }
}

fun shouldApplyRemoteCloudBookMetadataUpdate(
    localModifiedTimestamp: Long?,
    remoteModifiedTimestamp: Long
): Boolean {
    return sharedCloudBookReadingMetadataWinner(
        localModifiedTimestamp = localModifiedTimestamp,
        remoteModifiedTimestamp = remoteModifiedTimestamp
    ) == SharedCloudBookMetadataWinner.REMOTE
}

fun shouldUploadLocalCloudBookMetadataUpdate(
    localModifiedTimestamp: Long,
    remoteModifiedTimestamp: Long
): Boolean {
    return sharedCloudBookReadingMetadataWinner(
        localModifiedTimestamp = localModifiedTimestamp,
        remoteModifiedTimestamp = remoteModifiedTimestamp
    ) == SharedCloudBookMetadataWinner.LOCAL
}

fun shouldApplyRemoteCloudBookUpdate(
    localModifiedTimestamp: Long?,
    remoteModifiedTimestamp: Long,
    localSidecarModifiedTimestamp: Long = 0L
): Boolean {
    return sharedCloudBookMetadataWinner(
        localModifiedTimestamp = localModifiedTimestamp,
        remoteModifiedTimestamp = remoteModifiedTimestamp,
        localSidecarModifiedTimestamp = localSidecarModifiedTimestamp
    ) == SharedCloudBookMetadataWinner.REMOTE
}

fun shouldUploadLocalCloudBookUpdate(
    localModifiedTimestamp: Long,
    remoteModifiedTimestamp: Long,
    localSidecarModifiedTimestamp: Long = 0L
): Boolean {
    return sharedCloudBookMetadataWinner(
        localModifiedTimestamp = localModifiedTimestamp,
        remoteModifiedTimestamp = remoteModifiedTimestamp,
        localSidecarModifiedTimestamp = localSidecarModifiedTimestamp
    ) == SharedCloudBookMetadataWinner.LOCAL
}

fun shouldDownloadRemoteCloudBookContent(
    localFileAvailable: Boolean,
    localContentModifiedTimestamp: Long,
    remoteContentModifiedTimestamp: Long,
    remoteDeleted: Boolean = false
): Boolean {
    return !remoteDeleted &&
        remoteContentModifiedTimestamp > 0L &&
        (!localFileAvailable || remoteContentModifiedTimestamp > localContentModifiedTimestamp)
}

fun shouldUploadLocalCloudBookContent(
    localFileAvailable: Boolean,
    localContentModifiedTimestamp: Long,
    remoteContentModifiedTimestamp: Long?
): Boolean {
    return localFileAvailable &&
        localContentModifiedTimestamp > 0L &&
        localContentModifiedTimestamp > (remoteContentModifiedTimestamp ?: 0L)
}

fun sharedCloudBookContentFileName(bookId: String, type: FileType): String? {
    val extension = SharedFileCapabilities.primaryExtensionFor(type) ?: return null
    return "$bookId.$extension"
}
