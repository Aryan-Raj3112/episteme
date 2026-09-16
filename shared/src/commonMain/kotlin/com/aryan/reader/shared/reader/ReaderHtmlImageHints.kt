package com.aryan.reader.shared.reader

private val ReaderImgTagRegex = Regex("""(?is)<img\b(?:[^>"']|"[^"]*"|'[^']*')*>""")
private val ReaderImgSrcRegex = Regex("""(?is)\bsrc\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)""")
private val ReaderBitmapExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

/**
 * Adds async image loading hints to content `<img>` tags that lack them.
 *
 * Images load eagerly on purpose: chunk virtualization already windows content
 * (placeholders + IntersectionObserver + bridge), so per-image
 * `loading="lazy"` would be a second, competing lazy mechanism. On affected
 * WebViews lazy images decode (naturalWidth > 0) yet never receive layout
 * (0x0 boxes) because lazy intersection never fires for them, while images
 * with explicit CSS widths render fine. Eager loading inside attached chunks
 * keeps work bounded per chunk; `decoding="async"` keeps decode off the
 * scrolling thread.
 */
fun String.withReaderImageLoadingHints(): String {
    if (!contains("<img", ignoreCase = true)) return this
    return ReaderImgTagRegex.replace(this) { match ->
        var tag = match.value
        if (!tag.contains("loading=", ignoreCase = true)) tag = addReaderImgAttr(tag, """loading="eager"""")
        if (!tag.contains("decoding=", ignoreCase = true)) tag = addReaderImgAttr(tag, """decoding="async"""")
        tag
    }
}

/**
 * Injects `width`/`height` from real image bounds into `<img>` tags missing
 * them, so placeholders reserve the right space and scroll positions stop
 * jumping as illustrations decode. Resolution is platform-provided (zip entry,
 * resource store, or file) because only the loader knows where `src` lives.
 */
fun String.withReaderImageDimensions(resolveBytes: (String) -> ByteArray?): String {
    if (!contains("<img", ignoreCase = true)) return this
    return ReaderImgTagRegex.replace(this) { match ->
        injectReaderImageDimensions(match.value, resolveBytes)
    }
}

internal fun countReaderImages(html: String): Int {
    if (!html.contains("<img", ignoreCase = true)) return 0
    return ReaderImgTagRegex.findAll(html).count()
}

private fun injectReaderImageDimensions(tag: String, resolveBytes: (String) -> ByteArray?): String {
    if (tag.contains("width=", ignoreCase = true) && tag.contains("height=", ignoreCase = true)) return tag
    val src = ReaderImgSrcRegex.find(tag)?.groupValues?.getOrNull(1)?.unquoteReaderHtmlAttr().orEmpty()
    if (src.isBlank()) return tag
    if (src.startsWith("data:", ignoreCase = true) || src.startsWith("blob:", ignoreCase = true)) return tag
    if (src.startsWith("http://", ignoreCase = true) || src.startsWith("https://", ignoreCase = true)) return tag
    val extension = src.substringBefore('#').substringBefore('?').substringAfterLast('.', "").lowercase()
    if (extension !in ReaderBitmapExtensions) return tag
    val bytes = runCatching { resolveBytes(src) }.getOrNull() ?: return tag
    val (width, height) = parseReaderImageBounds(bytes) ?: return tag
    if (width <= 0 || height <= 0) return tag
    var out = tag
    if (!out.contains("width=", ignoreCase = true)) out = addReaderImgAttr(out, "width=\"$width\"")
    if (!out.contains("height=", ignoreCase = true)) out = addReaderImgAttr(out, "height=\"$height\"")
    return out
}

private fun addReaderImgAttr(tag: String, attr: String): String {
    val end = tag.lastIndexOf('>')
    if (end < 0) return tag
    val head = tag.substring(0, end).trimEnd()
    val withAttr = if (head.endsWith("/")) {
        head.dropLast(1).trimEnd() + " " + attr + " /"
    } else {
        "$head $attr"
    }
    return withAttr + tag.substring(end)
}

private fun String.unquoteReaderHtmlAttr(): String {
    if (length >= 2 && ((startsWith('"') && endsWith('"')) || (startsWith('\'') && endsWith('\'')))) {
        return substring(1, length - 1)
    }
    return this
}

/**
 * Reads bitmap dimensions from file headers without decoding pixels. Pure
 * Kotlin so every platform (and unit tests) share one implementation. Returns
 * null for vectors, truncated data, or unknown formats.
 */
fun parseReaderImageBounds(bytes: ByteArray): Pair<Int, Int>? {
    if (bytes.size < 10) return null
    return when {
        isPng(bytes) -> pngBounds(bytes)
        isGif(bytes) -> gifBounds(bytes)
        isBmp(bytes) -> bmpBounds(bytes)
        isJpeg(bytes) -> jpegBounds(bytes)
        isRiffWebp(bytes) -> webpBounds(bytes)
        else -> null
    }
}

private fun Byte.u8(): Int = toInt() and 0xFF

private fun ByteArray.u16beAt(index: Int): Int? {
    if (index < 0 || index + 1 >= size) return null
    return (this[index].u8() shl 8) or this[index + 1].u8()
}

private fun ByteArray.u16leAt(index: Int): Int? {
    if (index < 0 || index + 1 >= size) return null
    return this[index].u8() or (this[index + 1].u8() shl 8)
}

private fun ByteArray.u24leAt(index: Int): Int? {
    if (index < 0 || index + 2 >= size) return null
    return this[index].u8() or (this[index + 1].u8() shl 8) or (this[index + 2].u8() shl 16)
}

private fun ByteArray.u32beAt(index: Int): Long? {
    if (index < 0 || index + 3 >= size) return null
    return (this[index].u8().toLong() shl 24) or
        (this[index + 1].u8().toLong() shl 16) or
        (this[index + 2].u8().toLong() shl 8) or
        this[index + 3].u8().toLong()
}

private fun isPng(bytes: ByteArray): Boolean =
    bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() &&
        bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() &&
        bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte()

private fun pngBounds(bytes: ByteArray): Pair<Int, Int>? {
    // Width/height sit at fixed offsets in IHDR, the first chunk.
    val width = bytes.u32beAt(16) ?: return null
    val height = bytes.u32beAt(20) ?: return null
    if (width <= 0 || height <= 0 || width > 100_000 || height > 100_000) return null
    return width.toInt() to height.toInt()
}

private fun isGif(bytes: ByteArray): Boolean =
    bytes.size >= 10 &&
        bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() &&
        bytes[3] == 0x38.toByte() && (bytes[4] == 0x37.toByte() || bytes[4] == 0x39.toByte()) &&
        bytes[5] == 0x61.toByte()

private fun gifBounds(bytes: ByteArray): Pair<Int, Int>? {
    val width = bytes.u16leAt(6) ?: return null
    val height = bytes.u16leAt(8) ?: return null
    if (width <= 0 || height <= 0) return null
    return width to height
}

private fun isBmp(bytes: ByteArray): Boolean =
    bytes.size >= 26 && bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()

private fun bmpBounds(bytes: ByteArray): Pair<Int, Int>? {
    // BITMAPINFOHEADER and later: width/height as signed 32-bit at 18/22.
    if (bytes.size < 26) return null
    val dibSize = bytes.u32beAt(14) ?: return null
    if (dibSize < 40) return null
    var width = 0L
    var height = 0L
    for (shift in 0..3) {
        width = width or (bytes[18 + shift].u8().toLong() shl (shift * 8))
        height = height or (bytes[22 + shift].u8().toLong() shl (shift * 8))
    }
    // Height is signed (top-down BMPs store it negative); width is unsigned.
    val signedHeight = height.toInt()
    val unsignedWidth = width.toInt()
    if (unsignedWidth <= 0 || signedHeight == 0) return null
    val absHeight = if (signedHeight < 0) -(signedHeight.toLong()) else signedHeight.toLong()
    if (unsignedWidth > 100_000 || absHeight > 100_000) return null
    return unsignedWidth to absHeight.toInt()
}

private fun isJpeg(bytes: ByteArray): Boolean =
    bytes.size >= 3 &&
        bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()

private fun jpegBounds(bytes: ByteArray): Pair<Int, Int>? {
    // Walk markers until a Start-Of-Frame; stop at image data (SOS) or EOF.
    // Headers can sit past thumbnails, so scan generously but bounded.
    var cursor = 2
    val end = minOf(bytes.size, 512 * 1024)
    while (cursor + 3 < end) {
        if (bytes[cursor].u8() != 0xFF) return null
        var marker = bytes[cursor + 1].u8()
        // Skip fill bytes.
        while (marker == 0xFF && cursor + 3 < end) {
            cursor++
            marker = bytes[cursor + 1].u8()
        }
        if (marker == 0xD9 || marker == 0xDA) return null
        if (marker == 0xD8 || (marker in 0xD0..0xD7) || marker == 0x01) {
            cursor += 2
            continue
        }
        val length = bytes.u16beAt(cursor + 2) ?: return null
        if (length < 2 || cursor + length + 1 >= end) return null
        if (isJpegStartOfFrame(marker)) {
            val height = bytes.u16beAt(cursor + 5) ?: return null
            val width = bytes.u16beAt(cursor + 7) ?: return null
            if (width <= 0 || height <= 0) return null
            return width to height
        }
        cursor += 2 + length
    }
    return null
}

private fun isJpegStartOfFrame(marker: Int): Boolean =
    marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC

private fun isRiffWebp(bytes: ByteArray): Boolean =
    bytes.size >= 12 &&
        bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
        bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
        bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
        bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()

private fun webpBounds(bytes: ByteArray): Pair<Int, Int>? {
    if (bytes.size < 20) return null
    val fourCc = bytes.copyOfRange(12, 16).decodeToString()
    return when (fourCc) {
        "VP8 " -> {
            // Frame tag (3) + start code (3) + width/height (2 + 2, 14-bit).
            if (bytes.size < 30) return null
            if (bytes[23] != 0x9D.toByte() || bytes[24] != 0x01.toByte() || bytes[25] != 0x2A.toByte()) return null
            val width = (bytes.u16leAt(26) ?: return null) and 0x3FFF
            val height = (bytes.u16leAt(28) ?: return null) and 0x3FFF
            if (width <= 0 || height <= 0) return null
            width to height
        }
        "VP8L" -> {
            if (bytes.size < 25) return null
            if (bytes[20] != 0x2F.toByte()) return null
            val b0 = bytes[21].u8()
            val b1 = bytes[22].u8()
            val b2 = bytes[23].u8()
            val b3 = bytes[24].u8()
            val width = 1 + (b0 or ((b1 and 0x3F) shl 8))
            val height = 1 + (((b1 and 0xC0) shr 6) or (b2 shl 2) or ((b3 and 0x0F) shl 10))
            if (width <= 0 || height <= 0 || width > 100_000 || height > 100_000) return null
            width to height
        }
        "VP8X" -> {
            if (bytes.size < 30) return null
            val width = (bytes.u24leAt(24) ?: return null) + 1
            val height = (bytes.u24leAt(27) ?: return null) + 1
            if (width <= 0 || height <= 0 || width > 100_000 || height > 100_000) return null
            width to height
        }
        else -> null
    }
}
