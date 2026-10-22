import SwiftUI
import Shared

struct NotesListView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var quickActions: QuickActions
    @State private var editing: EditorTarget?
    /// The note a swipe asked to delete, held while the question is up.
    @State private var pendingDelete: PendingNoteDelete?

    var body: some View {
        NavigationStack {
            Group {
                if store.notes.isEmpty {
                    VStack(spacing: 8) {
                        Image(systemName: "square.and.pencil")
                            .font(.largeTitle)
                            .foregroundStyle(.secondary)
                        Text(store.query.isEmpty ? "No notes yet" : "No matches")
                            .font(.headline)
                        Text(store.query.isEmpty ? "Tap + to write one." : "Try a different search.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List {
                        ForEach(store.notes, id: \.id) { note in
                            Button {
                                editing = .existing(note.id)
                            } label: {
                                VStack(alignment: .leading, spacing: 2) {
                                    // The 📌 prefix is how the Android list marks a pinned note;
                                    // same marker here so the two apps read the same.
                                    Text((note.pinned ? "📌 " : "") + note.title)
                                        .font(.headline)
                                        .lineLimit(1)
                                        .accessibilityIdentifier("noteTitle")
                                    if !note.preview.isEmpty {
                                        Text(note.preview).font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
                                    }
                                }
                                // A button merges its children into one element, which would make
                                // the row's title readable only as part of the whole row.
                                .accessibilityElement(children: .contain)
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("note-\(note.title)")
                            // Pinning had no way in on iOS at all. NotesRepository sorts pinned
                            // notes to the top and Store.togglePin was wired up to ask it to, but
                            // nothing on this side ever called it — Android pins on a long press
                            // and the SwiftUI list simply never grew the equivalent.
                            .swipeActions(edge: .leading) {
                                Button { store.togglePin(note.id) } label: {
                                    Label(note.pinned ? "Unpin" : "Pin",
                                          systemImage: note.pinned ? "pin.slash" : "pin")
                                }
                                .tint(.orange)
                                .accessibilityIdentifier("pin-\(note.title)")
                            }
                            // Asks first. The list's swipe-to-delete removed the note for good —
                            // there is no trash to bring it back from — and a full swipe needed no
                            // tap at all. Tinted rather than given the destructive role, which
                            // takes the row away before the answer is in.
                            .swipeActions(edge: .trailing) {
                                Button { pendingDelete = PendingNoteDelete(id: note.id, title: note.title) } label: {
                                    Label("Delete", systemImage: "trash")
                                }
                                .tint(.red)
                                .accessibilityIdentifier("delete-\(note.title)")
                            }
                        }
                    }
                }
            }
            .alert(
                "Delete \u{201C}\(pendingDelete?.title ?? "this note")\u{201D}?",
                isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }),
                presenting: pendingDelete
            ) { pending in
                Button("Delete", role: .destructive) { store.delete(pending.id) }
                Button("Cancel", role: .cancel) {}
            } message: { _ in
                Text("It can't be brought back.")
            }
            .navigationTitle("Nishani")
            .searchable(text: $store.query, prompt: "Search notes")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { editing = .new() } label: { Image(systemName: "plus") }
                        .accessibilityIdentifier("newNote")
                        .accessibilityLabel("New note")
                }
            }
            .sheet(item: $editing) { target in
                NoteEditorView(target: target).environmentObject(store)
            }
            // The Home Screen's Write a new note: on launch, or later while the app is running.
            .onAppear(perform: takeQuickAction)
            .onChange(of: quickActions.pending) { _ in takeQuickAction() }
        }
    }

    private func takeQuickAction() {
        guard quickActions.pending == .newNote else { return }
        quickActions.pending = nil
        // A blank page even if another note is open: the sheet swaps, and the open note is saved
        // as its editor goes.
        editing = .new()
    }
}

/// A note waiting on the delete question: enough of it to ask about it by name.
private struct PendingNoteDelete {
    let id: String
    let title: String
}

enum EditorTarget: Identifiable {
    /// A note not written yet. Each opening has its own id, so a quick action that arrives while
    /// a new note is open still starts a blank one.
    case new(UUID = UUID())
    case existing(String)
    var id: String {
        switch self {
        case .new(let opening): return "new-\(opening)"
        case .existing(let id): return id
        }
    }
}
