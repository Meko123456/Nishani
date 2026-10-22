import SwiftUI

@main
struct NishaniApp: App {
    /// Takes the Home Screen quick actions; see QuickActions.swift.
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @StateObject private var store = Store()

    var body: some Scene {
        WindowGroup {
            NotesListView()
                .environmentObject(store)
                .environmentObject(QuickActions.shared)
        }
    }
}
