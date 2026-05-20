package com.aryan.reader.pdf

enum class AnnotationType {
    INK, TEXT
}

enum class InkType {
    PEN, HIGHLIGHTER, HIGHLIGHTER_ROUND, ERASER, FOUNTAIN_PEN, PENCIL, TEXT
}

data class PdfPoint(val x: Float, val y: Float, val timestamp: Long = 0L)
