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
import com.aryan.reader.shared.pdf.IosPdfTextPage
import com.aryan.reader.shared.pdf.PdfTextPageSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
actual fun rememberPdfTextPageSession(book: BookItem, pageIndex: Int): PdfTextPageSession? {
    var session by remember(book.path, pageIndex) { mutableStateOf<PdfTextPageSession?>(null) }

    LaunchedEffect(book.path, pageIndex) {
        session = withContext(Dispatchers.Main) {
            IosPdfiumRuntime.mutex.withLock {
                IosPdfTextPage.open(book.path, pageIndex)
            }
        }
    }

    DisposableEffect(book.path, pageIndex) {
        onDispose {
            session?.close()
            session = null
        }
    }

    return session
}
