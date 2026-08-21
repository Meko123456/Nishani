package io.github.meko123456.nishani.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownParserTest {

    @Test
    fun parsesHeadingsBulletsQuoteAndDivider() {
        val md = """
            # Title
            Some intro text.

            ## Section
            - first
            - second
            > a quote
            ---
        """.trimIndent()
        val blocks = MarkdownParser.parse(md)
        assertTrue(blocks[0] is MdBlock.Heading && (blocks[0] as MdBlock.Heading).level == 1)
        assertTrue(blocks[1] is MdBlock.Paragraph)
        assertTrue(blocks[2] is MdBlock.Heading && (blocks[2] as MdBlock.Heading).level == 2)
        assertTrue(blocks[3] is MdBlock.BulletItem)
        assertTrue(blocks[4] is MdBlock.BulletItem)
        assertTrue(blocks[5] is MdBlock.Quote)
        assertTrue(blocks[6] is MdBlock.Divider)
    }

    @Test
    fun parsesFencedCodeBlock() {
        val md = "text\n```\nval x = 1\nprintln(x)\n```\nafter"
        val blocks = MarkdownParser.parse(md)
        val code = blocks.filterIsInstance<MdBlock.CodeBlock>().single()
        assertEquals("val x = 1\nprintln(x)", code.code)
    }

    @Test
    fun parsesInlineBoldItalicCode() {
        val spans = MarkdownParser.parseInline("a **bold** and *italic* and `code` end")
        assertEquals(MdSpan.Text("a "), spans[0])
        assertEquals(MdSpan.Bold("bold"), spans[1])
        assertEquals(MdSpan.Italic("italic"), spans[3])
        assertEquals(MdSpan.Code("code"), spans[5])
    }

    @Test
    fun unclosedMarkersStayLiteral() {
        val spans = MarkdownParser.parseInline("a * b `c")
        assertEquals(1, spans.size)
        assertEquals(MdSpan.Text("a * b `c"), spans[0])
    }

    @Test
    fun consecutivePlainLinesFormOneParagraph() {
        val blocks = MarkdownParser.parse("line one\nline two\n\nnext")
        assertEquals(2, blocks.size)
        val first = blocks[0] as MdBlock.Paragraph
        assertEquals(listOf(MdSpan.Text("line one line two")), first.spans)
    }
}
