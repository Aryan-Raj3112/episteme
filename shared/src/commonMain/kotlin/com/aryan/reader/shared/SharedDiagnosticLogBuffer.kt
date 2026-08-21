package com.aryan.reader.shared

/**
 * Small bounded log buffer for platforms that cannot safely expose their
 * process log store to the app (notably iOS).  The buffer deliberately keeps
 * only recent entries and a byte-like character budget so diagnostics cannot
 * become an unbounded memory consumer while a reader is open for a long time.
 */
class SharedDiagnosticLogBuffer(
    private val maxEntries: Int = 1_200,
    private val maxCharacters: Int = 256 * 1024,
) {
    private val entries = ArrayDeque<String>()
    private var characterCount = 0

    fun append(entry: String) {
        val normalized = entry.trimEnd()
        if (normalized.isEmpty() || maxEntries <= 0 || maxCharacters <= 0) return

        val bounded = normalized.take(maxCharacters)
        entries.addLast(bounded)
        characterCount += bounded.length
        while (entries.size > maxEntries || characterCount > maxCharacters) {
            characterCount -= entries.removeFirst().length
        }
    }

    fun snapshot(): List<String> = entries.toList()

    fun clear() {
        entries.clear()
        characterCount = 0
    }

    val size: Int
        get() = entries.size
}
