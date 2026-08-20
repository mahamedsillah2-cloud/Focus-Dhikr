import Foundation

#if canImport(FamilyControls)
import FamilyControls
import DeviceActivity
import ManagedSettings

/// The one place that talks to Apple's Screen Time APIs.
///
/// Three frameworks are involved and they each do exactly one thing:
///
///  - **FamilyControls** asks the user for permission and shows the app picker.
///    It hands back opaque tokens: the app never learns that one of them is
///    Instagram.
///  - **DeviceActivity** watches usage in the background and tells an extension
///    when a threshold is crossed. It reports *events*, not minutes - there is
///    no API that returns "45 minutes used today".
///  - **ManagedSettings** applies the shield. The system draws it, not us.
///
/// See docs/LIMITES_PLATAFORMA.md for what this means for the six-phase flow.
@available(iOS 16.0, *)
public final class ScreenTimeController {

    public static let shared = ScreenTimeController()

    /// Named so the monitor extension can reach the same store.
    public static let storeName = ManagedSettingsStore.Name("focusdhikr")

    public static let activityName = DeviceActivityName("focusdhikr.daily")

    private let store = ManagedSettingsStore(named: ScreenTimeController.storeName)
    private let center = DeviceActivityCenter()
    private let shared = SharedStore.shared

    public init() {}

    // MARK: - Authorization

    public var authorizationStatus: AuthorizationStatus {
        AuthorizationCenter.shared.authorizationStatus
    }

    /// Requests Screen Time permission for this device.
    ///
    /// `.individual` rather than `.child`: this is the user limiting themselves,
    /// not a parent limiting someone else.
    public func requestAuthorization() async throws {
        try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
    }

    // MARK: - Selection

    public func loadSelection() -> FamilyActivitySelection {
        guard
            let data = shared.selectionData,
            let selection = try? JSONDecoder().decode(FamilyActivitySelection.self, from: data)
        else { return FamilyActivitySelection() }
        return selection
    }

    public func saveSelection(_ selection: FamilyActivitySelection) {
        shared.selectionData = try? JSONEncoder().encode(selection)
    }

    // MARK: - Monitoring

    /// Starts (or restarts) the daily schedule and the per-app thresholds.
    ///
    /// One `DeviceActivityEvent` per app, because a single event covering all of
    /// them would fire once for the whole group and could not tell Instagram's
    /// hour from TikTok's half hour.
    public func startMonitoring() throws {
        let selection = loadSelection()
        guard !selection.applicationTokens.isEmpty || !selection.categoryTokens.isEmpty else {
            stopMonitoring()
            return
        }

        // A full logical day, honouring the configurable reset hour so a late
        // night is not split in two by midnight.
        let resetHour = shared.dayResetHour
        let schedule = DeviceActivitySchedule(
            intervalStart: DateComponents(hour: resetHour, minute: 0),
            intervalEnd: DateComponents(hour: (resetHour + 23) % 24, minute: 59),
            repeats: true
        )

        var events: [DeviceActivityEvent.Name: DeviceActivityEvent] = [:]
        let limits = shared.limitMinutes

        for token in selection.applicationTokens {
            let key = Self.eventName(for: token)
            let minutes = limits[key.rawValue] ?? shared.defaultLimitMinutes
            events[key] = DeviceActivityEvent(
                applications: [token],
                threshold: DateComponents(minute: max(minutes, 1))
            )
        }

        if !selection.categoryTokens.isEmpty {
            events[DeviceActivityEvent.Name("categories")] = DeviceActivityEvent(
                categories: selection.categoryTokens,
                threshold: DateComponents(minute: max(shared.defaultLimitMinutes, 1))
            )
        }

        center.stopMonitoring([Self.activityName])
        try center.startMonitoring(Self.activityName, during: schedule, events: events)
    }

    public func stopMonitoring() {
        center.stopMonitoring([Self.activityName])
    }

    /// Stable per-token key, used both as the event name and as the limits key.
    ///
    /// Tokens are opaque but `Hashable` and `Codable`, so their encoded form is
    /// the only identifier available to us.
    public static func eventName(for token: ApplicationToken) -> DeviceActivityEvent.Name {
        let data = (try? JSONEncoder().encode(token)) ?? Data()
        return DeviceActivityEvent.Name("app.\(data.base64EncodedString().prefix(40))")
    }

    // MARK: - Shielding

    public func shieldAll() {
        let selection = loadSelection()
        store.shield.applications = selection.applicationTokens.isEmpty
            ? nil
            : selection.applicationTokens
        store.shield.applicationCategories = selection.categoryTokens.isEmpty
            ? nil
            : .specific(selection.categoryTokens)
    }

    public func shield(applications tokens: Set<ApplicationToken>) {
        store.shield.applications = tokens.isEmpty ? nil : tokens
    }

    /// Lifts every shield for `minutes`, then the monitor re-applies it.
    ///
    /// This is what the user earns by walking the whole gate. It is deliberately
    /// short: the point was never to hand back the rest of the day.
    public func grantTemporaryAccess(minutes: Int) {
        store.shield.applications = nil
        store.shield.applicationCategories = nil
        shared.grantExpiry = Date().addingTimeInterval(TimeInterval(minutes * 60))
    }

    public func clearGrantIfExpired() {
        guard let expiry = shared.grantExpiry, expiry <= Date() else { return }
        shared.grantExpiry = nil
        shieldAll()
    }

    public func clearAllShields() {
        store.shield.applications = nil
        store.shield.applicationCategories = nil
        shared.grantExpiry = nil
    }
}
#endif
