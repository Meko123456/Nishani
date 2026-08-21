import Foundation
import Shared

/// SwiftUI-facing wrapper around the shared Kotlin `NotesRepository`.
///
/// Parsing (MarkdownParser), the note model, persistence, and search all live once in the
/// KMP `shared` module; this type only adapts it to `ObservableObject` and supplies the
/// native NSUserDefaults-backed store. Notes persist through the same shared JSON format.
@MainActor
final class Store: ObservableObject {
    private let repo = NotesRepository(store: UserDefaultsKeyValueStore(defaults: .standard))

    @Published private(set) var notes: [Note] = []
    @Published var query: String = "" {
        didSet { refresh() }
    }

    init() { refresh() }

    func refresh() {
        notes = query.isEmpty ? repo.all() : repo.search(query: query)
    }

    func note(_ id: String) -> Note? { repo.get(id: id) }

    @discardableResult
    func save(id: String?, body: String) -> String {
        let noteId = id ?? "note-\(UUID().uuidString)"
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        repo.upsert(note: Note(id: noteId, body: body, updatedAt: now))
        refresh()
        return noteId
    }

    func delete(_ id: String) {
        repo.delete(id: id)
        refresh()
    }

    func parse(_ text: String) -> [MdBlock] {
        MarkdownParser.shared.parse(text: text)
    }
}
