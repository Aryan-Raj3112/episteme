package com.aryan.reader.shared.reader

object SharedTextBookFactory {
    fun fromPlainText(
        id: String,
        fileName: String,
        title: String,
        plainText: String,
        author: String? = null
    ): SharedEpubBook {
        return SharedEpubBook(
            id = id,
            fileName = fileName,
            title = title,
            author = author,
            chapters = listOf(
                SharedEpubChapter(
                    id = "chapter_0",
                    title = title,
                    plainText = plainText.ifBlank { "This document did not contain readable text." }
                )
            )
        )
    }

    fun fromHtml(
        id: String,
        fileName: String,
        title: String,
        html: String,
        author: String? = null
    ): SharedEpubBook {
        val sanitizedHtml = html.sanitizeReaderHtml()
        val body = sanitizedHtml.extractBodyOrSelf()
        return SharedEpubBook(
            id = id,
            fileName = fileName,
            title = title,
            author = author,
            chapters = listOf(
                SharedEpubChapter(
                    id = "chapter_0",
                    title = sanitizedHtml.tagText("h1")
                        .ifBlank { sanitizedHtml.tagText("title") }
                        .ifBlank { title },
                    plainText = sanitizedHtml.htmlToText().ifBlank { title },
                    htmlContent = body
                )
            )
        )
    }

    private fun String.extractBodyOrSelf(): String {
        return Regex("(?is)<body\\b[^>]*>(.*?)</body>")
            .find(this)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?: this
    }

    private fun String.tagText(tag: String): String {
        return Regex("<(?:[^:>]+:)?$tag\\b[^>]*>(.*?)</(?:[^:>]+:)?$tag>", RegexOption.IGNORE_CASE)
            .find(this)
            ?.groupValues
            ?.get(1)
            ?.htmlToText()
            .orEmpty()
    }

    private fun String.htmlToText(): String {
        return replace(Regex("(?is)<script.*?</script>"), "")
            .replace(Regex("(?is)<style.*?</style>"), "")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p\\s*>"), "\n\n")
            .replace(Regex("(?i)</h[1-6]\\s*>"), "\n\n")
            .replace(Regex("<[^>]+>"), " ")
            .decodeEntities()
            .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun String.decodeEntities(): String {
        return replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                match.groupValues[1].toIntOrNull(16)?.toChar()?.toString().orEmpty()
            }
            .replace(Regex("&#(\\d+);")) { match ->
                match.groupValues[1].toIntOrNull()?.toChar()?.toString().orEmpty()
            }
    }

    private fun String.sanitizeReaderHtml(): String {
        return replace(Regex("(?is)<script\\b.*?</script>"), "")
            .replace(Regex("(?is)<object\\b.*?</object>"), "")
            .replace(Regex("(?is)<embed\\b[^>]*>"), "")
            .replace(Regex("""(?i)\s+on[a-z]+\s*=\s*(['"]).*?\1"""), "")
    }
}
