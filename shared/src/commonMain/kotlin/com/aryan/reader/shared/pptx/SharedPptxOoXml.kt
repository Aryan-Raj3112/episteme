package com.aryan.reader.shared.pptx

import kotlin.math.roundToInt

/**
 * Single-source OOXML helpers shared by the JVM (Android/desktop) and iOS PPTX parsers.
 *
 * Android remains the benchmark: iOS behavior must match what is defined here, which is
 * extracted from the JVM parser without behavior change.
 */
object SharedPptxOoXml {
    const val EMU_PER_POINT = 12_700f
    const val DEFAULT_SLIDE_WIDTH_EMU = 12_192_000
    const val DEFAULT_SLIDE_HEIGHT_EMU = 6_858_000
    const val DEFAULT_TEXT_SIZE_PT = 18f
    const val MAX_NUMBERING_LEVELS = 9

    private val slideFileRegex = Regex("""ppt/slides/slide\d+\.xml""", RegexOption.IGNORE_CASE)
    private val slideNumberRegex = Regex("""slide(\d+)\.xml""", RegexOption.IGNORE_CASE)

    /** Accepts the OOXML boolean spellings used by PowerPoint (`1`/`true`/`on`). */
    fun isTruthyFlag(value: String?): Boolean {
        return value == "1" || value.equals("true", ignoreCase = true) || value.equals("on", ignoreCase = true)
    }

    fun emuToPoint(emu: Float): Float = emu / EMU_PER_POINT

    fun emuToPointInt(emu: Float): Int = (emu / EMU_PER_POINT).roundToInt().coerceAtLeast(1)

    /** Maps placeholder aliases (`ctrTitle`, `subTtl`) to their style family. */
    fun placeholderFamily(type: String): String {
        return when (type.lowercase()) {
            "ctrtitle" -> "title"
            "subttl" -> "subtitle"
            else -> type.lowercase()
        }
    }

    fun imageContentTypeForExtension(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            else -> "application/octet-stream"
        }
    }

    fun imageContentTypeForTarget(target: String): String {
        return imageContentTypeForExtension(target.substringAfterLast('.', ""))
    }

    fun normalizePartPath(path: String): String {
        val clean = path.replace('\\', '/').removePrefix("/")
        val parts = ArrayDeque<String>()
        clean.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(part)
            }
        }
        return parts.joinToString("/")
    }

    fun resolveRelationshipTarget(partPath: String, target: String, targetMode: String?): String {
        if (targetMode.equals("External", ignoreCase = true)) return target
        if (target.startsWith("http://") || target.startsWith("https://")) return target
        val cleanTarget = target.substringBefore('#').removePrefix("/")
        val base = partPath.substringBeforeLast('/', missingDelimiterValue = "")
        return normalizePartPath(if (target.startsWith("/")) cleanTarget else "$base/$cleanTarget")
    }

    fun relationshipsPathFor(partPath: String): String {
        val dir = partPath.substringBeforeLast('/', missingDelimiterValue = "")
        val name = partPath.substringAfterLast('/')
        return if (dir.isBlank()) "_rels/$name.rels" else "$dir/_rels/$name.rels"
    }

    fun hexColorOrNull(value: String): Int? {
        val clean = value.trim().removePrefix("#")
        if (clean.length != 6) return null
        val parsed = clean.toLongOrNull(16)?.toInt() ?: return null
        return SharedPptxColor.rgb(parsed shr 16, parsed shr 8 and 0xff, parsed and 0xff)
    }

    /** Unified preset table (superset of the JVM + iOS tables). */
    fun presetColor(name: String): Int? {
        return when (name.lowercase()) {
            "black" -> SharedPptxColor.BLACK
            "white" -> SharedPptxColor.WHITE
            "red" -> SharedPptxColor.RED
            "green" -> SharedPptxColor.GREEN
            "blue" -> SharedPptxColor.BLUE
            "yellow" -> SharedPptxColor.YELLOW
            "cyan", "aqua" -> SharedPptxColor.CYAN
            "magenta", "fuchsia" -> SharedPptxColor.MAGENTA
            "gray", "grey" -> SharedPptxColor.GRAY
            "dkgray", "dkgrey" -> SharedPptxColor.DKGRAY
            "ltgray", "ltgrey" -> SharedPptxColor.LTGRAY
            "orange" -> SharedPptxColor.rgb(255, 165, 0)
            "purple" -> SharedPptxColor.rgb(128, 0, 128)
            "brown" -> SharedPptxColor.rgb(165, 42, 42)
            else -> null
        }
    }

    fun isSlidePart(path: String): Boolean = slideFileRegex.matches(path)

    fun slideNumberOf(path: String): Int? {
        return slideNumberRegex.find(path)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    val slidePathComparator: Comparator<String> = compareBy<String>(
        { slideNumberOf(it) ?: Int.MAX_VALUE },
        { it },
    )

    fun formatAutoNumber(number: Int, type: String): String {
        val normalized = type.lowercase()
        val value = when {
            normalized.startsWith("alphalc") -> number.toAlphabeticLabel().lowercase()
            normalized.startsWith("alphauc") -> number.toAlphabeticLabel()
            normalized.startsWith("romanlc") -> number.toRomanNumeral().lowercase()
            normalized.startsWith("romanuc") -> number.toRomanNumeral()
            else -> number.toString()
        }
        return when {
            "parenboth" in normalized -> "($value)"
            "parenr" in normalized -> "$value)"
            "period" in normalized -> "$value."
            else -> value
        }
    }

    fun Int.toAlphabeticLabel(): String {
        var value = coerceAtLeast(1)
        val result = StringBuilder()
        while (value > 0) {
            value -= 1
            result.insert(0, ('A'.code + (value % 26)).toChar())
            value /= 26
        }
        return result.toString()
    }

    fun Int.toRomanNumeral(): String {
        var value = coerceIn(1, 3999)
        val numerals = listOf(
            1000 to "M",
            900 to "CM",
            500 to "D",
            400 to "CD",
            100 to "C",
            90 to "XC",
            50 to "L",
            40 to "XL",
            10 to "X",
            9 to "IX",
            5 to "V",
            4 to "IV",
            1 to "I",
        )
        return buildString {
            numerals.forEach { (amount, numeral) ->
                while (value >= amount) {
                    append(numeral)
                    value -= amount
                }
            }
        }
    }

    /** Normalizes Symbol/Wingdings private-use bullets to their readable equivalents. */
    fun normalizeBulletGlyph(glyph: String, typeface: String?): String {
        val family = typeface.orEmpty().lowercase()
        if ("wingdings" !in family && "symbol" !in family) return glyph
        return when (glyph) {
            "\u00A7", "\u00D8", "\u00B7", "\uF0B7" -> "\u2022"
            "\u00FC", "\uF0FC" -> "\u2713"
            "\u00A8", "\uF0A8" -> "\u25E6"
            else -> glyph
        }
    }
}
