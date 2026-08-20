package com.aryan.reader.paginatedreader

/**
 * Decodes the contents of a CSS string token.
 *
 * CSS escapes are not Java/Kotlin escapes: a backslash followed by one to six
 * hexadecimal digits denotes a Unicode code point and one optional whitespace
 * terminator is consumed.  A backslash followed by any other non-newline
 * character escapes that character literally.  Keeping this decoder separate
 * from the CSS declaration parser also lets generated-content consumers use the
 * same rules for inline and stylesheet declarations.
 */
internal fun decodeCssStringToken(raw: String): String {
    if ('\\' !in raw) return raw

    val result = StringBuilder(raw.length)
    var index = 0
    while (index < raw.length) {
        val current = raw[index]
        if (current != '\\') {
            result.append(current)
            index++
            continue
        }

        // A trailing backslash is an invalid escape. CSS's consume-escaped-
        // code-point algorithm substitutes U+FFFD for the missing code point.
        if (index + 1 >= raw.length) {
            result.append('\uFFFD')
            index++
            continue
        }

        val next = raw[index + 1]
        when {
            // CSS permits escaped newlines in strings as a line continuation.
            next == '\n' -> index += 2
            next == '\r' -> {
                index += if (index + 2 < raw.length && raw[index + 2] == '\n') 3 else 2
            }
            next == '\u000C' -> index += 2
            next.isCssHexDigit() -> {
                var cursor = index + 1
                var value = 0
                var digits = 0
                while (cursor < raw.length && digits < 6 && raw[cursor].isCssHexDigit()) {
                    value = (value shl 4) + raw[cursor].cssHexValue()
                    cursor++
                    digits++
                }

                // A single CSS whitespace code point terminates a hex escape.
                // CRLF is one newline for this purpose, so consume both units.
                if (cursor < raw.length && raw[cursor].isCssWhitespace()) {
                    cursor++
                    if (raw[cursor - 1] == '\r' && cursor < raw.length && raw[cursor] == '\n') {
                        cursor++
                    }
                }

                val codePoint = if (
                    value == 0 ||
                    value > 0x10FFFF ||
                    value in 0xD800..0xDFFF
                ) {
                    0xFFFD
                } else {
                    value
                }
                if (codePoint <= 0xFFFF) {
                    result.append(codePoint.toChar())
                } else {
                    val supplementary = codePoint - 0x10000
                    result.append((0xD800 + (supplementary shr 10)).toChar())
                    result.append((0xDC00 + (supplementary and 0x3FF)).toChar())
                }
                index = cursor
            }
            else -> {
                // This includes escaped quotes, backslashes, punctuation, and
                // non-ASCII characters. The escaped character is emitted as-is.
                result.append(next)
                index += 2
            }
        }
    }
    return result.toString()
}

/**
 * Materializes the string and attr() parts currently supported by generated
 * content. Unsupported content functions are ignored, matching the parser's
 * existing conservative behavior.
 */
internal fun materializeCssGeneratedContent(
    rawContent: String?,
    attributeReader: (String) -> String?
): String? {
    if (rawContent.isNullOrBlank()) return null
    val content = rawContent.trim()
    if (content.equals("none", ignoreCase = true) || content.equals("normal", ignoreCase = true)) return null

    val result = StringBuilder()
    var cursor = 0
    var matchedToken = false

    fun skipWhitespace() {
        while (cursor < content.length && content[cursor].isWhitespace()) cursor++
    }

    while (cursor < content.length) {
        skipWhitespace()
        if (cursor >= content.length) break

        when (content[cursor]) {
            '\'', '"' -> {
                matchedToken = true
                val quote = content[cursor++]
                val rawString = StringBuilder()
                var closed = false
                while (cursor < content.length) {
                    val char = content[cursor]
                    if (char == quote) {
                        cursor++
                        closed = true
                        break
                    }
                    if (char == '\\' && cursor + 1 < content.length) {
                        rawString.append(char)
                        rawString.append(content[cursor + 1])
                        cursor += 2
                    } else {
                        rawString.append(char)
                        cursor++
                    }
                }
                // Be permissive for malformed EPUB CSS: retain the token's
                // decoded text even if a closing quote is missing.
                result.append(decodeCssStringToken(rawString.toString()))
                if (!closed) break
            }

            else -> {
                val attrStart = cursor
                while (cursor < content.length && content[cursor].isLetter()) cursor++
                val functionName = content.substring(attrStart, cursor)
                skipWhitespace()
                if (functionName.equals("attr", ignoreCase = true) && cursor < content.length && content[cursor] == '(') {
                    val openParen = cursor++
                    var depth = 1
                    var quote: Char? = null
                    var escaped = false
                    while (cursor < content.length && depth > 0) {
                        val char = content[cursor]
                        when {
                            escaped -> escaped = false
                            char == '\\' -> escaped = true
                            quote != null -> if (char == quote) quote = null
                            char == '\'' || char == '"' -> quote = char
                            char == '(' -> depth++
                            char == ')' -> depth--
                        }
                        cursor++
                    }
                    if (depth == 0) {
                        val name = content.substring(openParen + 1, cursor - 1)
                            .trim()
                            .takeWhile { !it.isWhitespace() }
                        attributeReader(name)?.let(result::append)
                        matchedToken = true
                    }
                } else {
                    // Consume an unsupported bare token/function. If there was
                    // no supported token at all, preserve the historical
                    // fallback below.
                    while (cursor < content.length && !content[cursor].isWhitespace()) cursor++
                }
            }
        }
    }

    // A quoted whitespace string is still generated content. Only an empty
    // token means that there is nothing to append.
    if (matchedToken) return result.toString().takeIf { it.isNotEmpty() }
    return content
        .removeSurrounding("\"")
        .removeSurrounding("'")
        .let(::decodeCssStringToken)
        .takeIf { it.isNotBlank() }
}

private fun Char.isCssHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun Char.cssHexValue(): Int = when (this) {
    in '0'..'9' -> code - '0'.code
    in 'a'..'f' -> code - 'a'.code + 10
    else -> code - 'A'.code + 10
}

private fun Char.isCssWhitespace(): Boolean = this == ' ' || this == '\t' || this == '\n' || this == '\r' || this == '\u000C'
