package com.aryan.reader.shared.ios

/**
 * A reference to a book that lives in a user-linked folder, owned by the
 * document provider rather than by this app.
 *
 * Android reaches the same files through a persisted `content://` tree URI, so
 * a folder book needs no copy on either platform. iOS previously copied the
 * whole linked folder into `Application Support/LocalFolders/<name>/`, which
 * duplicated every byte the user already had on the device. This type replaces
 * that copy with a reference the app can resolve back to a real filesystem URL
 * through a security-scoped bookmark.
 *
 * The encoding is deliberately not a bare `file://` URL. A provider path is
 * only meaningful while its security scope is held, it moves when the app
 * container is relocated, and it must never be mistaken for an app-managed
 * path by destructive code such as `clearLocalCloudData`. Carrying the folder
 * name plus the folder-relative path makes all three facts explicit:
 *
 *  - the owning folder, so a bookmark lookup is possible;
 *  - a stable, container-independent identity for persistence and cache keys;
 *  - a value that is recognisably *not* a local file path.
 */
internal object SharedIosBookSourceRef {
    const val providerScheme: String = "ios-folder-book://"

    /**
     * Encodes a linked-folder book. [folderRelativePath] must be the path
     * relative to the linked folder root, using `/` separators and no leading
     * or trailing slash.
     */
    fun encode(folderName: String, folderRelativePath: String): String =
        providerScheme + encodeComponent(folderName) + "/" + normalizeRelative(folderRelativePath)

    fun decode(ref: String?): IosFolderBookRef? {
        val raw = ref?.trim()?.takeIf(String::isNotBlank) ?: return null
        if (!raw.startsWith(providerScheme)) return null
        val body = raw.removePrefix(providerScheme)
        val separator = body.indexOf('/')
        if (separator <= 0) return null
        val folderName = decodeComponent(body.substring(0, separator))
        val relativePath = normalizeRelative(body.substring(separator + 1))
        if (folderName.isBlank() || relativePath.isBlank()) return null
        return IosFolderBookRef(folderName = folderName, relativePath = relativePath)
    }

    fun isProviderRef(value: String?): Boolean =
        value?.trim()?.startsWith(providerScheme) == true

    /**
     * Books still pointing at the managed copy under `LocalFolders/`. Used by
     * the one-time migration off that copy; matching on the directory rather
     * than a prefix comparison keeps it correct if the app container moves.
     */
    fun isManagedCopyPath(value: String?): Boolean {
        val path = value?.trim()?.takeIf(String::isNotBlank) ?: return false
        if (isProviderRef(path)) return false
        return path.split('/').any { it == "LocalFolders" }
    }

    private fun normalizeRelative(relativePath: String): String =
        relativePath
            .split('/')
            .filter { it.isNotBlank() && it != "." }
            .joinToString("/")

    /**
     * Percent-encodes a single path segment. Encoding must be lossless: folder
     * names are arbitrary user text, and a lossy scheme would make a ref
     * resolve to a *different* folder, which reads the wrong book rather than
     * failing.
     */
    private fun encodeComponent(value: String): String {
        val bytes = value.encodeToByteArray()
        val out = StringBuilder(bytes.size)
        for (byte in bytes) {
            val code = byte.toInt().toChar()
            when {
                code.isLetterOrDigit() && code.code < 128 -> out.append(code)
                code == '-' || code == '_' || code == '.' -> out.append(code)
                else -> {
                    out.append('%')
                    out.append(HEX[(byte.toInt() shr 4) and 0xF])
                    out.append(HEX[byte.toInt() and 0xF])
                }
            }
        }
        return out.toString().ifBlank { "%20" }
    }

    private fun decodeComponent(value: String): String {
        if (!value.contains('%')) return value
        val bytes = ArrayList<Byte>(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '%' && index + 2 < value.length) {
                val high = HEX.indexOf(value[index + 1])
                val low = HEX.indexOf(value[index + 2])
                if (high >= 0 && low >= 0) {
                    bytes.add(((high shl 4) or low).toByte())
                    index += 3
                    continue
                }
            }
            char.toString().encodeToByteArray().forEach(bytes::add)
            index++
        }
        return bytes.toByteArray().decodeToString()
    }

    private const val HEX = "0123456789ABCDEF"
}

internal data class IosFolderBookRef(
    val folderName: String,
    val relativePath: String
) {
    /** Appends this book's relative path to a resolved folder root. */
    fun resolveAgainst(folderRoot: String): String =
        folderRoot.trimEnd('/') + "/" + relativePath
}
