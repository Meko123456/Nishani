package io.github.meko123456.nishani.shared

/**
 * Inline formatting inside a line of text. Each platform renders these natively
 * (Compose AnnotatedString on Android, SwiftUI Text on iOS).
 */
sealed interface MdSpan {
    data class Text(val text: String) : MdSpan
    data class Bold(val text: String) : MdSpan
    data class Italic(val text: String) : MdSpan
    data class Code(val text: String) : MdSpan
}

/** A block-level element of a parsed markdown document. */
sealed interface MdBlock {
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class BulletItem(val spans: List<MdSpan>) : MdBlock
    data class Quote(val spans: List<MdSpan>) : MdBlock
    data class CodeBlock(val code: String) : MdBlock
    data object Divider : MdBlock
}

/**
 * A small, dependency-free markdown parser: text → a list of [MdBlock]. Written once
 * in shared Kotlin so Android and iOS parse identically and only the rendering is native.
 * Supports headings (#/##/###), bullets (- / *), block quotes (>), fenced code (```),
 * horizontal rules (--- / ***), and inline **bold**, *italic*, and `code`.
 */
object MarkdownParser {

    fun parse(text: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val lines = text.replace("\r\n", "\n").split("\n")
        val paragraph = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks.add(MdBlock.Paragraph(parseInline(paragraph.joinToString(" "))))
                paragraph.clear()
            }
        }

        var i = 0
        while (i < lines.size) {
            val trimmed = lines[i].trim()
            when {
                trimmed.startsWith("```") -> {
                    flushParagraph()
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        code.append(lines[i]).append('\n')
                        i++
                    }
                    i++ // consume the closing fence (if present)
                    blocks.add(MdBlock.CodeBlock(code.toString().trimEnd('\n')))
                }
                trimmed.isEmpty() -> { flushParagraph(); i++ }
                trimmed == "---" || trimmed == "***" -> { flushParagraph(); blocks.add(MdBlock.Divider); i++ }
                trimmed.startsWith("### ") -> { flushParagraph(); blocks.add(MdBlock.Heading(3, parseInline(trimmed.substring(4)))); i++ }
                trimmed.startsWith("## ") -> { flushParagraph(); blocks.add(MdBlock.Heading(2, parseInline(trimmed.substring(3)))); i++ }
                trimmed.startsWith("# ") -> { flushParagraph(); blocks.add(MdBlock.Heading(1, parseInline(trimmed.substring(2)))); i++ }
                trimmed.startsWith("> ") -> { flushParagraph(); blocks.add(MdBlock.Quote(parseInline(trimmed.substring(2)))); i++ }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> { flushParagraph(); blocks.add(MdBlock.BulletItem(parseInline(trimmed.substring(2)))); i++ }
                else -> { paragraph.add(trimmed); i++ }
            }
        }
        flushParagraph()
        return blocks
    }

    /** Parses one line's inline formatting into [MdSpan]s. */
    fun parseInline(text: String): List<MdSpan> {
        val spans = mutableListOf<MdSpan>()
        val buffer = StringBuilder()

        fun flush() {
            if (buffer.isNotEmpty()) {
                spans.add(MdSpan.Text(buffer.toString()))
                buffer.clear()
            }
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        flush(); spans.add(MdSpan.Code(text.substring(i + 1, end))); i = end + 1
                    } else { buffer.append(c); i++ }
                }
                c == '*' && i + 1 < text.length && text[i + 1] == '*' -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        flush(); spans.add(MdSpan.Bold(text.substring(i + 2, end))); i = end + 2
                    } else { buffer.append(c); i++ }
                }
                c == '*' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > i) {
                        flush(); spans.add(MdSpan.Italic(text.substring(i + 1, end))); i = end + 1
                    } else { buffer.append(c); i++ }
                }
                else -> { buffer.append(c); i++ }
            }
        }
        flush()
        return spans
    }
}
