import SwiftUI
import UIKit

/// The Home Screen quick actions, listed under UIApplicationShortcutItems in Info.plist. A long
/// press on the icon offers them, as the launcher's shortcuts do on Android.
enum QuickAction: String {
    case newNote = "io.github.meko123456.nishani.new-note"
}

/// Where a quick action waits for the list to take it. The action can be what launched the app,
/// so it arrives before any view exists and is held here until NotesListView takes it.
final class QuickActions: ObservableObject {
    static let shared = QuickActions()

    @Published var pending: QuickAction?

    /// False for an item this version doesn't know, which tells iOS it wasn't handled.
    func take(_ item: UIApplicationShortcutItem) -> Bool {
        guard let action = QuickAction(rawValue: item.type) else { return false }
        pending = action
        return true
    }
}

/// SwiftUI has no hook for quick actions. A quick action that launches the app comes with the
/// scene's connection options; one used while the app is running goes to the scene's delegate.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        if let item = options.shortcutItem { _ = QuickActions.shared.take(item) }
        let configuration = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        configuration.delegateClass = SceneDelegate.self
        return configuration
    }
}

final class SceneDelegate: NSObject, UIWindowSceneDelegate {
    func windowScene(
        _ windowScene: UIWindowScene,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        completionHandler(QuickActions.shared.take(shortcutItem))
    }
}
