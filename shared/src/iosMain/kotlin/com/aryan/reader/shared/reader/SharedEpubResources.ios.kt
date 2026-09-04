package com.aryan.reader.shared.reader

import com.aryan.reader.shared.ios.IosEpubResourceStore

internal actual fun resolveSharedEpubResourceBytes(source: String): ByteArray? {
    val reference = parseSharedEpubResourceUrl(source) ?: return null
    return IosEpubResourceStore.bytesFor(reference.bookId, reference.entryPath)
}
