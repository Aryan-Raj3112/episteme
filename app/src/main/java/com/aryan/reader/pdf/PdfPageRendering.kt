// PdfPageComposable
@file:Suppress(
    "RemoveRedundantQualifierName", "COMPOSE_APPLIER_CALL_MISMATCH", "UnusedVariable", "unused"
)
package com.aryan.reader.pdf

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.zIndex
import androidx.core.graphics.scale
import com.aryan.reader.R
import com.aryan.reader.isCanvasSafeBitmap
import com.aryan.reader.ml.SpeechBubble
import com.aryan.reader.pdf.data.PdfAnnotation
import com.aryan.reader.pdf.data.PdfTextBox
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.ui.SharedSelectionMenuRect
import com.aryan.reader.shared.ui.SharedSelectionMenuSize
import com.aryan.reader.shared.ui.SharedSelectionMenuViewport
import com.aryan.reader.shared.ui.SharedPdfRichTextLayer
import com.aryan.reader.shared.ui.sharedSelectionMenuPlacement
import com.aryan.reader.shared.pdf.PdfReverseColorMode
import com.aryan.reader.shared.pdf.PdfReverseColorRect
import com.aryan.reader.shared.pdf.invertPdfArgbIfUnprotected
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.IdentityHashMap
import android.os.SystemClock
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import android.graphics.Paint as NativePaint

// Nonlinear conversion is intentionally serialized. A fling can invalidate
// several page/tile jobs in one frame; allowing every cancelled job to copy a
// multi-megapixel bitmap concurrently causes a native-memory spike and blocks
// the renderer long enough to produce jank. Row-level cancellation below lets
// the newest visible job take over quickly.
private val pdfReverseTransformMutex = Mutex()
private const val MAX_REVERSE_TILE_CACHE_ENTRIES = 16
private const val MAX_REVERSE_TILE_CACHE_BYTES = 32L * 1024L * 1024L


@Composable
internal fun OcrProcessingIndicator(position: Offset) {
    val infiniteTransition = rememberInfiniteTransition(label = "ocr_indicator_transition")
    val animatedRadius by infiniteTransition.animateFloat(
        initialValue = 20f, targetValue = 120f, animationSpec = infiniteRepeatable(
            animation = tween(1200), repeatMode = RepeatMode.Restart
        ), label = "ocr_radius"
    )
    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 0f, animationSpec = infiniteRepeatable(
            animation = tween(1200), repeatMode = RepeatMode.Restart
        ), label = "ocr_alpha"
    )

    val color = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            color = color.copy(alpha = animatedAlpha),
            radius = animatedRadius,
            center = position,
            style = Stroke(width = (4.dp * animatedAlpha).toPx())
        )
    }
}

@Composable
internal fun PdfBitmapLayer(
    renderedBitmap: Bitmap?,
    renderedTiles: List<PdfTile>,
    shouldDrawHighResTiles: Boolean,
    effectiveScale: Float,
    centeringOffsetX: Float,
    centeringOffsetY: Float,
    @Suppress("unused") canvasWidth: Float,
    @Suppress("unused") canvasHeight: Float,
    targetWidth: Int,
    targetHeight: Int,
    colorFilter: ColorFilter? = null,
    isDarkMode: Boolean = false,
    excludeImages: Boolean = false,
    reverseColorMode: PdfReverseColorMode = PdfReverseColorMode.RGB,
    imageRects: List<android.graphics.Rect> = emptyList(),
    textureBitmap: ImageBitmap? = null,
    textureAlpha: Float = 0f,
    textureBlendMode: BlendMode = BlendMode.Multiply
) {
    val renderBitmap = renderedBitmap
    val renderTiles = renderedTiles
    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
        translate(left = centeringOffsetX, top = centeringOffsetY) {
            clipRect(left = 0f, top = 0f, right = targetWidth.toFloat(), bottom = targetHeight.toFloat()) {
                if (
                    renderBitmap != null &&
                    renderBitmap.isCanvasSafeBitmap(
                        maxBytes = PDF_MAX_DRAW_BITMAP_BYTES,
                        maxDimension = PDF_MAX_DRAW_BITMAP_DIMENSION_PX
                    )
                ) {
                    val dstW = if (targetWidth > 0) targetWidth else renderBitmap.width
                    val dstH = if (targetHeight > 0) targetHeight else renderBitmap.height
                    val srcSize = IntSize(renderBitmap.width, renderBitmap.height)
                    val dstSize = IntSize(dstW, dstH)

                    drawImage(
                        image = renderBitmap.asImageBitmap(),
                        srcOffset = IntOffset.Zero,
                        srcSize = srcSize,
                        dstOffset = IntOffset.Zero,
                        dstSize = dstSize,
                        colorFilter = colorFilter,
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.High
                    )

                    if (excludeImages && colorFilter != null && imageRects.isNotEmpty()) {
                        imageRects.forEach { rect ->
                            val scaleX = renderBitmap.width.toFloat() / dstW.toFloat()
                            val scaleY = renderBitmap.height.toFloat() / dstH.toFloat()

                            val srcRectLeft = (rect.left * scaleX).roundToInt().coerceAtLeast(0)
                            val srcRectTop = (rect.top * scaleY).roundToInt().coerceAtLeast(0)
                            val srcRectRight = (rect.right * scaleX).roundToInt().coerceAtMost(renderBitmap.width)
                            val srcRectBottom = (rect.bottom * scaleY).roundToInt().coerceAtMost(renderBitmap.height)

                            val w = srcRectRight - srcRectLeft
                            val h = srcRectBottom - srcRectTop
                            if (w > 0 && h > 0) {
                                drawImage(
                                    image = renderBitmap.asImageBitmap(),
                                    srcOffset = IntOffset(srcRectLeft, srcRectTop),
                                    srcSize = IntSize(w, h),
                                    dstOffset = IntOffset(rect.left, rect.top),
                                    dstSize = IntSize(rect.width(), rect.height()),
                                    colorFilter = null,
                                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High
                                )
                            }
                        }
                    }

                    if (shouldDrawHighResTiles) {
                        renderedTiles.forEach { tile ->
                            if (
                                tile.bitmap.isCanvasSafeBitmap(
                                    maxBytes = PDF_MAX_DRAW_BITMAP_BYTES,
                                    maxDimension = PDF_MAX_DRAW_BITMAP_DIMENSION_PX
                                )
                            ) {
                                drawImage(
                                    image = tile.bitmap.asImageBitmap(),
                                    srcOffset = IntOffset.Zero,
                                    srcSize = IntSize(tile.bitmap.width, tile.bitmap.height),
                                    dstOffset = IntOffset(tile.renderRect.left, tile.renderRect.top),
                                    dstSize = IntSize(tile.renderRect.width(), tile.renderRect.height()),
                                    colorFilter = colorFilter,
                                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High
                                )

                                if (excludeImages && colorFilter != null && imageRects.isNotEmpty()) {
                                    imageRects.forEach { imgRect ->
                                        val intersectLeft = max(imgRect.left, tile.renderRect.left)
                                        val intersectTop = max(imgRect.top, tile.renderRect.top)
                                        val intersectRight = min(imgRect.right, tile.renderRect.right)
                                        val intersectBottom = min(imgRect.bottom, tile.renderRect.bottom)

                                        val iw = intersectRight - intersectLeft
                                        val ih = intersectBottom - intersectTop

                                        if (iw > 0 && ih > 0) {
                                            val scaleXBmp = tile.bitmap.width.toFloat() / tile.renderRect.width()
                                            val scaleYBmp = tile.bitmap.height.toFloat() / tile.renderRect.height()

                                            val srcLeft = ((intersectLeft - tile.renderRect.left) * scaleXBmp).roundToInt()
                                            val srcTop = ((intersectTop - tile.renderRect.top) * scaleYBmp).roundToInt()
                                            val srcRight = ((intersectRight - tile.renderRect.left) * scaleXBmp).roundToInt()
                                            val srcBottom = ((intersectBottom - tile.renderRect.top) * scaleYBmp).roundToInt()

                                            val srcW = srcRight - srcLeft
                                            val srcH = srcBottom - srcTop

                                            if (srcW > 0 && srcH > 0) {
                                                drawImage(
                                                    image = tile.bitmap.asImageBitmap(),
                                                    srcOffset = IntOffset(srcLeft, srcTop),
                                                    srcSize = IntSize(srcW, srcH),
                                                    dstOffset = IntOffset(intersectLeft, intersectTop),
                                                    dstSize = IntSize(iw, ih),
                                                    colorFilter = null,
                                                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (textureBitmap != null && textureAlpha > 0f) {
                        drawRect(
                            brush = ShaderBrush(ImageShader(textureBitmap, TileMode.Repeated, TileMode.Repeated)),
                            size = Size(dstW.toFloat(), dstH.toFloat()),
                            blendMode = textureBlendMode,
                            alpha = textureAlpha
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun rememberPdfReverseBitmap(
    bitmap: Bitmap?,
    mode: PdfReverseColorMode,
    protectedRects: List<Rect> = emptyList(),
    targetWidth: Int = bitmap?.width ?: 0,
    targetHeight: Int = bitmap?.height ?: 0,
    targetOriginX: Int = 0,
    targetOriginY: Int = 0,
    forceBitmapTransform: Boolean = false,
    transformGeneration: Long? = null,
): Bitmap? {
    return produceState<Bitmap?>(
        initialValue = if (mode == PdfReverseColorMode.RGB && !forceBitmapTransform) bitmap else null,
        bitmap,
        mode,
        protectedRects,
        targetWidth,
        targetHeight,
        targetOriginX,
        targetOriginY,
        forceBitmapTransform,
    ) {
        val source = bitmap
        if (source == null || (mode == PdfReverseColorMode.RGB && !forceBitmapTransform)) {
            value = source
            return@produceState
        }
        val activeTransformGeneration = transformGeneration
            ?: PdfReverseTransformGeneration.begin(mode)
        var transformed: Bitmap? = null
        try {
            transformed = withContext(Dispatchers.Default) {
                source.copyWithPdfReverseColor(
                    mode = mode,
                    protectedRects = protectedRects,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight,
                    targetOriginX = targetOriginX,
                    targetOriginY = targetOriginY,
                    forceBitmapTransform = forceBitmapTransform,
                    transformGeneration = activeTransformGeneration,
                )
            }
            value = transformed
            // Bitmap.recycle() is process-fatal when HWUI still has a reference
            // to the image from a previous frame. Retire it after Compose has
            // dropped this producer and HWUI has crossed several frames.
            awaitCancellation()
        } finally {
            transformed
                ?.takeUnless { it === source }
                ?.let { PdfReverseBitmapRetirement.schedule(it, "bitmap-producer-dispose") }
        }
    }.value
}

@Composable
internal fun rememberPdfReverseTiles(
    tiles: List<PdfTile>,
    mode: PdfReverseColorMode,
    protectedRects: List<Rect> = emptyList(),
    forceBitmapTransform: Boolean = false,
    transformGeneration: Long? = null,
): List<PdfTile> {
    // Tile state is appended/replaced incrementally while a zoom settles. Keep
    // transformed copies by source identity so adding one tile does not copy
    // and walk every already-visible tile again (the old map() implementation
    // made a 12-tile viewport perform roughly 78 full conversions).
    val transformedCache = remember(mode, forceBitmapTransform, protectedRects) {
        IdentityHashMap<Bitmap, PdfTile>()
    }
    DisposableEffect(transformedCache) {
        onDispose {
            val retired = synchronized(transformedCache) {
                val values = transformedCache.entries
                    .filter { (source, transformed) -> source !== transformed.bitmap }
                    .map { it.value }
                transformedCache.clear()
                values
            }
            retired.forEach { tile ->
                PdfReverseBitmapRetirement.schedule(tile.bitmap, "tile-cache-dispose")
            }
        }
    }
    return produceState(
        initialValue = if (mode == PdfReverseColorMode.RGB && !forceBitmapTransform) tiles else emptyList(),
        tiles,
        mode,
        protectedRects,
        forceBitmapTransform,
    ) {
        if (mode == PdfReverseColorMode.RGB && !forceBitmapTransform) {
            value = tiles
            return@produceState
        }
        val activeTransformGeneration = transformGeneration
            ?: PdfReverseTransformGeneration.begin(mode)
        var latestTransformed: List<PdfTile>? = null
        try {
            val transformed = withContext(Dispatchers.Default) {
            val activeSources = tiles.mapTo(HashSet()) { it.bitmap }
            val removed = mutableListOf<PdfTile>()
            synchronized(transformedCache) {
                val iterator = transformedCache.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (entry.key !in activeSources) {
                        removed += entry.value
                        iterator.remove()
                    }
                }
            }
            removed.forEach { PdfReverseBitmapRetirement.schedule(it.bitmap, "tile-source-removed") }
            tiles.map { tile ->
                var staleCached: PdfTile? = null
                val cached = synchronized(transformedCache) {
                    val candidate = transformedCache[tile.bitmap]
                    if (
                        candidate != null &&
                        candidate.tileId == tile.tileId &&
                        candidate.renderScale == tile.renderScale &&
                        candidate.renderRect == tile.renderRect
                    ) {
                        PdfReverseBitmapRetirement.retain(candidate.bitmap)
                        candidate
                    } else {
                        staleCached = candidate
                        transformedCache.remove(tile.bitmap)
                        null
                    }
                }
                staleCached
                    ?.takeUnless { it.bitmap === tile.bitmap }
                    ?.let { PdfReverseBitmapRetirement.schedule(it.bitmap, "tile-cache-stale") }
                cached?.let { return@map it }
                val tileProtectedRects = protectedRects.mapNotNull { rect ->
                    val left = max(rect.left, tile.renderRect.left)
                    val top = max(rect.top, tile.renderRect.top)
                    val right = min(rect.right, tile.renderRect.right)
                    val bottom = min(rect.bottom, tile.renderRect.bottom)
                    if (right <= left || bottom <= top) return@mapNotNull null
                    Rect(left, top, right, bottom)
                }
                var convertedBitmap: Bitmap? = null
                try {
                    convertedBitmap = tile.bitmap.copyWithPdfReverseColor(
                        mode = mode,
                        protectedRects = tileProtectedRects,
                        targetWidth = tile.renderRect.width(),
                        targetHeight = tile.renderRect.height(),
                        targetOriginX = tile.renderRect.left,
                        targetOriginY = tile.renderRect.top,
                        forceBitmapTransform = forceBitmapTransform,
                        transformGeneration = activeTransformGeneration,
                    )
                    currentCoroutineContext().ensureActive()
                    val outputBitmap = convertedBitmap
                    val converted = tile.copy(bitmap = outputBitmap)
                    convertedBitmap = null
                    PdfReverseBitmapRetirement.retain(converted.bitmap)
                    val cacheIt = synchronized(transformedCache) {
                        val existing = transformedCache[tile.bitmap]
                        val currentBytes = transformedCache.values.sumOf { cachedTile ->
                            cachedTile.bitmap.allocationByteCount.toLong().coerceAtLeast(0L)
                        } - (existing?.bitmap?.allocationByteCount?.toLong() ?: 0L)
                        if (
                            transformedCache.size < MAX_REVERSE_TILE_CACHE_ENTRIES &&
                            currentBytes + converted.bitmap.allocationByteCount <= MAX_REVERSE_TILE_CACHE_BYTES
                        ) {
                            transformedCache[tile.bitmap] = converted
                            true
                        } else {
                            false
                        }
                    }
                    if (!cacheIt) {
                        // The current produceState value owns this output until
                        // the next generation; its finally block retires it if
                        // it was intentionally left outside the bounded cache.
                    }
                    converted
                } finally {
                    convertedBitmap
                        ?.takeUnless { it === tile.bitmap }
                        ?.let { PdfReverseBitmapRetirement.schedule(it, "tile-transform-cancelled") }
                }
            }
            }
            latestTransformed = transformed
            value = transformed
            awaitCancellation()
        } finally {
            latestTransformed?.forEach { tile ->
                val isCached = synchronized(transformedCache) {
                    transformedCache.values.any { cached -> cached.bitmap === tile.bitmap }
                }
                if (!isCached && tile.bitmap !== tiles.firstOrNull { it.bitmap === tile.bitmap }?.bitmap) {
                    PdfReverseBitmapRetirement.schedule(tile.bitmap, "tile-producer-dispose")
                }
            }
        }
    }.value
}

/**
 * Applies the Okular-compatible nonlinear reverse transform once to a bitmap copy.
 * [protectedRects] are in the target/page coordinate space; those pixels are copied
 * unchanged for the Preserve Image Colors policy.
 */
private suspend fun Bitmap.copyWithPdfReverseColor(
    mode: PdfReverseColorMode,
    protectedRects: List<Rect>,
    targetWidth: Int,
    targetHeight: Int,
    targetOriginX: Int = 0,
    targetOriginY: Int = 0,
    forceBitmapTransform: Boolean = false,
    transformGeneration: Long,
): Bitmap {
    if (mode == PdfReverseColorMode.RGB && !forceBitmapTransform) return this
    currentCoroutineContext().ensureActive()
    val transformStartedAt = SystemClock.uptimeMillis()
    Timber.tag("PdfReversePerf").d(
        "transform-start mode=${mode.id} generation=$transformGeneration " +
            "source=${width}x$height protected=${protectedRects.size}"
    )

    val safeTargetWidth = targetWidth.coerceAtLeast(1)
    val safeTargetHeight = targetHeight.coerceAtLeast(1)
    val protectedSourceRects = protectedRects.mapNotNull { rect ->
        val left = (((rect.left - targetOriginX).toFloat() / safeTargetWidth) * width)
            .toInt().coerceIn(0, width)
        val top = (((rect.top - targetOriginY).toFloat() / safeTargetHeight) * height)
            .toInt().coerceIn(0, height)
        val right = (((rect.right - targetOriginX).toFloat() / safeTargetWidth) * width)
            .toInt().coerceIn(0, width)
        val bottom = (((rect.bottom - targetOriginY).toFloat() / safeTargetHeight) * height)
            .toInt().coerceIn(0, height)
        Rect(left, top, right, bottom)
            .takeIf { it.width() > 0 && it.height() > 0 }
            ?.let { PdfReverseColorRect(it.left, it.top, it.right, it.bottom) }
    }

    return pdfReverseTransformMutex.withLock {
        currentCoroutineContext().ensureActive()
        if (!PdfReverseTransformGeneration.isCurrent(transformGeneration)) {
            throw CancellationException("stale reverse-color transform generation")
        }
        // Lease the source only for the native copy. All pixel iteration below
        // is performed on the independent result, so a page/tile can be
        // returned to the pool as soon as this snapshot has been made.
        val result = PdfBitmapUseRegistry.withLease(this@copyWithPdfReverseColor) {
            copy(config ?: Bitmap.Config.ARGB_8888, true)
        } ?: throw CancellationException("source bitmap unavailable")
        if (width <= 0 || height <= 0) return@withLock result

        try {
            val pixels = IntArray(width)
            for (y in 0 until height) {
                currentCoroutineContext().ensureActive()
                if (!PdfReverseTransformGeneration.isCurrent(transformGeneration)) {
                    throw CancellationException("stale reverse-color transform generation")
                }
                result.getPixels(pixels, 0, width, 0, y, width, 1)
                for (x in 0 until width) {
                    pixels[x] = invertPdfArgbIfUnprotected(pixels[x], x, y, mode, protectedSourceRects)
                }
                result.setPixels(pixels, 0, width, 0, y, width, 1)
                }
            Timber.tag("PdfReversePerf").d(
                "transform-complete mode=${mode.id} generation=$transformGeneration " +
                    "size=${width}x$height bytes=${result.allocationByteCount} " +
                    "duration=${SystemClock.uptimeMillis() - transformStartedAt}ms"
            )
            result
        } catch (error: Throwable) {
            // A cancelled transform has not been handed to Compose/HWUI yet,
            // so dispose its private copy immediately instead of retaining a
            // multi-megapixel bitmap until the next GC cycle.
            safeRecyclePdfBitmap(result)
            if (error is CancellationException) {
                Timber.tag("PdfReversePerf").d(
                    "transform-cancel mode=${mode.id} generation=$transformGeneration " +
                        "size=${width}x$height duration=${SystemClock.uptimeMillis() - transformStartedAt}ms"
                )
            } else {
                Timber.tag("PdfReversePerf").e(error, "transform-failed mode=${mode.id}")
            }
            if (error is CancellationException) throw error
            throw error
        }
    }
}

@Composable
internal fun PdfHighlightsLayer(
    pageLinks: List<PageLink>,
    showAllTextHighlights: Boolean,
    actualBitmapWidthPx: Int,
    actualBitmapHeightPx: Int,
    mergedAllTextPageHighlightRects: List<Rect>,
    mergedTtsHighlightRects: List<Rect>,
    mergedSearchFocusedRects: List<Rect>,
    mergedSearchAllRects: List<Rect>,
    searchHighlightMode: SearchHighlightMode,
    ocrHoverHighlights: List<RectF>,
    mergedSelectionRects: List<Rect>,
    userHighlightScreenRects: List<Pair<PdfUserHighlight, List<Rect>>>,
    centeringOffsetX: Float,
    centeringOffsetY: Float,
    linkHighlightColor: Color,
    scrimColorForTextHighlight: Color,
    allTextPageHighlightColor: Color,
    ttsHighlightColor: Color,
    selectionHighlightColor: Color,
    customHighlightColors: Map<PdfHighlightColor, Color> = emptyMap()
) {
    Canvas(modifier = Modifier
        .fillMaxSize()
        .graphicsLayer()) {
        translate(left = centeringOffsetX, top = centeringOffsetY) {
            fun isVisible(r: Rect): Boolean {
                val left = r.left + centeringOffsetX
                val right = r.right + centeringOffsetX
                val top = r.top + centeringOffsetY
                val bottom = r.bottom + centeringOffsetY
                return left < size.width && right > 0 && top < size.height && bottom > 0
            }

            fun isVisible(r: PdfIntBounds): Boolean {
                val left = r.left + centeringOffsetX
                val right = r.right + centeringOffsetX
                val top = r.top + centeringOffsetY
                val bottom = r.bottom + centeringOffsetY
                return left < size.width && right > 0 && top < size.height && bottom > 0
            }

            // 1. Page Links
            pageLinks.forEach { link ->
                if (isVisible(link.highlightBounds)) {
                    drawRect(
                        color = linkHighlightColor, topLeft = Offset(
                            link.highlightBounds.left.toFloat(), link.highlightBounds.top.toFloat()
                        ), size = Size(
                            link.highlightBounds.width.toFloat(),
                            link.highlightBounds.height.toFloat()
                        )
                    )
                }
            }

            // 2. Search Results - BACKGROUND (Yellow)
            if (searchHighlightMode == SearchHighlightMode.ALL) {
                val yellowColor = Color(0xFFFFEB3B).copy(alpha = 0.4f)
                mergedSearchAllRects.forEach { rect ->
                    if (rect.width() > 0 && rect.height() > 0 && isVisible(rect)) {
                        val inflated = RectF(rect)
                        inflated.inset(-3f, -3f) // padding
                        drawRect(
                            color = yellowColor,
                            topLeft = Offset(inflated.left, inflated.top),
                            size = Size(inflated.width(), inflated.height())
                        )
                    }
                }
            }

            // 3. Search Results - FOCUSED (Orange + Border)
            val focusedColor = Color(0xFFFF6D00).copy(alpha = 0.4f)
            val focusedStroke = Color(0xFFFF6D00).copy(alpha = 0.9f)
            mergedSearchFocusedRects.forEach { rect ->
                if (rect.width() > 0 && rect.height() > 0 && isVisible(rect)) {
                    val inflated = RectF(rect)
                    inflated.inset(-5f, -5f) // Extra padding for focus

                    // Fill
                    drawRect(
                        color = focusedColor,
                        topLeft = Offset(inflated.left, inflated.top),
                        size = Size(inflated.width(), inflated.height())
                    )
                    // Border
                    drawRect(
                        color = focusedStroke,
                        topLeft = Offset(inflated.left, inflated.top),
                        size = Size(inflated.width(), inflated.height()),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            // 4. Scrim for Text Highlights
            if (showAllTextHighlights && actualBitmapWidthPx > 0 && actualBitmapHeightPx > 0 && scrimColorForTextHighlight.alpha > 0f) {
                with(drawContext.canvas.nativeCanvas) {
                    val checkPoint = saveLayer(null, null)
                    drawRect(
                        color = scrimColorForTextHighlight, topLeft = Offset.Zero, size = Size(
                            actualBitmapWidthPx.toFloat(), actualBitmapHeightPx.toFloat()
                        )
                    )
                    mergedAllTextPageHighlightRects.forEach { rect ->
                        if (rect.width() > 0 && rect.height() > 0) {
                            drawRect(
                                color = Color.Transparent,
                                topLeft = Offset(rect.left.toFloat(), rect.top.toFloat()),
                                size = Size(rect.width().toFloat(), rect.height().toFloat()),
                                blendMode = BlendMode.Clear
                            )
                        }
                    }
                    restoreToCount(checkPoint)
                }
            }

            // 5. All Text Highlights (Overlay)
            mergedAllTextPageHighlightRects.forEach { rect ->
                if (rect.width() > 0 && rect.height() > 0 && isVisible(rect)) {
                    drawRect(
                        color = allTextPageHighlightColor,
                        topLeft = Offset(rect.left.toFloat(), rect.top.toFloat()),
                        size = Size(rect.width().toFloat(), rect.height().toFloat())
                    )
                }
            }

            // 6. TTS Highlights
            mergedTtsHighlightRects.forEach { rect ->
                if (rect.width() > 0 && rect.height() > 0 && isVisible(rect)) {
                    drawRect(
                        color = ttsHighlightColor,
                        topLeft = Offset(rect.left.toFloat(), rect.top.toFloat()),
                        size = Size(rect.width().toFloat(), rect.height().toFloat())
                    )
                }
            }

            // 7. OCR Hover
            ocrHoverHighlights.forEach { rectF ->
                val left = rectF.left * actualBitmapWidthPx
                val top = rectF.top * actualBitmapHeightPx
                val width = rectF.width() * actualBitmapWidthPx
                val height = rectF.height() * actualBitmapHeightPx
                val absLeft = left + centeringOffsetX
                val absTop = top + centeringOffsetY
                if (absLeft < size.width && absTop < size.height && (absLeft + width) > 0 && (absTop + height) > 0) {
                    drawRect(
                        color = Color(0xFFFFAB00).copy(alpha = 0.5f), // Generic highlight color
                        topLeft = Offset(left, top), size = Size(width, height)
                    )
                }
            }

            // 8. User Selection
            mergedSelectionRects.forEach { lineRect ->
                if (lineRect.width() > 0 && lineRect.height() > 0 && isVisible(lineRect)) {
                    drawRect(
                        color = selectionHighlightColor,
                        topLeft = Offset(lineRect.left.toFloat(), lineRect.top.toFloat()),
                        size = Size(lineRect.width().toFloat(), lineRect.height().toFloat())
                    )
                }
            }

            // 9. Persistent User Highlights
            userHighlightScreenRects.forEach { (highlight, screenRects) ->
                val displayColor = highlight.resolvedColor(customHighlightColors)
                screenRects.forEach { r ->
                    if (isVisible(r)) {
                        drawPdfUserHighlight(
                            color = displayColor,
                            style = highlight.style,
                            rect = r
                        )
                    }
                }
            }
        }
    }
}

internal fun DrawScope.drawPdfUserHighlight(
    color: Color,
    style: HighlightStyle,
    rect: Rect
) {
    val left = rect.left.toFloat()
    val top = rect.top.toFloat()
    val width = rect.width().toFloat()
    val height = rect.height().toFloat()
    if (width <= 0f || height <= 0f) return
    when (style) {
        HighlightStyle.BACKGROUND -> drawRect(
            color = color.copy(alpha = 0.4f),
            topLeft = Offset(left, top),
            size = Size(width, height)
        )
        HighlightStyle.UNDERLINE -> drawPdfHighlightLine(
            color = color.copy(alpha = 0.92f),
            left = left,
            right = left + width,
            y = top + height * 0.86f,
            height = height
        )
        HighlightStyle.WAVY_UNDERLINE -> drawPdfHighlightWave(
            color = color.copy(alpha = 0.92f),
            left = left,
            right = left + width,
            baselineY = top + height * 0.86f,
            height = height
        )
        HighlightStyle.STRIKETHROUGH -> drawPdfHighlightLine(
            color = color.copy(alpha = 0.92f),
            left = left,
            right = left + width,
            y = top + height * 0.52f,
            height = height
        )
    }
}

internal fun DrawScope.drawPdfHighlightLine(
    color: Color,
    left: Float,
    right: Float,
    y: Float,
    height: Float
) {
    drawLine(
        color = color,
        start = Offset(left, y),
        end = Offset(right, y),
        strokeWidth = (height * 0.08f).coerceIn(1.5f, 4f),
        cap = StrokeCap.Round
    )
}

internal fun DrawScope.drawPdfHighlightWave(
    color: Color,
    left: Float,
    right: Float,
    baselineY: Float,
    height: Float
) {
    val amplitude = (height * 0.08f).coerceIn(1.2f, 3.5f)
    val wavelength = (height * 0.62f).coerceIn(6f, 14f)
    val path = Path()
    var x = left
    path.moveTo(x, baselineY)
    while (x < right) {
        val midX = (x + wavelength / 2f).coerceAtMost(right)
        val nextX = (x + wavelength).coerceAtMost(right)
        path.quadraticBezierTo(x + wavelength / 4f, baselineY - amplitude, midX, baselineY)
        path.quadraticBezierTo(x + wavelength * 0.75f, baselineY + amplitude, nextX, baselineY)
        x += wavelength
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = (height * 0.06f).coerceIn(1.2f, 3f), cap = StrokeCap.Round)
    )
}

@Suppress("SameParameterValue")
@Composable
internal fun PdfAnnotationLayer(
    actualBitmapWidthPx: Int,
    actualBitmapHeightPx: Int,
    annotationsProvider: () -> List<PdfAnnotation>,
    drawingState: PdfDrawingState?,
    centeringOffsetX: Float,
    centeringOffsetY: Float,
    pageIndex: Int
) {
    SideEffect { Timber.tag("PdfPerf").v("ANNOT_LAYER: Recomposing Page $pageIndex") }

    val staticAnnotations = annotationsProvider()

    val staticRenderData = remember(staticAnnotations, actualBitmapWidthPx, actualBitmapHeightPx) {
        val startTime = System.nanoTime()
        val data = staticAnnotations.mapNotNull { annot ->
            PdfAnnotationRenderHelper.createRenderData(
                annot, actualBitmapWidthPx, actualBitmapHeightPx
            )
        }
        val duration = (System.nanoTime() - startTime) / 1_000_000f
        Timber.tag("PdfPerf").d("ANNOT_LAYER: Processed ${staticAnnotations.size} static annots in ${duration}ms")
        data
    }
    val currentAnnotation = remember(drawingState, pageIndex) {
        derivedStateOf {
            val annot = drawingState?.currentAnnotation
            val result = if (annot?.pageIndex == pageIndex) annot else null
            if (drawingState != null) {
                Timber.tag("PdfDrawPerf").v(
                    "DerivedState Calc Page $pageIndex: Global=${annot?.pageIndex} -> Result=${result != null}"
                )
            }
            result
        }
    }.value

    SideEffect {
        Timber.tag("PdfDrawPerf").v(
            "ANNOT LAYER: State Check Page $pageIndex | AnnotHash: ${currentAnnotation?.hashCode()} | AnnotPoints: ${currentAnnotation?.points?.size}"
        )
    }

    val activeRenderData = remember(
        currentAnnotation,
        currentAnnotation?.points?.size,
        actualBitmapWidthPx,
        actualBitmapHeightPx
    ) {
        val startTime = System.nanoTime()
        val res = currentAnnotation?.let { annot ->
            PdfAnnotationRenderHelper.createRenderData(annot, actualBitmapWidthPx, actualBitmapHeightPx)
        }
        val duration = (System.nanoTime() - startTime) / 1_000_000f
        if (duration > 0.5f) {
            Timber.tag("PdfPerf").v("ANNOT_LAYER: Active path gen took ${duration}ms")
        }
        res
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val drawStart = System.nanoTime()
        translate(left = centeringOffsetX, top = centeringOffsetY) {
            fun drawData(data: AnnotationRenderData) {
                when (data) {
                    is AnnotationRenderData.Standard -> {
                        drawPath(
                            path = data.path, color = data.color, style = Stroke(
                                width = data.strokeWidth, cap = data.cap, join = StrokeJoin.Round
                            ), blendMode = data.blendMode
                        )
                    }

                    is AnnotationRenderData.Fountain -> {
                        drawPath(
                            path = data.path,
                            color = data.color,
                            style = androidx.compose.ui.graphics.drawscope.Fill
                        )
                    }

                    is AnnotationRenderData.Pencil -> {
                        val texture = PdfTextureGenerator.getNoiseTexture()
                        drawIntoCanvas { canvas ->
                            val paint = NativePaint().apply {
                                isAntiAlias = true
                                style = NativePaint.Style.STROKE
                                strokeCap = NativePaint.Cap.ROUND
                                strokeJoin = NativePaint.Join.ROUND
                                strokeWidth = data.strokeWidth
                                shader = BitmapShader(
                                    texture, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT
                                )
                                colorFilter = PorterDuffColorFilter(
                                    data.color.toArgb(), PorterDuff.Mode.SRC_IN
                                )
                                alpha = (data.color.alpha * data.velocityAlpha * 255).toInt()
                            }
                            canvas.nativeCanvas.drawPath(data.path, paint)
                        }
                    }
                }
            }

            staticRenderData.forEach { drawData(it) }
            activeRenderData?.let { drawData(it) }
        }
        val drawDuration = (System.nanoTime() - drawStart) / 1_000_000f
        if (drawDuration > 2f) {
            Timber.tag("PdfPerf").v("ANNOT_DRAW: Canvas draw took ${drawDuration}ms (Page $pageIndex)")
        }
    }
}

@Composable
internal fun PdfPageStaticLayer(
    data: PageStaticData,
    renderedBitmap: Bitmap?,
    renderedTiles: List<PdfTile>,
) {
    PdfBitmapLayer(
        renderedBitmap = renderedBitmap,
        renderedTiles = renderedTiles,
        shouldDrawHighResTiles = data.shouldDrawHighResTiles,
        effectiveScale = data.effectiveScale,
        centeringOffsetX = data.centeringOffsetX,
        centeringOffsetY = data.centeringOffsetY,
        canvasWidth = data.canvasWidth,
        canvasHeight = data.canvasHeight,
        targetWidth = data.targetWidth,
        targetHeight = data.targetHeight,
        colorFilter = data.colorFilter.item,
        isDarkMode = data.isDarkMode,
        excludeImages = data.excludeImages,
        reverseColorMode = data.reverseColorMode,
        imageRects = data.imageRects.item,
        textureBitmap = data.textureBitmap.item,
        textureAlpha = data.textureAlpha,
        textureBlendMode = data.textureBlendMode
    )
}

@Composable
internal fun PdfPageSelectionsLayer(
    pageLinks: List<PageLink>,
    showAllTextHighlights: Boolean,
    actualBitmapWidthPx: Int,
    actualBitmapHeightPx: Int,
    mergedAllTextPageHighlightRects: List<Rect>,
    mergedTtsHighlightRects: List<Rect>,
    mergedSearchFocusedRects: List<Rect>,
    mergedSearchAllRects: List<Rect>,
    searchHighlightMode: SearchHighlightMode,
    ocrHoverHighlights: List<RectF>,
    mergedSelectionRects: List<Rect>,
    userHighlightScreenRects: List<Pair<PdfUserHighlight, List<Rect>>>,
    centeringOffsetX: Float,
    centeringOffsetY: Float,
    linkHighlightColor: Color,
    scrimColorForTextHighlight: Color,
    allTextPageHighlightColor: Color,
    ttsHighlightColor: Color,
    selectionHighlightColor: Color,
    customHighlightColors: Map<PdfHighlightColor, Color> = emptyMap()
) {
    SideEffect {
        Timber.tag("PdfDrawPerf").v("SELECTIONS LAYER: Recomposing")
        Timber.tag("PdfHighlightDebug").v("PdfPageSelectionsLayer Recomposing. userHighlights count: ${userHighlightScreenRects.size}")
    }
    val highlightStart = System.nanoTime()

    PdfHighlightsLayer(
        pageLinks = pageLinks,
        showAllTextHighlights = showAllTextHighlights,
        actualBitmapWidthPx = actualBitmapWidthPx,
        actualBitmapHeightPx = actualBitmapHeightPx,
        mergedAllTextPageHighlightRects = mergedAllTextPageHighlightRects,
        mergedTtsHighlightRects = mergedTtsHighlightRects,
        mergedSearchFocusedRects = mergedSearchFocusedRects,
        mergedSearchAllRects = mergedSearchAllRects,
        searchHighlightMode = searchHighlightMode,
        ocrHoverHighlights = ocrHoverHighlights,
        mergedSelectionRects = mergedSelectionRects,
        userHighlightScreenRects = userHighlightScreenRects,
        centeringOffsetX = centeringOffsetX,
        centeringOffsetY = centeringOffsetY,
        linkHighlightColor = linkHighlightColor,
        scrimColorForTextHighlight = scrimColorForTextHighlight,
        allTextPageHighlightColor = allTextPageHighlightColor,
        ttsHighlightColor = ttsHighlightColor,
        selectionHighlightColor = selectionHighlightColor,
        customHighlightColors = customHighlightColors
    )

    val highlightTime = (System.nanoTime() - highlightStart) / 1_000_000f
    if (highlightTime > 1f) {
        SideEffect {
            Timber.tag("PdfPerformance")
                .v("PdfHighlightsLayer composition/draw took ${highlightTime}ms")
        }
    }
}

@Composable
internal fun PdfPageRenderer(
    staticData: PageStaticData,
    selectionData: PageSelectionData,
    totalPages: Int,
    annotationsProvider: () -> List<PdfAnnotation>,
    drawingState: PdfDrawingState?,
    onCanvasSizeChanged: (Float, Float) -> Unit,
    scale: Float,
    uiScale: Float,
    offset: Offset,
    startHandlePos: Offset?,
    endHandlePos: Offset?,
    teardropWidthPx: Float,
    teardropHeightPx: Float,
    activeDraggingHandle: Handle?,
    showMagnifier: Boolean,
    magnifierCenterTarget: Offset,
    magnifierZoomFactor: Float,
    menuState: CustomPdfMenuState?,
    onMenuDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onAiDefine: (String) -> Unit,
    onTranslate: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSelectAll: () -> Unit,
    onShowUpsellDialog: () -> Unit,
    isProUser: Boolean,
    isBookmarked: Boolean,
    onBookmarkClick: () -> Unit,
    centeringPaddingTop: Dp,
    centeringPaddingEnd: Dp,
    isPerformingOcr: Boolean,
    ocrRipplePos: Offset?,
    layoutCoordinates: LayoutCoordinates?,
    contentToScreenCoordinates: (Offset) -> Offset,
    density: Density,
    isVerticalScroll: Boolean,
    showPageNumberOverlay: Boolean,
    isScrolling: Boolean,
    isEditMode: Boolean,
    selectedTool: InkType,
    eraserPosition: Offset?,
    isStylusEraserOverride: Boolean,
    richTextController: RichTextController?,
    textBoxes: List<PdfTextBox>,
    selectedTextBoxId: String?,
    onTextBoxChange: (PdfTextBox) -> Unit,
    onTextBoxSelect: (String) -> Unit,
    onTextBoxDragStart: (PdfTextBox, Offset, Offset) -> Unit,
    onTextBoxDrag: (Offset) -> Unit,
    onTextBoxDragEnd: () -> Unit,
    onDragPageTurn: (Int) -> Unit,
    draggingBoxId: String? = null,
    customHighlightColors: Map<PdfHighlightColor, Color> = emptyMap(),
    onPaletteClick: (() -> Unit)? = null,
    onHighlightAdd: (Int, Pair<Int, Int>, String, PdfHighlightColor, HighlightStyle) -> Unit,
    onHighlightUpdate: (String, PdfHighlightColor, HighlightStyle?) -> Unit,
    onHighlightDelete: (String) -> Unit,
    onTts: (Int, Int) -> Unit,
    activeToolThickness: Float,
    eraserToolThickness: Float,
    onNote: (String?) -> Unit,
    isBubbleZoomModeActive: Boolean = false,
    isActivePage: Boolean = true,
    isDetectingBubbles: Boolean = false,
    detectedBubbles: List<SpeechBubble> = emptyList(),
    animatingBubbleIndex: Int = -1,
    bubbleExpansionProgress: Float = 0f,
    expandedBubbleRender: ExpandedBubbleRender? = null
) {
    val mainProtectedRects = if (staticData.excludeImages) {
        staticData.imageRects.item
    } else {
        emptyList()
    }
    // One generation represents the visible page mode. Every base/tile/popup
    // request for this page shares it, so an auxiliary RGB copy for Preserve
    // Images cannot cancel the main nonlinear transform accidentally.
    val pageTransformGeneration = PdfReverseTransformGeneration.begin(
        staticData.reverseColorMode
    )
    // Do not transform stale high-resolution tiles while motion has disabled
    // their draw path. The base bitmap remains available immediately and the
    // current generation will transform only tiles that can be displayed.
    val mainTilesForTransform = if (staticData.shouldDrawHighResTiles) {
        staticData.tiles.item
    } else {
        emptyList()
    }
    val renderedMainBitmap = rememberPdfReverseBitmap(
        bitmap = staticData.bitmap.item,
        mode = staticData.reverseColorMode,
        protectedRects = mainProtectedRects,
        targetWidth = staticData.targetWidth,
        targetHeight = staticData.targetHeight,
        transformGeneration = pageTransformGeneration,
    )
    val renderedMainTiles = rememberPdfReverseTiles(
        tiles = mainTilesForTransform,
        mode = staticData.reverseColorMode,
        protectedRects = mainProtectedRects,
        transformGeneration = pageTransformGeneration,
    )

    val popupMode = if (
        showMagnifier || (isBubbleZoomModeActive && bubbleExpansionProgress > 0f)
    ) staticData.reverseColorMode else PdfReverseColorMode.RGB
    // Magnifier rendering has one source bitmap/filter for the whole sample, so
    // Preserve Image Colors must be baked into its base/tile copies instead of
    // relying on the main page's draw-time restoration pass.
    val popupProtectedRects = if (staticData.excludeImages) staticData.imageRects.item else emptyList()
    val popupNeedsBakedRgb = popupMode == PdfReverseColorMode.RGB && popupProtectedRects.isNotEmpty()
    val popupSharesMainTransform =
        popupMode == staticData.reverseColorMode &&
            popupNeedsBakedRgb.not() &&
            popupProtectedRects == mainProtectedRects
    val popupBitmap = if (popupSharesMainTransform) {
        renderedMainBitmap
    } else {
        rememberPdfReverseBitmap(
            bitmap = staticData.bitmap.item,
            mode = popupMode,
            protectedRects = popupProtectedRects,
            targetWidth = staticData.targetWidth,
            targetHeight = staticData.targetHeight,
            forceBitmapTransform = popupNeedsBakedRgb,
            transformGeneration = pageTransformGeneration,
        )
    }
    val popupTiles = if (popupSharesMainTransform) {
        if (showMagnifier) renderedMainTiles else emptyList()
    } else {
        rememberPdfReverseTiles(
            tiles = if (showMagnifier) staticData.tiles.item else emptyList(),
            mode = popupMode,
            protectedRects = popupProtectedRects,
            forceBitmapTransform = popupNeedsBakedRgb,
            transformGeneration = pageTransformGeneration,
        )
    }
    val popupExpandedBubbleBitmap = rememberPdfReverseBitmap(
        bitmap = expandedBubbleRender?.bitmap,
        mode = popupMode,
        transformGeneration = pageTransformGeneration,
    )
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }) {

            // Layer 1: The Heavy Bitmap
            Box(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                PdfPageStaticLayer(
                    data = staticData,
                    renderedBitmap = renderedMainBitmap,
                    renderedTiles = renderedMainTiles,
                )
            }

            // Layer 2: The Lightweight Highlights
            PdfPageSelectionsLayer(
                pageLinks = selectionData.pageLinks.item,
                showAllTextHighlights = selectionData.showAllTextHighlights,
                actualBitmapWidthPx = selectionData.actualBitmapWidthPx,
                actualBitmapHeightPx = selectionData.actualBitmapHeightPx,
                mergedAllTextPageHighlightRects = selectionData.mergedAllTextPageHighlightRects.item,
                mergedTtsHighlightRects = selectionData.mergedTtsHighlightRects.item,
                mergedSearchFocusedRects = selectionData.mergedSearchFocusedRects.item,
                mergedSearchAllRects = selectionData.mergedSearchAllRects.item,
                searchHighlightMode = selectionData.searchHighlightMode,
                ocrHoverHighlights = selectionData.ocrHoverHighlights.item,
                mergedSelectionRects = selectionData.mergedSelectionRects.item,
                userHighlightScreenRects = selectionData.userHighlightScreenRects.item,
                centeringOffsetX = selectionData.centeringOffsetX,
                centeringOffsetY = selectionData.centeringOffsetY,
                linkHighlightColor = selectionData.linkHighlightColor,
                scrimColorForTextHighlight = selectionData.scrimColorForTextHighlight,
                allTextPageHighlightColor = selectionData.allTextPageHighlightColor,
                ttsHighlightColor = selectionData.ttsHighlightColor,
                selectionHighlightColor = selectionData.selectionHighlightColor,
                customHighlightColors = selectionData.customHighlightColors.item
            )

            // Layer 3: Annotations & Text
            if (staticData.targetWidth > 0 && staticData.targetHeight > 0) {
                Box(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                    PdfAnnotationLayer(
                        actualBitmapWidthPx = staticData.targetWidth,
                        actualBitmapHeightPx = staticData.targetHeight,
                        annotationsProvider = annotationsProvider,
                        drawingState = drawingState,
                        centeringOffsetX = staticData.centeringOffsetX,
                        centeringOffsetY = staticData.centeringOffsetY,
                        pageIndex = selectionData.pageIndex
                    )
                }

                if (richTextController != null) {
                    val isEditable = isEditMode && selectedTool == InkType.TEXT
                    val hasContent = richTextController.pageLayouts.any {
                        it.pageIndex == selectionData.pageIndex
                    } || richTextController.hasRenderableText

                    if (isEditable || hasContent) {
                        SharedPdfRichTextLayer(
                            pageIndex = selectionData.pageIndex,
                            controller = richTextController.sharedDelegate,
                            pageWidth = staticData.targetWidth.toFloat(),
                            pageHeight = staticData.targetHeight.toFloat(),
                            isTextEditingEnabled = isEditable && selectedTextBoxId == null,
                            centeringOffsetX = staticData.centeringOffsetX,
                            centeringOffsetY = staticData.centeringOffsetY,
                            isDarkMode = staticData.isDarkMode,
                            isScrolling = isScrolling
                        )
                    }
                }

                if (textBoxes.isNotEmpty()) {
                    androidx.compose.runtime.SideEffect {
                        Timber.tag("PdfTextBoxDebug").d("PdfPageRenderer parent graphicsLayer applied | scale=$scale | offset=$offset | Centering: X=${staticData.centeringOffsetX}, Y=${staticData.centeringOffsetY}")
                    }
                }

                textBoxes.forEach { box ->
                    val isDraggingThisBox = (box.id == draggingBoxId)
                    val boxAlpha = if (isDraggingThisBox) 0f else 1f

                    key(box.id) {
                        ResizableTextBox(
                            box = box,
                            isSelected = (box.id == selectedTextBoxId),
                            isEditMode = isEditMode,
                            isDarkMode = staticData.isDarkMode,
                            scale = uiScale,
                            pageWidthPx = staticData.targetWidth.toFloat(),
                            pageHeightPx = staticData.targetHeight.toFloat(),
                            handlePosition = HandlePosition.AUTO,
                            onBoundsChanged = { newBounds ->
                                Timber.tag("PdfTextBoxDebug").v("PdfPageRenderer onBoundsChanged [ID: ${box.id}] bounds=$newBounds draggingBoxId=$draggingBoxId")
                                if (draggingBoxId != box.id) {
                                    onTextBoxChange(box.copy(relativeBounds = newBounds))
                                } else {
                                    Timber.tag("PdfTextBoxDebug").d("PdfPageRenderer onBoundsChanged IGNORED because box[ID: ${box.id}] is being dragged globally")
                                }
                            },
                            onTextChanged = { newText ->
                                onTextBoxChange(box.copy(text = newText))
                            },
                            onSelect = {
                                Timber.tag("PdfTextBoxDebug").d("PdfPageRenderer onSelect propagated[ID: ${box.id}]")
                                onTextBoxSelect(box.id)
                            },
                            onDragStart = { touchOffset ->
                                Timber.tag("PdfTextBoxDebug").d("PdfPageRenderer onDragStart[ID: ${box.id}] isVerticalScroll=$isVerticalScroll | offset=$touchOffset")
                                if (isVerticalScroll) {
                                    val topLeft = Offset(
                                        box.relativeBounds.left * staticData.targetWidth,
                                        box.relativeBounds.top * staticData.targetHeight
                                    )
                                    onTextBoxDragStart(box, topLeft, touchOffset)
                                } else {
                                    onTextBoxDragStart(box, Offset.Zero, touchOffset)
                                }
                            },
                            onDrag = { delta, currentBounds ->
                                Timber.tag("PdfTextBoxDebug").v("PdfPageRenderer onDrag [ID: ${box.id}] delta=$delta currentBounds=$currentBounds scale=$scale")
                                if (isVerticalScroll) {
                                    onTextBoxDrag(delta)
                                } else {
                                    val scaledDelta = delta * scale
                                    onTextBoxDrag(scaledDelta)

                                    val width = staticData.targetWidth
                                    val edgeThreshold = 60f
                                    if (width > 0) {
                                        if (currentBounds.left < edgeThreshold && delta.x < 0) {
                                            onDragPageTurn(-1)
                                        } else if (currentBounds.right > width - edgeThreshold && delta.x > 0) {
                                            onDragPageTurn(1)
                                        }
                                    }
                                }
                            },
                            onDragEnd = {
                                Timber.tag("PdfTextBoxDebug").d("PdfPageRenderer onDragEnd[ID: ${box.id}]")
                                onTextBoxDragEnd()
                            },
                            onDragCancel = {
                                Timber.tag("PdfTextBoxDebug").d("PdfPageRenderer onDragCancel [ID: ${box.id}]")
                                onTextBoxDragEnd()
                            },
                            modifier = Modifier
                                .zIndex(10f)
                                .offset {
                                    IntOffset(
                                        staticData.centeringOffsetX.roundToInt(),
                                        staticData.centeringOffsetY.roundToInt()
                                    )
                                }
                                .alpha(boxAlpha)
                        )
                    }
                }
            }

            // Layer 4: Page Number Indicator
            if (showPageNumberOverlay && totalPages > 0) {
                val pageNumColor = if (staticData.isDarkMode) {
                    Color.White
                } else {
                    Color.Black
                }

                Box(modifier = Modifier
                    .offset {
                        IntOffset(
                            x = staticData.centeringOffsetX.toInt(),
                            y = staticData.centeringOffsetY.toInt()
                        )
                    }
                    .size(width = with(density) {
                        staticData.targetWidth.toDp()
                    }, height = with(density) {
                        staticData.targetHeight.toDp()
                    })) {
                    Text(
                        text = "${selectionData.pageIndex + 1}/$totalPages",
                        color = pageNumColor.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp, fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 12.dp)
                    )
                }
            }
        }

        // Capture size for coordinate conversions
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (staticData.canvasWidth != size.width || staticData.canvasHeight != size.height) {
                onCanvasSizeChanged(size.width, size.height)
            }
        }

        val teardropPainter = painterResource(id = R.drawable.teardrop)

        if (isEditMode && (selectedTool == InkType.ERASER || isStylusEraserOverride) && eraserPosition != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val eraserStrokeWidth = resolveEraserStrokeWidth(
                    isStylusEraserOverride,
                    activeToolThickness,
                    eraserToolThickness
                )
                val radiusPx = if (eraserStrokeWidth > 0f && staticData.targetWidth > 0) {
                    eraserStrokeWidth * staticData.targetWidth * scale
                } else {
                    8.dp.toPx()
                }

                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = radiusPx,
                    center = eraserPosition
                )

                drawCircle(
                    color = Color.Black,
                    radius = radiusPx,
                    center = eraserPosition,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val handleColor = Color.Blue
            val tiltAngleDegrees = 30f

            startHandlePos?.let { contentPos ->
                val position = contentToScreenCoordinates(contentPos)
                translate(left = position.x - teardropWidthPx / 2, top = position.y) {
                    rotate(degrees = tiltAngleDegrees, pivot = Offset(teardropWidthPx / 2f, 0f)) {
                        with(teardropPainter) {
                            draw(
                                size = Size(teardropWidthPx, teardropHeightPx),
                                colorFilter = ColorFilter.tint(handleColor)
                            )
                        }
                    }
                }
            }
            endHandlePos?.let { contentPos ->
                val position = contentToScreenCoordinates(contentPos)
                translate(left = position.x - teardropWidthPx / 2, top = position.y) {
                    rotate(degrees = -tiltAngleDegrees, pivot = Offset(teardropWidthPx / 2f, 0f)) {
                        with(teardropPainter) {
                            draw(
                                size = Size(teardropWidthPx, teardropHeightPx),
                                colorFilter = ColorFilter.tint(handleColor)
                            )
                        }
                    }
                }
            }
        }

        if (showMagnifier && activeDraggingHandle != null && staticData.bitmap.item != null) {
            val handleContentPos = when (activeDraggingHandle) {
                Handle.START -> startHandlePos
                Handle.END -> endHandlePos
            }

            handleContentPos?.let { contentPos ->
                val pos = contentToScreenCoordinates(contentPos)

                val magnifierWidth = 120.dp
                val magnifierHeight = 60.dp
                val magnifierOffsetAboveHandle = 24.dp
                val effectiveScale = staticData.effectiveScale

                val effectiveZoomFactor = if (isVerticalScroll && effectiveScale > 1f) {
                    effectiveScale * 1.25f
                } else {
                    magnifierZoomFactor
                }

                val popupPositionProvider = remember(pos, layoutCoordinates, density) {
                    object : androidx.compose.ui.window.PopupPositionProvider {
                        override fun calculatePosition(
                            anchorBounds: androidx.compose.ui.unit.IntRect,
                            windowSize: androidx.compose.ui.unit.IntSize,
                            layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                            popupContentSize: androidx.compose.ui.unit.IntSize
                        ): androidx.compose.ui.unit.IntOffset {
                            val coords = layoutCoordinates ?: return androidx.compose.ui.unit.IntOffset.Zero

                            val windowPos = coords.localToWindow(pos)
                            val offsetPx = with(density) { magnifierOffsetAboveHandle.toPx() }

                            val x = (windowPos.x - popupContentSize.width / 2).toInt()
                            val y = (windowPos.y - popupContentSize.height - offsetPx).toInt()

                            return androidx.compose.ui.unit.IntOffset(x, y)
                        }
                    }
                }

                androidx.compose.ui.window.Popup(
                    popupPositionProvider = popupPositionProvider,
                    properties = androidx.compose.ui.window.PopupProperties(
                        focusable = false,
                        dismissOnClickOutside = false,
                        dismissOnBackPress = false,
                        usePlatformDefaultWidth = false
                    )
                ) {
                    MagnifierComposable(
                        sourceBitmap = (popupBitmap ?: requireNotNull(staticData.bitmap.item)).asImageBitmap(),
                        tiles = if (staticData.shouldDrawHighResTiles) {
                            if (staticData.reverseColorMode == PdfReverseColorMode.RGB) staticData.tiles.item else popupTiles
                        } else emptyList(),
                        currentScale = effectiveScale,
                        magnifierCenterOnBitmap = magnifierCenterTarget,
                        contentWidthPx = staticData.targetWidth,
                        contentHeightPx = staticData.targetHeight,
                        magnifierWidth = magnifierWidth,
                        magnifierHeight = magnifierHeight,
                        zoomFactor = effectiveZoomFactor,
                        selectionRectsInContentCoords = selectionData.mergedSelectionRects.item,
                        highlightColor = Color(0x6633B5E5),
                        colorFilter = if (popupNeedsBakedRgb || staticData.reverseColorMode != PdfReverseColorMode.RGB) {
                            null
                        } else {
                            staticData.colorFilter.item
                        },
                        modifier = Modifier
                    )
                }
            }
        }

        var menuSuppressedForCurrentSelection by remember(menuState) { mutableStateOf(false) }
        var wasScrollingForCurrentSelection by remember(menuState) { mutableStateOf(isScrolling) }
        LaunchedEffect(isScrolling, menuState) {
            // A menu can be created before the long-press gesture has fully left the
            // scrolling state. Only later motion of an established menu should suppress it.
            if (isScrolling && !wasScrollingForCurrentSelection && menuState != null) {
                menuSuppressedForCurrentSelection = true
            }
            wasScrollingForCurrentSelection = isScrolling
        }

        if (menuState != null) {
            BackHandler(enabled = true, onBack = onMenuDismiss)
        }

        if (
            menuState != null &&
            shouldShowPdfSelectionMenu(
                hasMenu = true,
                isPageMoving = isScrolling,
                suppressedForCurrentSelection = menuSuppressedForCurrentSelection,
            ) &&
            draggingBoxId == null &&
            activeDraggingHandle == null
        ) {
            if (menuState.anchorRect.width() > 0 || menuState.anchorRect.height() > 0) {
                val popupPositionProvider = remember(menuState.anchorRect, density, offset, scale, layoutCoordinates) {
                    object : PopupPositionProvider {
                        override fun calculatePosition(
                            anchorBounds: IntRect,
                            windowSize: IntSize,
                            layoutDirection: LayoutDirection,
                            popupContentSize: IntSize
                        ): IntOffset {
                            val coords = layoutCoordinates ?: return IntOffset.Zero

                            val topLeftLocal = contentToScreenCoordinates(Offset(
                                menuState.anchorRect.left.toFloat(),
                                menuState.anchorRect.top.toFloat()))
                            val bottomRightLocal = contentToScreenCoordinates(Offset(
                                menuState.anchorRect.right.toFloat(),
                                menuState.anchorRect.bottom.toFloat()))

                            val topLeftWindow = coords.localToWindow(topLeftLocal)
                            val bottomRightWindow = coords.localToWindow(bottomRightLocal)

                            val gapPx = with(density) { 16.dp.toPx() }
                            val placement = sharedSelectionMenuPlacement(
                                viewport = SharedSelectionMenuViewport(windowSize.width, windowSize.height),
                                popup = SharedSelectionMenuSize(popupContentSize.width, popupContentSize.height),
                                selection = SharedSelectionMenuRect(
                                    left = topLeftWindow.x,
                                    top = topLeftWindow.y,
                                    right = bottomRightWindow.x,
                                    bottom = bottomRightWindow.y
                                ),
                                marginPx = gapPx,
                                gapPx = gapPx
                            )
                            return IntOffset(placement.x, placement.y)
                        }
                    }
                }

                PdfSelectionMenuPopup(
                    menuState = menuState,
                    popupPositionProvider = popupPositionProvider,
                    onDismiss = onMenuDismiss,
                    onCopy = onCopy,
                    onAiDefine = onAiDefine,
                    onTranslate = onTranslate,
                    onSearch = onSearch,
                    onSelectAll = onSelectAll,
                    onColorSelected = { color, style ->
                        if (menuState.isExistingHighlight && menuState.highlightId != null) {
                            onHighlightUpdate(menuState.highlightId, color, style)
                        } else {
                            onHighlightAdd(
                                selectionData.pageIndex, menuState.charRange, menuState.selectedText,
                                color,
                                style
                            )
                        }
                        onMenuDismiss()
                    },
                    onDelete = {
                        if (menuState.isExistingHighlight && menuState.highlightId != null) {
                            onHighlightDelete(menuState.highlightId)
                        }
                        onMenuDismiss()
                    },
                    onTts = {
                        onTts(selectionData.pageIndex, menuState.charRange.first)
                        onMenuDismiss()
                    },
                    onNote = { style ->
                        if (menuState.isExistingHighlight && menuState.highlightId != null) {
                            onNote(menuState.highlightId)
                        } else {
                            onNote(null)
                            onHighlightAdd(
                                selectionData.pageIndex, menuState.charRange, menuState.selectedText,
                                PdfHighlightColor.YELLOW,
                                style
                            )
                        }
                        onMenuDismiss()
                    },
                    customHighlightColors = selectionData.customHighlightColors.item,
                    onPaletteClick = onPaletteClick
                )
            }
        }

        BookmarkButton(
            isBookmarked = isBookmarked,
            onClick = onBookmarkClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = centeringPaddingTop, end = centeringPaddingEnd)
        )

        if (isPerformingOcr && ocrRipplePos != null) {
            OcrProcessingIndicator(position = ocrRipplePos)
        }

        if (isBubbleZoomModeActive && isActivePage) {
            if (isDetectingBubbles) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (detectedBubbles.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize().zIndex(20f)) {
                    // Draw shadow-like hints for unexpanded bubbles
                    detectedBubbles.forEachIndexed { index, bubble ->
                        val hintAlpha = if (index == animatingBubbleIndex) 0.35f * (1f - bubbleExpansionProgress) else 0.35f
                        if (hintAlpha > 0f) {
                            val left = bubble.bounds.left + staticData.centeringOffsetX
                            val top = bubble.bounds.top + staticData.centeringOffsetY
                            val width = bubble.bounds.width()
                            val height = bubble.bounds.height()

                            if (bubble.maskBitmap != null) {
                                drawImage(
                                    image = bubble.maskBitmap.asImageBitmap(),
                                    dstOffset = IntOffset(left.toInt(), top.toInt()),
                                    dstSize = IntSize(width.toInt(), height.toInt()),
                                    colorFilter = ColorFilter.tint(Color.Black.copy(alpha = hintAlpha)),
                                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High
                                )
                            } else {
                                drawRoundRect(
                                    color = Color.Black.copy(alpha = hintAlpha),
                                    topLeft = Offset(left, top),
                                    size = Size(width, height),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f, 24f)
                                )
                            }
                        }
                    }

                    if (animatingBubbleIndex in detectedBubbles.indices && staticData.bitmap.item != null && bubbleExpansionProgress > 0f) {
                        val baseBitmap = popupBitmap ?: requireNotNull(staticData.bitmap.item)
                        if (
                            !baseBitmap.isCanvasSafeBitmap(
                                maxBytes = PDF_MAX_DRAW_BITMAP_BYTES,
                                maxDimension = PDF_MAX_DRAW_BITMAP_DIMENSION_PX
                            )
                        ) {
                            return@Canvas
                        }
                        val safeExpandedBubbleRender = expandedBubbleRender?.takeIf {
                            it.bitmap.isCanvasSafeBitmap(
                                maxBytes = PDF_MAX_DRAW_BITMAP_BYTES,
                                maxDimension = PDF_MAX_DRAW_BITMAP_DIMENSION_PX
                            )
                        }
                        val expandedBitmap = safeExpandedBubbleRender?.let {
                            popupExpandedBubbleBitmap ?: it.bitmap
                        }
                        val bubble = detectedBubbles[animatingBubbleIndex]
                        val left = bubble.bounds.left + staticData.centeringOffsetX
                        val top = bubble.bounds.top + staticData.centeringOffsetY
                        val logicalWidth = bubble.bounds.width()
                        val logicalHeight = bubble.bounds.height()
                        val pivotX = left + logicalWidth / 2f
                        val pivotY = top + logicalHeight / 2f
                        val targetZoomFactor = safeExpandedBubbleRender?.zoomFactor ?: computeDynamicBubbleZoomFactor(
                            bubbleBounds = bubble.bounds,
                            viewportWidth = staticData.canvasWidth,
                            viewportHeight = staticData.canvasHeight
                        )
                        val zoomFactor = androidx.compose.ui.util.lerp(1f, targetZoomFactor, bubbleExpansionProgress)

                        withTransform({
                            scale(zoomFactor, zoomFactor, Offset(pivotX, pivotY))
                        }) {
                            val dstOffset = IntOffset(left.toInt(), top.toInt())
                            val dstSize = IntSize(logicalWidth.toInt(), logicalHeight.toInt())

                            val renderScaleX = baseBitmap.width.toFloat() / staticData.targetWidth.toFloat()
                            val renderScaleY = baseBitmap.height.toFloat() / staticData.targetHeight.toFloat()

                            val srcOffset = IntOffset(
                                (bubble.bounds.left * renderScaleX).toInt(),
                                (bubble.bounds.top * renderScaleY).toInt()
                            )
                            val srcSize = IntSize(
                                (logicalWidth * renderScaleX).toInt(),
                                (logicalHeight * renderScaleY).toInt()
                            )

                            if (bubble.maskBitmap != null) {
                                drawImage(
                                    image = bubble.maskBitmap.asImageBitmap(),
                                    dstOffset = IntOffset(left.toInt() + 12, top.toInt() + 12),
                                    dstSize = dstSize,
                                    colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.5f * bubbleExpansionProgress)),
                                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High
                                )
                            } else {
                                drawRoundRect(
                                    color = Color.Black.copy(alpha = 0.5f * bubbleExpansionProgress),
                                    topLeft = Offset(left + 12f, top + 12f),
                                    size = Size(logicalWidth, logicalHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f, 24f)
                                )
                            }

                            if (bubble.maskBitmap != null) {
                                val rect = androidx.compose.ui.geometry.Rect(
                                    dstOffset.x.toFloat(),
                                    dstOffset.y.toFloat(),
                                    dstOffset.x.toFloat() + dstSize.width,
                                    dstOffset.y.toFloat() + dstSize.height
                                )
                                drawContext.canvas.saveLayer(rect, androidx.compose.ui.graphics.Paint())
                                drawImage(
                                    image = (expandedBitmap ?: baseBitmap).asImageBitmap(),
                                    srcOffset = if (expandedBitmap != null) IntOffset.Zero else srcOffset,
                                    srcSize = expandedBitmap?.let { IntSize(it.width, it.height) } ?: srcSize,
                                    dstOffset = dstOffset,
                                    dstSize = dstSize,
                                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High
                                )
                                drawImage(
                                    image = bubble.maskBitmap.asImageBitmap(),
                                    dstOffset = dstOffset,
                                    dstSize = dstSize,
                                    blendMode = BlendMode.DstIn,
                                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High
                                )
                                drawContext.canvas.restore()
                            } else {
                                clipRect(left, top, left + logicalWidth, top + logicalHeight) {
                                    drawImage(
                                        image = (expandedBitmap ?: baseBitmap).asImageBitmap(),
                                        srcOffset = if (expandedBitmap != null) IntOffset.Zero else srcOffset,
                                        srcSize = expandedBitmap?.let { IntSize(it.width, it.height) } ?: srcSize,
                                        dstOffset = dstOffset,
                                        dstSize = dstSize
                                    )
                                }
                                drawRect(
                                    color = Color.White.copy(alpha = 0.5f * bubbleExpansionProgress),
                                    topLeft = Offset(left, top),
                                    size = Size(logicalWidth, logicalHeight),
                                    style = Stroke(width = 4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun getNativePointer(obj: Any): Long {
    val priorityFields = listOf("pagePtr", "mNativePage", "page")

    for (name in priorityFields) {
        try {
            val field = obj.javaClass.getDeclaredField(name)
            field.isAccessible = true
            val value = field.get(obj)
            if (value is Long && value != 0L) return value
            if (value != null && value !is Long) {
                val nestedPtr = getNativePointer(value)
                if (nestedPtr != 0L) return nestedPtr
            }
        } catch (_: Exception) {}
    }

    try {
        for (field in obj.javaClass.declaredFields) {
            if (field.type == Long::class.java || field.type == Long::class.javaPrimitiveType) {
                field.isAccessible = true
                val value = field.get(obj) as Long
                if (value > 0xFFFFFFFFL) return value
            }
        }
    } catch (_: Exception) {}

    return 0L
}
