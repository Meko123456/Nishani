package io.github.meko123456.nishani.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.meko123456.nishani.android.ui.NoteEditorScreen
import io.github.meko123456.nishani.android.ui.NotesListScreen
import io.github.meko123456.nishani.android.ui.NotesViewModel
import io.github.meko123456.nishani.android.ui.theme.NishaniTheme
import io.github.meko123456.nishani.shared.sharedNoteText

class MainActivity : ComponentActivity() {
    /** Text another app shared to Nishani, numbered so sharing the same text twice still counts. */
    private var handover by mutableStateOf<Pair<String, Int>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on first creation: a rotation recreates the activity with the same intent, and must
        // not open the shared text again over the note it became.
        if (savedInstanceState == null) take(intent)
        setContent {
            NishaniTheme {
                val vm: NotesViewModel = viewModel()
                val draft = vm.draft
                val context = LocalContext.current

                // Shared from another app: a new note, the open one saved first.
                LaunchedEffect(handover?.second) {
                    handover?.let { (text, _) ->
                        vm.draft?.let(vm::saveDraft)
                        vm.openEditor(null, text)
                    }
                }

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
                        onDelete = vm::deleteOpenNote,
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
                        deleted = vm.deleted,
                        onUndoDelete = vm::undoDelete,
                        onDeleteSettled = vm::forgetDeleted,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        take(intent)
    }

    /** A plain-text SEND becomes a new note; anything else, the launcher's MAIN included, is ignored. */
    private fun take(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() } ?: return
        val note = sharedNoteText(intent.getStringExtra(Intent.EXTRA_SUBJECT), text)
        handover = note to ((handover?.second ?: 0) + 1)
    }
}
