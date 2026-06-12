package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.aryan.reader.ReaderFontDiagnosticsTag
import com.aryan.reader.readerFontDiagnosticSummary
import com.aryan.reader.shared.detectFontVariant
import com.aryan.reader.shared.familyFilenameSignature
import com.aryan.reader.shared.fontWeightCssDescriptor
import timber.log.Timber
import java.io.File

private val supportedEpubFontExtensions = setOf("ttf", "otf", "woff", "woff2")

fun expandFontFacesWithSiblings(
    fontFaces: List<FontFaceInfo>,
    extractionPath: String
): List<FontFaceInfo> {
    if (fontFaces.isEmpty()) return emptyList()

    Timber.tag(ReaderFontDiagnosticsTag).i(
        "epub.siblings.start inputCount=${fontFaces.size} extractionPath='$extractionPath'"
    )

    val result = fontFaces.toMutableList()
    val existingKeys = result.mapTo(mutableSetOf()) { it.variantKey() }
    val extractionRoot = File(extractionPath)

    fontFaces.forEach { fontFace ->
        val sourceFile = fontFace.resolvedFile(extractionRoot).takeIf { it.isFile } ?: return@forEach
        val sourceSignature = sourceFile.familyFilenameSignature()
        if (sourceSignature.isBlank()) return@forEach
        val parent = sourceFile.parentFile ?: return@forEach

        Timber.tag(ReaderFontDiagnosticsTag).i(
            "epub.siblings.source family='${fontFace.fontFamily}' src='${fontFace.src}' " +
                "file='${sourceFile.name}' " +
                readerFontDiagnosticSummary(sourceFile.nameWithoutExtension)
        )

        parent.listFiles()
            ?.asSequence()
            ?.filter { candidate ->
                candidate.isFile &&
                    candidate.extension.lowercase() in supportedEpubFontExtensions &&
                    candidate.nameWithoutExtension.familyFilenameSignature() == sourceSignature
            }
            ?.forEach { candidate ->
                val variant = candidate.nameWithoutExtension.detectFontVariant()
                if (variant == null) {
                    Timber.tag(ReaderFontDiagnosticsTag).w(
                        "epub.siblings.skipNoVariant file='${candidate.name}' " +
                            readerFontDiagnosticSummary(candidate.nameWithoutExtension)
                    )
                    return@forEach
                }
                val src = candidate.toFontFaceSrc(extractionRoot)
                val inferred = fontFace.copy(
                    src = src,
                    fontWeight = variant.weight,
                    fontStyle = variant.style
                )
                if (existingKeys.add(inferred.variantKey())) {
                    Timber.tag(ReaderFontDiagnosticsTag).i(
                        "epub.siblings.add family='${fontFace.fontFamily}' src='$src' variant=$variant"
                    )
                    result += inferred
                } else {
                    Timber.tag(ReaderFontDiagnosticsTag).i(
                        "epub.siblings.skipDuplicate family='${fontFace.fontFamily}' src='$src' variant=$variant"
                    )
                }
            }
    }

    Timber.tag(ReaderFontDiagnosticsTag).i("epub.siblings.done outputCount=${result.size}")
    return result
}

fun buildEpubFontFaceCss(
    fontFaces: List<FontFaceInfo>,
    extractionPath: String
): String {
    val extractionRoot = File(extractionPath)
    return expandFontFacesWithSiblings(fontFaces, extractionPath)
        .distinctBy { it.variantKey() }
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

private fun FontFaceInfo.variantKey(): String {
    return listOf(
        fontFamily.trim().lowercase(),
        src.replace('\\', '/').lowercase(),
        fontWeight?.weight ?: FontWeight.Normal.weight,
        fontStyle ?: FontStyle.Normal
    ).joinToString(separator = "|")
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

private fun File.familyFilenameSignature(): String {
    return nameWithoutExtension.familyFilenameSignature()
}

private fun String.cssString(): String = replace("\\", "\\\\").replace("'", "\\'")

private fun String.cssUrlString(): String = replace("\\", "\\\\").replace("'", "%27")
