package com.aryan.reader.pdf

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
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
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.buildPdfInkCubicSegments
import com.aryan.reader.shared.pdf.canPoolPdfBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.aryan.reader.pdf.data.PdfAnnotation
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.IdentityHashMap
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
    private val maxHeapBytes = Runtime.getRuntime().maxMemory()

    private fun pooledBytes(): Long = pool.sumOf { bitmap ->
        if (bitmap.isRecycled) 0L else bitmap.allocationByteCount.toLong()
    }

    @Synchronized
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
        return try {
            createBitmap(width, height)
        } catch (error: OutOfMemoryError) {
            // Mismatched buffers are useless for this request. Drop our references and retry once;
            // never call Bitmap.recycle because HWUI may still hold a recently drawn bitmap.
            pool.clear()
            createBitmap(width, height)
        }
    }

    fun get(size: Int): Bitmap = get(size, size)

    fun recycle(bitmap: Bitmap) {
        // A reverse-colour transform may be reading this bitmap on a worker
        // thread while the page is being replaced.  Do not return it to the
        // pool until that read has completed; otherwise the next page can
        // reuse the same native buffer and Bitmap.getPixels() aborts the app.
        PdfBitmapUseRegistry.deferRecycle(bitmap) {
            synchronized(this) {
                // Overflow bitmaps are left for GC; HWUI may still reference
                // recently drawn bitmaps.
                if (!bitmap.isRecycled &&
                    pool.size < MAX_POOL_SIZE &&
                    canPoolPdfBitmap(pooledBytes(), bitmap.allocationByteCount.toLong(), maxHeapBytes)
                ) {
                    pool.offer(bitmap)
                }
            }
        }
    }

    @Synchronized
    fun clear() {
        while (!pool.isEmpty()) {
            pool.poll()
        }
    }
}

/**
 * Small ownership gate for Bitmap work that outlives a composition frame.
 *
 * Compose can dispose a page while a nonlinear reverse-colour conversion is
 * still running. Android's Bitmap API aborts the process (rather than throwing)
 * when getPixels/copy races with recycle or pool reuse, so every asynchronous
 * reader must hold a lease and every recycle must pass through this gate.
 */
internal object PdfBitmapUseRegistry {
    private val activeLeases = IdentityHashMap<Bitmap, Int>()
    private val pendingRecycles = IdentityHashMap<Bitmap, (() -> Unit)>()

    fun <T> withLease(bitmap: Bitmap, block: () -> T): T? {
        synchronized(this) {
            if (bitmap.isRecycled) return null
            activeLeases[bitmap] = (activeLeases[bitmap] ?: 0) + 1
        }
        return try {
            block()
        } finally {
            synchronized(this) {
                val remaining = (activeLeases[bitmap] ?: 1) - 1
                if (remaining > 0) {
                    activeLeases[bitmap] = remaining
                } else {
                    val pending = pendingRecycles.remove(bitmap)
                    // Keep the registry lock while the deferred action runs.
                    // If it ran after activeLeases was removed, a new lease
                    // could be acquired in the gap and then be recycled by
                    // this stale action.
                    try {
                        pending?.invoke()
                    } finally {
                        activeLeases.remove(bitmap)
                    }
                }
            }
        }
    }

    fun deferRecycle(bitmap: Bitmap, action: () -> Unit) {
        val runNow = synchronized(this) {
            if ((activeLeases[bitmap] ?: 0) > 0) {
                // Keep the first action. A later caller may be a duplicate
                // cleanup path; running both would double-pool/recycle.
                pendingRecycles.putIfAbsent(bitmap, action)
                false
            } else {
                true
            }
        }
        if (runNow) action()
    }
}

internal fun safeRecyclePdfBitmap(bitmap: Bitmap?) {
    if (bitmap == null) return
    PdfBitmapUseRegistry.deferRecycle(bitmap) {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

/**
 * Retires transformed bitmaps only after HWUI has had several frame boundaries
 * to drop the old display list. Recycling a bitmap directly from a Compose
 * coroutine is unsafe: the coroutine can be cancelled before the render node
 * has stopped referring to the bitmap and libhwui aborts the process instead
 * of reporting a recoverable exception.
 *
 * This queue deliberately keeps a strong reference until retirement. That is a
 * bounded, short-lived cost and is preferable to retaining every cancelled
 * transform until an arbitrary native GC pass. Calling [retain] cancels a
 * pending retirement when a cached tile becomes visible again.
 */
internal object PdfReverseBitmapRetirement {
    private const val RETIRE_AFTER_FRAMES = 4

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = IdentityHashMap<Bitmap, Long>()
    private var nextToken = 0L

    fun retain(bitmap: Bitmap?) {
        if (bitmap == null) return
        runOnMain {
            synchronized(this) {
                pending.remove(bitmap)
            }
        }
    }

    fun schedule(bitmap: Bitmap?, reason: String) {
        if (bitmap == null || bitmap.isRecycled) return
        runOnMain {
            val token = synchronized(this) {
                if (bitmap.isRecycled || pending.containsKey(bitmap)) return@synchronized null
                nextToken += 1
                pending[bitmap] = nextToken
                nextToken
            } ?: return@runOnMain

            Timber.tag("PdfReversePerf").d(
                "bitmap-retire-scheduled bytes=${bitmap.allocationByteCount} " +
                    "frames=$RETIRE_AFTER_FRAMES reason=$reason"
            )
            awaitFrame(bitmap, token, RETIRE_AFTER_FRAMES)
        }
    }

    private fun awaitFrame(bitmap: Bitmap, token: Long, remainingFrames: Int) {
        Choreographer.getInstance().postFrameCallback {
            val stillPending = synchronized(this) { pending[bitmap] == token }
            if (!stillPending) return@postFrameCallback

            if (remainingFrames > 1) {
                awaitFrame(bitmap, token, remainingFrames - 1)
                return@postFrameCallback
            }

            synchronized(this) {
                if (pending[bitmap] == token) pending.remove(bitmap)
            }
            if (!bitmap.isRecycled) {
                val bytes = bitmap.allocationByteCount
                safeRecyclePdfBitmap(bitmap)
                Timber.tag("PdfReversePerf").d(
                    "bitmap-retired bytes=$bytes"
                )
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else mainHandler.post(block)
    }
}

/**
 * A mode change invalidates every in-flight nonlinear transform, including
 * work belonging to a page that is still retained just outside the viewport.
 * The transform loop checks this generation per row so the newest mode owns
 * the single worker slot without waiting for stale multi-megapixel work.
 */
internal object PdfReverseTransformGeneration {
    private var generation = 0L
    private var activeMode: com.aryan.reader.shared.pdf.PdfReverseColorMode? = null

    @Synchronized
    fun begin(mode: com.aryan.reader.shared.pdf.PdfReverseColorMode): Long {
        if (activeMode != mode) {
            activeMode = mode
            generation += 1
            Timber.tag("PdfReversePerf").d("transform-generation=$generation mode=${mode.id}")
        }
        return generation
    }

    @Synchronized
    fun isCurrent(candidate: Long): Boolean = candidate == generation
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
                buildPdfInkCubicSegments(
                    points = annot.points.map { PdfPagePoint(it.x, it.y, it.timestamp) },
                    scaleX = widthPx.toFloat(),
                    scaleY = heightPx.toFloat(),
                ).forEach { segment ->
                    path.cubicTo(
                        segment.control1.x, segment.control1.y,
                        segment.control2.x, segment.control2.y,
                        segment.end.x, segment.end.y,
                    )
                }

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
        if (currentAnnotation == null) return
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
