package io.github.meko123456.nishani.android.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import io.github.meko123456.nishani.shared.Note
import io.github.meko123456.nishani.shared.NotesRepository
import io.github.meko123456.nishani.shared.PrefsKeyValueStore
import io.github.meko123456.nishani.shared.noteText
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

    /**
     * The note open in the editor: its id (null until a new note's first save) and its text as
     * typed, or a null draft when the editor is closed. Held here, not in the screens, so a rotation
     * keeps the editor open on the same note with every keystroke. In memory only: a whole note, an
     * imported file say, can be too big for saved instance state.
     */
    var editingId by mutableStateOf<String?>(null)
        private set
    var draft by mutableStateOf<String?>(null)
        private set

    fun openEditor(id: String?, body: String) {
        editingId = id
        // An imported file, or a note imported before this, can carry a BOM and "\r\n" endings.
        draft = noteText(body)
        deleted = null // moving on to another note lets the last delete stand
    }

    fun editDraft(text: String) {
        draft = text
    }

    /** Autosave and leaving. A new note gets its id on its first save and keeps it. */
    fun saveDraft(body: String) {
        if (body.isNotBlank()) editingId = save(editingId, body)
    }

    fun closeEditor() {
        editingId = null
        draft = null
    }

    /**
     * The note deleted last, until its Undo is used or let go. In memory only, like the draft: a
     * whole note can be too big for saved state.
     */
    var deleted by mutableStateOf<Note?>(null)
        private set

    /**
     * Deletes the note open in the editor and closes it. The trash icon did this at one tap with no
     * way back; now the list offers Undo. The draft is saved first, so Undo brings back what was on
     * screen rather than the autosave before it.
     */
    fun deleteOpenNote() {
        val id = editingId ?: return
        draft?.let(::saveDraft)
        deleted = repo.get(id)
        repo.delete(id)
        closeEditor()
        refresh()
    }

    /** Puts the note back as it was, time and pin included, so it returns to its place. */
    fun undoDelete() {
        deleted?.let(repo::upsert)
        deleted = null
        refresh()
    }

    /** The Undo went unused: the delete stands. */
    fun forgetDeleted() {
        deleted = null
    }

    fun togglePin(id: String) {
        repo.togglePin(id)
        refresh()
    }
}
