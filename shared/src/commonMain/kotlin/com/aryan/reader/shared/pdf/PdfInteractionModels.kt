package com.aryan.reader.shared.pdf

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

enum class PdfAnnotationKind {
    INK,
    TEXT,
    HIGHLIGHT
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
    val boundsList: List<PdfPageBounds> = emptyList(),
    val text: String = "",
    val note: String? = null,
    val colorArgb: Int,
    val backgroundArgb: Int = 0x00FFFFFF,
    val strokeWidth: Float = 2f,
    val fontSize: Float = 16f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikeThrough: Boolean = false,
    val fontPath: String? = null,
    val fontName: String? = null,
    val rangeStartIndex: Int? = null,
    val rangeEndIndex: Int? = null,
    val createdAt: Long = 0L
)

@Serializable
data class SharedPdfEmbeddedAnnotation(
    val id: String,
    val pageIndex: Int,
    val index: Int,
    val subtype: Int,
    val bounds: PdfPageBounds,
    val contents: String = "",
    val author: String = "",
    val name: String = "",
    val inReplyTo: String = "",
    val replies: List<SharedPdfEmbeddedAnnotation> = emptyList()
) {
    val hasVisibleText: Boolean
        get() = contents.isNotBlank() || replies.any { it.hasVisibleText }
}

object SharedPdfEmbeddedAnnotationThreads {
    fun group(
        annotations: List<SharedPdfEmbeddedAnnotation>,
        geometryTolerance: Float = 0.02f
    ): List<SharedPdfEmbeddedAnnotation> {
        if (annotations.isEmpty()) return emptyList()

        val byName = annotations
            .filter { it.name.isNotBlank() }
            .associateBy { it.name }
        val childrenByParentId = mutableMapOf<String, MutableList<SharedPdfEmbeddedAnnotation>>()
        val roots = mutableListOf<SharedPdfEmbeddedAnnotation>()

        annotations.forEach { annotation ->
            val parent = byName[annotation.inReplyTo]
            if (parent != null && parent.id != annotation.id) {
                childrenByParentId.getOrPut(parent.id) { mutableListOf() } += annotation
            } else {
                roots += annotation
            }
        }

        fun attachReplies(
            annotation: SharedPdfEmbeddedAnnotation,
            visitedIds: Set<String> = emptySet()
        ): SharedPdfEmbeddedAnnotation {
            if (annotation.id in visitedIds) return annotation.copy(replies = emptyList())
            val nextVisited = visitedIds + annotation.id
            val replies = childrenByParentId[annotation.id]
                .orEmpty()
                .map { attachReplies(it, nextVisited) }
            return annotation.copy(replies = annotation.replies + replies)
        }

        val groupedRoots = mutableListOf<MutableList<SharedPdfEmbeddedAnnotation>>()
        roots.map { attachReplies(it) }.forEach { annotation ->
            val group = groupedRoots.firstOrNull { existingGroup ->
                existingGroup.firstOrNull()?.bounds?.inflatedBy(geometryTolerance)?.intersects(annotation.bounds) == true
            }
            if (group == null) {
                groupedRoots += mutableListOf(annotation)
            } else {
                group += annotation
            }
        }

        return groupedRoots
            .mapNotNull { group ->
                val root = group.firstOrNull() ?: return@mapNotNull null
                root.copy(replies = root.replies + group.drop(1))
            }
            .filter { it.hasVisibleText }
    }
}

data class PdfToolConfig(
    val colorArgb: Int,
    val strokeWidth: Float
)

object SharedPdfAnnotationDefaults {
    val penPalette: List<Int> = listOf(
        0xFF000000.toInt(),
        0xFFFF0000.toInt(),
        0xFF0000FF.toInt(),
        0xFF4CAF50.toInt(),
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
            PdfInkTool.PEN -> PdfToolConfig(0xFFFF0000.toInt(), 0.008f)
            PdfInkTool.FOUNTAIN_PEN -> PdfToolConfig(0xFF0000FF.toInt(), 0.008f)
            PdfInkTool.PENCIL -> PdfToolConfig(0xFF444444.toInt(), 0.008f)
            PdfInkTool.HIGHLIGHTER -> PdfToolConfig(0x8CFF9800.toInt(), 0.035f)
            PdfInkTool.HIGHLIGHTER_ROUND -> PdfToolConfig(0x8CFFEB3B.toInt(), 0.035f)
            PdfInkTool.ERASER -> PdfToolConfig(0x00000000, 0.03f)
            PdfInkTool.TEXT -> PdfToolConfig(0xFF000000.toInt(), 0.02f)
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

private fun PdfPageBounds.inflatedBy(amount: Float): PdfPageBounds {
    return PdfPageBounds(
        left = (left - amount).coerceAtLeast(0f),
        top = (top - amount).coerceAtLeast(0f),
        right = (right + amount).coerceAtMost(1f),
        bottom = (bottom + amount).coerceAtMost(1f)
    )
}

private fun PdfPageBounds.intersects(other: PdfPageBounds): Boolean {
    return left <= other.right &&
        right >= other.left &&
        top <= other.bottom &&
        bottom >= other.top
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
