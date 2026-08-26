package io.github.meko123456.nishani.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class InlineParsingTest {

    @Test
    fun parsesBold() =
        assertEquals(listOf(MdSpan.Bold("loud")), MarkdownParser.parseInline("**loud**"))

    @Test
    fun parsesUnderscoreBold() =
        assertEquals(listOf(MdSpan.Bold("loud")), MarkdownParser.parseInline("__loud__"))

    @Test
    fun parsesItalic() =
        assertEquals(listOf(MdSpan.Italic("soft")), MarkdownParser.parseInline("*soft*"))

    @Test
    fun parsesUnderscoreItalic() =
        assertEquals(listOf(MdSpan.Italic("soft")), MarkdownParser.parseInline("_soft_"))

    @Test
    fun parsesCode() =
        assertEquals(listOf(MdSpan.Code("val x")), MarkdownParser.parseInline("`val x`"))

    @Test
    fun parsesStrikethrough() =
        assertEquals(listOf(MdSpan.Strikethrough("gone")), MarkdownParser.parseInline("~~gone~~"))

    @Test
    fun parsesLink() =
        assertEquals(
            listOf(MdSpan.Link("Kotlin", "https://kotlinlang.org")),
            MarkdownParser.parseInline("[Kotlin](https://kotlinlang.org)"),
        )

    @Test
    fun mixesSpansInOrder() {
        val spans = MarkdownParser.parseInline("a **b** c *d* e `f`")
        assertEquals(MdSpan.Text("a "), spans[0])
        assertEquals(MdSpan.Bold("b"), spans[1])
        assertEquals(MdSpan.Italic("d"), spans[3])
        assertEquals(MdSpan.Code("f"), spans[5])
    }

    @Test
    fun boldWinsOverItalicForDoubleStars() =
        assertEquals(listOf(MdSpan.Bold("x")), MarkdownParser.parseInline("**x**"))

    @Test
    fun codeContentIsNotFurtherParsed() =
        assertEquals(listOf(MdSpan.Code("**not bold**")), MarkdownParser.parseInline("`**not bold**`"))

    @Test
    fun arithmeticIsNotMistakenForEmphasis() {
        // Emphasis can't open before whitespace or close after it (CommonMark flanking).
        assertEquals(listOf(MdSpan.Text("2 * 3 * 4 = 24")), MarkdownParser.parseInline("2 * 3 * 4 = 24"))
    }

    @Test
    fun emphasisStillWorksWhenTightlyWrapped() =
        assertEquals(listOf(MdSpan.Italic("yes")), MarkdownParser.parseInline("*yes*"))

    @Test
    fun unmatchedMarkerStaysLiteral() =
        assertEquals(listOf(MdSpan.Text("a * b")), MarkdownParser.parseInline("a * b"))

    @Test
    fun danglingBacktickIsLiteral() =
        assertEquals(listOf(MdSpan.Text("a ` b")), MarkdownParser.parseInline("a ` b"))

    @Test
    fun emptyEmphasisIsLiteral() =
        assertEquals(listOf(MdSpan.Text("****")), MarkdownParser.parseInline("****"))

    @Test
    fun linkWithoutDestinationIsLiteral() =
        assertEquals(listOf(MdSpan.Text("[label]()")), MarkdownParser.parseInline("[label]()"))

    @Test
    fun bracketsWithoutParensAreLiteral() =
        assertEquals(listOf(MdSpan.Text("[just brackets]")), MarkdownParser.parseInline("[just brackets]"))

    @Test
    fun plainTextPassesThrough() =
        assertEquals(listOf(MdSpan.Text("nothing special")), MarkdownParser.parseInline("nothing special"))

    @Test
    fun visibleTextSurvivesParsing() {
        // Whatever the markup, the words a reader sees must all still be there.
        val cases = mapOf(
            "**a** *b* `c` ~~d~~" to "a b c d",
            "see [Kotlin](https://kotlinlang.org) now" to "see Kotlin now",
            "* unmatched" to "* unmatched",
            "trailing **" to "trailing **",
            "plain words only" to "plain words only",
        )
        for ((input, expected) in cases) {
            val rendered = MarkdownParser.parseInline(input).joinToString("") { visibleText(it) }
            assertEquals(expected, rendered, "visible text changed for: $input")
        }
    }

    /** What a reader would actually see — link destinations are not displayed. */
    private fun visibleText(span: MdSpan): String = when (span) {
        is MdSpan.Text -> span.text
        is MdSpan.Bold -> span.text
        is MdSpan.Italic -> span.text
        is MdSpan.Code -> span.text
        is MdSpan.Strikethrough -> span.text
        is MdSpan.Link -> span.text
    }
}
