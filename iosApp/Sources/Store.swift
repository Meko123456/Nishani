import Foundation
import Shared

/// SwiftUI-facing wrapper around the shared Kotlin `NotesRepository`.
///
/// Parsing (MarkdownParser), the note model, persistence, and search all live once in the
/// KMP `shared` module; this type only adapts it to `ObservableObject` and supplies the
/// native NSUserDefaults-backed store. Notes persist through the same shared JSON format.
@MainActor
final class Store: ObservableObject {
    private let repo = NotesRepository(store: UserDefaultsKeyValueStore(defaults: Store.backingDefaults()))

    /// Where notes are written: the real defaults for the app, a throwaway suite for a UI test.
    ///
    /// This app is offline-first, which is another way of saying it remembers everything — so a UI
    /// test run against `.standard` inherits every note the previous run wrote, and the second run
    /// of a test that counts notes fails for a reason that has nothing to do with the code.
    ///
    /// `UITEST_DEFAULTS_RESET` is separate from the suite name on purpose: a test that relaunches
    /// to check what survived needs the second launch to find the first one's writes still there.
    private static func backingDefaults() -> UserDefaults {
        let env = ProcessInfo.processInfo.environment
        guard let suite = env["UITEST_DEFAULTS_SUITE"], let defaults = UserDefaults(suiteName: suite) else {
            return .standard
        }
        if env["UITEST_DEFAULTS_RESET"] == "1" { defaults.removePersistentDomain(forName: suite) }
        return defaults
    }

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
        // saveBody, not upsert. upsert replaces the whole row, so an editor that rebuilds a Note
        // out of only the fields it is editing quietly drops the rest — which is how Android came
        // to unpin a note every time it was edited. This side carried the pinned flag across by
        // hand instead, which worked but kept the trap one copy-paste away. The rule belongs in
        // the shared module, and that is where saveBody keeps it.
        _ = repo.saveBody(id: noteId, body: body, now: now)
        refresh()
        return noteId
    }

    /// Pins or unpins a note; pinned notes sort to the top (shared logic).
    func togglePin(_ id: String) {
        repo.togglePin(id: id)
        refresh()
    }

    func delete(_ id: String) {
        repo.delete(id: id)
        refresh()
    }

    func parse(_ text: String) -> [MdBlock] {
        MarkdownParser.shared.parse(text: text)
    }
}
