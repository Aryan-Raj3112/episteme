package com.aryan.reader.pdf

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.LruCache
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.aryan.reader.pdf.data.PdfAnnotation
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import android.graphics.Color as AndroidColor

data class PdfTile(val bitmap: Bitmap, val renderRect: Rect, val tileId: Int, val renderScale: Float = 1f)

object PdfInkGeometry {
    fun calculateFountainPenPoints(
        points: List<PdfPoint>, baseWidth: Float, pageWidth: Float, pageHeight: Float
    ): Pair<List<Offset>, List<Offset>> {
        if (points.size < 2) return Pair(emptyList(), emptyList())

        if (points.size % 50 == 0) {
            Timber.tag("FountainPenDebug").d(
                "Calculate Points: PWidth=$pageWidth, PHeight=$pageHeight, BaseW=$baseWidth, Pts=${points.size}"
            )
        }

        val leftSide = mutableListOf<Offset>()
        val rightSide = mutableListOf<Offset>()

        val computedWidths = FloatArray(points.size)
        computedWidths[0] = baseWidth

        val velocityFactor = 300f

        for (i in 1 until points.size) {
            val p1 = points[i - 1]
            val p2 = points[i]

            val dxNorm = p2.x - p1.x
            val dyNorm = p2.y - p1.y
            val aspect = if (pageWidth > 0 && pageHeight > 0) pageHeight / pageWidth else 1f
            val distNorm = sqrt(dxNorm * dxNorm + (dyNorm * aspect) * (dyNorm * aspect))

            val timeDelta = (p2.timestamp - p1.timestamp).coerceAtLeast(1)
            val velocityNorm = distNorm / timeDelta

            val targetWidth = (baseWidth * (1f / (1f + velocityNorm * velocityFactor))).coerceIn(
                baseWidth * 0.2f, baseWidth * 1.4f
            )

            computedWidths[i] = computedWidths[i - 1] * 0.6f + targetWidth * 0.4f

            if (i < 5) {
                Timber.tag("FountainPenDebug").v(
                    "Pt[$i]: dt=$timeDelta, velNorm=$velocityNorm, width=${computedWidths[i]} (base=$baseWidth)"
                )
            }
        }

        for (i in 0 until points.size - 1) {
            val pCurrent = points[i]
            val pNext = points[i + 1]

            val curX = pCurrent.x * pageWidth
            val curY = pCurrent.y * pageHeight
            val nextX = pNext.x * pageWidth
            val nextY = pNext.y * pageHeight

            val angle = atan2(nextY - curY, nextX - curX)
            val normalAngle = angle - (PI / 2f).toFloat()

            val w = computedWidths[i] / 2f

            leftSide.add(Offset((curX + cos(normalAngle) * w), (curY + sin(normalAngle) * w)))
            rightSide.add(Offset((curX - cos(normalAngle) * w), (curY - sin(normalAngle) * w)))
        }

        val lastIdx = points.lastIndex
        val lastP = points[lastIdx]
        val prevP = points[lastIdx - 1]

        val lastX = lastP.x * pageWidth
        val lastY = lastP.y * pageHeight
        val prevX = prevP.x * pageWidth
        val prevY = prevP.y * pageHeight

        val lastAngle = atan2(lastY - prevY, lastX - prevX)
        val lastNormal = lastAngle - (PI / 2f).toFloat()
        val lastW = computedWidths[lastIdx] / 2f

        leftSide.add(Offset((lastX + cos(lastNormal) * lastW), (lastY + sin(lastNormal) * lastW)))
        rightSide.add(Offset((lastX - cos(lastNormal) * lastW), (lastY - sin(lastNormal) * lastW)))

        return Pair(leftSide, rightSide)
    }
}

internal object PdfBitmapPool {
    private val pool = ConcurrentLinkedQueue<Bitmap>()
    private const val MAX_POOL_SIZE = 4

    fun get(width: Int, height: Int): Bitmap {
        val iterator = pool.iterator()
        while (iterator.hasNext()) {
            val bitmap = iterator.next()
            if (bitmap.width == width && bitmap.height == height && !bitmap.isRecycled) {
                iterator.remove()
                bitmap.eraseColor(AndroidColor.TRANSPARENT)
                return bitmap
            }
        }
        return createBitmap(width, height)
    }

    fun get(size: Int): Bitmap = get(size, size)

    fun recycle(bitmap: Bitmap) {
        // Overflow bitmaps are left for GC; HWUI may still reference recently drawn bitmaps.
        if (!bitmap.isRecycled && pool.size < MAX_POOL_SIZE) {
            pool.offer(bitmap)
        }
    }

    fun clear() {
        while (!pool.isEmpty()) {
            pool.poll()
        }
    }
}

internal object PdfThumbnailCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8

    private data class CacheEntry(val bitmap: Bitmap, val sizeKb: Int)

    private val memoryCache = object : LruCache<String, CacheEntry>(cacheSize) {
        override fun sizeOf(key: String, entry: CacheEntry): Int {
            return entry.sizeKb
        }
    }

    fun get(pageId: String): Bitmap? {
        return memoryCache.get(pageId)?.bitmap?.takeUnless { it.isRecycled }
    }

    fun put(pageId: String, bitmap: Bitmap) {
        if (get(pageId) == null) {
            val sizeKb = (bitmap.allocationByteCount / 1024).coerceAtLeast(1)
            memoryCache.put(pageId, CacheEntry(bitmap, sizeKb))
        }
    }

    fun clear() {
        memoryCache.evictAll()
    }
}

internal object PdfTextureGenerator {
    private var noiseBitmap: Bitmap? = null

    fun getNoiseTexture(): Bitmap {
        if (noiseBitmap == null) {
            val size = 256
            val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    val isGrain = Math.random() > 0.4
                    if (isGrain) {
                        val alpha = (Math.random() * 100 + 100).toInt()
                        bitmap[x, y] = AndroidColor.argb(alpha, 0, 0, 0)
                    } else {
                        bitmap[x, y] = AndroidColor.TRANSPARENT
                    }
                }
            }
            noiseBitmap = bitmap
        }
        return noiseBitmap!!
    }
}

internal sealed interface AnnotationRenderData {
    data class Standard(
        val path: Path,
        val color: Color,
        val strokeWidth: Float,
        val cap: StrokeCap,
        val blendMode: BlendMode
    ) : AnnotationRenderData

    data class Fountain(val path: Path, val color: Color) : AnnotationRenderData

    data class Pencil(
        val path: android.graphics.Path,
        val color: Color,
        val strokeWidth: Float,
        val velocityAlpha: Float
    ) : AnnotationRenderData
}

internal object PdfAnnotationRenderHelper {
    fun createRenderData(annot: PdfAnnotation, widthPx: Int, heightPx: Int): AnnotationRenderData? {
        val startTime = System.nanoTime()
        if (annot.points.isEmpty()) return null

        if (annot.points.size == 1) {
            val point = annot.points[0]
            val x = point.x * widthPx
            val y = point.y * heightPx

            val path = if (annot.inkType == InkType.PENCIL) android.graphics.Path() else Path()

            if (path is android.graphics.Path) {
                path.moveTo(x, y)
                path.lineTo(x, y)
                return AnnotationRenderData.Pencil(
                    path = path,
                    color = annot.color,
                    strokeWidth = annot.strokeWidth * widthPx,
                    velocityAlpha = 1.0f
                )
            } else if (path is Path) {
                if (annot.inkType == InkType.FOUNTAIN_PEN) {
                    val radius = (annot.strokeWidth * widthPx) / 2f
                    path.addOval(
                        androidx.compose.ui.geometry.Rect(
                            center = Offset(x, y), radius = radius
                        )
                    )
                    return AnnotationRenderData.Fountain(path = path, color = annot.color)
                }

                path.moveTo(x, y)
                path.lineTo(x, y)

                val cap = when (annot.inkType) {
                    InkType.HIGHLIGHTER -> StrokeCap.Butt
                    InkType.HIGHLIGHTER_ROUND -> StrokeCap.Round
                    else -> StrokeCap.Round
                }

                return AnnotationRenderData.Standard(
                    path = path,
                    color = annot.color,
                    strokeWidth = annot.strokeWidth * widthPx,
                    cap = cap,
                    blendMode = BlendMode.SrcOver
                )
            }
        }

        val result = when (annot.inkType) {
            InkType.PENCIL -> {
                val path = android.graphics.Path()
                val first = annot.points[0]
                path.moveTo(first.x * widthPx, first.y * heightPx)
                var totalDist = 0f
                for (i in 1 until annot.points.size) {
                    val p0 = annot.points[i - 1]
                    val p1 = annot.points[i]
                    val p0x = p0.x * widthPx
                    val p0y = p0.y * heightPx
                    val p1x = p1.x * widthPx
                    val p1y = p1.y * heightPx
                    val midX = (p0x + p1x) / 2f
                    val midY = (p0y + p1y) / 2f
                    val dx = p1x - p0x
                    val dy = p1y - p0y
                    totalDist += sqrt(dx * dx + dy * dy)

                    if (i == 1) path.lineTo(midX, midY)
                    else path.quadTo(p0x, p0y, midX, midY)
                }
                val last = annot.points.last()
                path.lineTo(last.x * widthPx, last.y * heightPx)

                val duration =
                    (annot.points.last().timestamp - annot.points.first().timestamp).coerceAtLeast(1)
                val velocity = totalDist / duration
                val velocityAlphaFactor = (1f - (velocity - 0.2f) / 1.8f).coerceIn(0.4f, 1.0f)

                AnnotationRenderData.Pencil(
                    path = path,
                    color = annot.color,
                    strokeWidth = annot.strokeWidth * widthPx,
                    velocityAlpha = velocityAlphaFactor
                )
            }

            InkType.FOUNTAIN_PEN -> {
                val baseStrokeWidth = annot.strokeWidth * widthPx
                val path = Path()

                val (leftSide, rightSide) = PdfInkGeometry.calculateFountainPenPoints(
                    annot.points, baseStrokeWidth, widthPx.toFloat(), heightPx.toFloat()
                )

                if (leftSide.isNotEmpty()) {
                    path.moveTo(leftSide[0].x, leftSide[0].y)

                    for (i in 1 until leftSide.size) {
                        path.lineTo(leftSide[i].x, leftSide[i].y)
                    }

                    for (i in rightSide.size - 1 downTo 0) {
                        path.lineTo(rightSide[i].x, rightSide[i].y)
                    }

                    path.close()
                }

                AnnotationRenderData.Fountain(path = path, color = annot.color)
            }

            else -> {
                val path = Path()
                val first = annot.points[0]
                path.moveTo(first.x * widthPx, first.y * heightPx)
                for (i in 1 until annot.points.size) {
                    val p0 = annot.points[i - 1]
                    val p1 = annot.points[i]
                    val p0x = p0.x * widthPx
                    val p0y = p0.y * heightPx
                    val p1x = p1.x * widthPx
                    val p1y = p1.y * heightPx
                    val midX = (p0x + p1x) / 2f
                    val midY = (p0y + p1y) / 2f
                    if (i == 1) path.lineTo(midX, midY)
                    else path.quadraticTo(p0x, p0y, midX, midY)
                }
                val last = annot.points.last()
                path.lineTo(last.x * widthPx, last.y * heightPx)

                val blendMode = when (annot.inkType) {
                    InkType.HIGHLIGHTER, InkType.HIGHLIGHTER_ROUND -> BlendMode.Multiply
                    else -> BlendMode.SrcOver
                }

                val cap = when (annot.inkType) {
                    InkType.HIGHLIGHTER -> StrokeCap.Butt
                    InkType.HIGHLIGHTER_ROUND -> StrokeCap.Round
                    else -> StrokeCap.Round
                }

                AnnotationRenderData.Standard(
                    path = path,
                    color = annot.color,
                    strokeWidth = annot.strokeWidth * widthPx,
                    cap = cap,
                    blendMode = blendMode
                )
            }
        }
        val duration = (System.nanoTime() - startTime) / 1_000_000f
        if (duration > 1f) {
            Timber.tag("PdfPerf").v("Path Gen: Type=${annot.inkType}, Pts=${annot.points.size}, Time=${duration}ms")
        }
        return result
    }
}

@Stable
class PdfDrawingState {
    var currentAnnotation by mutableStateOf<PdfAnnotation?>(null)
        private set
    private val currentPoints = mutableListOf<PdfPoint>()

    fun onDrawStart(pageIndex: Int, point: PdfPoint, type: InkType, color: Color, width: Float) {
        currentPoints.clear()
        currentPoints.add(point)
        currentAnnotation = PdfAnnotation(
            type = AnnotationType.INK,
            inkType = type,
            pageIndex = pageIndex,
            points = currentPoints.toList(),
            color = color,
            strokeWidth = width
        )
    }

    fun onDraw(point: PdfPoint) {
        currentPoints.add(point)
        currentAnnotation = currentAnnotation?.copy(points = currentPoints.toList())
    }

    fun onDrawCancel() {
        currentAnnotation = null
        currentPoints.clear()
    }

    fun onDrawEnd(): PdfAnnotation? {
        val finalAnnot = currentAnnotation
        currentAnnotation = null
        currentPoints.clear()
        return finalAnnot
    }

    fun updateDrag(point: PdfPoint) {
        if (currentPoints.isNotEmpty()) {
            val start = currentPoints.first()
            currentPoints.clear()
            currentPoints.add(start)
            currentPoints.add(point)
            currentAnnotation = currentAnnotation?.copy(points = currentPoints.toList())
        }
    }
}
