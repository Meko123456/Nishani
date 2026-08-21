import SwiftUI
import Shared

struct NotesListView: View {
    @EnvironmentObject var store: Store
    @State private var editing: EditorTarget?

    var body: some View {
        NavigationStack {
            Group {
                if store.notes.isEmpty {
                    ContentUnavailableView(
                        store.query.isEmpty ? "No notes yet" : "No matches",
                        systemImage: "square.and.pencil",
                        description: Text(store.query.isEmpty ? "Tap + to write one." : "Try a different search.")
                    )
                } else {
                    List {
                        ForEach(store.notes, id: \.id) { note in
                            Button {
                                editing = .existing(note.id)
                            } label: {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(note.title).font(.headline).lineLimit(1)
                                    if !note.preview.isEmpty {
                                        Text(note.preview).font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
                                    }
                                }
                            }
                            .buttonStyle(.plain)
                        }
                        .onDelete { indexSet in
                            indexSet.map { store.notes[$0].id }.forEach { store.delete($0) }
                        }
                    }
                }
            }
            .navigationTitle("Nishani")
            .searchable(text: $store.query, prompt: "Search notes")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { editing = .new } label: { Image(systemName: "plus") }
                }
            }
            .sheet(item: $editing) { target in
                NoteEditorView(target: target).environmentObject(store)
            }
        }
    }
}

enum EditorTarget: Identifiable {
    case new
    case existing(String)
    var id: String {
        switch self {
        case .new: return "new"
        case .existing(let id): return id
        }
    }
}
