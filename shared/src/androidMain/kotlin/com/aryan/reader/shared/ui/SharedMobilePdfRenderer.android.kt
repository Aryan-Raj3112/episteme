package com.aryan.reader.shared.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfReverseColorMode
import com.aryan.reader.shared.pdf.invertPdfArgb
import com.aryan.reader.shared.pdf.PDF_ZOOM_RENDER_SETTLE_MILLIS
import com.aryan.reader.shared.pdf.PDF_ZOOM_TILE_CACHE_MAX_BYTES
import com.aryan.reader.shared.pdf.PdfTileLruCache
import com.aryan.reader.shared.pdf.planPdfZoomTiles
import io.legere.pdfiumandroid.suspend.PdfiumCoreKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

internal object AndroidSharedPdfiumRuntime {
    val mutex = Mutex()
    val core by lazy { PdfiumCoreKt(Dispatchers.Default) }
}

@Composable
internal actual fun rememberSharedMobilePdfPageRender(
    book: BookItem,
    pageIndex: Int,
    zoomScale: Float,
    password: String?,
    reverseColorMode: PdfReverseColorMode,
    preserveImageColors: Boolean,
): SharedMobilePdfPageRender {
    val context = LocalContext.current.applicationContext
    registerSharedAndroidMobileApplicationContext(context)
    var render by remember(book.path, pageIndex, password, reverseColorMode, preserveImageColors) { mutableStateOf(SharedMobilePdfPageRender()) }
    LaunchedEffect(book.path, pageIndex, password, reverseColorMode, preserveImageColors) {
        render = AndroidSharedPdfiumRenderer.render(context, book, pageIndex, password, reverseColorMode, preserveImageColors)
    }
    return render
}

@Composable
internal actual fun rememberSharedMobilePdfPageThumbnail(
    book: BookItem,
    pageIndex: Int,
    password: String?,
    reverseColorMode: PdfReverseColorMode,
    preserveImageColors: Boolean,
): SharedMobilePdfPageThumbnail {
    val context = LocalContext.current.applicationContext
    registerSharedAndroidMobileApplicationContext(context)
    var thumbnail by remember(book.path, pageIndex, password, reverseColorMode, preserveImageColors) {
        mutableStateOf(AndroidPdfThumbnailCache.get(book, pageIndex, password, reverseColorMode, preserveImageColors))
    }
    LaunchedEffect(book.path, pageIndex, password, reverseColorMode, preserveImageColors) {
        if (thumbnail == null) {
            thumbnail = AndroidPdfThumbnailCache.get(book, pageIndex, password, reverseColorMode, preserveImageColors)
                ?: AndroidSharedPdfiumRenderer.renderThumbnail(context, book, pageIndex, password, reverseColorMode, preserveImageColors)
                    ?.also { AndroidPdfThumbnailCache.put(book, pageIndex, password, reverseColorMode, preserveImageColors, it) }
                ?: SharedMobilePdfPageThumbnail()
        }
    }
    return thumbnail ?: SharedMobilePdfPageThumbnail()
}

@Composable
internal actual fun rememberSharedMobilePdfTileRenders(
    book: BookItem,
    pageIndex: Int,
    pageAspectRatio: Float,
    zoomScale: Float,
    visibleBounds: PdfPageBounds?,
    password: String?,
    reverseColorMode: PdfReverseColorMode,
    preserveImageColors: Boolean,
): List<SharedMobilePdfTileRender> {
    val context = LocalContext.current.applicationContext
    registerSharedAndroidMobileApplicationContext(context)
    var settledZoomScale by remember(book.path, pageIndex, password) { mutableStateOf(zoomScale) }
    var zoomIsSettling by remember(book.path, pageIndex, password) { mutableStateOf(false) }
    LaunchedEffect(zoomScale) {
        zoomIsSettling = true
        delay(PDF_ZOOM_RENDER_SETTLE_MILLIS)
        settledZoomScale = zoomScale
        zoomIsSettling = false
    }
    val requests = remember(pageAspectRatio, settledZoomScale, visibleBounds) {
        visibleBounds?.let { planPdfZoomTiles(pageAspectRatio, settledZoomScale, it) }.orEmpty()
    }
    var tiles by remember(book.path, pageIndex, password, reverseColorMode, preserveImageColors) {
        mutableStateOf<List<SharedMobilePdfTileRender>>(emptyList())
    }
    LaunchedEffect(book.path, pageIndex, requests, password, zoomIsSettling, reverseColorMode, preserveImageColors) {
        if (zoomIsSettling) return@LaunchedEffect
        if (requests.isEmpty()) {
            tiles = emptyList()
            return@LaunchedEffect
        }
        val cached = AndroidPdfTileCache.get(book, pageIndex, password, reverseColorMode, preserveImageColors, requests)
        val cachedIds = cached.mapTo(mutableSetOf()) { it.request.id }
        val missing = requests.filterNot { it.id in cachedIds }
        if (missing.isEmpty()) {
            tiles = cached
            return@LaunchedEffect
        }
        val rendered = AndroidSharedPdfiumRenderer.renderTiles(
            context, book, pageIndex, missing, password, reverseColorMode, preserveImageColors,
        )
        coroutineContext.ensureActive()
        AndroidPdfTileCache.put(book, pageIndex, password, reverseColorMode, preserveImageColors, rendered)
        tiles = cached + rendered
    }
    return tiles
}

internal actual suspend fun sharedMobilePdfOcrTextBounds(
    book: BookItem,
    pageIndex: Int,
    password: String?,
): List<PdfPageBounds> {
    val context = sharedAndroidMobileApplicationContext() ?: return emptyList()
    val adapter = SharedAndroidPdfOcrRegistry.adapter ?: return emptyList()
    val bitmap = AndroidSharedPdfiumRenderer.renderBitmap(context, book, pageIndex, password)
        ?: return emptyList()
    return try {
        adapter.textLineBounds(bitmap)
    } finally {
        bitmap.recycle()
    }
}

fun interface SharedAndroidPdfOcrAdapter {
    suspend fun textLineBounds(bitmap: Bitmap): List<PdfPageBounds>
}

fun installSharedAndroidPdfOcrAdapter(adapter: SharedAndroidPdfOcrAdapter?) {
    SharedAndroidPdfOcrRegistry.adapter = adapter
}

private object SharedAndroidPdfOcrRegistry {
    @Volatile
    var adapter: SharedAndroidPdfOcrAdapter? = null
}

private object AndroidPdfThumbnailCache {
    private const val MaxEntries = 96
    private val entries = LinkedHashMap<String, SharedMobilePdfPageThumbnail>()

    fun get(
        book: BookItem,
        pageIndex: Int,
        password: String?,
        reverseColorMode: PdfReverseColorMode,
        preserveImageColors: Boolean,
    ) = synchronized(entries) { entries[key(book, pageIndex, password, reverseColorMode, preserveImageColors)] }

    fun put(
        book: BookItem,
        pageIndex: Int,
        password: String?,
        reverseColorMode: PdfReverseColorMode,
        preserveImageColors: Boolean,
        value: SharedMobilePdfPageThumbnail,
    ) {
        synchronized(entries) {
            entries[key(book, pageIndex, password, reverseColorMode, preserveImageColors)] = value
            while (entries.size > MaxEntries) entries.remove(entries.keys.first())
        }
    }

    private fun key(
        book: BookItem,
        pageIndex: Int,
        password: String?,
        reverseColorMode: PdfReverseColorMode,
        preserveImageColors: Boolean,
    ) = "${book.path.orEmpty()}|${book.fileContentModifiedTimestamp}|$pageIndex|${password.hashCode()}|${reverseColorMode.id}|$preserveImageColors"
}

private object AndroidPdfTileCache {
    private val entries = PdfTileLruCache<SharedMobilePdfTileRender>(PDF_ZOOM_TILE_CACHE_MAX_BYTES)

    fun get(
        book: BookItem,
        pageIndex: Int,
        password: String?,
        reverseColorMode: PdfReverseColorMode,
        preserveImageColors: Boolean,
        requests: List<com.aryan.reader.shared.pdf.PdfZoomTileRequest>,
    ): List<SharedMobilePdfTileRender> = synchronized(this) {
        requests.mapNotNull { entries.get(key(book, pageIndex, password, reverseColorMode, preserveImageColors, it)) }
    }

    fun put(
        book: BookItem,
        pageIndex: Int,
        password: String?,
        reverseColorMode: PdfReverseColorMode,
        preserveImageColors: Boolean,
        renders: List<SharedMobilePdfTileRender>,
    ) {
        synchronized(this) {
            renders.forEach { render ->
                val key = key(book, pageIndex, password, reverseColorMode, preserveImageColors, render.request)
                val bytes = render.request.widthPx.toLong() * render.request.heightPx * 4L
                entries.put(key, render, bytes)
            }
        }
    }

    private fun key(
        book: BookItem,
        pageIndex: Int,
        password: String?,
        reverseColorMode: PdfReverseColorMode,
        preserveImageColors: Boolean,
        request: com.aryan.reader.shared.pdf.PdfZoomTileRequest,
    ) = "${book.path.orEmpty()}|${book.fileContentModifiedTimestamp}|$pageIndex|${password.hashCode()}|" +
        "${reverseColorMode.id}|$preserveImageColors|${request.fullWidthPx}x${request.fullHeightPx}|${request.id}"
}

private object AndroidSharedPdfiumRenderer {
    suspend fun renderBitmap(
        context: Context,
        book: BookItem,
        pageIndex: Int,
        password: String?,
    ): Bitmap? = runCatching {
        AndroidSharedPdfiumRuntime.mutex.withLock {
            context.openSharedPdfDescriptor(book).use { pfd ->
                AndroidSharedPdfiumRuntime.core.newDocument(pfd, password).use { document ->
                    val count = document.getPageCount()
                    if (count <= 0) return@withLock null
                    document.openPage(pageIndex.coerceIn(0, count - 1))!!.use { page ->
                        val width = page.getPageWidthPoint().coerceAtLeast(1)
                        val height = page.getPageHeightPoint().coerceAtLeast(1)
                        val aspect = width.toFloat() / height.toFloat()
                        val targetHeight = SharedPdfPageRenderHeightPx
                        val targetWidth = (targetHeight * aspect).roundToInt().coerceAtLeast(1)
                        Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.renderPageBitmap(bitmap, 0, 0, targetWidth, targetHeight, true)
                        }
                    }
                }
            }
        }
    }.getOrNull()

    suspend fun render(
        context: Context,
        book: BookItem,
        pageIndex: Int,
        password: String?,
        reverseColorMode: PdfReverseColorMode = PdfReverseColorMode.RGB,
        preserveImageColors: Boolean = false,
    ): SharedMobilePdfPageRender = runCatching {
        AndroidSharedPdfiumRuntime.mutex.withLock {
            context.openSharedPdfDescriptor(book).use { pfd ->
                AndroidSharedPdfiumRuntime.core.newDocument(pfd, password).use { document ->
                    val count = document.getPageCount()
                    require(count > 0) { "PDF has no pages" }
                    document.openPage(pageIndex.coerceIn(0, count - 1))!!.use { page ->
                        val width = page.getPageWidthPoint().coerceAtLeast(1)
                        val height = page.getPageHeightPoint().coerceAtLeast(1)
                        val aspect = width.toFloat() / height.toFloat()
                        val targetHeight = SharedPdfPageRenderHeightPx
                        val targetWidth = (targetHeight * aspect).roundToInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.renderPageBitmap(bitmap, 0, 0, targetWidth, targetHeight, true)
                        val rasterizedMode = bitmap.applyPdfReverseColorMode(reverseColorMode, preserveImageColors)
                        SharedMobilePdfPageRender(
                            pageCount = count,
                            aspectRatio = aspect,
                            bitmap = bitmap.asImageBitmap(),
                            rasterizedReverseColorMode = rasterizedMode,
                        )
                    }
                }
            }
        }
    }.getOrElse { error ->
        val passwordError = error.message?.contains("password", ignoreCase = true) == true ||
            error.message?.contains("security", ignoreCase = true) == true
        SharedMobilePdfPageRender(
            errorMessage = error.message ?: "Could not open PDF",
            openError = if (passwordError) SharedMobilePdfOpenError.PASSWORD_REQUIRED else SharedMobilePdfOpenError.INVALID_DOCUMENT,
        )
    }

    suspend fun renderThumbnail(
        context: Context,
        book: BookItem,
        pageIndex: Int,
        password: String?,
        reverseColorMode: PdfReverseColorMode = PdfReverseColorMode.RGB,
        preserveImageColors: Boolean = false,
    ): SharedMobilePdfPageThumbnail? = runCatching {
        AndroidSharedPdfiumRuntime.mutex.withLock {
            context.openSharedPdfDescriptor(book).use { pfd ->
                AndroidSharedPdfiumRuntime.core.newDocument(pfd, password).use { document ->
                    val count = document.getPageCount()
                    if (count <= 0) return@withLock null
                    document.openPage(pageIndex.coerceIn(0, count - 1))!!.use { page ->
                        val width = page.getPageWidthPoint().coerceAtLeast(1)
                        val height = page.getPageHeightPoint().coerceAtLeast(1)
                        val aspect = width.toFloat() / height.toFloat()
                        val targetWidth = SharedPdfThumbnailWidthPx
                        val targetHeight = (targetWidth / aspect).roundToInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.renderPageBitmap(bitmap, 0, 0, targetWidth, targetHeight, true)
                        bitmap.applyPdfReverseColorMode(
                            mode = reverseColorMode,
                            preserveImageColors = preserveImageColors,
                            forceRgbTransform = true,
                        )
                        SharedMobilePdfPageThumbnail(bitmap.asImageBitmap(), aspect)
                    }
                }
            }
        }
    }.getOrNull()

    suspend fun renderTiles(
        context: Context,
        book: BookItem,
        pageIndex: Int,
        requests: List<com.aryan.reader.shared.pdf.PdfZoomTileRequest>,
        password: String?,
        reverseColorMode: PdfReverseColorMode = PdfReverseColorMode.RGB,
        preserveImageColors: Boolean = false,
    ): List<SharedMobilePdfTileRender> {
        if (requests.isEmpty()) return emptyList()
        return runCatching {
            AndroidSharedPdfiumRuntime.mutex.withLock {
                context.openSharedPdfDescriptor(book).use { pfd ->
                    AndroidSharedPdfiumRuntime.core.newDocument(pfd, password).use { document ->
                        val count = document.getPageCount()
                        if (count <= 0) return@withLock emptyList()
                        document.openPage(pageIndex.coerceIn(0, count - 1))!!.use { page ->
                            requests.map { request ->
                                val bitmap = Bitmap.createBitmap(
                                    request.widthPx, request.heightPx, Bitmap.Config.ARGB_8888,
                                )
                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                page.renderPageBitmap(
                                    bitmap,
                                    -request.leftPx,
                                    -request.topPx,
                                    request.fullWidthPx,
                                    request.fullHeightPx,
                                    true,
                                )
                                val rasterizedMode = bitmap.applyPdfReverseColorMode(reverseColorMode, preserveImageColors)
                                SharedMobilePdfTileRender(
                                    request = request,
                                    bitmap = bitmap.asImageBitmap(),
                                    rasterizedReverseColorMode = rasterizedMode,
                                )
                            }
                        }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}

/**
 * Applies nonlinear Okular modes once per cached render, never from Canvas' draw loop.
 * Android's legacy renderer supplies image rectangles for preserve-image mode; the shared
 * Pdfium adapter has no portable object-bound API, so it safely falls back to the full page.
 */
private fun Bitmap.applyPdfReverseColorMode(
    mode: PdfReverseColorMode,
    @Suppress("UNUSED_PARAMETER") preserveImageColors: Boolean,
    forceRgbTransform: Boolean = false,
): PdfReverseColorMode? {
    if (mode == PdfReverseColorMode.RGB && !forceRgbTransform) return null
    val row = IntArray(width)
    for (y in 0 until height) {
        getPixels(row, 0, width, 0, y, width, 1)
        for (x in row.indices) row[x] = invertPdfArgb(row[x], mode)
        setPixels(row, 0, width, 0, y, width, 1)
    }
    return mode
}

internal fun Context.openSharedPdfDescriptor(book: BookItem): android.os.ParcelFileDescriptor {
    val value = book.path?.trim().orEmpty()
    require(value.isNotBlank()) { "PDF path is unavailable" }
    val uri = Uri.parse(value)
    if (uri.scheme.isNullOrBlank()) {
        return android.os.ParcelFileDescriptor.open(
            java.io.File(value), android.os.ParcelFileDescriptor.MODE_READ_ONLY,
        )
    }
    return requireNotNull(contentResolver.openFileDescriptor(uri, "r")) { "Could not open PDF" }
}

private const val SharedPdfPageRenderHeightPx = 2048
private const val SharedPdfThumbnailWidthPx = 320
