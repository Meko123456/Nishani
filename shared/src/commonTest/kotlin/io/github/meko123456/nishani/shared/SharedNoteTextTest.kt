package io.github.meko123456.nishani.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedNoteTextTest {

    @Test
    fun aSharedPageBecomesANoteHeadedWithItsTitle() {
        assertEquals(
            "# Tbilisi - Wikipedia\n\nhttps://en.wikipedia.org/wiki/Tbilisi",
            sharedNoteText("Tbilisi - Wikipedia", "https://en.wikipedia.org/wiki/Tbilisi"),
        )
    }

    @Test
    fun withoutASubjectTheTextIsTheNote() {
        assertEquals("Buy bread", sharedNoteText(null, "  Buy bread \r\n"))
        assertEquals("Buy bread", sharedNoteText("   ", "Buy bread"))
    }

    @Test
    fun aSubjectTheTextAlreadyCarriesIsNotSaidTwice() {
        assertEquals(
            "Meeting at 6 - see you there",
            sharedNoteText("Meeting at 6", "Meeting at 6 - see you there"),
        )
    }
}
