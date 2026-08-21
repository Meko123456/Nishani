import SwiftUI

@main
struct NishaniApp: App {
    @StateObject private var store = Store()

    var body: some Scene {
        WindowGroup {
            NotesListView()
                .environmentObject(store)
        }
    }
}
