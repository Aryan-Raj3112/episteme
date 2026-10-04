package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The marker a highlight is painted with has to survive wrapping across lines.
 *
 * `box-decoration-break: clone` gives each line fragment its own box, which is what a line decoration
 * needs, and with a `border-radius` on the marker it also draws a rounded corner at every seam. The
 * fill then pulls back from the joint and a highlight spanning three lines renders as three bands with
 * pale notches between them. The radius is the only part that has to go.
 */
class ReaderHtmlStylesHighlightMarkerTest {

    private val settings = ReaderSettings(pageSpreadMode = ReaderPageSpreadMode.SINGLE)

    private val styles: String = readerDocumentStyles(
        settings = settings,
        bookCss = "",
        customFontCss = "",
        appearance = settings.toDocumentAppearanceCss(textureDataUri = null),
        align = "left",
        family = "sans-serif",
        verticalMarginY = 0
    )

    /**
     * The rule that neutralises publication CSS on the marker, by whatever selector reaches it.
     *
     * Comments are stripped first: the rule is documented in place, and the prose names both
     * properties, so matching on raw CSS text would find the explanation instead of the declaration.
     */
    private val markerNeutralisingRule: String = styles
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .split("}")
        .firstOrNull { it.contains("box-decoration-break") }
        .orEmpty()

    @Test
    fun markerRuleIsPresentSoTheTestIsLookingAtTheRightRule() {
        assertTrue(
            markerNeutralisingRule.contains("box-decoration-break"),
            "expected a marker rule that sets box-decoration-break"
        )
    }

    @Test
    fun markerDoesNotRoundTheCornersOfEveryLineFragment() {
        assertFalse(
            markerNeutralisingRule.contains("border-radius"),
            "a border-radius on a marker with box-decoration-break: clone draws a notch at every line " +
                "seam, which showed up as pale gaps in a highlight split over several lines"
        )
    }

    @Test
    fun markerStillClonesLineFragmentsForLineDecorations() {
        assertTrue(
            markerNeutralisingRule.contains("box-decoration-break: clone"),
            "underlines and strikethroughs still need one box per line fragment"
        )
    }
}