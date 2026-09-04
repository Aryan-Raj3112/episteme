package com.aryan.reader.shared.docparse

/**
 * md4c's HTML renderer emits LaTeX math spans as
 * `<x-equation>tex</x-equation>` (display variant adds `type="display"`),
 * with the TeX source HTML-escaped. The reader's downstream pipeline expects
 * math as block-level placeholders, so this adapter rewrites every equation
 * into a classed span whose escaped text content is the raw TeX:
 *  - `<span class="math-display">tex</span>` for `$$...$$`;
 *  - `<span class="math-inline">tex</span>` for `$...$`.
 *
 * Spans are used for both variants so md4c's wrapping `<p>` stays intact
 * through the sanitizer; the importer renders their TeX to inline SVG via
 * MathJax before chapters hit disk, so every reader surface (native
 * semantic, paginated worker, WebView) displays rendered equations. The TeX
 * is stored as the span's escaped text content, exactly as md4c wrote it.
 */
internal object Md4cHtmlAdapter {

    private val equationTag = Regex(
        pattern = """<x-equation(\s+type="display")?>(.*?)</x-equation>""",
        options = setOf(RegexOption.DOT_MATCHES_ALL),
    )

    fun normalize(html: String): String = html.replace(equationTag) { match ->
        val cssClass = if (match.groupValues[1].isNotBlank()) "math-display" else "math-inline"
        val tex = match.groupValues[2].trim()
        if (tex.isEmpty()) {
            ""
        } else {
            // md4c already HTML-escaped the TeX inside <x-equation>; pass it
            // through unchanged so escaping stays single-layer.
            """<span class="$cssClass">$tex</span>"""
        }
    }
}
