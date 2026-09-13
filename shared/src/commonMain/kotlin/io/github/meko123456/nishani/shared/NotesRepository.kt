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

    /**
     * Saves [body] against [id] at [now], creating the note if it is new and keeping everything else
     * about an existing one — notably whether it is pinned.
     *
     * [upsert] replaces the whole row, which makes it easy to drop a field by rebuilding a [Note]
     * from only the parts being edited. That is exactly what happened: the Android editor rebuilt
     * the note without `pinned`, so editing a pinned note unpinned it, while the iOS editor carried
     * the flag across by hand. An editor wants this call, not [upsert].
     */
    fun saveBody(id: String, body: String, now: Long): Note {
        val note = Note(id = id, body = body, updatedAt = now, pinned = get(id)?.pinned ?: false)
        upsert(note)
        return note
    }

    fun delete(id: String) {
        persist(load().filterNot { it.id == id })
    }

    /** Case-insensitive substring search over the note body, newest first. */
    fun search(query: String): List<Note> = NoteSearch.filter(all(), query)
}
