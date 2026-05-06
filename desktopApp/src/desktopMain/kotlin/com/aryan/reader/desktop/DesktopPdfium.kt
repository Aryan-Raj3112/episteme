package com.aryan.reader.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.aryan.reader.shared.PdfTocEntry
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfZoomSpec
import com.aryan.reader.shared.pdf.PdfiumAnnotationSubtype
import com.aryan.reader.shared.pdf.SharedPdfEmbeddedAnnotation
import com.aryan.reader.shared.pdf.SharedPdfEmbeddedAnnotationThreads
import com.aryan.reader.shared.pdf.SharedPdfIndexedPage
import com.aryan.reader.shared.pdf.SharedPdfSearchIndex
import com.aryan.reader.shared.pdf.SharedPdfSearchResult
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteOrder
import kotlin.math.roundToInt

data class DesktopPdfDocument(
    val path: String,
    val title: String,
    val pageCount: Int,
    val pageSizes: List<DesktopPdfPageSize>,
    val toc: List<PdfTocEntry> = emptyList(),
    val embeddedAnnotations: List<SharedPdfEmbeddedAnnotation> = emptyList()
) {
    private val textPageCache = LinkedHashMap<Int, DesktopPdfTextPageData>()
    private val searchIndex = SharedPdfSearchIndex(pageCount)

    fun textPageData(pageIndex: Int): DesktopPdfTextPageData {
        if (pageIndex !in 0 until pageCount) return DesktopPdfTextPageData()
        val cached = synchronized(textPageCache) { textPageCache[pageIndex] }
        if (cached != null) return cached
        val loaded = DesktopPdfium.loadTextPageData(this, pageIndex)
        return cacheTextPageData(pageIndex, loaded)
    }

    fun cacheTextPageData(pageIndex: Int, data: DesktopPdfTextPageData): DesktopPdfTextPageData {
        if (pageIndex !in 0 until pageCount) return data
        synchronized(textPageCache) {
            textPageCache[pageIndex] = data
        }
        cacheSearchTextPage(pageIndex, data.text)
        return data
    }

    fun cacheSearchTextPage(pageIndex: Int, text: String) {
        if (pageIndex !in 0 until pageCount) return
        synchronized(searchIndex) {
            searchIndex.putPage(pageIndex, text)
        }
    }

    fun isSearchTextPageIndexed(pageIndex: Int): Boolean {
        return synchronized(searchIndex) { searchIndex.hasPage(pageIndex) }
    }

    fun indexedSearchTextPageCount(): Int {
        return synchronized(searchIndex) { searchIndex.indexedPageCount }
    }

    fun indexedSearchPages(): List<SharedPdfIndexedPage> {
        return synchronized(searchIndex) { searchIndex.indexedPages() }
    }

    fun searchIndexed(query: String): List<SharedPdfSearchResult> {
        return synchronized(searchIndex) { searchIndex.search(query) }
    }

    fun close() {
        DesktopPdfium.closeDocument(path)
    }
}

data class DesktopPdfPageSize(
    val width: Float,
    val height: Float
)

data class DesktopPdfPageRender(
    val image: ImageBitmap,
    val width: Int,
    val height: Int
)

data class DesktopPdfTextChar(
    val index: Int,
    val char: Char,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val hasBounds: Boolean
        get() = right > left && bottom > top
}

data class DesktopPdfTextRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class DesktopPdfTextPageData(
    val text: String = "",
    val chars: List<DesktopPdfTextChar> = emptyList()
)

object DesktopPdfium {
    private const val FPDF_ANNOT = 0x01
    private const val FPDF_LCD_TEXT = 0x02
    private const val FPDF_RENDER_NO_SMOOTHTEXT = 0x1000
    private const val FPDF_BITMAP_BGRA = 4

    private val pdfiumDll: File by lazy(::resolvePdfiumDll)
    private val zoomSpec = PdfZoomSpec()
    private val api: PdfiumLibrary by lazy {
        require(pdfiumDll.exists()) {
            "Missing Pdfium DLL. Expected pdfium-v8-win-x64 under third_party/pdfium/win-x64-v8/bin/pdfium.dll."
        }
        Native.load(pdfiumDll.absolutePath, PdfiumLibrary::class.java)
    }

    private var initialized = false
    private val openDocuments = LinkedHashMap<String, DesktopOpenPdfDocument>()

    fun isAvailable(): Boolean = pdfiumDll.exists()

    private fun loadDocument(file: File, password: String?): DesktopOpenPdfDocument {
        val pathHasNonAscii = file.absolutePath.any { it.code > 0x7F }
        logPdfiumOpen(
            "open_start path=\"${file.absolutePath}\" exists=${file.exists()} " +
                "canRead=${file.canRead()} size=${runCatching { file.length() }.getOrDefault(-1L)} " +
                "nonAsciiPath=$pathHasNonAscii dll=\"${pdfiumDll.absolutePath}\""
        )
        val pathError = if (pathHasNonAscii) {
            logPdfiumOpen("path_load_skipped reason=non_ascii_path path=\"${file.absolutePath}\"")
            null
        } else {
            val pathDocument = api.FPDF_LoadDocument(file.absolutePath, password)
            if (pathDocument != null) {
                logPdfiumOpen("path_load_success path=\"${file.absolutePath}\"")
                return DesktopOpenPdfDocument(pointer = pathDocument)
            }

            api.FPDF_GetLastError().also { errorCode ->
                logPdfiumOpen(
                    "path_load_failed code=$errorCode message=\"${pdfiumLoadErrorMessage(errorCode)}\" " +
                        "path=\"${file.absolutePath}\""
                )
            }
        }

        val bytes = runCatching { file.readBytes() }
            .onFailure { throwable ->
                logPdfiumOpen("read_bytes_failed path=\"${file.absolutePath}\" error=\"${throwable.message.orEmpty()}\"")
            }
            .getOrNull()
        if (bytes != null && bytes.size > 0) {
            logPdfiumOpen("memory_load_start bytes=${bytes.size} path=\"${file.absolutePath}\"")
            val memory = Memory(bytes.size.toLong())
            memory.write(0, bytes, 0, bytes.size)
            val memoryDocument = api.FPDF_LoadMemDocument(memory, bytes.size, password)
            if (memoryDocument != null) {
                logPdfiumOpen("memory_load_success bytes=${bytes.size} path=\"${file.absolutePath}\"")
                return DesktopOpenPdfDocument(pointer = memoryDocument, backingMemory = memory)
            }
            val memoryError = api.FPDF_GetLastError()
            logPdfiumOpen(
                "memory_load_failed code=$memoryError message=\"${pdfiumLoadErrorMessage(memoryError)}\" " +
                    "bytes=${bytes.size} path=\"${file.absolutePath}\""
            )
            val pathMessage = pathError?.let { "path load: ${pdfiumLoadErrorMessage(it)}" }
                ?: "path load skipped for non-ASCII path"
            error(
                "Pdfium could not open ${file.name}. ${pdfiumLoadErrorMessage(memoryError)} " +
                    "($pathMessage)."
            )
        }

        logPdfiumOpen("memory_load_skipped reason=empty_or_unreadable path=\"${file.absolutePath}\"")
        val pathMessage = pathError?.let(::pdfiumLoadErrorMessage) ?: "path load skipped for non-ASCII path"
        error("Pdfium could not open ${file.name}. $pathMessage")
    }

    @Synchronized
    fun load(file: File, password: String? = null): DesktopPdfDocument {
        initLibrary()
        val startedAt = System.currentTimeMillis()
        val loadedDocument = loadDocument(file, password)
        val document = loadedDocument.pointer
        openDocuments[file.absolutePath] = loadedDocument

        try {
            val pageCount = api.FPDF_GetPageCount(document)
            logPdfiumOpen("metadata_loaded pageCount=$pageCount elapsedMs=${System.currentTimeMillis() - startedAt}")
            val pageSizes = (0 until pageCount).map { pageIndex ->
                loadPage(document, pageIndex).usePointer { page ->
                    DesktopPdfPageSize(
                        width = api.FPDF_GetPageWidthF(page),
                        height = api.FPDF_GetPageHeightF(page)
                    )
                }
            }
            logPdfiumOpen("page_sizes_loaded pages=$pageCount elapsedMs=${System.currentTimeMillis() - startedAt}")

            logPdfiumOpen("text_index_deferred pages=$pageCount elapsedMs=${System.currentTimeMillis() - startedAt}")
            val toc = extractTableOfContents(document, pageCount)
            logPdfiumOpen("toc_extracted entries=${toc.size} elapsedMs=${System.currentTimeMillis() - startedAt}")
            val embeddedAnnotations = extractEmbeddedAnnotations(document, pageSizes)
            logPdfiumOpen(
                "embedded_annotations_extracted count=${embeddedAnnotations.size} " +
                    "elapsedMs=${System.currentTimeMillis() - startedAt}"
            )

            val result = DesktopPdfDocument(
                path = file.absolutePath,
                title = file.nameWithoutExtension,
                pageCount = pageCount,
                pageSizes = pageSizes,
                toc = toc,
                embeddedAnnotations = embeddedAnnotations
            )
            logPdfiumOpen("open_complete elapsedMs=${System.currentTimeMillis() - startedAt}")
            return result
        } catch (throwable: Throwable) {
            openDocuments.remove(file.absolutePath)
            api.FPDF_CloseDocument(document)
            throw throwable
        }
    }

    @Synchronized
    fun closeDocument(path: String) {
        openDocuments.remove(path)?.let { api.FPDF_CloseDocument(it.pointer) }
    }

    fun indexSearchPages(
        document: DesktopPdfDocument,
        onProgress: (indexedPageCount: Int, pageCount: Int) -> Unit = { _, _ -> },
        shouldContinue: () -> Boolean = { true }
    ) {
        val startedAt = System.currentTimeMillis()
        onProgress(document.indexedSearchTextPageCount(), document.pageCount)
        for (pageIndex in 0 until document.pageCount) {
            if (!shouldContinue()) {
                logPdfiumOpen(
                    "search_index_cancelled pages=${document.indexedSearchTextPageCount()}/${document.pageCount} " +
                        "elapsedMs=${System.currentTimeMillis() - startedAt}"
                )
                return
            }
            val wasIndexed = document.isSearchTextPageIndexed(pageIndex)
            if (!wasIndexed) {
                val text = loadTextOnlyPage(document, pageIndex)
                document.cacheSearchTextPage(pageIndex, text)
            }
            val indexed = document.indexedSearchTextPageCount()
            if (pageIndex == document.pageCount - 1 || (!wasIndexed && indexed % 25 == 0)) {
                onProgress(indexed, document.pageCount)
            }
        }
        logPdfiumOpen(
            "search_index_complete pages=${document.indexedSearchTextPageCount()}/${document.pageCount} " +
                "elapsedMs=${System.currentTimeMillis() - startedAt}"
        )
    }

    @Synchronized
    fun loadTextOnlyPage(document: DesktopPdfDocument, pageIndex: Int): String {
        val nativeDocument = openDocuments[document.path]?.pointer ?: return ""
        if (document.pageSizes.getOrNull(pageIndex) == null) return ""
        return extractPageText(nativeDocument, pageIndex)
    }

    @Synchronized
    fun loadTextPageData(document: DesktopPdfDocument, pageIndex: Int): DesktopPdfTextPageData {
        val nativeDocument = openDocuments[document.path]?.pointer ?: return DesktopPdfTextPageData()
        val pageSize = document.pageSizes.getOrNull(pageIndex) ?: return DesktopPdfTextPageData()
        return extractPageTextData(nativeDocument, pageIndex, pageSize)
    }

    fun search(document: DesktopPdfDocument, query: String): List<SharedPdfSearchResult> {
        return document.searchIndexed(query)
    }

    @Synchronized
    fun renderPage(
        document: DesktopPdfDocument,
        pageIndex: Int,
        scale: Float,
        renderAnnotations: Boolean = true
    ): DesktopPdfPageRender {
        val nativeDocument = openDocuments[document.path]?.pointer ?: error("PDF document is not open.")
        val pageSize = document.pageSizes.getOrNull(pageIndex) ?: error("Invalid PDF page index $pageIndex.")
        val safeScale = zoomSpec.safeRenderScale(pageSize.width, pageSize.height, scale)
        val width = (pageSize.width * safeScale).roundToInt().coerceAtLeast(1)
        val height = (pageSize.height * safeScale).roundToInt().coerceAtLeast(1)
        val stride = width * 4
        val memory = Memory((stride * height).toLong())
        memory.clear(memory.size())

        val bitmap = api.FPDFBitmap_CreateEx(width, height, FPDF_BITMAP_BGRA, memory, stride)
            ?: error("Pdfium could not allocate render bitmap.")

        try {
            api.FPDFBitmap_FillRect(bitmap, 0, 0, width, height, -1)
            loadPage(nativeDocument, pageIndex).usePointer { page ->
                val flags = FPDF_LCD_TEXT or
                    (if (renderAnnotations) FPDF_ANNOT else FPDF_RENDER_NO_SMOOTHTEXT)
                api.FPDF_RenderPageBitmap(bitmap, page, 0, 0, width, height, 0, flags)
            }
            return DesktopPdfPageRender(
                image = memory.toBufferedImage(width, height, stride).toComposeImageBitmap(),
                width = width,
                height = height
            )
        } finally {
            api.FPDFBitmap_Destroy(bitmap)
        }
    }

    @Synchronized
    fun charIndexAt(
        document: DesktopPdfDocument,
        pageIndex: Int,
        normalizedX: Float,
        normalizedY: Float,
        viewportWidth: Int? = null,
        viewportHeight: Int? = null,
        tolerance: Float = 0.006f
    ): Int? {
        val nativeDocument = openDocuments[document.path]?.pointer ?: return null
        val pageSize = document.pageSizes.getOrNull(pageIndex) ?: return null
        val viewport = pageSize.normalizedViewport(viewportWidth, viewportHeight)
        return runCatching {
            loadPage(nativeDocument, pageIndex).usePointer { page ->
                val textPage = api.FPDFText_LoadPage(page) ?: return@usePointer null
                try {
                    val pagePoint = deviceToPagePoint(
                        page = page,
                        viewport = viewport,
                        normalizedX = normalizedX,
                        normalizedY = normalizedY
                    )
                    api.FPDFText_GetCharIndexAtPos(
                        textPage,
                        pagePoint.first,
                        pagePoint.second,
                        (pageSize.width * tolerance).toDouble(),
                        (pageSize.height * tolerance).toDouble()
                    ).takeIf { it >= 0 }
                } finally {
                    api.FPDFText_ClosePage(textPage)
                }
            }
        }.getOrNull()
    }

    @Synchronized
    fun textRectsForRange(
        document: DesktopPdfDocument,
        pageIndex: Int,
        startIndex: Int,
        endIndex: Int,
        viewportWidth: Int? = null,
        viewportHeight: Int? = null
    ): List<DesktopPdfTextRect> {
        val nativeDocument = openDocuments[document.path]?.pointer ?: return emptyList()
        val pageSize = document.pageSizes.getOrNull(pageIndex) ?: return emptyList()
        val viewport = pageSize.normalizedViewport(viewportWidth, viewportHeight)
        val first = minOf(startIndex, endIndex).coerceAtLeast(0)
        val count = (maxOf(startIndex, endIndex) - first + 1).coerceAtLeast(1)
        return runCatching {
            loadPage(nativeDocument, pageIndex).usePointer { page ->
                val textPage = api.FPDFText_LoadPage(page) ?: return@usePointer emptyList()
                try {
                    val rectCount = api.FPDFText_CountRects(textPage, first, count)
                    (0 until rectCount).mapNotNull { rectIndex ->
                        val left = DoubleArray(1)
                        val top = DoubleArray(1)
                        val right = DoubleArray(1)
                        val bottom = DoubleArray(1)
                        val hasRect = api.FPDFText_GetRect(textPage, rectIndex, left, top, right, bottom) != 0
                        if (!hasRect || right[0] <= left[0] || top[0] <= bottom[0]) {
                            null
                        } else {
                            val bounds = pageToNormalizedBounds(
                                page = page,
                                pageSize = pageSize,
                                viewport = viewport,
                                left = left[0],
                                top = top[0],
                                right = right[0],
                                bottom = bottom[0]
                            )
                            DesktopPdfTextRect(
                                left = bounds.left,
                                top = bounds.top,
                                right = bounds.right,
                                bottom = bounds.bottom
                            )
                        }
                    }
                } finally {
                    api.FPDFText_ClosePage(textPage)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun extractPageText(document: Pointer, pageIndex: Int): String {
        return runCatching {
            loadPage(document, pageIndex).usePointer { page ->
                val textPage = api.FPDFText_LoadPage(page) ?: return@usePointer ""
                try {
                    val charCount = api.FPDFText_CountChars(textPage)
                    extractText(textPage, charCount)
                } finally {
                    api.FPDFText_ClosePage(textPage)
                }
            }
        }.getOrDefault("")
    }

    private fun extractPageTextData(document: Pointer, pageIndex: Int, pageSize: DesktopPdfPageSize): DesktopPdfTextPageData {
        return runCatching {
            loadPage(document, pageIndex).usePointer { page ->
                val textPage = api.FPDFText_LoadPage(page) ?: return@usePointer DesktopPdfTextPageData()
                try {
                    val charCount = api.FPDFText_CountChars(textPage)
                    if (charCount <= 0) return@usePointer DesktopPdfTextPageData()
                    val text = extractText(textPage, charCount)
                    val chars = (0 until charCount).mapNotNull { index ->
                        val unicode = api.FPDFText_GetUnicode(textPage, index)
                        if (unicode <= 0) return@mapNotNull null
                        val left = DoubleArray(1)
                        val right = DoubleArray(1)
                        val bottom = DoubleArray(1)
                        val top = DoubleArray(1)
                        val hasBox = api.FPDFText_GetCharBox(textPage, index, left, right, bottom, top) != 0
                        if (!hasBox) {
                            DesktopPdfTextChar(index, unicode.toChar(), 0f, 0f, 0f, 0f)
                        } else {
                            val bounds = pageToNormalizedBounds(
                                page = page,
                                pageSize = pageSize,
                                viewport = pageSize.normalizedViewport(),
                                left = left[0],
                                top = top[0],
                                right = right[0],
                                bottom = bottom[0]
                            )
                            DesktopPdfTextChar(
                                index = index,
                                char = unicode.toChar(),
                                left = bounds.left,
                                top = bounds.top,
                                right = bounds.right,
                                bottom = bounds.bottom
                            )
                        }
                    }
                    DesktopPdfTextPageData(text = text, chars = chars)
                } finally {
                    api.FPDFText_ClosePage(textPage)
                }
            }
        }.getOrDefault(DesktopPdfTextPageData())
    }

    private fun extractText(textPage: Pointer, charCount: Int): String {
        if (charCount <= 0) return ""
        val buffer = Memory(((charCount + 1) * 2L))
        val written = api.FPDFText_GetText(textPage, 0, charCount, buffer)
        return if (written <= 0) {
            ""
        } else {
            buffer.getCharArray(0, written).concatToString().trimEnd('\u0000')
        }
    }

    private fun extractTableOfContents(document: Pointer, pageCount: Int): List<PdfTocEntry> {
        val entries = mutableListOf<PdfTocEntry>()

        fun visit(parent: Pointer?, level: Int) {
            var bookmark = api.FPDFBookmark_GetFirstChild(document, parent)
            while (bookmark != null) {
                val title = bookmarkTitle(bookmark)
                val pageIndex = bookmarkPageIndex(document, bookmark, pageCount)
                if (title.isNotBlank() && pageIndex != null) {
                    entries += PdfTocEntry(
                        title = title,
                        pageIndex = pageIndex,
                        nestLevel = level
                    )
                }
                visit(bookmark, level + 1)
                bookmark = api.FPDFBookmark_GetNextSibling(document, bookmark)
            }
        }

        runCatching { visit(null, 0) }
        return entries
    }

    private fun bookmarkTitle(bookmark: Pointer): String {
        val lengthBytes = api.FPDFBookmark_GetTitle(bookmark, null, 0)
        if (lengthBytes <= 2) return ""
        val buffer = Memory(lengthBytes.toLong())
        val writtenBytes = api.FPDFBookmark_GetTitle(bookmark, buffer, lengthBytes)
        if (writtenBytes <= 2) return ""
        return String(buffer.getByteArray(0, writtenBytes), Charsets.UTF_16LE)
            .trimEnd('\u0000')
    }

    private fun bookmarkPageIndex(document: Pointer, bookmark: Pointer, pageCount: Int): Int? {
        val dest = api.FPDFBookmark_GetDest(document, bookmark) ?: return null
        return api.FPDFDest_GetDestPageIndex(document, dest)
            .takeIf { it in 0 until pageCount }
    }

    private fun extractEmbeddedAnnotations(
        document: Pointer,
        pageSizes: List<DesktopPdfPageSize>
    ): List<SharedPdfEmbeddedAnnotation> {
        return pageSizes.flatMapIndexed { pageIndex, pageSize ->
            runCatching {
                loadPage(document, pageIndex).usePointer { page ->
                    val count = api.FPDFPage_GetAnnotCount(page).coerceAtLeast(0)
                    val rawAnnotations = (0 until count).mapNotNull { index ->
                        extractEmbeddedAnnotation(page, pageIndex, index, pageSize)
                    }
                    SharedPdfEmbeddedAnnotationThreads.group(rawAnnotations)
                }
            }.getOrDefault(emptyList())
        }
    }

    private fun extractEmbeddedAnnotation(
        page: Pointer,
        pageIndex: Int,
        index: Int,
        pageSize: DesktopPdfPageSize
    ): SharedPdfEmbeddedAnnotation? {
        val annotation = api.FPDFPage_GetAnnot(page, index) ?: return null
        try {
            val subtype = api.FPDFAnnot_GetSubtype(annotation)
            if (subtype == PdfiumAnnotationSubtype.LINK) return null
            val bounds = annotationBounds(page, annotation, pageSize) ?: return null
            val contents = annotationStringValue(annotation, "Contents")
                .ifBlank { annotationStringValue(annotation, "RC") }
            val name = annotationStringValue(annotation, "NM")
            return SharedPdfEmbeddedAnnotation(
                id = "embedded_${pageIndex}_${name.ifBlank { index.toString() }}",
                pageIndex = pageIndex,
                index = index,
                subtype = subtype,
                bounds = bounds,
                contents = contents,
                author = annotationStringValue(annotation, "T"),
                name = name,
                inReplyTo = annotationStringValue(annotation, "IRT")
            )
        } finally {
            api.FPDFPage_CloseAnnot(annotation)
        }
    }

    private fun annotationBounds(
        page: Pointer,
        annotation: Pointer,
        pageSize: DesktopPdfPageSize
    ): PdfPageBounds? {
        val rect = Memory(16)
        if (api.FPDFAnnot_GetRect(annotation, rect) == 0) return null
        val left = rect.getFloat(0).toDouble()
        val top = rect.getFloat(4).toDouble()
        val right = rect.getFloat(8).toDouble()
        val bottom = rect.getFloat(12).toDouble()
        if (left == right || top == bottom) return null
        val normalized = pageToNormalizedBounds(
            page = page,
            pageSize = pageSize,
            left = minOf(left, right),
            top = maxOf(top, bottom),
            right = maxOf(left, right),
            bottom = minOf(top, bottom)
        )
        return PdfPageBounds(
            left = normalized.left,
            top = normalized.top,
            right = normalized.right,
            bottom = normalized.bottom
        ).takeIf { it.right > it.left && it.bottom > it.top }
    }

    private fun annotationStringValue(annotation: Pointer, key: String): String {
        val lengthBytes = api.FPDFAnnot_GetStringValue(annotation, key, null, 0)
        if (lengthBytes <= 2) return ""
        val buffer = Memory(lengthBytes.toLong())
        val writtenBytes = api.FPDFAnnot_GetStringValue(annotation, key, buffer, lengthBytes)
        if (writtenBytes <= 2) return ""
        return String(buffer.getByteArray(0, writtenBytes), Charsets.UTF_16LE)
            .trimEnd('\u0000')
            .cleanEmbeddedAnnotationText()
    }

    private fun String.cleanEmbeddedAnnotationText(): String {
        return replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }

    private fun loadPage(document: Pointer, pageIndex: Int): PointerResource {
        val page = api.FPDF_LoadPage(document, pageIndex)
            ?: error("Pdfium could not open page ${pageIndex + 1}.")
        return PointerResource(page, api::FPDF_ClosePage)
    }

    private fun initLibrary() {
        if (!initialized) {
            api.FPDF_InitLibrary()
            initialized = true
        }
    }

    private fun resolvePdfiumDll(): File {
        val overridePath = System.getProperty("reader.pdfium.dll")
            ?: System.getenv("READER_PDFIUM_DLL")
        if (!overridePath.isNullOrBlank()) {
            return File(overridePath).absoluteFile
        }

        val relativePath = listOf("third_party", "pdfium", "win-x64-v8", "bin", "pdfium.dll")
            .joinToString(File.separator)
        val roots = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .take(6)
            .toList()

        return roots
            .map { File(it, relativePath).absoluteFile }
            .firstOrNull { it.exists() }
            ?: File(File(System.getProperty("user.dir")).absoluteFile, relativePath).absoluteFile
    }

    private fun Memory.toBufferedImage(width: Int, height: Int, stride: Int): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val buffer = getByteBuffer(0, size()).order(ByteOrder.LITTLE_ENDIAN)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            buffer.position(y * stride)
            for (x in 0 until width) {
                val b = buffer.get().toInt() and 0xFF
                val g = buffer.get().toInt() and 0xFF
                val r = buffer.get().toInt() and 0xFF
                val a = buffer.get().toInt() and 0xFF
                pixels[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        image.setRGB(0, 0, width, height, pixels, 0, width)
        return image
    }

    private fun pdfiumLoadErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            0 -> "No Pdfium error detail was reported."
            1 -> "Pdfium reported an unknown load error."
            2 -> "The file was not found or could not be opened."
            3 -> "The file is not in a PDF format supported by this Pdfium build, or Pdfium detected corruption."
            4 -> "A password is required or the supplied password is incorrect."
            5 -> "The PDF uses an unsupported security scheme."
            6 -> "Pdfium could not load the document page tree."
            7 -> "Pdfium could not load XFA data."
            8 -> "Pdfium could not lay out XFA data."
            else -> "Pdfium reported load error code $errorCode."
        }
    }

    private fun logPdfiumOpen(message: String) {
        println("DesktopPdfiumOpen $message")
    }

    private data class DesktopOpenPdfDocument(
        val pointer: Pointer,
        val backingMemory: Memory? = null
    )

    private class PointerResource(
        private val pointer: Pointer,
        private val closer: (Pointer) -> Unit
    ) {
        fun <T> usePointer(block: (Pointer) -> T): T {
            try {
                return block(pointer)
            } finally {
                closer(pointer)
            }
        }
    }

    private data class NormalizedViewport(
        val width: Int,
        val height: Int
    )

    private data class NormalizedBounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    private fun DesktopPdfPageSize.normalizedViewport(widthOverride: Int? = null, heightOverride: Int? = null): NormalizedViewport {
        return NormalizedViewport(
            width = widthOverride?.coerceAtLeast(1) ?: width.roundToInt().coerceAtLeast(1),
            height = heightOverride?.coerceAtLeast(1) ?: height.roundToInt().coerceAtLeast(1)
        )
    }

    private fun pageToNormalizedBounds(
        page: Pointer,
        pageSize: DesktopPdfPageSize,
        viewport: NormalizedViewport = pageSize.normalizedViewport(),
        left: Double,
        top: Double,
        right: Double,
        bottom: Double
    ): NormalizedBounds {
        val topLeft = pageToDevicePoint(page, viewport, left, top)
        val bottomRight = pageToDevicePoint(page, viewport, right, bottom)
        val deviceLeft = minOf(topLeft.first, bottomRight.first).toFloat()
        val deviceRight = maxOf(topLeft.first, bottomRight.first).toFloat()
        val deviceTop = minOf(topLeft.second, bottomRight.second).toFloat()
        val deviceBottom = maxOf(topLeft.second, bottomRight.second).toFloat()
        return NormalizedBounds(
            left = (deviceLeft / viewport.width).coerceIn(0f, 1f),
            top = (deviceTop / viewport.height).coerceIn(0f, 1f),
            right = (deviceRight / viewport.width).coerceIn(0f, 1f),
            bottom = (deviceBottom / viewport.height).coerceIn(0f, 1f)
        )
    }

    private fun pageToDevicePoint(
        page: Pointer,
        viewport: NormalizedViewport,
        pageX: Double,
        pageY: Double
    ): Pair<Int, Int> {
        val deviceX = IntArray(1)
        val deviceY = IntArray(1)
        api.FPDF_PageToDevice(
            page,
            0,
            0,
            viewport.width,
            viewport.height,
            0,
            pageX,
            pageY,
            deviceX,
            deviceY
        )
        return deviceX[0] to deviceY[0]
    }

    private fun deviceToPagePoint(
        page: Pointer,
        viewport: NormalizedViewport,
        normalizedX: Float,
        normalizedY: Float
    ): Pair<Double, Double> {
        val pageX = DoubleArray(1)
        val pageY = DoubleArray(1)
        api.FPDF_DeviceToPage(
            page,
            0,
            0,
            viewport.width,
            viewport.height,
            0,
            (normalizedX.coerceIn(0f, 1f) * viewport.width).roundToInt(),
            (normalizedY.coerceIn(0f, 1f) * viewport.height).roundToInt(),
            pageX,
            pageY
        )
        return pageX[0] to pageY[0]
    }

    @Suppress("FunctionName")
    private interface PdfiumLibrary : Library {
        fun FPDF_InitLibrary()
        fun FPDF_LoadDocument(filePath: String, password: String?): Pointer?
        fun FPDF_LoadMemDocument(dataBuf: Pointer, size: Int, password: String?): Pointer?
        fun FPDF_CloseDocument(document: Pointer)
        fun FPDF_GetLastError(): Int
        fun FPDF_GetPageCount(document: Pointer): Int
        fun FPDFBookmark_GetFirstChild(document: Pointer, bookmark: Pointer?): Pointer?
        fun FPDFBookmark_GetNextSibling(document: Pointer, bookmark: Pointer): Pointer?
        fun FPDFBookmark_GetTitle(bookmark: Pointer, buffer: Pointer?, buflen: Int): Int
        fun FPDFBookmark_GetDest(document: Pointer, bookmark: Pointer): Pointer?
        fun FPDFDest_GetDestPageIndex(document: Pointer, dest: Pointer): Int
        fun FPDF_LoadPage(document: Pointer, pageIndex: Int): Pointer?
        fun FPDF_ClosePage(page: Pointer)
        fun FPDF_GetPageWidthF(page: Pointer): Float
        fun FPDF_GetPageHeightF(page: Pointer): Float
        fun FPDFPage_GetAnnotCount(page: Pointer): Int
        fun FPDFPage_GetAnnot(page: Pointer, index: Int): Pointer?
        fun FPDFPage_CloseAnnot(annotation: Pointer)
        fun FPDFAnnot_GetSubtype(annotation: Pointer): Int
        fun FPDFAnnot_GetRect(annotation: Pointer, rect: Pointer): Int
        fun FPDFAnnot_GetStringValue(annotation: Pointer, key: String, buffer: Pointer?, buflen: Int): Int
        fun FPDFBitmap_CreateEx(width: Int, height: Int, format: Int, firstScan: Pointer, stride: Int): Pointer?
        fun FPDFBitmap_FillRect(bitmap: Pointer, left: Int, top: Int, width: Int, height: Int, color: Int)
        fun FPDFBitmap_Destroy(bitmap: Pointer)
        fun FPDF_RenderPageBitmap(
            bitmap: Pointer,
            page: Pointer,
            startX: Int,
            startY: Int,
            sizeX: Int,
            sizeY: Int,
            rotate: Int,
            flags: Int
        )

        fun FPDFText_LoadPage(page: Pointer): Pointer?
        fun FPDFText_ClosePage(textPage: Pointer)
        fun FPDFText_CountChars(textPage: Pointer): Int
        fun FPDFText_GetText(textPage: Pointer, startIndex: Int, count: Int, result: Pointer): Int
        fun FPDFText_GetUnicode(textPage: Pointer, index: Int): Int
        fun FPDFText_GetCharBox(
            textPage: Pointer,
            index: Int,
            left: DoubleArray,
            right: DoubleArray,
            bottom: DoubleArray,
            top: DoubleArray
        ): Int
        fun FPDFText_GetCharIndexAtPos(
            textPage: Pointer,
            x: Double,
            y: Double,
            xTolerance: Double,
            yTolerance: Double
        ): Int
        fun FPDFText_CountRects(textPage: Pointer, startIndex: Int, count: Int): Int
        fun FPDFText_GetRect(
            textPage: Pointer,
            rectIndex: Int,
            left: DoubleArray,
            top: DoubleArray,
            right: DoubleArray,
            bottom: DoubleArray
        ): Int
        fun FPDF_PageToDevice(
            page: Pointer,
            startX: Int,
            startY: Int,
            sizeX: Int,
            sizeY: Int,
            rotate: Int,
            pageX: Double,
            pageY: Double,
            deviceX: IntArray,
            deviceY: IntArray
        )
        fun FPDF_DeviceToPage(
            page: Pointer,
            startX: Int,
            startY: Int,
            sizeX: Int,
            sizeY: Int,
            rotate: Int,
            deviceX: Int,
            deviceY: Int,
            pageX: DoubleArray,
            pageY: DoubleArray
        )
    }
}
