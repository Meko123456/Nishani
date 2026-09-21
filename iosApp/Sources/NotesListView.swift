import SwiftUI
import Shared

struct NotesListView: View {
    @EnvironmentObject var store: Store
    @State private var editing: EditorTarget?

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
                        .accessibilityIdentifier("newNote")
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
