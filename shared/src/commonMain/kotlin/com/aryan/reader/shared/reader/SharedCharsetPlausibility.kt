package com.aryan.reader.shared.reader

/**
 * Language-model scoring for legacy charset candidates on platforms without a
 * statistical charset detector (juniversalchardet covers the JVM; Kotlin/Native
 * delegates to CoreFoundation's ICU-backed converter registry).
 *
 * Problem being solved: a byte string that strictly decodes under several
 * codepages is not evidence of correctness. Cyrillic bytes decode "cleanly"
 * as mojibake under windows-1252; GB18030 decodes Big5 and EUC-KR text into
 * plausible-but-wrong Han. The models below assign each candidate codepage a
 * natural-language plausibility score for its decoded output, and callers pick
 * the highest-scoring candidate instead of the first one that happens to parse.
 *
 * Two model families:
 * - [CjkGate]: strict structural gating for CJK encodings, which are mutually
 *   decodable (GB18030 is a superset of GBK and decodes Big5/EUC payloads
 *   without error). Acceptance requires the decoded text's script profile
 *   (Han, kana, Hangul) to match what the candidate encoding produces for real
 *   text in its own script — mojibake decodes fail the gate or score low.
 * - Single-byte tables: character-frequency models per script family
 *   (Latin/Cyrillic/Greek/Hebrew/Arabic), mirroring how juniversalchardet's
 *   language models score single-byte decodes. A wrong codepage permutes the
 *   letters and lands on rare characters, scoring far below the correct one.
 *   Latin's model includes ASCII letter frequencies so heavily-accented
 *   Western text wins over Cyrillic/Greek mojibake decodes.
 *
 * Weights are unnormalized corpus-level letter frequencies (they only need to
 * rank candidates for the same payload, not sum to 1 across models).
 */
object SharedCharsetPlausibility {

    /** Decoded-text script profile fractions used by [cjkAccepts]. */
    data class ScriptProfile(
        val hanRatio: Float,
        val kanaRatio: Float,
        val hangulRatio: Float,
        val halfwidthKanaRatio: Float
    ) {
        val kanaTotalRatio: Float get() = kanaRatio + halfwidthKanaRatio
    }

    /** Computes the script profile of decoded CJK candidate output. */
    fun scriptProfile(decoded: String): ScriptProfile {
        var han = 0
        var kana = 0
        var hangul = 0
        var halfwidthKana = 0
        for (char in decoded) {
            val code = char.code
            when {
                code in HanStart..HanEnd || code > MaxBmpHan -> han++
                code in KanaStart..KanaEnd -> kana++
                code in HangulStart..HangulEnd -> hangul++
                code in HalfwidthKanaStart..HalfwidthKanaEnd -> halfwidthKana++
            }
        }
        val total = decoded.length.coerceAtLeast(1)
        return ScriptProfile(
            hanRatio = han / total.toFloat(),
            kanaRatio = kana / total.toFloat(),
            hangulRatio = hangul / total.toFloat(),
            halfwidthKanaRatio = halfwidthKana / total.toFloat()
        )
    }

    /**
     * Script gates for CJK candidates. Each real CJK document strongly shows
     * its own script in the decoded output; cross-script mojibake decodes do
     * not (they either fail strict decode — verified offline for all
     * single-byte Cyrillic/Greek/Hebrew/Arabic/Turkish/CE fixtures — or land
     * on wrong-script characters).
     */
    fun cjkAccepts(candidate: SharedCjkCandidate, profile: ScriptProfile): Boolean {
        return when (candidate) {
            SharedCjkCandidate.SHIFT_JIS, SharedCjkCandidate.EUC_JP ->
                profile.kanaTotalRatio > CjkKanaThreshold
            SharedCjkCandidate.BIG5 ->
                profile.hanRatio > CjkHanThreshold &&
                    profile.kanaTotalRatio < CjkForeignScriptThreshold &&
                    profile.hangulRatio < CjkForeignScriptThreshold
            SharedCjkCandidate.EUC_KR ->
                profile.hangulRatio > CjkHanThreshold
            SharedCjkCandidate.GB18030 ->
                profile.hanRatio > CjkHanThreshold &&
                    profile.kanaTotalRatio < CjkForeignScriptThreshold &&
                    profile.hangulRatio < CjkForeignScriptThreshold
        }
    }

    /**
     * True when the decoded text contains C0/C1 control characters other than
     * common whitespace. Natural-language text in any supported legacy
     * codepage never contains them, so their presence is decisive evidence of
     * a wrong-codepage decode (e.g. Central European bytes mapped through
     * ISO-8859-15 produce 0x80–0x9F controls).
     */
    fun hasControlCharacters(decoded: String): Boolean {
        for (char in decoded) {
            val code = char.code
            val isWhitespace = code == TabCode || code == NewlineCode ||
                code == CarriageReturnCode || code == FormFeedCode
            if ((code < SpaceCode || code in C1Start..C1End) && !isWhitespace) {
                return true
            }
        }
        return false
    }

    /**
     * Scores a single-byte decode against the language model of the script
     * family the candidate codepage targets. Higher is more plausible; scores
     * are only comparable across candidates for the same source bytes.
     */
    fun scoreSingleByteDecode(decoded: String, model: SharedSingleByteModel): Float {
        var letterCount = 0
        var weightSum = 0f
        for (char in decoded) {
            if (!char.isLetter()) continue
            letterCount++
            weightSum += model.weightFor(char.lowercaseChar())
        }
        if (letterCount == 0) return 0f
        return weightSum / letterCount
    }

    private const val SpaceCode = 0x20
    private const val TabCode = 0x09
    private const val NewlineCode = 0x0A
    private const val CarriageReturnCode = 0x0D
    private const val FormFeedCode = 0x0C
    private const val C1Start = 0x7F
    private const val C1End = 0x9F

    private const val HanStart = 0x4E00
    private const val HanEnd = 0x9FFF
    private const val MaxBmpHan = 0x20000 // astral Han (GB18030 extension chars)
    private const val KanaStart = 0x3040
    private const val KanaEnd = 0x30FF
    private const val HangulStart = 0xAC00
    private const val HangulEnd = 0xD7A3
    private const val HalfwidthKanaStart = 0xFF61
    private const val HalfwidthKanaEnd = 0xFF9F

    private const val CjkKanaThreshold = 0.05f
    private const val CjkHanThreshold = 0.5f
    private const val CjkForeignScriptThreshold = 0.05f
}

/** CJK candidates evaluated by [SharedCharsetPlausibility.cjkAccepts]. */
enum class SharedCjkCandidate { SHIFT_JIS, EUC_JP, BIG5, EUC_KR, GB18030 }

/**
 * Letter-frequency model for a single-byte script family. [weightFor] returns
 * the corpus frequency of a lowercase decoded letter (0 for unknown letters).
 */
fun interface SharedSingleByteModel {
    fun weightFor(lowercaseChar: Char): Float
}

private fun charFrequencyModel(vararg pairs: Pair<Char, Float>): SharedSingleByteModel {
    val table = pairs.toMap()
    return SharedSingleByteModel { table[it] ?: 0f }
}

/** English/Western-European letters including accented characters. */
val SharedLatinSingleByteModel: SharedSingleByteModel = charFrequencyModel(
    'e' to 0.101f, 't' to 0.075f, 'a' to 0.065f, 'o' to 0.061f, 'i' to 0.057f,
    'n' to 0.056f, 's' to 0.053f, 'h' to 0.050f, 'r' to 0.049f, 'l' to 0.033f,
    'd' to 0.037f, 'c' to 0.030f, 'u' to 0.023f, 'm' to 0.024f, 'f' to 0.020f,
    'p' to 0.019f, 'g' to 0.017f, 'w' to 0.017f, 'y' to 0.017f, 'b' to 0.012f,
    'v' to 0.008f, 'k' to 0.006f,
    'é' to 0.012f, 'ü' to 0.008f, 'ä' to 0.010f, 'ö' to 0.006f, 'ß' to 0.006f,
    'ñ' to 0.006f, 'ç' to 0.005f, 'à' to 0.006f, 'è' to 0.006f, 'ê' to 0.004f,
    'â' to 0.003f, 'î' to 0.005f, 'ô' to 0.005f, 'û' to 0.002f, 'ù' to 0.002f,
    'ï' to 0.002f, 'á' to 0.008f, 'í' to 0.006f, 'ó' to 0.005f, 'ú' to 0.004f,
    'ą' to 0.003f, 'ć' to 0.003f, 'ę' to 0.004f, 'ł' to 0.005f, 'ń' to 0.003f,
    'ś' to 0.004f, 'ż' to 0.004f, 'č' to 0.003f, 'ě' to 0.003f, 'ř' to 0.003f,
    'š' to 0.004f, 'ž' to 0.003f, 'ý' to 0.003f, 'ğ' to 0.006f, 'ı' to 0.008f,
    'ş' to 0.005f, 'å' to 0.003f, 'ø' to 0.002f, 'æ' to 0.002f, 'œ' to 0.001f,
    'ð' to 0.001f, 'þ' to 0.001f, 'ă' to 0.002f, 'ţ' to 0.002f, 'ő' to 0.003f,
    'ű' to 0.002f
)

/** Russian (Cyrillic) letters; windows-1251, KOI8-R and ISO-8859-5 decode to these. */
val SharedCyrillicSingleByteModel: SharedSingleByteModel = charFrequencyModel(
    'о' to 0.109f, 'е' to 0.085f, 'т' to 0.063f, 'а' to 0.062f, 'и' to 0.062f,
    'н' to 0.055f, 'с' to 0.047f, 'р' to 0.043f, 'в' to 0.039f, 'л' to 0.037f,
    'м' to 0.034f, 'к' to 0.031f, 'д' to 0.031f, 'п' to 0.027f, 'у' to 0.027f,
    'я' to 0.021f, 'ы' to 0.017f, 'з' to 0.017f, 'ь' to 0.015f, 'б' to 0.015f,
    'г' to 0.014f, 'ч' to 0.013f, 'й' to 0.010f, 'х' to 0.009f, 'ж' to 0.008f,
    'ю' to 0.008f, 'ш' to 0.007f, 'ц' to 0.005f, 'щ' to 0.004f, 'э' to 0.003f,
    'ё' to 0.002f, 'ъ' to 0.0003f
)

/** Greek letters; windows-1253 and ISO-8859-7 decode to these. */
val SharedGreekSingleByteModel: SharedSingleByteModel = charFrequencyModel(
    'α' to 0.108f, 'ο' to 0.081f, 'τ' to 0.076f, 'η' to 0.073f, 'ι' to 0.072f,
    'σ' to 0.072f, 'ν' to 0.062f, 'ε' to 0.062f, 'κ' to 0.046f, 'μ' to 0.045f,
    'π' to 0.045f, 'ρ' to 0.043f, 'λ' to 0.043f, 'γ' to 0.036f, 'δ' to 0.032f,
    'υ' to 0.030f, 'ω' to 0.031f, 'θ' to 0.024f, 'φ' to 0.022f, 'β' to 0.015f,
    'χ' to 0.014f, 'ξ' to 0.007f, 'ψ' to 0.008f, 'ζ' to 0.004f
)

/** Hebrew letters (right-to-left block); windows-1255 and ISO-8859-8 decode to these. */
val SharedHebrewSingleByteModel: SharedSingleByteModel = charFrequencyModel(
    'י' to 0.109f, 'ו' to 0.104f, 'ה' to 0.099f, 'ב' to 0.086f, 'ר' to 0.082f,
    'ל' to 0.074f, 'מ' to 0.065f, 'ת' to 0.059f, 'ש' to 0.055f, 'א' to 0.048f,
    'נ' to 0.042f, 'ע' to 0.042f, 'ד' to 0.038f, 'ק' to 0.035f, 'ח' to 0.028f,
    'פ' to 0.024f, 'ט' to 0.018f, 'ג' to 0.016f, 'ס' to 0.014f, 'צ' to 0.013f,
    'ז' to 0.010f
)

/** Arabic letters; windows-1256 and ISO-8859-6 decode to these. */
val SharedArabicSingleByteModel: SharedSingleByteModel = charFrequencyModel(
    'ا' to 0.129f, 'ل' to 0.117f, 'ي' to 0.086f, 'م' to 0.075f, 'و' to 0.062f,
    'ن' to 0.062f, 'ر' to 0.058f, 'ت' to 0.053f, 'ب' to 0.049f, 'ع' to 0.045f,
    'ه' to 0.043f, 'س' to 0.038f, 'ف' to 0.036f, 'ك' to 0.035f, 'ق' to 0.027f,
    'أ' to 0.027f, 'ج' to 0.023f, 'ح' to 0.021f, 'ط' to 0.019f, 'د' to 0.020f,
    'خ' to 0.015f, 'ش' to 0.015f, 'ض' to 0.011f, 'ذ' to 0.010f, 'ظ' to 0.006f,
    'غ' to 0.005f
)
