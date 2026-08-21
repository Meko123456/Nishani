package io.github.meko123456.nishani.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotesTest {

    @Test
    fun searchIsCaseInsensitiveSubstring() {
        val notes = listOf(
            Note("1", "Grocery LIST\nmilk", 3),
            Note("2", "Meeting notes", 2),
            Note("3", "shopping list ideas", 1),
        )
        val hits = NoteSearch.filter(notes, "list").map { it.id }
        assertEquals(listOf("1", "3"), hits)
    }

    @Test
    fun blankSearchReturnsAll() {
        val notes = listOf(Note("1", "a", 1), Note("2", "b", 2))
        assertEquals(2, NoteSearch.filter(notes, "  ").size)
    }

    @Test
    fun titleAndPreviewDerivedFromBody() {
        val note = Note("1", "# My Note\nthe body preview here", 1)
        assertEquals("My Note", note.title)
        assertEquals("the body preview here", note.preview)
    }

    @Test
    fun repositoryUpsertGetDeleteAndOrder() {
        val repo = NotesRepository(InMemoryKeyValueStore())
        repo.upsert(Note("a", "first", 1))
        repo.upsert(Note("b", "second", 2))
        assertEquals("second", repo.all().first().body) // newest first
        assertEquals("first", repo.get("a")?.body)
        repo.upsert(Note("a", "edited", 3)) // replace by id
        assertEquals("edited", repo.get("a")?.body)
        repo.delete("b")
        assertNull(repo.get("b"))
        assertEquals(1, repo.all().size)
    }
}
