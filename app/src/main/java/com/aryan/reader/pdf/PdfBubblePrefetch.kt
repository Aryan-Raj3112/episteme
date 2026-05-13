package com.aryan.reader.pdf

internal const val PDF_BUBBLE_PREFETCH_RADIUS = 1

internal fun buildPdfBubblePrefetchOrder(
    currentPage: Int,
    totalPages: Int,
    radius: Int = PDF_BUBBLE_PREFETCH_RADIUS
): List<Int> {
    if (totalPages <= 0 || radius < 0) return emptyList()

    val clampedCurrentPage = currentPage.coerceIn(0, totalPages - 1)
    val ordered = LinkedHashSet<Int>()
    ordered += clampedCurrentPage

    for (distance in 1..radius) {
        val next = clampedCurrentPage + distance
        val previous = clampedCurrentPage - distance
        if (next in 0 until totalPages) ordered += next
        if (previous in 0 until totalPages) ordered += previous
    }

    return ordered.toList()
}
