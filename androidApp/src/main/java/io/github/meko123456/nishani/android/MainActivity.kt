package io.github.meko123456.nishani.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
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
                val draft = vm.draft
                val context = LocalContext.current

                // Import a .md file into a new note via the Storage Access Framework.
                val importLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    uri?.let {
                        val body = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() }
                        if (!body.isNullOrBlank()) vm.openEditor(null, body)
                    }
                }

                // Saves first, as the toolbar's back arrow does: leaving cancels the 600 ms autosave,
                // so the last keystrokes were lost.
                BackHandler(enabled = draft != null) {
                    draft?.let(vm::saveDraft)
                    vm.closeEditor()
                }

                if (draft != null) {
                    NoteEditorScreen(
                        text = draft,
                        onTextChange = vm::editDraft,
                        canDelete = vm.editingId != null,
                        onSave = vm::saveDraft,
                        onDelete = { vm.editingId?.let { vm.delete(it) }; vm.closeEditor() },
                        onBack = vm::closeEditor,
                    )
                } else {
                    NotesListScreen(
                        notes = vm.notes,
                        query = vm.query,
                        onQuery = vm::onQuery,
                        onOpen = { id -> vm.openEditor(id, vm.note(id)?.body ?: "") },
                        onTogglePin = vm::togglePin,
                        onNew = { vm.openEditor(null, "") },
                        onImport = { importLauncher.launch(arrayOf("text/*")) },
                    )
                }
            }
        }
    }
}
