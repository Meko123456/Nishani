package io.github.meko123456.nishani.shared

import kotlinx.serialization.Serializable

/** A single markdown note. [body] is the raw markdown; the title/preview are derived. */
@Serializable
data class Note(
    val id: String,
    val body: String,
    val updatedAt: Long,
    val pinned: Boolean = false,
) {
    /** Display title: the first line with text, leading markdown markers stripped. */
    val title: String
        get() = textLines()
            .map { it.trimStart('#', '>', '-', '*', ' ') }
            .firstOrNull { it.isNotBlank() }
            ?.capped(80)
            ?: "Untitled"

    /** A short second-line preview for the list. */
    val preview: String
        get() = textLines().drop(1).firstOrNull()?.capped(120) ?: ""

    /**
     * The lines with text in them, as plain text. Both platforms show the title and preview as
     * plain strings, so markup left in them showed as written: "Pack for the **trip**". Lines in
     * a code fence are code and stay as written; the fences themselves are markup only.
     */
    private fun textLines(): Sequence<String> = sequence {
        var inFence = false
        for (line in noteText(body).lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                inFence = !inFence
                continue
            }
            val text = if (inFence) trimmed else trimmed.asPlainLine().trim()
            if (text.isNotBlank()) yield(text)
        }
    }
}

/**
 * Text as a note keeps it: no byte-order mark in front, and "\n" between lines.
 *
 * A file saved by Windows Notepad starts with a BOM and ends its lines with "\r\n". Imported as it
 * was, the BOM stood in front of the first heading's "#": "# Shopping" was a paragraph in the
 * preview, and the list's title kept its hash.
 */
fun noteText(raw: String): String = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')

/**
 * One line of markdown as the words it shows, read by the parser the preview uses: a heading, list
 * or quote marker goes, a rule comes back empty, and inline marks go while their text stays.
 */
internal fun String.asPlainLine(): String = when (val block = MarkdownParser.parse(this).firstOrNull()) {
    is MdBlock.Heading -> block.spans.plainText()
    is MdBlock.Paragraph -> block.spans.plainText()
    is MdBlock.BulletItem -> block.spans.plainText()
    is MdBlock.OrderedItem -> block.spans.plainText()
    is MdBlock.Quote -> block.spans.plainText()
    is MdBlock.CodeBlock, MdBlock.Divider, null -> ""
}

private fun List<MdSpan>.plainText(): String = joinToString("") { span ->
    when (span) {
        is MdSpan.Text -> span.text
        is MdSpan.Bold -> span.text
        is MdSpan.Italic -> span.text
        is MdSpan.Code -> span.text
        is MdSpan.Strikethrough -> span.text
        is MdSpan.Link -> span.text
    }
}

/** Pure, case-insensitive substring search over notes — testable without any store. */
object NoteSearch {
    fun filter(notes: List<Note>, query: String): List<Note> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return notes
        return notes.filter { it.body.lowercase().contains(q) }
    }
}
