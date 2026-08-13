package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.pdf.SharedPdfEmbeddedAnnotation
import com.aryan.reader.shared.pdf.loadIosPdfEmbeddedAnnotations

@Composable
internal actual fun rememberSharedMobilePdfEmbeddedAnnotations(
    book: BookItem,
    pageIndex: Int,
    password: String?,
): List<SharedPdfEmbeddedAnnotation> {
    var annotations by remember(book.path, pageIndex, password) {
        mutableStateOf(emptyList<SharedPdfEmbeddedAnnotation>())
    }
    LaunchedEffect(book.path, pageIndex, password) {
        annotations = loadIosPdfEmbeddedAnnotations(book.path, pageIndex, password)
    }
    return annotations
}
