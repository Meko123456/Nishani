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

    @Test
    fun parsesQuote() {
        val quote = MarkdownParser.parse("> to be").single()
        assertEquals(listOf(MdSpan.Text("to be")), (quote as MdBlock.Quote).spans)
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
