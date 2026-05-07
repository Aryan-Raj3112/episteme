package com.aryan.reader.shared

import java.security.MessageDigest

internal actual fun localFolderSyncSha256ShortHex(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }.take(12)
}
