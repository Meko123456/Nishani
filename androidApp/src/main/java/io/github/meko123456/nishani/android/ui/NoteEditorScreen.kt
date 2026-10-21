package io.github.meko123456.nishani.android.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.meko123456.nishani.shared.MarkdownParser
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    text: String,
    onTextChange: (String) -> Unit,
    canDelete: Boolean,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var preview by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val field = remember { FocusRequester() }
    // Blank notes aren't saved, so a note that opens empty is a new one, from + or the New note
    // shortcut, and the next thing is writing it: the field has the keyboard at once.
    val openedEmpty = remember { text.isEmpty() }

    // Export the note as a .md file via the Storage Access Framework.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.use { os -> os.write(text.toByteArray()) } }
    }

    // Debounced autosave: persist ~600ms after the last keystroke.
    LaunchedEffect(text) {
        delay(600)
        if (text.isNotBlank()) onSave(text)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (preview) "Preview" else "Edit") },
                navigationIcon = {
                    IconButton(onClick = { if (text.isNotBlank()) onSave(text); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { preview = !preview }) {
                        Icon(Icons.Default.Edit, contentDescription = if (preview) "Edit" else "Preview")
                    }
                    IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(send, "Share note"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = {
                        val name = text.lineSequence().firstOrNull { it.isNotBlank() }
                            ?.trim()?.trimStart('#', ' ')?.take(40)?.ifBlank { "note" } ?: "note"
                        exportLauncher.launch("$name.md")
                    }) {
                        Text(
                            "md",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.clearAndSetSemantics { contentDescription = "Export as .md file" },
                        )
                    }
                    if (canDelete) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (preview) {
            MarkdownText(
                blocks = MarkdownParser.parse(text),
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            )
        } else {
            TextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("Write in markdown…  # heading, - bullet, **bold**") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                // Ends at the top of the keyboard. Without this the window was panned instead: the line
                // being edited stayed hidden until the first keystroke, which then pushed the toolbar off
                // the top of the screen.
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
                    .focusRequester(field),
            )
            LaunchedEffect(Unit) { if (openedEmpty) field.requestFocus() }
        }
    }
}
