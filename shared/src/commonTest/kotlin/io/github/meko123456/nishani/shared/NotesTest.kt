package io.github.meko123456.nishani.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun savingAnEditKeepsTheNotePinned() {
        val repo = NotesRepository(InMemoryKeyValueStore())
        repo.upsert(Note("n1", "shopping", 1, pinned = true))

        repo.saveBody("n1", "shopping and cleaning", now = 2)

        val saved = repo.get("n1")!!
        assertEquals("shopping and cleaning", saved.body)
        assertEquals(2L, saved.updatedAt)
        // The editor autosaves on every keystroke. Rebuilding the note from just the id and body
        // dropped this flag, so one character unpinned a note the user had deliberately pinned.
        assertTrue(saved.pinned, "editing a pinned note must not unpin it")
    }

    @Test
    fun savingANewNoteStartsItUnpinned() {
        val repo = NotesRepository(InMemoryKeyValueStore())

        val created = repo.saveBody("n2", "a fresh note", now = 5)

        assertEquals(false, created.pinned)
        assertEquals("a fresh note", repo.get("n2")?.body)
    }

    @Test
    fun savingAnEditDoesNotDisturbOtherNotes() {
        val repo = NotesRepository(InMemoryKeyValueStore())
        repo.upsert(Note("a", "first", 1, pinned = true))
        repo.upsert(Note("b", "second", 2))

        repo.saveBody("a", "first, edited", now = 3)

        assertEquals(2, repo.all().size)
        assertEquals("second", repo.get("b")?.body)
        assertTrue(repo.get("a")!!.pinned)
    }
}
