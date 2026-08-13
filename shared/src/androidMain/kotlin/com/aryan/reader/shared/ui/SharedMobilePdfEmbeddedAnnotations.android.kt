package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.pdf.SharedPdfEmbeddedAnnotation

/** Android's production PDF host performs its own embedded-annotation extraction. */
@Composable
internal actual fun rememberSharedMobilePdfEmbeddedAnnotations(
    book: BookItem,
    pageIndex: Int,
    password: String?,
): List<SharedPdfEmbeddedAnnotation> = emptyList()
