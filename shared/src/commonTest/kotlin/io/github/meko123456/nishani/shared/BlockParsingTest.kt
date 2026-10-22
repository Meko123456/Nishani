package io.github.meko123456.nishani.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BlockParsingTest {

    @Test
    fun parsesHeadingLevels() {
        val blocks = MarkdownParser.parse("# One\n\n## Two\n\n###### Six")
        assertEquals(1, (blocks[0] as MdBlock.Heading).level)
        assertEquals(2, (blocks[1] as MdBlock.Heading).level)
        assertEquals(6, (blocks[2] as MdBlock.Heading).level)
    }

    @Test
    fun sevenHashesIsNotAHeading() {
        assertIs<MdBlock.Paragraph>(MarkdownParser.parse("####### too deep").single())
    }

    @Test
    fun hashWithoutSpaceIsNotAHeading() {
        assertIs<MdBlock.Paragraph>(MarkdownParser.parse("#hashtag").single())
    }

    @Test
    fun parsesBulletMarkers() {
        val blocks = MarkdownParser.parse("- dash\n* star\n+ plus")
        assertEquals(3, blocks.size)
        assertTrue(blocks.all { it is MdBlock.BulletItem })
    }

    @Test
    fun parsesOrderedItemsKeepingTheirNumbers() {
        val blocks = MarkdownParser.parse("1. first\n2. second\n10) tenth")
        assertEquals(1, (blocks[0] as MdBlock.OrderedItem).number)
        assertEquals(2, (blocks[1] as MdBlock.OrderedItem).number)
        assertEquals(10, (blocks[2] as MdBlock.OrderedItem).number)
    }

    /** As in CommonMark: a list item wrapped by hand is one item, indented or not. */
    @Test
    fun aPlainLineStraightAfterAListItemContinuesIt() {
        assertEquals(
            listOf(MdBlock.BulletItem(listOf(MdSpan.Text("a long bullet that wraps onto the next line")))),
            MarkdownParser.parse("- a long bullet that\n  wraps onto the next line"),
        )
        assertEquals(
            listOf(
                MdBlock.OrderedItem(1, listOf(MdSpan.Text("first, carried on"))),
                MdBlock.OrderedItem(2, listOf(MdSpan.Text("second"))),
            ),
            MarkdownParser.parse("1. first,\ncarried on\n2. second"),
        )
        // Inline marks still work across the join.
        assertEquals(
            listOf(MdBlock.BulletItem(listOf(MdSpan.Text("buy "), MdSpan.Bold("fresh bread"), MdSpan.Text(" today")))),
            MarkdownParser.parse("- buy **fresh\nbread** today"),
        )
    }

    @Test
    fun aListItemEndsAtABlankLineOrAnotherBlock() {
        val one = MdBlock.BulletItem(listOf(MdSpan.Text("one")))
        assertEquals(listOf(one, MdBlock.Paragraph(listOf(MdSpan.Text("two")))), MarkdownParser.parse("- one\n\ntwo"))
        assertEquals(listOf(one, MdBlock.Heading(2, listOf(MdSpan.Text("Next")))), MarkdownParser.parse("- one\n## Next"))
        assertEquals(listOf(one, MdBlock.Quote(listOf(MdSpan.Text("said")))), MarkdownParser.parse("- one\n> said"))
        assertEquals(listOf(one, MdBlock.Divider), MarkdownParser.parse("- one\n---"))
    }

    @Test
    fun parsesQuote() {
        val quote = MarkdownParser.parse("> to be").single()
        assertEquals(listOf(MdSpan.Text("to be")), (quote as MdBlock.Quote).spans)
    }

    @Test
    fun consecutiveQuoteLinesAreOneQuote() {
        val quote = MarkdownParser.parse("> The best time to plant a tree\n> was twenty years ago.").single()
        assertEquals(
            listOf(MdSpan.Text("The best time to plant a tree was twenty years ago.")),
            (quote as MdBlock.Quote).spans,
        )
    }

    @Test
    fun aBareQuoteMarkerSeparatesQuotesInsteadOfMakingAnEmptyOne() {
        assertEquals(
            listOf(MdBlock.Quote(listOf(MdSpan.Text("first"))), MdBlock.Quote(listOf(MdSpan.Text("second")))),
            MarkdownParser.parse("> first\n>\n> second"),
        )
        assertEquals(emptyList(), MarkdownParser.parse(">"))
    }

    @Test
    fun aQuoteEndsAtABlankLine() {
        assertEquals(
            listOf(
                MdBlock.Paragraph(listOf(MdSpan.Text("before"))),
                MdBlock.Quote(listOf(MdSpan.Text("quoted"))),
                MdBlock.Paragraph(listOf(MdSpan.Text("after"))),
            ),
            MarkdownParser.parse("before\n> quoted\n\nafter"),
        )
    }

    /** CommonMark's laziness: the lines after a quote's first may leave out the ">". */
    @Test
    fun aPlainLineStraightAfterAQuoteContinuesIt() {
        assertEquals(
            listOf(MdBlock.Quote(listOf(MdSpan.Text("one two three")))),
            MarkdownParser.parse("> one\ntwo\n> three"),
        )
    }

    /** Only paragraph text is lazy: a list item, a heading or a rule after a quote is its own block. */
    @Test
    fun onlyParagraphTextContinuesAQuote() {
        val quoted = MdBlock.Quote(listOf(MdSpan.Text("quoted")))
        assertEquals(listOf(quoted, MdBlock.BulletItem(listOf(MdSpan.Text("item")))), MarkdownParser.parse("> quoted\n- item"))
        assertEquals(listOf(quoted, MdBlock.Heading(2, listOf(MdSpan.Text("Next")))), MarkdownParser.parse("> quoted\n## Next"))
        assertEquals(listOf(quoted, MdBlock.Divider), MarkdownParser.parse("> quoted\n---"))
        assertEquals(listOf(quoted, MdBlock.Paragraph(listOf(MdSpan.Text("after")))), MarkdownParser.parse("> quoted\n>\nafter"))
    }

    @Test
    fun parsesFencedCodeWithLanguage() {
        val code = MarkdownParser.parse("```kotlin\nval x = 1\n```").single() as MdBlock.CodeBlock
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1", code.code)
    }

    @Test
    fun fencedCodeWithoutLanguageHasNullLanguage() {
        val code = MarkdownParser.parse("```\nplain\n```").single() as MdBlock.CodeBlock
        assertEquals(null, code.language)
    }

    @Test
    fun markdownInsideCodeIsNotParsed() {
        val code = MarkdownParser.parse("```\n# not a heading\n- not a bullet\n```").single() as MdBlock.CodeBlock
        assertEquals("# not a heading\n- not a bullet", code.code)
    }

    @Test
    fun unterminatedFenceStillProducesACodeBlock() {
        val code = MarkdownParser.parse("```\nleft open").single() as MdBlock.CodeBlock
        assertEquals("left open", code.code)
    }

    @Test
    fun parsesDividerVariants() {
        val blocks = MarkdownParser.parse("---\n\n***\n\n___")
        assertEquals(3, blocks.size)
        assertTrue(blocks.all { it === MdBlock.Divider })
    }

    @Test
    fun consecutiveLinesJoinIntoOneParagraph() {
        val blocks = MarkdownParser.parse("line one\nline two\n\nsecond para")
        assertEquals(2, blocks.size)
        assertEquals(listOf(MdSpan.Text("line one line two")), (blocks[0] as MdBlock.Paragraph).spans)
    }

    @Test
    fun blankInputProducesNoBlocks() {
        assertTrue(MarkdownParser.parse("").isEmpty())
        assertTrue(MarkdownParser.parse("\n\n   \n").isEmpty())
    }

    @Test
    fun handlesWindowsLineEndings() {
        val blocks = MarkdownParser.parse("# Title\r\n\r\nbody")
        assertEquals(2, blocks.size)
        assertIs<MdBlock.Heading>(blocks[0])
    }

    @Test
    fun parsesARealisticDocument() {
        val doc = """
            # Shopping

            Need to buy **milk** and `eggs`.

            - dairy
            - bread

            1. go to shop
            2. pay

            > don't forget the list

            ```kotlin
            val list = listOf("milk")
            ```

            ---
        """.trimIndent()
        val blocks = MarkdownParser.parse(doc)
        assertIs<MdBlock.Heading>(blocks[0])
        assertIs<MdBlock.Paragraph>(blocks[1])
        assertIs<MdBlock.BulletItem>(blocks[2])
        assertIs<MdBlock.OrderedItem>(blocks[4])
        assertIs<MdBlock.Quote>(blocks[6])
        assertIs<MdBlock.CodeBlock>(blocks[7])
        assertEquals(MdBlock.Divider, blocks[8])
    }
}
