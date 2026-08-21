package io.github.meko123456.nishani.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.meko123456.nishani.android.ui.NoteEditorScreen
import io.github.meko123456.nishani.android.ui.NotesListScreen
import io.github.meko123456.nishani.android.ui.NotesViewModel
import io.github.meko123456.nishani.android.ui.theme.NishaniTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NishaniTheme {
                val vm: NotesViewModel = viewModel()
                var editingId by remember { mutableStateOf<String?>(null) }
                var editorInitial by remember { mutableStateOf("") }
                var isEditing by remember { mutableStateOf(false) }

                BackHandler(enabled = isEditing) { isEditing = false }

                if (isEditing) {
                    NoteEditorScreen(
                        initialBody = editorInitial,
                        canDelete = editingId != null,
                        onSave = { body -> editingId = vm.save(editingId, body) },
                        onDelete = { editingId?.let { vm.delete(it) }; isEditing = false },
                        onBack = { isEditing = false },
                    )
                } else {
                    NotesListScreen(
                        notes = vm.notes,
                        query = vm.query,
                        onQuery = vm::onQuery,
                        onOpen = { id ->
                            editingId = id
                            editorInitial = vm.note(id)?.body ?: ""
                            isEditing = true
                        },
                        onNew = {
                            editingId = null
                            editorInitial = ""
                            isEditing = true
                        },
                    )
                }
            }
        }
    }
}
