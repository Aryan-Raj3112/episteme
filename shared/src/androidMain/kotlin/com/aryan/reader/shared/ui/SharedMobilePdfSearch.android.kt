package com.aryan.reader.shared.ui

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.pdf.SharedPdfSearchIndex
import com.aryan.reader.shared.pdf.SharedPdfSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
internal actual suspend fun searchSharedMobilePdf(
    book: BookItem,
    query: String,
    password: String?,
): List<SharedPdfSearchResult> {
    val context = AndroidSharedMobileContext.applicationContext ?: return emptyList()
    if (query.isBlank()) return emptyList()
    return runCatching {
        withContext(Dispatchers.IO) {
            AndroidSharedPdfiumRuntime.mutex.withLock {
                context.openSharedPdfDescriptor(book).use { pfd ->
                    AndroidSharedPdfiumRuntime.core.newDocument(pfd, password).use { document ->
                        val pageCount = document.getPageCount()
                        val index = SharedPdfSearchIndex(pageCount)
                        for (pageIndex in 0 until pageCount) {
                            currentCoroutineContext().ensureActive()
                            val text = document.openPage(pageIndex)?.use { page ->
                                page.openTextPage().use { textPage ->
                                    val count = textPage.textPageCountChars()
                                    if (count > 0) textPage.textPageGetText(0, count).orEmpty() else ""
                                }
                            }.orEmpty()
                            index.putPage(pageIndex, text.trimEnd('\u0000'))
                        }
                        index.search(query)
                    }
                }
            }
        }
    }.getOrDefault(emptyList())
}
