import DeviceActivity
import FamilyControls
import ManagedSettings
import Foundation

/// Applies the shield when a limit is reached.
///
/// This runs in its own process, woken by the system, with a very small memory
/// budget (around 6 MB) and no UI whatsoever. So it does one thing: turn the
/// shield on. Everything the user sees afterwards is the system's shield, and
/// everything they *do* happens back in the app.
class DeviceActivityMonitorExtension: DeviceActivityMonitor {

    private let store = ManagedSettingsStore(named: ManagedSettingsStore.Name("focusdhikr"))
    private let shared = SharedStore.shared

    /// A per-app threshold was crossed: shield that app.
    override func eventDidReachThreshold(
        _ event: DeviceActivityEvent.Name,
        activity: DeviceActivityName
    ) {
        super.eventDidReachThreshold(event, activity: activity)

        // A grant the user earned by walking the whole gate is honoured until it
        // expires; re-shielding here would undo it a second after it was given.
        if let expiry = shared.grantExpiry, expiry > Date() { return }

        guard
            let data = shared.selectionData,
            let selection = try? JSONDecoder().decode(FamilyActivitySelection.self, from: data)
        else { return }

        if event.rawValue == "categories" {
            store.shield.applicationCategories = selection.categoryTokens.isEmpty
                ? nil
                : .specific(selection.categoryTokens)
            return
        }

        // Match the token this event was created for. Tokens are opaque, so the
        // encoded form is the only thing we can compare on.
        let matching = selection.applicationTokens.filter { token in
            ScreenTimeEventNaming.name(for: token) == event.rawValue
        }
        let existing = store.shield.applications ?? []
        store.shield.applications = existing.union(matching.isEmpty ? selection.applicationTokens : matching)
    }

    /// A new logical day started: clear yesterday's shields and grants.
    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)
        store.shield.applications = nil
        store.shield.applicationCategories = nil
        shared.grantExpiry = nil
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        store.shield.applications = nil
        store.shield.applicationCategories = nil
    }
}

/// Token naming shared with the app, duplicated here rather than importing the
/// full controller: this extension must stay tiny.
enum ScreenTimeEventNaming {
    static func name(for token: ApplicationToken) -> String {
        let data = (try? JSONEncoder().encode(token)) ?? Data()
        return "app.\(data.base64EncodedString().prefix(40))"
    }
}
