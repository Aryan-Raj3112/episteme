package com.aryan.reader.shared.opds

import com.aryan.reader.shared.SharedFileCapabilities

object SharedOpdsSearch {
    suspend fun buildSearchUrl(
        searchLink: String,
        query: String,
        openSearchTemplateResolver: suspend (String) -> String?
    ): String {
        val template = if (searchLink.hasSearchTemplateToken()) {
            searchLink
        } else {
            openSearchTemplateResolver(searchLink) ?: searchLink
        }
        return expandSearchTemplate(template, query)
    }

    fun expandSearchTemplate(template: String, query: String): String {
        val encoded = query.percentEncode()
        val expandedSearchTerms = template.replace("{searchTerms}", encoded)
        if (expandedSearchTerms != template) return expandedSearchTerms

        val queryTemplate = Regex("""\{([?&])([^}]+)\}""").find(template)
        if (queryTemplate != null) {
            val operator = queryTemplate.groupValues[1]
            val variables = queryTemplate.groupValues[2]
                .split(',')
                .map { it.substringBefore(':').substringBefore('*').trim() }
                .filter { it.isNotBlank() }
            val parameterName = variables.firstOrNull { it.equals("searchTerms", ignoreCase = true) }
                ?: variables.firstOrNull()
                ?: "query"
            val prefix = template.substringBefore(queryTemplate.value)
            val suffix = template.substringAfter(queryTemplate.value)
            val separator = when {
                operator == "&" -> "&"
                prefix.contains("?") -> "&"
                else -> "?"
            }
            return "$prefix$separator$parameterName=$encoded$suffix"
        }

        val expandedQuery = template
            .replace("{query}", encoded)
            .replace("{keyword}", encoded)
        if (expandedQuery != template) return expandedQuery

        val separator = if (template.contains("?")) "&" else "?"
        return "$template${separator}query=$encoded"
    }

    private fun String.hasSearchTemplateToken(): Boolean {
        return contains("{searchTerms}") ||
            Regex("""\{[?&][^}]+\}""").containsMatchIn(this) ||
            contains("{query}") ||
            contains("{keyword}")
    }
}

object SharedOpdsDownloadNamer {
    fun resolveExtension(
        acquisition: OpdsAcquisition,
        contentDisposition: String?,
        urlPathSegment: String?
    ): String {
        val candidates = listOfNotNull(
            extractContentDispositionFilename(contentDisposition),
            urlPathSegment
        )

        candidates.forEach { candidate ->
            extensionSuffixFromName(candidate.percentDecode())?.let { return it }
        }

        return when (acquisition.formatName) {
            "EPUB" -> ".epub"
            "PDF" -> ".pdf"
            "PPTX" -> ".pptx"
            "MOBI" -> ".mobi"
            "FB2" -> ".fb2"
            "CBZ" -> ".cbz"
            "CBR" -> ".cbr"
            "CB7" -> ".cb7"
            "MD" -> ".md"
            "HTML" -> ".html"
            "TXT" -> ".txt"
            else -> ".epub"
        }
    }

    fun safeFileStem(title: String, fallback: String = "opds_book"): String {
        val safe = title
            .replace(Regex("""[^a-zA-Z0-9._-]+"""), "_")
            .trim('_')
            .take(80)
        return safe.ifBlank { fallback }
    }

    fun extractContentDispositionFilename(contentDisposition: String?): String? {
        if (contentDisposition.isNullOrBlank()) return null
        val encodedFilename = Regex("""filename\*=UTF-8''([^;]+)""", RegexOption.IGNORE_CASE)
            .find(contentDisposition)
            ?.groupValues
            ?.getOrNull(1)
        if (!encodedFilename.isNullOrBlank()) return encodedFilename.trim('"')

        return Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE)
            .find(contentDisposition)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.trim('"')
    }

    private fun extensionSuffixFromName(fileName: String?): String? {
        if (fileName.isNullOrBlank()) return null
        val cleanName = fileName.substringBefore('?').substringBefore('#')
        val extension = cleanName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .takeIf { it.isNotBlank() }
            ?: return null
        if (SharedFileCapabilities.fileTypeForName(cleanName) == com.aryan.reader.shared.FileType.UNKNOWN) return null
        return ".$extension"
    }
}

object SharedOpdsStreamUri {
    private const val SCHEME_PREFIX = "opds-pse://stream"

    fun build(reference: OpdsStreamReference): String {
        return "$SCHEME_PREFIX?id=${reference.id.percentEncode()}" +
            "&count=${reference.count}" +
            "&url=${reference.urlTemplate.percentEncode()}" +
            reference.catalogId?.let { "&catalogId=${it.percentEncode()}" }.orEmpty()
    }

    fun parse(uriString: String?): OpdsStreamReference? {
        if (uriString.isNullOrBlank() || !uriString.startsWith(SCHEME_PREFIX)) return null
        val query = uriString.substringAfter('?', missingDelimiterValue = "")
        val params = query.split('&')
            .mapNotNull { pair ->
                if (pair.isBlank()) return@mapNotNull null
                val key = pair.substringBefore('=').percentDecode()
                val value = pair.substringAfter('=', missingDelimiterValue = "").percentDecode()
                key to value
            }
            .toMap()
        val id = params["id"]?.takeIf { it.isNotBlank() } ?: return null
        val count = params["count"]?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val url = params["url"]?.takeIf { it.isNotBlank() } ?: return null
        return OpdsStreamReference(
            id = id,
            count = count,
            urlTemplate = url,
            catalogId = params["catalogId"]?.takeIf { it.isNotBlank() }
        )
    }
}

fun String.percentEncode(): String {
    val bytes = encodeToByteArray()
    return buildString(bytes.size) {
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xFF
            val char = value.toChar()
            if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "-_.~") {
                append(char)
            } else {
                append('%')
                append(value.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }
}

fun String.percentDecode(): String {
    val bytes = mutableListOf<Byte>()
    var index = 0
    while (index < length) {
        val char = this[index]
        if (char == '%' && index + 2 < length) {
            val value = substring(index + 1, index + 3).toIntOrNull(16)
            if (value != null) {
                bytes += value.toByte()
                index += 3
                continue
            }
        }
        val encoded = char.toString().encodeToByteArray()
        encoded.forEach { bytes += it }
        index += 1
    }
    return bytes.toByteArray().decodeToString()
}

object SharedOpdsText {
    fun cleanSummary(summary: String?): String {
        if (summary.isNullOrBlank()) return ""
        return summary
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""</p\s*>""", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("""<[^>]+>"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
