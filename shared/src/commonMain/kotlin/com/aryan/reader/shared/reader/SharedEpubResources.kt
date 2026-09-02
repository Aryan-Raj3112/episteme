package com.aryan.reader.shared.reader

internal const val SharedEpubResourceScheme = "reader-epub-res"
private const val SharedEpubResourceHost = "r"
private const val SharedEpubResourceUrlPrefix = "$SharedEpubResourceScheme://$SharedEpubResourceHost/"

data class SharedEpubResourceReference(
    val bookId: String,
    val entryPath: String
)

fun sharedEpubResourceUrl(bookId: String, entryPath: String): String =
    SharedEpubResourceUrlPrefix +
        bookId.sharedEpubResourcePercentEncoded() + "/" +
        entryPath.sharedEpubResourcePercentEncoded()

fun isSharedEpubResourceUrl(source: String): Boolean =
    source.startsWith(SharedEpubResourceUrlPrefix, ignoreCase = true)

fun parseSharedEpubResourceUrl(source: String): SharedEpubResourceReference? {
    if (!isSharedEpubResourceUrl(source)) return null
    val remainder = source.substring(SharedEpubResourceUrlPrefix.length)
    val separator = remainder.indexOf('/')
    if (separator <= 0) return null
    val bookId = remainder.substring(0, separator).sharedEpubResourcePercentDecoded()
    val entryPath = remainder.substring(separator + 1)
        .substringBefore('#')
        .substringBefore('?')
        .sharedEpubResourcePercentDecoded()
    if (bookId.isBlank() || entryPath.isBlank()) return null
    return SharedEpubResourceReference(bookId = bookId, entryPath = entryPath)
}

private val SharedEpubSchemeServableExtensions = setOf(
    "jpg", "jpeg", "png", "gif", "svg", "webp", "avif",
    "mp3", "m4a", "aac", "ogg", "wav", "mp4", "webm"
)

fun isSharedEpubSchemeServableResource(entryPath: String): Boolean =
    entryPath.substringBefore('#')
        .substringBefore('?')
        .substringAfterLast('.', "")
        .lowercase() in SharedEpubSchemeServableExtensions

fun sharedEpubResourceMimeType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "xhtml", "html", "htm" -> "application/xhtml+xml"
    "css" -> "text/css"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "svg" -> "image/svg+xml"
    "webp" -> "image/webp"
    "avif" -> "image/avif"
    "ttf" -> "font/ttf"
    "otf" -> "font/otf"
    "woff" -> "font/woff"
    "woff2" -> "font/woff2"
    "mp3" -> "audio/mpeg"
    "m4a" -> "audio/mp4"
    "aac" -> "audio/aac"
    "ogg" -> "audio/ogg"
    "wav" -> "audio/wav"
    "mp4" -> "video/mp4"
    "webm" -> "video/webm"
    else -> "application/octet-stream"
}

internal expect fun resolveSharedEpubResourceBytes(source: String): ByteArray?

private const val SharedEpubResourceHex = "0123456789ABCDEF"

private fun String.sharedEpubResourcePercentEncoded(): String {
    val output = StringBuilder(length + 16)
    encodeToByteArray().forEach { byte ->
        val value = byte.toInt() and 0xFF
        val char = value.toChar()
        if (
            char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' ||
            char == '-' || char == '.' || char == '_' || char == '~'
        ) {
            output.append(char)
        } else {
            output.append('%')
                .append(SharedEpubResourceHex[value ushr 4])
                .append(SharedEpubResourceHex[value and 0x0F])
        }
    }
    return output.toString()
}

private fun String.sharedEpubResourcePercentDecoded(): String {
    if (!contains('%')) return this
    val output = ArrayList<Byte>(length)
    var index = 0
    while (index < length) {
        val char = this[index]
        if (char == '%' && index + 2 < length) {
            val value = substring(index + 1, index + 3).toIntOrNull(16)
            if (value != null) {
                output += value.toByte()
                index += 3
                continue
            }
        }
        output += char.toString().encodeToByteArray().toList()
        index++
    }
    return output.toByteArray().decodeToString()
}
