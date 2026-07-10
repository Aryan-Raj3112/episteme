package com.aryan.reader.shared.ui

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.pdf.SharedPdfSearchResult

internal expect suspend fun searchSharedMobilePdf(
    book: BookItem,
    query: String
): List<SharedPdfSearchResult>
