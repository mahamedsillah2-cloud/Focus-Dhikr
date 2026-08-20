import Foundation

#if canImport(FamilyControls)
import FamilyControls
import DeviceActivity
import ManagedSettings

/// The one place that talks to Apple's Screen Time APIs.
///
/// Three frameworks, each doing exactly one thing:
///
///  - **FamilyControls** asks for permission and shows the app picker. It hands
///    back opaque tokens: the app never learns that one of them is Instagram.
///  - **DeviceActivity** watches usage in the background and wakes an extension
///    when a threshold is crossed or a window opens. It reports *events*, not
///    minutes - there is no API that returns "45 minutes used today".
///  - **ManagedSettings** applies the shield. The system draws it, not us.
///
/// See docs/AUDITORIA_IOS.md for what each of those costs us.
@available(iOS 16.0, *)
public final class ScreenTimeController {

    public static let shared = ScreenTimeController()

    /// Named so every extension reaches the same store.
    public static let storeName = ManagedSettingsStore.Name("focusdhikr")

    public static let dailyActivity = DeviceActivityName("focusdhikr.daily")

    /// Past this many apps the app stops registering the intermediate 50 % and
    /// 80 % thresholds. Apple documents that `startMonitoring` throws when
    /// asked to watch too much, without saying where the line is, so the app
    /// stays well short of it and says so on screen.
    public static let detailedTrackingAppLimit = 8

    /// Apple's documented ceiling for `shield.applications`.
    public static let maxShieldedApps = 50

    private let store = ManagedSettingsStore(named: ScreenTimeController.storeName)
    private let center = DeviceActivityCenter()
    private let shared: SharedStore

    public init(shared: SharedStore = .shared) {
        self.shared = shared
    }

    // MARK: - Authorization

    public var authorizationStatus: AuthorizationStatus {
        AuthorizationCenter.shared.authorizationStatus
    }

    /// `.individual` rather than `.child`: this is the user limiting
    /// themselves, not a parent limiting someone else. The trade-off is real
    /// and documented - see the audit, section 1a.
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
        pruneOrphanedSettings(keeping: selection)
    }

    /// Limits and window memberships for apps that are no longer selected are
    /// dead weight that would come back to life if the user ever re-picked the
    /// same app months later with a limit they no longer remember setting.
    private func pruneOrphanedSettings(keeping selection: FamilyActivitySelection) {
        let live = Set(selection.applicationTokens.map(TokenKey.key(for:)))
        shared.limitMinutes = shared.limitMinutes.filter { live.contains($0.key) }
        shared.scheduleWindows = shared.scheduleWindows.map { window in
            var copy = window
            copy.appKeys = window.appKeys.filter { live.contains($0) }
            return copy
        }
    }

    public func tokensByKey() -> [String: ApplicationToken] {
        loadSelection().applicationTokens.reduce(into: [:]) { result, token in
            result[TokenKey.key(for: token)] = token
        }
    }

    public func limit(for key: String) -> Int {
        shared.limitMinutes[key] ?? shared.defaultLimitMinutes
    }

    public func setLimit(_ minutes: Int, for key: String) {
        var all = shared.limitMinutes
        all[key] = max(minutes, 1)
        shared.limitMinutes = all
    }

    // MARK: - Monitoring

    /// Registers everything DeviceActivity needs to wake the monitor extension:
    /// one daily activity carrying the per-app thresholds, plus one activity
    /// per schedule window.
    ///
    /// Windows are separate activities because a `DeviceActivitySchedule` is a
    /// single interval: the daily one runs the whole logical day, and 22:00 to
    /// 08:00 is a different interval that has to be told apart by name when the
    /// callback arrives.
    @discardableResult
    public func startMonitoring() throws -> Int {
        let selection = loadSelection()
        let tokens = selection.applicationTokens

        guard !tokens.isEmpty || !selection.categoryTokens.isEmpty else {
            stopMonitoring()
            return 0
        }

        center.stopMonitoring(center.activities)

        // A full logical day: the reset hour keeps a late night from being cut
        // in half by midnight and handed a fresh allowance at 00:00.
        let resetHour = shared.dayResetHour
        let daily = DeviceActivitySchedule(
            intervalStart: DateComponents(hour: resetHour, minute: 0),
            intervalEnd: DateComponents(hour: (resetHour + 23) % 24, minute: 59),
            repeats: true
        )

        let detailed = shared.detailedUsageTracking && tokens.count <= Self.detailedTrackingAppLimit
        var events: [DeviceActivityEvent.Name: DeviceActivityEvent] = [:]

        for token in tokens {
            let key = TokenKey.key(for: token)
            let limit = limit(for: key)
            for stage in UsageFloor.stages(forLimit: limit, detailed: detailed) {
                events[TokenKey.eventName(key: key, thresholdMinutes: stage)] = makeEvent(
                    applications: [token],
                    minutes: stage
                )
            }
        }

        // Categories get a single threshold: iOS reports the category as a
        // whole, so there is no per-app number to split it into.
        if !selection.categoryTokens.isEmpty {
            let minutes = max(shared.defaultLimitMinutes, 1)
            events[TokenKey.eventName(key: "categories", thresholdMinutes: minutes)] =
                DeviceActivityEvent(
                    categories: selection.categoryTokens,
                    threshold: DateComponents(minute: minutes)
                )
        }

        try center.startMonitoring(Self.dailyActivity, during: daily, events: events)

        var registered = 1
        for window in shared.scheduleWindows where window.enabled {
            // The system rejects very short intervals, and a window shorter
            // than a quarter of an hour is not a bedtime rule anyway.
            guard window.durationMinutes >= 15 else { continue }
            let schedule = DeviceActivitySchedule(
                intervalStart: DateComponents(
                    hour: window.startMinuteOfDay / 60,
                    minute: window.startMinuteOfDay % 60
                ),
                intervalEnd: DateComponents(
                    hour: window.endMinuteOfDay / 60,
                    minute: window.endMinuteOfDay % 60
                ),
                repeats: true
            )
            let name = DeviceActivityName(TokenKey.windowActivityRawValue(window.id))
            // One failing window must not take the daily limits down with it.
            do {
                try center.startMonitoring(name, during: schedule)
                registered += 1
            } catch {
                continue
            }
        }

        applyShields()
        return registered
    }

    /// `includesPastActivity` is what makes a limit added at 15:00 count the
    /// time already spent today instead of starting from zero. It arrived in
    /// iOS 17.4; before that, the first day after a change is simply generous.
    private func makeEvent(applications: Set<ApplicationToken>, minutes: Int) -> DeviceActivityEvent {
        let threshold = DateComponents(minute: minutes)
        if #available(iOS 17.4, *) {
            return DeviceActivityEvent(
                applications: applications,
                threshold: threshold,
                includesPastActivity: true
            )
        }
        return DeviceActivityEvent(applications: applications, threshold: threshold)
    }

    public func stopMonitoring() {
        center.stopMonitoring(center.activities)
    }

    // MARK: - Shielding

    /// Recomputes the whole shield set from the rules, rather than adding and
    /// removing tokens as events arrive.
    ///
    /// Incremental shielding is where these apps rot: a missed callback, a
    /// restart or a time-zone change leaves a stale token shielded forever and
    /// the user has no idea why. Recomputing means the worst case is a screen
    /// that is briefly wrong, not one that is permanently wrong.
    @discardableResult
    public func applyShields(now: Date = Date()) -> [String: ShieldReason] {
        let byKey = tokensByKey()
        let dayKey = DayBoundary.dayKey(for: now, resetHour: shared.dayResetHour)
        let clock = DayBoundary.clock(for: now)

        let reasons = ShieldDecision.evaluate(
            keys: Array(byKey.keys),
            limits: shared.limitMinutes,
            defaultLimit: shared.defaultLimitMinutes,
            usedMinutes: shared.usageFloorsToday(dayKey: dayKey),
            windows: shared.scheduleWindows,
            minuteOfDay: clock.minuteOfDay,
            isoDayOfWeek: clock.isoDayOfWeek,
            grantActive: shared.hasLiveGrant,
            grantedKey: shared.grantAppKey
        )

        let tokens = Set(reasons.keys.compactMap { byKey[$0] }.prefix(Self.maxShieldedApps))
        store.shield.applications = tokens.isEmpty ? nil : tokens
        shared.shieldReasons = reasons.mapValues(\.rawValue)

        applyRemovalPolicy()
        return reasons
    }

    /// Discipline mode's one genuinely hard setting. It denies removing *any*
    /// app on the device, which is why it is opt-in and off by default.
    private func applyRemovalPolicy() {
        let deny = shared.strictMode && shared.denyAppRemoval
        store.application.denyAppRemoval = deny ? true : nil
    }

    /// Lifts every shield for `minutes`. This is what the user earns by walking
    /// the whole gate: deliberately short, because the point was never to hand
    /// back the rest of the day.
    public func grantTemporaryAccess(minutes: Int, appKey: String? = nil) {
        shared.grantExpiry = Date().addingTimeInterval(TimeInterval(minutes * 60))
        shared.grantAppKey = appKey
        if appKey == nil {
            // The shield never told us which app this was, so the only honest
            // thing to do is let the user through everywhere for those minutes.
            store.shield.applications = nil
            store.shield.applicationCategories = nil
        } else {
            applyShields()
        }
    }

    public func clearGrantIfExpired() {
        guard let expiry = shared.grantExpiry, expiry <= Date() else { return }
        shared.grantExpiry = nil
        shared.grantAppKey = nil
        applyShields()
    }

    public func clearAllShields() {
        store.shield.applications = nil
        store.shield.applicationCategories = nil
        store.application.denyAppRemoval = nil
        shared.grantExpiry = nil
        shared.grantAppKey = nil
        shared.shieldReasons = [:]
    }
}
#endif
