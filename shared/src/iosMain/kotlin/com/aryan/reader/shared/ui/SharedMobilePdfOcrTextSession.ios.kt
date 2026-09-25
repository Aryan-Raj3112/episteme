package com.aryan.reader.shared.ui

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.pdf.IosPdfOcrLanguagePreferences
import com.aryan.reader.shared.pdf.IosPdfOcrTextPageSession
import com.aryan.reader.shared.pdf.PdfTextPageSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual suspend fun openSharedMobilePdfOcrTextSession(
    book: BookItem,
    pageIndex: Int,
    password: String?,
): PdfTextPageSession? = withContext(Dispatchers.Default) {
    IosPdfOcrTextPageSession.open(
        path = book.path,
        pageIndex = pageIndex,
        password = password,
        languages = IosPdfOcrLanguagePreferences.languages,
    )
}
