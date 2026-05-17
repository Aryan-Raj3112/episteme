package com.aryan.reader

import com.aryan.reader.shared.SharedFileCapabilities

internal fun resolveFileTypeFromName(fileName: String?): FileType? {
    return SharedFileCapabilities.resolveFileTypeForName(fileName)
}

internal fun resolveFileTypeFromMetadata(fileName: String?, mimeType: String?): FileType? {
    return SharedFileCapabilities.resolveFileTypeForMetadata(fileName, mimeType)
}

internal fun isCodeOrDataFileName(fileName: String): Boolean {
    return SharedFileCapabilities.isCodeOrDataFileName(fileName)
}

internal fun isManualOnlyReaderFileName(fileName: String?): Boolean {
    return SharedFileCapabilities.isManualOnlyReaderFileName(fileName)
}

internal fun isManualOnlyReaderMimeType(mimeType: String?): Boolean {
    return SharedFileCapabilities.isManualOnlyReaderMimeType(mimeType)
}

internal fun isLocalFolderSyncEligibleFile(name: String, mimeType: String?): Boolean {
    return SharedFileCapabilities.isLocalFolderSyncEligibleFile(name, mimeType)
}

internal fun resolveFileExtensionSuffixFromName(fileName: String?): String? {
    return SharedFileCapabilities.fileExtensionSuffixForName(fileName)
}
