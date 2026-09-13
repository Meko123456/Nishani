package io.github.meko123456.nishani.android.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import io.github.meko123456.nishani.shared.Note
import io.github.meko123456.nishani.shared.NotesRepository
import io.github.meko123456.nishani.shared.PrefsKeyValueStore
import java.util.UUID

/** Bridges the shared [NotesRepository] to Compose state. Persistence + search are shared logic. */
class NotesViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = NotesRepository(PrefsKeyValueStore(app))

    var query by mutableStateOf("")
        private set
    var notes by mutableStateOf<List<Note>>(emptyList())
        private set

    init { refresh() }

    private fun refresh() {
        notes = if (query.isBlank()) repo.all() else repo.search(query)
    }

    fun onQuery(q: String) {
        query = q
        refresh()
    }

    fun note(id: String): Note? = repo.get(id)

    /** Saves a new or existing note and returns its id (used by autosave). */
    fun save(id: String?, body: String): String {
        val noteId = id ?: "note-${UUID.randomUUID()}"
        // saveBody, not upsert: upsert replaces the whole row, and rebuilding the note from just the
        // id and body dropped its pinned flag. The editor autosaves 600 ms after a keystroke, so one
        // character was enough to unpin a note - on Android only, because the iOS editor happened to
        // carry the flag across by hand.
        repo.saveBody(noteId, body, System.currentTimeMillis())
        refresh()
        return noteId
    }

    fun delete(id: String) {
        repo.delete(id)
        refresh()
    }

    fun togglePin(id: String) {
        repo.togglePin(id)
        refresh()
    }
}
