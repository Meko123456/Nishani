package io.github.meko123456.nishani.shared

/**
 * Inline formatting inside a line of text.
 *
 * These are deliberately render-agnostic: map them to a Compose `AnnotatedString`,
 * a SwiftUI `Text`, HTML, or anything else you like.
 */
sealed interface MdSpan {
    /** Plain, unformatted text. */
    data class Text(val text: String) : MdSpan

    /** `**bold**` */
    data class Bold(val text: String) : MdSpan

    /** `*italic*` or `_italic_` */
    data class Italic(val text: String) : MdSpan

    /** `` `code` `` */
    data class Code(val text: String) : MdSpan

    /** `~~struck~~` */
    data class Strikethrough(val text: String) : MdSpan

    /** `[label](destination)` */
    data class Link(val text: String, val destination: String) : MdSpan
}

/** A block-level element of a parsed markdown document. */
sealed interface MdBlock {
    /** `# Heading` — [level] is 1..6. */
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock

    /** A run of text separated from its neighbours by a blank line. */
    data class Paragraph(val spans: List<MdSpan>) : MdBlock

    /** `- item` / `* item`. */
    data class BulletItem(val spans: List<MdSpan>) : MdBlock

    /** `1. item` — [number] is the literal number written in the source. */
    data class OrderedItem(val number: Int, val spans: List<MdSpan>) : MdBlock

    /** `> quoted` */
    data class Quote(val spans: List<MdSpan>) : MdBlock

    /** A fenced ``` block; [language] is the info string, if any. */
    data class CodeBlock(val code: String, val language: String? = null) : MdBlock

    /** `---`, `***` or `___` */
    data object Divider : MdBlock
}

/**
 * A tiny, dependency-free markdown parser: text in, a list of [MdBlock] out.
 *
 * It covers the subset people actually write in notes and READMEs — headings, lists,
 * quotes, fenced code, rules, and inline emphasis/code/links — and deliberately stops
 * there. The output is a plain data tree with no rendering opinions, so the same parse
 * can drive Jetpack Compose on Android, SwiftUI on iOS, or HTML on the server.
 *
 * ```
 * val blocks = Markdown.parse("# Title\n- one\n- two")
 * ```
 *
 * Malformed input never throws: unmatched markers are kept as literal text.
 */
object MarkdownParser {

    /** Parses a whole document into block-level nodes. */
    fun parse(text: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val lines = text.replace("\r\n", "\n").split("\n")
        val paragraph = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += MdBlock.Paragraph(parseInline(paragraph.joinToString(" ")))
                paragraph.clear()
            }
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            when {
                trimmed.startsWith("```") -> {
                    flushParagraph()
                    val language = trimmed.removePrefix("```").trim().ifEmpty { null }
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        code.append(lines[i]).append('\n')
                        i++
                    }
                    i++ // consume the closing fence, if the document has one
                    blocks += MdBlock.CodeBlock(code.toString().trimEnd('\n'), language)
                }

                trimmed.isEmpty() -> {
                    flushParagraph()
                    i++
                }

                isDivider(trimmed) -> {
                    flushParagraph()
                    blocks += MdBlock.Divider
                    i++
                }

                else -> {
                    val heading = headingOf(trimmed)
                    val bullet = bulletOf(trimmed)
                    val ordered = orderedOf(trimmed)
                    when {
                        heading != null -> {
                            flushParagraph()
                            blocks += heading
                        }
                        trimmed.startsWith("> ") || trimmed == ">" -> {
                            flushParagraph()
                            blocks += MdBlock.Quote(parseInline(trimmed.removePrefix(">").trim()))
                        }
                        bullet != null -> {
                            flushParagraph()
                            blocks += bullet
                        }
                        ordered != null -> {
                            flushParagraph()
                            blocks += ordered
                        }
                        else -> paragraph += trimmed
                    }
                    i++
                }
            }
        }
        flushParagraph()
        return blocks
    }

    /** Parses a single line's inline formatting. Exposed for rendering list labels and the like. */
    fun parseInline(text: String): List<MdSpan> {
        val spans = mutableListOf<MdSpan>()
        val buffer = StringBuilder()

        fun flush() {
            if (buffer.isNotEmpty()) {
                spans += MdSpan.Text(buffer.toString())
                buffer.clear()
            }
        }

        var i = 0
        while (i < text.length) {
            val rest = text.substring(i)
            val span = codeSpan(rest) ?: linkSpan(rest) ?: strikeSpan(rest) ?: boldSpan(rest) ?: italicSpan(rest)
            if (span != null) {
                flush()
                spans += span.first
                i += span.second
            } else {
                buffer.append(text[i])
                i++
            }
        }
        flush()
        return spans
    }

    // --- block helpers --------------------------------------------------------

    private fun isDivider(line: String): Boolean =
        line.length >= 3 && (line.all { it == '-' } || line.all { it == '*' } || line.all { it == '_' })

    private fun headingOf(line: String): MdBlock.Heading? {
        val hashes = line.takeWhile { it == '#' }.length
        if (hashes !in 1..6) return null
        if (line.length <= hashes || line[hashes] != ' ') return null
        return MdBlock.Heading(hashes, parseInline(line.substring(hashes + 1).trim()))
    }

    private fun bulletOf(line: String): MdBlock.BulletItem? {
        val marker = line.take(2)
        if (marker != "- " && marker != "* " && marker != "+ ") return null
        return MdBlock.BulletItem(parseInline(line.substring(2).trim()))
    }

    private fun orderedOf(line: String): MdBlock.OrderedItem? {
        val digits = line.takeWhile { it.isDigit() }
        if (digits.isEmpty() || digits.length > 9) return null
        val afterDigits = line.drop(digits.length)
        if (!afterDigits.startsWith(". ") && !afterDigits.startsWith(") ")) return null
        val number = digits.toIntOrNull() ?: return null
        return MdBlock.OrderedItem(number, parseInline(afterDigits.drop(2).trim()))
    }

    // --- inline helpers: each returns the span and how many chars it consumed ---

    private fun codeSpan(rest: String): Pair<MdSpan, Int>? {
        if (!rest.startsWith("`")) return null
        val end = rest.indexOf('`', 1)
        if (end <= 0) return null
        return MdSpan.Code(rest.substring(1, end)) to end + 1
    }

    private fun linkSpan(rest: String): Pair<MdSpan, Int>? {
        if (!rest.startsWith("[")) return null
        val labelEnd = rest.indexOf(']')
        if (labelEnd <= 0 || labelEnd + 1 >= rest.length || rest[labelEnd + 1] != '(') return null
        val destEnd = rest.indexOf(')', labelEnd + 2)
        if (destEnd <= 0) return null
        val label = rest.substring(1, labelEnd)
        val destination = rest.substring(labelEnd + 2, destEnd)
        if (destination.isEmpty()) return null
        return MdSpan.Link(label, destination) to destEnd + 1
    }

    private fun strikeSpan(rest: String): Pair<MdSpan, Int>? = delimited(rest, "~~")?.let {
        MdSpan.Strikethrough(it.first) to it.second
    }

    private fun boldSpan(rest: String): Pair<MdSpan, Int>? =
        (delimited(rest, "**") ?: delimited(rest, "__"))?.let { MdSpan.Bold(it.first) to it.second }

    private fun italicSpan(rest: String): Pair<MdSpan, Int>? =
        (delimited(rest, "*") ?: delimited(rest, "_"))?.let { MdSpan.Italic(it.first) to it.second }

    /**
     * Content between a matching pair of [marker]s, plus the total chars consumed.
     *
     * Following CommonMark's flanking rule, emphasis may not open directly before
     * whitespace or close directly after it — so arithmetic like `2 * 3 * 4` stays
     * literal text instead of turning into italics.
     */
    private fun delimited(rest: String, marker: String): Pair<String, Int>? {
        if (!rest.startsWith(marker)) return null
        val end = rest.indexOf(marker, marker.length)
        if (end <= marker.length - 1) return null
        val content = rest.substring(marker.length, end)
        if (content.isEmpty()) return null
        if (content.first().isWhitespace() || content.last().isWhitespace()) return null
        return content to end + marker.length
    }
}
