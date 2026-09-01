package com.aryan.reader.shared

import java.security.MessageDigest

internal actual fun localFolderSyncSha256Hex(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
