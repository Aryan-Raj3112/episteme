package com.aryan.reader.shared

/**
 * Case-insensitive natural ordering for user-visible titles and names.
 *
 * Splits both strings into digit / non-digit runs. Non-digit runs compare
 * case-insensitively; digit runs compare by numeric value (stripped length,
 * then lexicographically, so arbitrarily long runs never overflow) with
 * fewer leading zeros first as a deterministic tie-break. A trailing
 * case-sensitive comparison only breaks exact ties so ordering stays
 * deterministic across platforms.
 *
 * Deliberately regex-free and stdlib-only so it behaves identically on
 * JVM, Android, and Kotlin/Native.
 */
fun compareNatural(a: String, b: String): Int {
    if (a === b) return 0
    var i = 0
    var j = 0
    val n = a.length
    val m = b.length
    while (i < n && j < m) {
        val ca = a[i]
        val cb = b[j]
        val da = ca in '0'..'9'
        val db = cb in '0'..'9'
        if (da && db) {
            var k = i
            while (k < n && a[k] in '0'..'9') k++
            var l = j
            while (l < m && b[l] in '0'..'9') l++
            val rawA = a.substring(i, k)
            val rawB = b.substring(j, l)
            val strippedA = rawA.trimStart('0').takeIf { it.isNotEmpty() } ?: "0"
            val strippedB = rawB.trimStart('0').takeIf { it.isNotEmpty() } ?: "0"
            val lengthCmp = strippedA.length.compareTo(strippedB.length)
            if (lengthCmp != 0) return lengthCmp
            val digitsCmp = strippedA.compareTo(strippedB)
            if (digitsCmp != 0) return digitsCmp
            val leadingZeroCmp = rawA.length.compareTo(rawB.length)
            if (leadingZeroCmp != 0) return leadingZeroCmp
            i = k
            j = l
            continue
        }
        val la = ca.lowercaseChar()
        val lb = cb.lowercaseChar()
        if (la != lb) return la.compareTo(lb)
        i++
        j++
    }
    if (i < n) return 1
    if (j < m) return -1
    return a.compareTo(b)
}

/** Natural ordering comparator for plain strings. */
val naturalStringComparator: Comparator<String> = Comparator(::compareNatural)

/** Comparator that orders items by a natural-sort string key. */
fun <T> compareByNatural(selector: (T) -> String): Comparator<T> =
    Comparator { x, y -> compareNatural(selector(x), selector(y)) }
