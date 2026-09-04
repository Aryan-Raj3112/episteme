package com.aryan.reader.shared.docparse

/**
 * Portable entry point for Markdown -> HTML conversion.
 *
 * Android binds this to the vendored md4c C parser (CommonMark 0.31 +
 * tables/strikethrough/tasklists/autolinks/LaTeX math spans). Other platforms
 * fall back to the hand-written [SharedMarkdownConverter] until their native
 * bindings land; the flag surface is shared so behavior stays aligned.
 */
expect object SharedMarkdownParser {
    /** Returns true when the platform binds the native md4c parser. */
    fun isNative(): Boolean

    /**
     * Converts markdown to HTML.
     *
     * [flags] is an OR-combination of [MdFlag] values. Only honored when
     * [isNative] is true; the fallback converter always applies its fixed
     * (matching) feature set.
     */
    fun toHtml(markdown: String, flags: Int): String?
}

/** md4c parser flag values (mirrors md4c.h). */
object MdFlag {
    const val COLLAPSE_WHITESPACE = 0x1
    const val PERMISSIVE_URL_AUTOLINKS = 0x4
    const val PERMISSIVE_EMAIL_AUTOLINKS = 0x8
    const val TABLES = 0x100
    const val STRIKETHROUGH = 0x200
    const val PERMISSIVE_WWW_AUTOLINKS = 0x400
    const val TASKLISTS = 0x800
    const val LATEX_MATH_SPANS = 0x1000
}

/**
 * Feature parity contract shared by every backend: the flags Android passes to
 * md4c must match what [SharedMarkdownConverter] implements by hand, so both
 * pipelines accept the same markdown dialect.
 */
object SharedMarkdownFlags {
    val DEFAULT: Int =
        MdFlag.TABLES or
            MdFlag.STRIKETHROUGH or
            MdFlag.TASKLISTS or
            MdFlag.PERMISSIVE_URL_AUTOLINKS or
            MdFlag.PERMISSIVE_EMAIL_AUTOLINKS or
            MdFlag.PERMISSIVE_WWW_AUTOLINKS or
            MdFlag.LATEX_MATH_SPANS
}
