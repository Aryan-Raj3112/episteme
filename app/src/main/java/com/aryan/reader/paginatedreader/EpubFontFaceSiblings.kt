package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.aryan.reader.ReaderFontDiagnosticsTag
import com.aryan.reader.readerFontDiagnosticSummary
import com.aryan.reader.shared.fontWeightCssDescriptor
import timber.log.Timber
import java.io.File

fun expandFontFacesWithSiblings(
    fontFaces: List<FontFaceInfo>,
    extractionPath: String
): List<FontFaceInfo> {
    if (fontFaces.isEmpty()) return emptyList()

    Timber.tag(ReaderFontDiagnosticsTag).i(
        "epub.siblings.start inputCount=${fontFaces.size} extractionPath='$extractionPath'"
    )

    val extractionRoot = File(extractionPath)
    val result = inferEpubFontFaceSiblings(fontFaces) candidates@{ fontFace ->
        val sourceFile = fontFace.resolvedFile(extractionRoot).takeIf { it.isFile }
            ?: return@candidates emptyList()
        val parent = sourceFile.parentFile ?: return@candidates emptyList()

        Timber.tag(ReaderFontDiagnosticsTag).i(
            "epub.siblings.source family='${fontFace.fontFamily}' src='${fontFace.src}' " +
                "file='${sourceFile.name}' " +
                readerFontDiagnosticSummary(sourceFile.nameWithoutExtension)
        )

        listFontFiles(parent).map { candidate ->
            EpubFontSiblingCandidate(candidate.name, candidate.toFontFaceSrc(extractionRoot))
        }
    }

    Timber.tag(ReaderFontDiagnosticsTag).i("epub.siblings.done outputCount=${result.size}")
    return result
}

/**
 * Font sibling expansion runs on every reader screen entry, chapter render and paginator
 * construction and used to re-list and stat the same font directories synchronously during
 * composition on the main thread (ANR source). Extracted EPUB font directories are immutable
 * while a book is loaded, so listings are memoized per directory in a small bounded LRU.
 */
private const val MAX_CACHED_FONT_DIRECTORIES = 8

private val fontDirectoryListings: MutableMap<String, List<File>> =
    object : LinkedHashMap<String, List<File>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<File>>): Boolean =
            size > MAX_CACHED_FONT_DIRECTORIES
    }

private fun listFontFiles(parent: File): List<File> =
    synchronized(fontDirectoryListings) {
        fontDirectoryListings.getOrPut(parent.absolutePath) {
            parent.listFiles()?.filter(File::isFile).orEmpty()
        }
    }

fun buildEpubFontFaceCss(
    fontFaces: List<FontFaceInfo>,
    extractionPath: String
): String {
    val extractionRoot = File(extractionPath)
    return expandFontFacesWithSiblings(fontFaces, extractionPath)
        .distinctBy { it.epubVariantKey() }
        .mapNotNull { fontFace ->
            val file = fontFace.resolvedFile(extractionRoot).takeIf { it.isFile } ?: return@mapNotNull null
            val family = fontFace.fontFamily.cssString()
            val url = file.toURI().toString().cssUrlString()
            val weight = file.nameWithoutExtension.fontWeightCssDescriptor(fontFace.fontWeight ?: FontWeight.Normal)
            val style = if (fontFace.fontStyle == FontStyle.Italic) "italic" else "normal"
            Timber.tag(ReaderFontDiagnosticsTag).i(
                "epub.css.face family='$family' file='${file.name}' fontWeight='$weight' fontStyle='$style' " +
                    readerFontDiagnosticSummary(file.nameWithoutExtension)
            )
            "@font-face { font-family: '$family'; src: url('$url'); font-weight: $weight; font-style: $style; }"
        }
        .joinToString(separator = " ")
}

private fun FontFaceInfo.resolvedFile(extractionRoot: File): File {
    val source = File(src)
    return if (source.isAbsolute) source else File(extractionRoot, src)
}

private fun File.toFontFaceSrc(extractionRoot: File): String {
    val relative = runCatching {
        extractionRoot.toPath().relativize(toPath()).toString()
    }.getOrNull()
    return relative
        ?.takeIf { !it.startsWith("..") && it.isNotBlank() }
        ?.replace(File.separatorChar, '/')
        ?: absolutePath
}

private fun String.cssString(): String = replace("\\", "\\\\").replace("'", "\\'")

private fun String.cssUrlString(): String = replace("\\", "\\\\").replace("'", "%27")
