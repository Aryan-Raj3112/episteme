package com.aryan.reader.paginatedreader

/**
 * Table-cell weight shared by both native stacks (B1 step 3, table block
 * kind).
 *
 * Android's two table branches and shared's cell sizing all mapped
 * `colspan` to a row weight with the identical floor. Only the mapping lives
 * here — width distribution (gap subtraction, remainder absorption) stays
 * per-stack: shared subtracts cell gaps and absorbs rounding, Android has no
 * gaps, so sharing the distributor would change Android rendering.
 */

/** Row weight for a cell spanning [colspan] columns; non-positive spans weigh 1. */
fun nativeTableCellWeight(colspan: Int): Float = colspan.coerceAtLeast(1).toFloat()
