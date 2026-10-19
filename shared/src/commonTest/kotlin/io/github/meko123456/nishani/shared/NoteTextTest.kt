package io.github.meko123456.nishani.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NoteTextTest {

    // As Windows Notepad saves it: a byte-order mark, then lines ending in \r\n.
    private val fromWindows = "\uFEFF# Shopping\r\n\r\n- milk\r\n- **bread**\r\n"

    @Test
    fun aWindowsFileComesInAsPlainLines() {
        assertEquals("# Shopping\n\n- milk\n- **bread**\n", noteText(fromWindows))
        assertEquals("one\ntwo", noteText("one\rtwo"))
    }

    @Test
    fun itsFirstHeadingIsAHeadingAgain() {
        // With the BOM in front, "# Shopping" parsed as a paragraph and kept its hash.
        assertIs<MdBlock.Heading>(MarkdownParser.parse(noteText(fromWindows)).first())
    }

    @Test
    fun aNoteStoredWithABomStillTakesItsTitleWithoutTheHash() {
        assertEquals("Shopping", Note(id = "n1", body = fromWindows, updatedAt = 0).title)
        assertEquals("milk", Note(id = "n1", body = fromWindows, updatedAt = 0).preview)
    }

    @Test
    fun ordinaryTextIsUntouched() {
        assertEquals("# Title\n\nbody", noteText("# Title\n\nbody"))
    }
}
