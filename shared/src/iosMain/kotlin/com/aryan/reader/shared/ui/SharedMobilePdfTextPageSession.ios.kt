@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.pdf.IosPdfiumRuntime
import com.aryan.reader.shared.pdf.IosPdfOcrTextPageSession
import com.aryan.reader.shared.pdf.IosPdfOcrLanguagePreferences
import com.aryan.reader.shared.pdf.IosPdfTextPage
import com.aryan.reader.shared.pdf.PdfTextPageSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
actual fun rememberPdfTextPageSession(
    book: BookItem,
    pageIndex: Int,
    password: String?,
): PdfTextPageSession? {
    val ocrLanguages = IosPdfOcrLanguagePreferences.languages
    var session by remember(book.path, pageIndex, password, ocrLanguages) { mutableStateOf<PdfTextPageSession?>(null) }

    LaunchedEffect(book.path, pageIndex, password, ocrLanguages) {
        session = withContext(Dispatchers.Default) {
            val nativeSession = IosPdfiumRuntime.mutex.withLock {
                IosPdfTextPage.open(book.path, pageIndex, password)
            }
            if (nativeSession?.pageCharCount ?: 0 > 0) {
                nativeSession
            } else {
                nativeSession?.close()
                // Scanned/image-only pages have no PDFium text page. Vision supplies the same
                // character/geometry contract so selection, copy, search highlights, and TTS
                // can continue through the shared reader path.
                IosPdfOcrTextPageSession.open(book.path, pageIndex, password, ocrLanguages)
            }
        }
    }

    DisposableEffect(book.path, pageIndex, password, ocrLanguages) {
        onDispose {
            session?.close()
            session = null
        }
    }

    return session
}
