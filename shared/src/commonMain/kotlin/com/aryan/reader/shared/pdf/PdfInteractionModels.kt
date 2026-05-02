package com.aryan.reader.shared.pdf

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

enum class PdfAnnotationKind {
    INK,
    TEXT
}

enum class PdfInkTool {
    PEN,
    HIGHLIGHTER,
    HIGHLIGHTER_ROUND,
    ERASER,
    FOUNTAIN_PEN,
    PENCIL,
    TEXT
}

@Serializable
data class PdfPagePoint(
    val x: Float,
    val y: Float,
    val timestamp: Long = 0L
)

@Serializable
data class PdfPageBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

@Serializable
data class SharedPdfAnnotation(
    val id: String,
    val pageIndex: Int,
    val kind: PdfAnnotationKind,
    val tool: PdfInkTool = PdfInkTool.PEN,
    val points: List<PdfPagePoint> = emptyList(),
    val bounds: PdfPageBounds? = null,
    val text: String = "",
    val colorArgb: Int,
    val backgroundArgb: Int = 0x00FFFFFF,
    val strokeWidth: Float = 2f,
    val fontSize: Float = 16f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val createdAt: Long = 0L
)

data class PdfToolConfig(
    val colorArgb: Int,
    val strokeWidth: Float
)

object SharedPdfAnnotationDefaults {
    val penPalette: List<Int> = listOf(
        0xFF111111.toInt(),
        0xFFD32F2F.toInt(),
        0xFF1976D2.toInt(),
        0xFF388E3C.toInt(),
        0xFFFFFFFF.toInt()
    )

    val highlighterPalette: List<Int> = listOf(
        0x8CFF9800.toInt(),
        0x8CFFEB3B.toInt(),
        0x8C81C784.toInt(),
        0x8C64B5F6.toInt(),
        0x8CE1BEE7.toInt()
    )

    fun configFor(tool: PdfInkTool): PdfToolConfig {
        return when (tool) {
            PdfInkTool.PEN -> PdfToolConfig(0xFF111111.toInt(), 2.5f)
            PdfInkTool.FOUNTAIN_PEN -> PdfToolConfig(0xFF111111.toInt(), 3.5f)
            PdfInkTool.PENCIL -> PdfToolConfig(0xFF616161.toInt(), 1.8f)
            PdfInkTool.HIGHLIGHTER -> PdfToolConfig(0x8CFFEB3B.toInt(), 12f)
            PdfInkTool.HIGHLIGHTER_ROUND -> PdfToolConfig(0x8CFF9800.toInt(), 16f)
            PdfInkTool.ERASER -> PdfToolConfig(0x00000000, 18f)
            PdfInkTool.TEXT -> PdfToolConfig(0xFF111111.toInt(), 1f)
        }
    }
}

@Serializable
data class SharedPdfAnnotationStore(
    val version: Int = 1,
    val annotations: List<SharedPdfAnnotation> = emptyList()
)

object SharedPdfAnnotationSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun encode(annotations: List<SharedPdfAnnotation>): String {
        return json.encodeToString(SharedPdfAnnotationStore(annotations = annotations))
    }

    fun decode(raw: String): List<SharedPdfAnnotation> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<SharedPdfAnnotationStore>(raw).annotations
        }.getOrElse {
            runCatching { json.decodeFromString<List<SharedPdfAnnotation>>(raw) }.getOrDefault(emptyList())
        }
    }
}

data class PdfZoomSpec(
    val min: Float = 0.65f,
    val max: Float = 3.0f,
    val default: Float = 1.35f,
    val maxRenderPixels: Int = 18_000_000
) {
    fun clamp(value: Float): Float = value.coerceIn(min, max)

    fun safeRenderScale(pageWidth: Float, pageHeight: Float, requestedScale: Float): Float {
        val clamped = clamp(requestedScale)
        val pixelCount = pageWidth * pageHeight * clamped * clamped
        if (pixelCount <= maxRenderPixels) return clamped
        val fitScale = kotlin.math.sqrt(maxRenderPixels / (pageWidth * pageHeight))
        return fitScale.coerceAtMost(clamped).coerceAtLeast(0.1f)
    }

    fun renderSize(pageWidth: Float, pageHeight: Float, requestedScale: Float): Pair<Int, Int> {
        val renderScale = safeRenderScale(pageWidth, pageHeight, requestedScale)
        return (pageWidth * renderScale).roundToInt().coerceAtLeast(1) to
            (pageHeight * renderScale).roundToInt().coerceAtLeast(1)
    }
}
