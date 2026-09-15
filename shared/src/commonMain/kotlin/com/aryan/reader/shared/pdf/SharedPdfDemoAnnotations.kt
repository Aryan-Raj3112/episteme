package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.currentTimestamp

/**
 * Shared port of Android's DemoAnnotationGenerator (app DemoAnnotationGenerator.kt)
 * and demo_art.svg "Try Episteme!" artwork.
 *
 * Android exposes this behind BuildConfig.DEBUG toolbar actions (bug-report for
 * the hardcoded generator, brush for importing demo_art.svg). Both draw the same
 * single-stroke handwritten "Try Episteme!" with decorative dots + underline.
 * iOS debug builds reuse this generator so the artwork matches Android exactly
 * (same 800-unit coordinate space, same centering, same colors/widths).
 */
object SharedPdfDemoAnnotations {
    private const val SVG_WIDTH = 800f
    private const val TARGET_WIDTH_PERCENT = 0.8f
    private const val START_Y = 0.2f

    private data class Dot(val cx: Float, val cy: Float, val r: Float, val argb: Int)

    // Android Color(...).copy(alpha=...) baked to ARGB (alpha*255 rounded).
    private val decorativeDots = listOf(
        Dot(120f, 90f, 5f, argbWithAlpha(0xF59E0B, 0.7f)),
        Dot(680f, 210f, 6f, argbWithAlpha(0xEC4899, 0.7f)),
        Dot(700f, 110f, 4f, argbWithAlpha(0x8B5CF6, 0.7f)),
        Dot(150f, 230f, 5f, argbWithAlpha(0x10B981, 0.6f)),
        Dot(650f, 80f, 4f, argbWithAlpha(0xF59E0B, 0.6f)),
        Dot(90f, 180f, 3f, argbWithAlpha(0xEC4899, 0.5f)),
        Dot(720f, 170f, 5f, argbWithAlpha(0x8B5CF6, 0.6f)),
    )

    // Same stroke data as Android DemoAnnotationGenerator.TEXT_STROKES_DATA ("Try Episteme!").
    private val textStrokesData = listOf(
        "M 80 115 L 140 115 M 110 115 L 110 175 Q 110 185 115 185",
        "M 150 145 L 150 180 M 150 155 Q 155 145 165 145 Q 172 145 175 150",
        "M 182 148 Q 188 165 195 175 M 208 148 Q 200 165 195 175 L 192 195 Q 188 215 175 212 Q 165 208 172 198",
        "M 240 110 L 240 180 M 240 110 L 285 110 M 240 145 L 275 145 M 240 180 L 285 180",
        "M 305 145 L 305 215 M 305 158 Q 305 145 320 145 Q 340 145 345 160 Q 348 170 345 180 Q 340 195 320 195 Q 305 195 305 182",
        "M 365 145 L 365 180 M 365 130 L 365 132",
        "M 428 148 Q 418 143 408 145 Q 398 147 395 155 Q 393 162 400 165 Q 410 170 420 168 Q 428 166 430 172 Q 432 180 422 183 Q 412 186 402 182",
        "M 445 125 L 445 175 Q 445 185 455 185 Q 465 185 470 180 M 435 145 L 460 145",
        "M 495 165 L 522 165 Q 522 145 508 145 Q 488 145 492 170 Q 495 190 525 185",
        "M 545 145 L 545 180 M 545 155 Q 545 145 555 145 Q 565 145 565 155 L 565 180 M 565 155 Q 565 145 575 145 Q 585 145 585 155 L 585 180",
        "M 605 165 L 632 165 Q 632 145 618 145 Q 598 145 602 170 Q 605 190 635 185",
        "M 660 125 L 660 165 M 660 178 L 660 182",
    )

    private const val UNDERLINE_DATA = "M 180 200 Q 400 220 620 200"

    private const val TEXT_COLOR_ARGB = 0xFF418377.toInt()
    private const val UNDERLINE_COLOR_ARGB = 0x99EC4899.toInt()
    private const val TEXT_STROKE_WIDTH = 0.004f
    private const val UNDERLINE_STROKE_WIDTH = 0.005f

    fun generate(
        pageIndex: Int,
        baseTimestamp: Long = currentTimestamp(),
        idPrefix: String = "demo_try_episteme",
    ): List<SharedPdfAnnotation> {
        val annotations = mutableListOf<SharedPdfAnnotation>()
        val scale = TARGET_WIDTH_PERCENT / SVG_WIDTH
        val startX = (1f - TARGET_WIDTH_PERCENT) / 2f
        var clock = baseTimestamp
        var strokeIndex = 0

        fun nextId(): String = "${idPrefix}_${pageIndex}_${strokeIndex++}_${clock}"

        fun toPdf(x: Float, y: Float, timestamp: Long): PdfPagePoint {
            val pdfX = startX + (x * scale)
            val pdfY = START_Y + (y * scale)
            return PdfPagePoint(pdfX, pdfY, timestamp)
        }

        decorativeDots.forEach { dot ->
            val center = toPdf(dot.cx, dot.cy, clock)
            val points = listOf(
                center,
                center.copy(x = center.x + 0.0001f, timestamp = clock + 10),
            )
            val relativeThickness = (dot.r / SVG_WIDTH) * 2.5f
            annotations.add(
                SharedPdfAnnotation(
                    id = nextId(),
                    pageIndex = pageIndex,
                    kind = PdfAnnotationKind.INK,
                    tool = PdfInkTool.PEN,
                    points = points,
                    colorArgb = dot.argb,
                    strokeWidth = relativeThickness,
                    createdAt = clock,
                ),
            )
            clock += 50
        }

        textStrokesData.forEach { raw ->
            // Android splits by 'M' so the pen lifts instead of connecting letters.
            parseSvgPathStrokes(raw).forEach { stroke ->
                if (stroke.isEmpty()) return@forEach
                val pdfPoints = stroke.map { p ->
                    clock += 8
                    toPdf(p.x, p.y, clock)
                }
                if (pdfPoints.isNotEmpty()) {
                    annotations.add(
                        SharedPdfAnnotation(
                            id = nextId(),
                            pageIndex = pageIndex,
                            kind = PdfAnnotationKind.INK,
                            tool = PdfInkTool.FOUNTAIN_PEN,
                            points = pdfPoints,
                            colorArgb = TEXT_COLOR_ARGB,
                            strokeWidth = TEXT_STROKE_WIDTH,
                            createdAt = clock,
                        ),
                    )
                    clock += 150
                }
            }
        }

        parseSvgPathStrokes(UNDERLINE_DATA).forEach { stroke ->
            if (stroke.isEmpty()) return@forEach
            val pdfPoints = stroke.map { p ->
                clock += 5
                toPdf(p.x, p.y, clock)
            }
            annotations.add(
                SharedPdfAnnotation(
                    id = nextId(),
                    pageIndex = pageIndex,
                    kind = PdfAnnotationKind.INK,
                    tool = PdfInkTool.PEN,
                    points = pdfPoints,
                    colorArgb = UNDERLINE_COLOR_ARGB,
                    strokeWidth = UNDERLINE_STROKE_WIDTH,
                    createdAt = clock,
                ),
            )
        }

        return annotations
    }

    internal data class SvgPoint(val x: Float, val y: Float)

    /**
     * Minimal SVG path parser for the demo data (M/L/Q, absolute + relative).
     * Returns one point list per pen-down stroke; M starts a new stroke.
     */
    internal fun parseSvgPathStrokes(pathData: String): List<List<SvgPoint>> {
        val tokens = tokenizePathData(pathData)
        if (tokens.isEmpty()) return emptyList()
        val strokes = mutableListOf<MutableList<SvgPoint>>()
        var current = mutableListOf<SvgPoint>()
        var cx = 0f
        var cy = 0f
        var i = 0
        var cmd = 'M'

        fun flush() {
            if (current.size >= 2 || (current.size == 1)) {
                // Keep single-point strokes (dots like i-dots); the caller
                // already filters empties. Android flatten keeps them too.
                strokes.add(current)
            }
            current = mutableListOf()
        }

        while (i < tokens.size) {
            val token = tokens[i]
            if (token.length == 1 && token[0].isLetter()) {
                cmd = token[0]
                i++
                if (cmd == 'M' || cmd == 'm') {
                    if (current.isNotEmpty()) flush()
                }
                continue
            }
            when (cmd) {
                'M', 'm' -> {
                    if (i + 1 >= tokens.size) break
                    val x = tokens[i].toFloatOrNull() ?: break
                    val y = tokens[i + 1].toFloatOrNull() ?: break
                    i += 2
                    if (cmd == 'm') {
                        cx += x
                        cy += y
                    } else {
                        cx = x
                        cy = y
                    }
                    if (current.isNotEmpty()) flush()
                    current.add(SvgPoint(cx, cy))
                    // Implicit lineto pairs after moveto.
                    cmd = if (cmd == 'm') 'l' else 'L'
                }
                'L', 'l' -> {
                    if (i + 1 >= tokens.size) break
                    val x = tokens[i].toFloatOrNull() ?: break
                    val y = tokens[i + 1].toFloatOrNull() ?: break
                    i += 2
                    if (cmd == 'l') {
                        cx += x
                        cy += y
                    } else {
                        cx = x
                        cy = y
                    }
                    current.add(SvgPoint(cx, cy))
                }
                'Q', 'q' -> {
                    if (i + 3 >= tokens.size) break
                    val c1x = tokens[i].toFloatOrNull() ?: break
                    val c1y = tokens[i + 1].toFloatOrNull() ?: break
                    val ex = tokens[i + 2].toFloatOrNull() ?: break
                    val ey = tokens[i + 3].toFloatOrNull() ?: break
                    i += 4
                    val absC1x = if (cmd == 'q') cx + c1x else c1x
                    val absC1y = if (cmd == 'q') cy + c1y else c1y
                    val absEx = if (cmd == 'q') cx + ex else ex
                    val absEy = if (cmd == 'q') cy + ey else ey
                    // Sample quadratic bezier (matches Path.approximate density roughly).
                    val steps = 16
                    for (s in 1..steps) {
                        val t = s.toFloat() / steps
                        val mt = 1f - t
                        val bx = mt * mt * cx + 2f * mt * t * absC1x + t * t * absEx
                        val by = mt * mt * cy + 2f * mt * t * absC1y + t * t * absEy
                        current.add(SvgPoint(bx, by))
                    }
                    cx = absEx
                    cy = absEy
                }
                else -> {
                    // Unsupported command for demo data; skip its coords.
                    i++
                }
            }
            // A new M token starts a new stroke; handled at top of loop,
            // but consecutive movetos without explicit command also split.
            if (i < tokens.size && tokens[i].length == 1 && tokens[i][0] == 'M') {
                if (current.isNotEmpty()) flush()
            }
        }
        if (current.isNotEmpty()) strokes.add(current)
        return strokes.filter { it.isNotEmpty() }
    }

    private fun tokenizePathData(pathData: String): List<String> {
        val normalized = pathData.replace(",", " ")
        val tokens = mutableListOf<String>()
        var current = StringBuilder()
        fun flushNumber() {
            if (current.isNotEmpty()) {
                tokens.add(current.toString())
                current = StringBuilder()
            }
        }
        var idx = 0
        while (idx < normalized.length) {
            val c = normalized[idx]
            when {
                c.isLetter() -> {
                    flushNumber()
                    tokens.add(c.toString())
                    idx++
                }
                c.isDigit() || c == '.' || c == '-' || c == '+' -> {
                    // Handle minus as separator (e.g. "185-5" is rare here but cheap).
                    if ((c == '-' || c == '+') && current.isNotEmpty()) {
                        flushNumber()
                    }
                    current.append(c)
                    idx++
                }
                c.isWhitespace() -> {
                    flushNumber()
                    idx++
                }
                else -> {
                    flushNumber()
                    idx++
                }
            }
        }
        flushNumber()
        return tokens
    }

    private fun argbWithAlpha(rgb: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (a shl 24) or (rgb and 0x00FFFFFF)
    }
}
