package com.aryan.reader.shared.docparse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises [SharedMarkdownParser] on whatever backend the host provides:
 * the native md4c binding on device/instrumented runs, the hand-written
 * converter fallback in JVM host tests. Math-span assertions only apply when
 * the native backend is active, since only md4c parses `$...$` today.
 */
class SharedMarkdownParserTest {

    private val native = SharedMarkdownParser.isNative()

    @Test
    fun headingsAndTablesConvert() {
        val markdown = """
            # Title

            | A | B |
            |---|---|
            | 1 | 2 |
        """.trimIndent()

        val html = SharedMarkdownParser.toHtml(markdown, SharedMarkdownFlags.DEFAULT)!!

        assertTrue("<h1" in html)
        assertTrue("<table>" in html)
    }

    @Test
    fun strikethroughAndTaskListsConvert() {
        val markdown = "- [x] done\n- [ ] todo\n\n~~struck~~"

        val html = SharedMarkdownParser.toHtml(markdown, SharedMarkdownFlags.DEFAULT)!!

        assertTrue("checkbox" in html)
        assertTrue("struck" in html)
    }

    @Test
    fun inlineMathBecomesMathSpanWhenNative() {
        val html = SharedMarkdownParser.toHtml(
            "Inline \$f(x)=ax^2+bx+c\$ equation.",
            SharedMarkdownFlags.DEFAULT,
        )!!

        if (!native) {
            assertTrue("\$f(x)=ax^2+bx+c\$" in html)
            return
        }
        assertTrue("""<span class="math-inline">f(x)=ax^2+bx+c</span>""" in html)
    }

    @Test
    fun displayMathBecomesDisplaySpanWhenNative() {
        val markdown = """
            Before

            $$
            x = \frac{-b \pm \sqrt{b^2-4ac}}{2a}
            $$

            After
        """.trimIndent()

        val html = SharedMarkdownParser.toHtml(markdown, SharedMarkdownFlags.DEFAULT)!!

        if (!native) {
            return
        }
        assertTrue("""<span class="math-display">""" in html)
        assertTrue("""\frac{-b \pm \sqrt{b^2-4ac}}{2a}""" in html)
    }

    @Test
    fun mathInsideCodeFenceStaysCode() {
        val markdown = "```\n\$not_math\$\n```"

        val html = SharedMarkdownParser.toHtml(markdown, SharedMarkdownFlags.DEFAULT)!!

        assertTrue("<code>" in html)
        if (native) {
            assertTrue("math-inline" !in html)
        }
    }

    @Test
    fun dollarAloneStaysLiteralTextWhenNative() {
        val html = SharedMarkdownParser.toHtml("Costs 5 dollars: \$5 and \$10.", SharedMarkdownFlags.DEFAULT)!!

        if (!native) {
            return
        }
        // md4c requires closing delimiters; "$5 and $" is not an equation.
        assertTrue("math-inline" !in html)
        assertTrue("\$5 and \$10." in html)
    }

    @Test
    fun htmlEscapingInTexContent() {
        val html = SharedMarkdownParser.toHtml("\$a \\& b < c\$", SharedMarkdownFlags.DEFAULT)!!

        if (!native) {
            return
        }
        // TeX is stored escaped so it survives sanitization intact.
        assertTrue("""<span class="math-inline">a \&amp; b &lt; c</span>""" in html)
    }

    @Test
    fun horizontalRuleStaysInFlow() {
        val html = SharedMarkdownParser.toHtml("before\n\n---\n\nafter", SharedMarkdownFlags.DEFAULT)!!

        assertTrue("<hr" in html)
        assertTrue("before" in html && "after" in html)
    }

    @Test
    fun fallbackBackendMatchesConverter() {
        if (native) return
        val markdown = "# Fallback\n\ntext"
        val html = SharedMarkdownParser.toHtml(markdown, SharedMarkdownFlags.DEFAULT)!!
        val expected = SharedMarkdownConverter.convert(markdown).joinToString("\n") { it.html }
        assertEquals(expected, html)
    }
}
