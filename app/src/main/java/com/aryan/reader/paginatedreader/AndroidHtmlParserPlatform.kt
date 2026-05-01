package com.aryan.reader.paginatedreader

import android.graphics.BitmapFactory
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.net.URLDecoder
import java.nio.file.Paths

object AndroidHtmlResourceResolver : HtmlResourceResolver {
    override fun resolvePath(chapterAbsPath: String, extractionBasePath: String, src: String): String? {
        if (src.isBlank()) return null
        val decodedSrc = try {
            URLDecoder.decode(src, "UTF-8")
        } catch (_: Exception) {
            src
        }
        val parentPath = File(chapterAbsPath).parent ?: ""
        val relativePath = Paths.get(parentPath, decodedSrc).normalize().toString()
        val fromRelativeFile = File(extractionBasePath, relativePath)

        return try {
            when {
                fromRelativeFile.exists() -> fromRelativeFile.canonicalFile.absolutePath
                File(extractionBasePath, decodedSrc).exists() -> File(extractionBasePath, decodedSrc).canonicalFile.absolutePath
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun readText(path: String): String? {
        return runCatching { File(path).readText() }.getOrNull()
    }

    override fun imageDimensions(path: String): Pair<Float?, Float?>? {
        return runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                options.outWidth.toFloat() to options.outHeight.toFloat()
            } else {
                null
            }
        }.getOrNull()
    }
}

object AndroidHtmlFontFamilyLoader : HtmlFontFamilyLoader {
    override fun load(fontFaces: List<FontFaceInfo>, extractionBasePath: String): Map<String, FontFamily> {
        return loadFontFamilies(fontFaces, extractionBasePath)
    }
}

fun androidHtmlToSemanticBlocks(
    html: String,
    cssRules: OptimizedCssRules,
    textStyle: androidx.compose.ui.text.TextStyle,
    chapterAbsPath: String,
    extractionBasePath: String,
    density: androidx.compose.ui.unit.Density,
    fontFamilyMap: Map<String, FontFamily>,
    constraints: androidx.compose.ui.unit.Constraints,
    imageDimensionsCache: Map<String, Pair<Float, Float>> = emptyMap(),
    mathSvgCache: Map<String, String> = emptyMap()
): List<SemanticBlock> {
    return htmlToSemanticBlocks(
        html = html,
        cssRules = cssRules,
        textStyle = textStyle,
        chapterAbsPath = chapterAbsPath,
        extractionBasePath = extractionBasePath,
        density = density,
        fontFamilyMap = fontFamilyMap,
        constraints = constraints,
        imageDimensionsCache = imageDimensionsCache,
        mathSvgCache = mathSvgCache,
        resourceResolver = AndroidHtmlResourceResolver,
        fontFamilyLoader = AndroidHtmlFontFamilyLoader
    )
}
