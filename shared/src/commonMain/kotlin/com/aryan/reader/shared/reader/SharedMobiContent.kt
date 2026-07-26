package com.aryan.reader.shared.reader

internal data class SharedMobiTocPoint(
    val title: String,
    val bytePosition: Int,
)

internal data class SharedMobiSection(
    val title: String,
    val html: String,
)

internal fun splitMobiHtml(
    html: String,
    toc: List<SharedMobiTocPoint>,
    fallbackTitle: String,
): List<SharedMobiSection> {
    val bytes = html.encodeToByteArray()
    val points = toc
        .filter { it.bytePosition in 0 until bytes.size }
        .distinctBy { it.bytePosition }
        .sortedBy { it.bytePosition }
    if (points.isNotEmpty()) {
        return points.mapIndexedNotNull { index, point ->
            val end = points.getOrNull(index + 1)?.bytePosition ?: bytes.size
            if (end <= point.bytePosition) return@mapIndexedNotNull null
            SharedMobiSection(
                title = point.title.ifBlank { "Chapter ${index + 1}" },
                html = bytes.copyOfRange(point.bytePosition, end).decodeToString(),
            )
        }.takeIf { it.isNotEmpty() } ?: listOf(SharedMobiSection(fallbackTitle, html))
    }

    val pageBreak = Regex("""<mbp:pagebreak\b[^>]*?/?>""", RegexOption.IGNORE_CASE)
    val sections = pageBreak.split(html).filter { it.isNotBlank() }
    return if (sections.size > 1) {
        sections.mapIndexed { index, section ->
            SharedMobiSection("Chapter ${index + 1}", section)
        }
    } else {
        listOf(SharedMobiSection(fallbackTitle, html))
    }
}

internal fun rewriteMobiResourceReferences(
    html: String,
    imageDataUris: List<String>,
    cssDataUris: Map<Int, String>,
): String {
    var rewritten = Regex(
        """kindle:flow:(\d+)(?:\?[^"' >]*)?""",
        RegexOption.IGNORE_CASE,
    ).replace(html) { match ->
        cssDataUris[match.groupValues[1].toIntOrNull()] ?: match.value
    }
    rewritten = Regex(
        """kindle:embed:(\d+)(?:\?[^"' >]*)?""",
        RegexOption.IGNORE_CASE,
    ).replace(rewritten) { match ->
        imageDataUris.getOrNull(match.groupValues[1].toIntOrNull()?.minus(1) ?: -1) ?: match.value
    }
    return Regex(
        """<img\b([^>]*?)\srecindex=["']?(\d+)["']?([^>]*)>""",
        RegexOption.IGNORE_CASE,
    ).replace(rewritten) { match ->
        val source = imageDataUris.getOrNull(match.groupValues[2].toIntOrNull()?.minus(1) ?: -1)
            ?: return@replace match.value
        val attributes = match.groupValues[1] + match.groupValues[3]
        val withoutSource = attributes.replace(
            Regex("""\s+src\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)""", RegexOption.IGNORE_CASE),
            "",
        )
        """<img$withoutSource src="$source">"""
    }
}
