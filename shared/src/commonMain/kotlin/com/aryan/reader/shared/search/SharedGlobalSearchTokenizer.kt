package com.aryan.reader.shared.search

/**
 * Portable tokenizer. One implementation for queries and documents so they
 * cannot drift. No `java.*`: lowercase via `kotlin.text`, diacritics via a
 * small manual fold, CJK via range checks.
 *
 * CJK (Chinese/Japanese/Korean) has no spaces between words, so emitting
 * whitespace-separated "words" finds nothing. Each CJK character is its own
 * token; Latin/digit runs are word tokens. Mixed text yields both.
 */
object SharedGlobalSearchTokenizer {

    fun tokenize(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val out = mutableListOf<String>()
        val word = StringBuilder()
        fun flushWord() {
            if (word.isEmpty()) return
            val folded = foldDiacritics(word.toString().lowercase())
            if (folded.length >= SharedGlobalSearchPolicy.MIN_TOKEN_LENGTH) out += folded
            word.clear()
        }
        for (ch in text) {
            when {
                isCjk(ch) -> {
                    flushWord()
                    // CJK tokens are single chars, lowercased where applicable
                    // (Hangul jamo/case aside, lowercase is a no-op for Han).
                    out += ch.toString().lowercase()
                }
                ch.isLetterOrDigit() -> word.append(ch)
                ch == '\'' || ch == '\u2019' -> word.append(ch)
                else -> flushWord()
            }
        }
        flushWord()
        return out
    }

    fun tokenizeQuery(raw: String): List<String> = tokenize(raw)

    fun isCjk(ch: Char): Boolean {
        val v = ch.code
        return v in 0x4E00..0x9FFF || // CJK Unified Ideographs
            v in 0x3400..0x4DBF || // Extension A
            v in 0x3040..0x309F || // Hiragana
            v in 0x30A0..0x30FF || // Katakana
            v in 0xAC00..0xD7AF || // Hangul Syllables
            v in 0x1100..0x11FF // Hangul Jamo
    }

    /**
     * Manual Latin diacritics fold so `commonMain` needs no
     * `java.text.Normalizer`. Covers Latin-1 Supplement + common
     * Latin Extended-A used in book text; unknown chars pass through.
     */
    fun foldDiacritics(value: String): String {
        if (value.isEmpty()) return value
        var changed = false
        val sb = StringBuilder(value.length)
        for (ch in value) {
            sb.append(
                when (ch) {
                    'à', 'á', 'â', 'ã', 'ä', 'å', 'ā', 'ă', 'ą' -> { changed = true; 'a' }
                    'ç', 'ć', 'ĉ', 'ċ', 'č' -> { changed = true; 'c' }
                    'ď', 'đ', 'ð' -> { changed = true; 'd' }
                    'è', 'é', 'ê', 'ë', 'ē', 'ĕ', 'ė', 'ę', 'ě' -> { changed = true; 'e' }
                    'ĝ', 'ğ', 'ġ', 'ģ' -> { changed = true; 'g' }
                    'ĥ', 'ħ' -> { changed = true; 'h' }
                    'ì', 'í', 'î', 'ï', 'ĩ', 'ī', 'ĭ', 'į', 'ı' -> { changed = true; 'i' }
                    'ĵ' -> { changed = true; 'j' }
                    'ķ' -> { changed = true; 'k' }
                    'ĺ', 'ļ', 'ľ', 'ŀ', 'ł' -> { changed = true; 'l' }
                    'ñ', 'ń', 'ņ', 'ň' -> { changed = true; 'n' }
                    'ò', 'ó', 'ô', 'õ', 'ö', 'ø', 'ō', 'ŏ', 'ő', 'œ' -> { changed = true; 'o' }
                    'ŕ', 'ŗ', 'ř' -> { changed = true; 'r' }
                    'ś', 'ŝ', 'ş', 'š', 'ș', 'ß' -> { changed = true; 's' }
                    'ţ', 'ť', 'ŧ', 'ț' -> { changed = true; 't' }
                    'ù', 'ú', 'û', 'ü', 'ũ', 'ū', 'ŭ', 'ů', 'ű', 'ų' -> { changed = true; 'u' }
                    'ŵ' -> { changed = true; 'w' }
                    'ý', 'ÿ', 'ŷ' -> { changed = true; 'y' }
                    'ź', 'ż', 'ž' -> { changed = true; 'z' }
                    'š' -> { changed = true; 's' }
                    else -> ch
                },
            )
        }
        return if (changed) sb.toString() else value
    }
}

/**
 * Parses raw UI input. `"quoted"` spans become phrases; remaining text
 * becomes AND terms; the last term is a prefix when input lacks trailing
 * whitespace (as-you-type). Capped at [SharedGlobalSearchPolicy.MAX_QUERY_TERMS].
 */
fun parseSharedSearchQuery(raw: String): SharedSearchQuery {
    val phrases = mutableListOf<String>()
    val phraseRegex = Regex("\"([^\"]+)\"")
    var withoutPhrases = raw
    for (m in phraseRegex.findAll(raw)) {
        val inner = m.groupValues[1]
        if (inner.isNotBlank()) phrases += inner
        withoutPhrases = withoutPhrases.replace(m.value, " ")
    }
    val terms = SharedGlobalSearchTokenizer.tokenizeQuery(withoutPhrases)
        .take(SharedGlobalSearchPolicy.MAX_QUERY_TERMS)
    val hasTrailingSpace = raw.isNotEmpty() && raw.last().isWhitespace()
    val prefixTerm = if (!hasTrailingSpace && terms.isNotEmpty() && phrases.isEmpty()) {
        // Prefix only for single/partial-word typing; multi-term queries
        // already constrain enough without prefix expansion.
        terms.last().takeIf { it.length >= 2 && !SharedGlobalSearchTokenizer.isCjk(it[0]) }
    } else {
        null
    }
    return SharedSearchQuery(raw = raw, terms = terms, phrases = phrases, prefixTerm = prefixTerm)
}
