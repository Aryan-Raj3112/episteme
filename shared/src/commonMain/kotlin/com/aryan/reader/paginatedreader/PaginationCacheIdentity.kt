package com.aryan.reader.paginatedreader

/** Stable content identity; extracted-file timestamps are intentionally excluded. */
fun sharedPaginationChapterContentVersion(
    chapterPath: String,
    htmlFilePath: String,
    htmlContent: String,
    plainTextCharacterCount: Int,
    plainTextHash: Int,
    backingFileLength: Long,
    backingFileCrc32: Long,
): Int = buildString {
    append(chapterPath)
    append('|')
    append(htmlFilePath)
    append('|')
    append(htmlContent.length)
    append('|')
    append(htmlContent.hashCode())
    append('|')
    append(plainTextCharacterCount)
    append('|')
    append(plainTextHash)
    append('|')
    append(backingFileLength)
    append('|')
    append(backingFileCrc32)
}.hashCode()

/** Deterministic font signature that does not depend on extraction-directory paths. */
fun sharedPaginationFontSignature(
    fontFaces: List<FontFaceInfo>,
    sourceFingerprint: (String) -> String = { "" },
): Int = fontFaces
    .map { face ->
        listOf(
            face.fontFamily.trim().lowercase(),
            face.src.replace('\\', '/').substringAfterLast('/').lowercase(),
            face.fontWeight?.weight ?: 400,
            face.fontStyle?.toString() ?: "normal",
            sourceFingerprint(face.src),
        ).joinToString("|")
    }
    .sorted()
    .joinToString(";")
    .hashCode()

fun sharedPaginationLayoutConfigHash(parts: List<Pair<String, Any?>>): Int = parts
    .joinToString(separator = "|") { (name, value) -> "$name=${value.orEmptyCacheValue()}" }
    .hashCode()

private fun Any?.orEmptyCacheValue(): String = this?.toString().orEmpty()
