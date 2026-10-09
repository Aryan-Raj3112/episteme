package com.aryan.reader.shared.reader

/**
 * The `Uri` scheme media overlay audio is addressed by, and the entry path inside it.
 *
 * Pure string handling, deliberately not `android.net.Uri`. Two reasons, one practical and one
 * structural: the encoding has to be identical on both platforms so an overlay plays the same file
 * on each, and a data source's addressing is exactly the kind of thing worth being able to test
 * without a device. `Uri.parse` is available only on Android, which would have left this untestable
 * in the one place the logic actually lives.
 *
 * Everything except the unreserved characters is percent-encoded, **including the slashes**. That is
 * stricter than a path segment encoder needs to be, and it is the point: an entry path is opaque, and
 * encoding the separator too means no later step can read the path as a hierarchy and re-resolve a
 * `..` against it.
 */
object SharedMediaOverlayAudioUri {

    /**
     * A scheme no other subsystem claims.
     *
     * `file` and `content` are excluded deliberately — those are the schemes the default Android data
     * sources handle, and reusing one would make a zip entry indistinguishable from a real file.
     */
    const val scheme = "reader-epub-audio"

    private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    /** The full Uri for an archive entry. */
    fun uriFor(entryPath: String): String = "$scheme:///${percentEncode(entryPath)}"

    /**
     * The archive entry a Uri names, or null when it is not one of ours.
     *
     * Returns null rather than throwing for a `file:` or `https:` Uri: those are in-spec in a `par`
     * and are handed to the ordinary sources instead, so an unusual book is played rather than
     * rejected.
     */
    fun entryPathOf(uri: String): String? {
        val prefix = "$scheme:///"
        if (!uri.startsWith(prefix, ignoreCase = true)) return null
        val encoded = uri.substring(prefix.length)
        if (encoded.isEmpty()) return null
        return percentDecode(encoded).takeIf(String::isNotBlank)
    }

    /**
     * Percent-encodes anything a Uri path could otherwise reinterpret.
     *
     * Case-folded hex, and non-ASCII encoded as UTF-8 bytes — a chapter named in Japanese must
     * produce a Uri that survives being round-tripped through a media framework, not one that
     * silently decodes to garbage.
     */
    fun percentEncode(value: String): String {
        val out = StringBuilder(value.length + 16)
        for (byte in value.encodeToByteArray()) {
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            if (UNRESERVED.indexOf(char) >= 0) {
                out.append(char)
            } else {
                out.append('%')
                out.append(HEX[code shr 4])
                out.append(HEX[code and 0x0F])
            }
        }
        return out.toString()
    }

    /**
     * Reverses [percentEncode], leaving malformed escapes as written.
     *
     * A truncated or non-hex escape is passed through rather than dropped: it cannot occur from our
     * own encoder, so seeing one means the Uri came from somewhere else, and quietly deleting bytes
     * would turn a bad name into a different entry.
     */
    fun percentDecode(value: String): String {
        if ('%' !in value) return value
        val bytes = ArrayList<Byte>(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '%' && index + 2 < value.length) {
                val high = hexValue(value[index + 1])
                val low = hexValue(value[index + 2])
                if (high >= 0 && low >= 0) {
                    bytes.add(((high shl 4) or low).toByte())
                    index += 3
                    continue
                }
            }
            // A literal character, encoded as its own UTF-8 bytes so multi-byte text survives.
            for (byte in char.toString().encodeToByteArray()) bytes.add(byte)
            index++
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun hexValue(char: Char): Int = when (char) {
        in '0'..'9' -> char - '0'
        in 'a'..'f' -> char - 'a' + 10
        in 'A'..'F' -> char - 'A' + 10
        else -> -1
    }

    private val HEX = "0123456789ABCDEF".toCharArray()
}