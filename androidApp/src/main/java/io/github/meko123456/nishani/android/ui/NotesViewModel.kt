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
        repo.upsert(Note(noteId, body, System.currentTimeMillis()))
        refresh()
        return noteId
    }

    fun delete(id: String) {
        repo.delete(id)
        refresh()
    }
}
