package com.aryan.reader.shared.ui

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.PdfTocEntry
import io.legere.pdfiumandroid.api.Bookmark
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
internal actual suspend fun loadSharedMobilePdfOutline(
    book: BookItem,
    password: String?,
): List<PdfTocEntry> {
    val context = AndroidSharedMobileContext.applicationContext ?: return emptyList()
    return runCatching {
        withContext(Dispatchers.IO) {
            AndroidSharedPdfiumRuntime.mutex.withLock {
                context.openSharedPdfDescriptor(book).use { pfd ->
                    AndroidSharedPdfiumRuntime.core.newDocument(pfd, password).use { document ->
                        fun flatten(
                            bookmarks: List<io.legere.pdfiumandroid.api.Bookmark>,
                            level: Int,
                            destination: MutableList<PdfTocEntry>,
                        ) {
                            bookmarks.forEach { bookmark ->
                                destination += PdfTocEntry(
                                    title = bookmark.title ?: "Untitled Chapter",
                                    pageIndex = bookmark.pageIdx.toInt(),
                                    nestLevel = level,
                                )
                                flatten(bookmark.children, level + 1, destination)
                            }
                        }
                        buildList { flatten(document.getAndroidCompatiblePdfTableOfContents(), 0, this) }
                    }
                }
            }
        }
    }.getOrDefault(emptyList())
}

/**
 * Mirrors Android's production workaround for pdfiumandroid's depth-state leak,
 * which can truncate bookmark siblings. Reflection is intentionally isolated here
 * and falls back to the library traversal if its internals change.
 */
suspend fun PdfDocumentKt.getAndroidCompatiblePdfTableOfContents(): List<Bookmark> = runCatching {
    val documentField = PdfDocumentKt::class.java.getDeclaredField("document").apply { isAccessible = true }
    val documentWrapper = documentField.get(this) ?: return getTableOfContents()
    val nativeDocumentField = documentWrapper.javaClass.getDeclaredField("nativeDocument").apply {
        isAccessible = true
    }
    val nativeDocument = nativeDocumentField.get(documentWrapper) ?: return getTableOfContents()
    val pointerField = documentWrapper.javaClass.getDeclaredField("mNativeDocPtr").apply {
        isAccessible = true
    }
    val documentPointer = pointerField.get(documentWrapper) as Long
    val longType = Long::class.javaPrimitiveType!!
    val nativeClass = nativeDocument.javaClass
    val titleMethod = nativeClass.getMethod("getBookmarkTitle", longType)
    val destinationMethod = nativeClass.getMethod("getBookmarkDestIndex", longType, longType)
    val firstChildMethod = nativeClass.getMethod("getFirstChildBookmark", longType, longType)
    val siblingMethod = nativeClass.getMethod("getSiblingBookmark", longType, longType)
    val visited = mutableSetOf<Long>()

    fun walk(destination: MutableList<Bookmark>, startPointer: Long, level: Int) {
        var currentPointer = startPointer
        while (currentPointer != 0L && visited.add(currentPointer)) {
            val bookmark = Bookmark().apply {
                mNativePtr = currentPointer
                title = titleMethod.invoke(nativeDocument, currentPointer) as? String ?: "Untitled"
                pageIdx = destinationMethod.invoke(nativeDocument, documentPointer, currentPointer) as Long
            }
            destination += bookmark
            val firstChild = firstChildMethod.invoke(
                nativeDocument,
                documentPointer,
                currentPointer,
            ) as Long
            if (firstChild != 0L && level < AndroidSharedPdfMaxOutlineDepth) {
                walk(bookmark.children, firstChild, level + 1)
            }
            currentPointer = siblingMethod.invoke(
                nativeDocument,
                documentPointer,
                currentPointer,
            ) as Long
        }
    }

    val result = mutableListOf<Bookmark>()
    val firstRoot = firstChildMethod.invoke(nativeDocument, documentPointer, 0L) as Long
    if (firstRoot != 0L) walk(result, firstRoot, 0)
    result.ifEmpty { getTableOfContents() }
}.getOrElse { getTableOfContents() }

private const val AndroidSharedPdfMaxOutlineDepth = 128
