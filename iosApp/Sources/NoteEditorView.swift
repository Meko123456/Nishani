import SwiftUI
import Shared

struct NoteEditorView: View {
    let target: EditorTarget
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) private var dismiss

    @State private var text: String = ""
    @State private var noteId: String?
    @State private var showPreview = false

    var body: some View {
        NavigationStack {
            Group {
                if showPreview {
                    ScrollView {
                        MarkdownView(blocks: store.parse(text))
                            .padding()
                    }
                } else {
                    TextEditor(text: $text)
                        .font(.system(.body, design: .monospaced))
                        .padding(8)
                }
            }
            .navigationTitle(showPreview ? "Preview" : "Edit")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { saveIfNeeded(); dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button { showPreview.toggle() } label: {
                        Image(systemName: showPreview ? "pencil" : "eye")
                    }
                }
                if !text.isEmpty {
                    ToolbarItem(placement: .primaryAction) {
                        ShareLink(item: text) { Image(systemName: "square.and.arrow.up") }
                    }
                }
            }
        }
        .onAppear {
            if case let .existing(id) = target {
                noteId = id
                text = store.note(id)?.body ?? ""
            }
        }
        // Debounced autosave: persist shortly after typing stops.
        .task(id: text) {
            try? await Task.sleep(nanoseconds: 600_000_000)
            saveIfNeeded()
        }
    }

    private func saveIfNeeded() {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        noteId = store.save(id: noteId, body: text)
    }
}
