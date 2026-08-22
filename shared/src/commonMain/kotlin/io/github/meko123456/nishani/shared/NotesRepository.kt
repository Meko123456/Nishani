package io.github.meko123456.nishani.shared

import kotlinx.serialization.json.Json

/**
 * The single source of truth for notes, persisted as JSON in a [KeyValueStore].
 * All logic (serialization, ordering, search) lives here in shared Kotlin; the id and
 * timestamp are supplied by the caller so the module stays platform-agnostic.
 */
class NotesRepository(private val store: KeyValueStore) {

    private val json = Json { ignoreUnknownKeys = true }
    private val key = "notes"

    private fun load(): List<Note> =
        store.getString(key)?.let { runCatching { json.decodeFromString<List<Note>>(it) }.getOrNull() }
            ?: emptyList()

    private fun persist(notes: List<Note>) {
        store.putString(key, json.encodeToString(notes))
    }

    /** Pinned first, then newest first. */
    fun all(): List<Note> = load().sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })

    /** Flips the pinned flag on a note. */
    fun togglePin(id: String) {
        val n = get(id) ?: return
        upsert(n.copy(pinned = !n.pinned))
    }

    fun get(id: String): Note? = load().firstOrNull { it.id == id }

    /** Inserts or replaces a note by id, then re-sorts on read. */
    fun upsert(note: Note) {
        val notes = load().filterNot { it.id == note.id } + note
        persist(notes)
    }

    fun delete(id: String) {
        persist(load().filterNot { it.id == id })
    }

    /** Case-insensitive substring search over the note body, newest first. */
    fun search(query: String): List<Note> = NoteSearch.filter(all(), query)
}
