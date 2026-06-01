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
    NONE,
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
data class SharedPdfAnnotationComment(
    val id: String,
    val parentId: String? = null,
    val author: String = "",
    val contents: String = "",
    val createdAt: Long = 0L,
    val modifiedAt: Long = 0L
)

const val DEFAULT_SHARED_PDF_COMMENT_AUTHOR = "Reader"

fun List<SharedPdfAnnotationComment>.visiblePdfAnnotationComments(): List<SharedPdfAnnotationComment> {
    val visibleCommentIds = filter { it.contents.isNotBlank() }.map { it.id }.toSet()
    return filter { it.contents.isNotBlank() }
        .map { comment ->
            if (comment.parentId != null && comment.parentId !in visibleCommentIds) {
                comment.copy(parentId = null)
            } else {
                comment
            }
        }
}

fun List<SharedPdfAnnotationComment>.pdfCommentChildren(parentId: String?): List<SharedPdfAnnotationComment> {
    return filter { it.parentId == parentId }
        .sortedWith(compareBy({ it.createdAt.takeIf { timestamp -> timestamp > 0L } ?: Long.MAX_VALUE }, { it.id }))
}

fun List<SharedPdfAnnotationComment>.withoutPdfCommentThread(commentId: String): List<SharedPdfAnnotationComment> {
    val childrenByParentId = groupBy { it.parentId }
    val idsToRemove = mutableSetOf<String>()

    fun collect(id: String) {
        if (!idsToRemove.add(id)) return
        childrenByParentId[id].orEmpty().forEach { child -> collect(child.id) }
    }

    collect(commentId)
    return filterNot { it.id in idsToRemove }
}

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
    val comments: List<SharedPdfAnnotationComment> = emptyList(),
    val colorArgb: Int,
    val backgroundArgb: Int = 0x00FFFFFF,
    val strokeWidth: Float = 2f,
    val fontSize: Float = 16f,
    val pageRelativeFontSize: Float? = null,
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

    val highlighterPalette: List<Int> = SharedPdfAndroidHighlightColors.palette.take(4)

    fun configFor(tool: PdfInkTool): PdfToolConfig {
        return when (tool) {
            PdfInkTool.NONE -> PdfToolConfig(0x00000000, 0.008f)
            PdfInkTool.PEN -> PdfToolConfig(0xFFFF0000.toInt(), 0.008f)
            PdfInkTool.FOUNTAIN_PEN -> PdfToolConfig(0xFF0000FF.toInt(), 0.008f)
            PdfInkTool.PENCIL -> PdfToolConfig(0xFF444444.toInt(), 0.008f)
            PdfInkTool.HIGHLIGHTER -> PdfToolConfig(highlighterPalette[0], 0.035f)
            PdfInkTool.HIGHLIGHTER_ROUND -> PdfToolConfig(highlighterPalette[1], 0.035f)
            PdfInkTool.ERASER -> PdfToolConfig(0x00000000, 0.03f)
            PdfInkTool.TEXT -> PdfToolConfig(0xFF000000.toInt(), 0.02f)
        }
    }
}

data class SharedPdfHighlighterPalette(
    val colors: List<Int> = defaultColors
) {
    fun sanitized(): SharedPdfHighlighterPalette {
        val normalized = colors
            .filter { it != 0 }
            .map { it.withPdfHighlighterAlpha() }
            .take(MaxColors)
        val filled = if (normalized.isEmpty()) {
            defaultColors
        } else {
            normalized + defaultColors.drop(normalized.size)
        }
        return copy(colors = filled.take(MaxColors))
    }

    fun withColorAt(slotIndex: Int, colorArgb: Int): SharedPdfHighlighterPalette {
        val nextColors = sanitized().colors.toMutableList()
        if (slotIndex !in nextColors.indices) return sanitized()
        nextColors[slotIndex] = colorArgb.withPdfHighlighterAlpha()
        return copy(colors = nextColors).sanitized()
    }

    companion object {
        const val DefaultAlpha: Int = 0x8C
        const val MaxColors: Int = 4
        val defaultColors: List<Int>
            get() = SharedPdfAnnotationDefaults.highlighterPalette
                .take(MaxColors)
                .map { it.withPdfHighlighterAlpha() }
    }
}

object SharedPdfAndroidHighlightColors {
    const val StoredAlpha: Int = 0x8C
    const val RenderAlpha: Float = 0.4f

    val orderedNames: List<String> = listOf("ORANGE", "YELLOW", "GREEN", "BLUE", "PURPLE")

    val colorsByName: Map<String, Int> = mapOf(
        "ORANGE" to 0xFFFF9800.toInt(),
        "YELLOW" to 0xFFFFEB3B.toInt(),
        "GREEN" to 0xFF81C784.toInt(),
        "BLUE" to 0xFF64B5F6.toInt(),
        "PURPLE" to 0xFFE1BEE7.toInt()
    )

    val palette: List<Int>
        get() = orderedNames.map(::argbForName)

    fun argbForName(name: String): Int {
        val opaqueArgb = colorsByName[name.uppercase()] ?: colorsByName.getValue("ORANGE")
        return (StoredAlpha shl 24) or (opaqueArgb and 0x00FFFFFF)
    }

    fun nearestName(argb: Int): String {
        val rgb = argb and 0x00FFFFFF
        return colorsByName.minByOrNull { (_, color) ->
            val candidate = color and 0x00FFFFFF
            val dr = ((rgb shr 16) and 0xFF) - ((candidate shr 16) and 0xFF)
            val dg = ((rgb shr 8) and 0xFF) - ((candidate shr 8) and 0xFF)
            val db = (rgb and 0xFF) - (candidate and 0xFF)
            dr * dr + dg * dg + db * db
        }?.key ?: "ORANGE"
    }

    fun nearestArgb(argb: Int): Int {
        return argbForName(nearestName(argb))
    }
}

private fun Int.withPdfHighlighterAlpha(): Int {
    return (SharedPdfHighlighterPalette.DefaultAlpha shl 24) or (this and 0x00FFFFFF)
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
