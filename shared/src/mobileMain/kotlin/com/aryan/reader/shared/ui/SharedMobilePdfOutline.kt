package com.aryan.reader.shared.ui

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.PdfTocEntry

internal expect suspend fun loadSharedMobilePdfOutline(book: BookItem): List<PdfTocEntry>
