package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.pdf.SharedPdfEmbeddedAnnotation

@Composable
internal expect fun rememberSharedMobilePdfEmbeddedAnnotations(
    book: BookItem,
    pageIndex: Int,
    password: String? = null,
): List<SharedPdfEmbeddedAnnotation>
