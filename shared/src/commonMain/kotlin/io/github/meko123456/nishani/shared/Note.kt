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
    /** Display title: the first non-blank line with leading markdown markers stripped. */
    val title: String
        get() = body.lineSequence()
            .map { it.trim().trimStart('#', '>', '-', '*', ' ') }
            .firstOrNull { it.isNotBlank() }
            ?.take(80)
            ?: "Untitled"

    /** A short second-line preview for the list. */
    val preview: String
        get() = body.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .drop(1)
            .firstOrNull()
            ?.take(120)
            ?: ""
}

/** Pure, case-insensitive substring search over notes — testable without any store. */
object NoteSearch {
    fun filter(notes: List<Note>, query: String): List<Note> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return notes
        return notes.filter { it.body.lowercase().contains(q) }
    }
}
