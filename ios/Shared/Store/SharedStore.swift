import Foundation

#if canImport(ManagedSettings)
import ManagedSettings
#endif

/// Settings and state shared between the app and its extensions.
///
/// The app, the DeviceActivity monitor and the two shield extensions are
/// separate processes. An App Group container is the only thing they can all
/// see, so every piece of state that crosses that boundary lives here.
///
/// Nothing here ever leaves the device: there is no networking code in this
/// target and no server to send it to.
public final class SharedStore: @unchecked Sendable {

    /// Must match the App Group in every target's entitlements.
    public static let appGroupID = "group.com.focusdhikr.shared"

    public static let shared = SharedStore()

    private let defaults: UserDefaults

    public init(defaults: UserDefaults? = nil) {
        // Falling back to .standard keeps previews and unit tests working on a
        // machine with no provisioning profile for the App Group.
        self.defaults = defaults ?? UserDefaults(suiteName: SharedStore.appGroupID) ?? .standard
    }

    private enum Key {
        static let selection = "familyActivitySelection"
        static let limits = "appLimitMinutes"
        static let defaultLimit = "defaultLimitMinutes"
        static let goals = "goals"
        static let windows = "scheduleWindows"
        static let attempts = "attempts"
        static let attemptCounts = "attemptCountsByDay"
        static let strictMode = "strictMode"
        static let spiritualDepth = "spiritualDepth"
        static let enabledDhikr = "enabledDhikrIds"
        static let showTranslations = "showTranslations"
        static let acknowledgementSentence = "acknowledgementSentence"
        static let dayResetHour = "dayResetHour"
        static let keepWrittenReasons = "keepWrittenReasons"
        static let onboardingComplete = "onboardingComplete"
        static let pendingGate = "pendingGate"
        static let grantExpiry = "grantExpiry"
        static let grantAppKey = "grantAppKey"
        static let emergencyUses = "emergencyUses"
        static let emergencyPerWeek = "emergencyPerWeek"
        static let usageFloors = "usageFloorsByDay"
        static let reminders = "reminders"
        static let denyAppRemoval = "denyAppRemoval"
        static let lastShieldReasons = "lastShieldReasons"
        static let detailedUsage = "detailedUsageTracking"
        static let appNames = "appDisplayNames"
    }

    // MARK: - Codable helpers

    private func decode<T: Decodable>(_ type: T.Type, _ key: String) -> T? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }

    private func encode<T: Encodable>(_ value: T, _ key: String) {
        guard let data = try? JSONEncoder().encode(value) else { return }
        defaults.set(data, forKey: key)
    }

    // MARK: - Selected apps

    #if canImport(FamilyControls)
    /// The opaque set of apps the user picked.
    ///
    /// iOS deliberately never tells the app which apps these are - the tokens
    /// carry no name or icon. Only the shield extension, at block time, learns
    /// the display name. See docs/LIMITES_PLATAFORMA.md.
    public var selectionData: Data? {
        get { defaults.data(forKey: Key.selection) }
        set { defaults.set(newValue, forKey: Key.selection) }
    }
    #endif

    // MARK: - Limits

    /// Per-app daily limit in minutes, keyed by the token's stable hash.
    public var limitMinutes: [String: Int] {
        get { decode([String: Int].self, Key.limits) ?? [:] }
        set { encode(newValue, Key.limits) }
    }

    public var defaultLimitMinutes: Int {
        get { defaults.object(forKey: Key.defaultLimit) as? Int ?? 60 }
        set { defaults.set(newValue, forKey: Key.defaultLimit) }
    }

    // MARK: - Goals and windows

    public var goals: [Goal] {
        get { decode([Goal].self, Key.goals) ?? [] }
        set { encode(newValue, Key.goals) }
    }

    public var scheduleWindows: [ScheduleWindow] {
        get { decode([ScheduleWindow].self, Key.windows) ?? [] }
        set { encode(newValue, Key.windows) }
    }

    public var activeGoals: [Goal] { goals.filter(\.active) }

    // MARK: - Gate history

    public var attempts: [GateAttempt] {
        get { decode([GateAttempt].self, Key.attempts) ?? [] }
        set { encode(newValue, Key.attempts) }
    }

    public func recordAttempt(_ attempt: GateAttempt) {
        var all = attempts
        if let index = all.firstIndex(where: { $0.id == attempt.id }) {
            all[index] = attempt
        } else {
            all.append(attempt)
        }
        // Keep the file small; the stats screens only look back 30 days.
        let cutoff = Calendar.current.date(byAdding: .day, value: -90, to: Date()) ?? .distantPast
        attempts = all.filter { $0.startedAt >= cutoff }
    }

    /// Passes through the gate for `dayKey`, used to escalate friction.
    public func attemptsToday(dayKey: String) -> Int {
        attempts.filter { $0.dayKey == dayKey }.count
    }

    // MARK: - Settings

    public var strictMode: Bool {
        get { defaults.bool(forKey: Key.strictMode) }
        set { defaults.set(newValue, forKey: Key.strictMode) }
    }

    public var spiritualDepth: String {
        get { defaults.string(forKey: Key.spiritualDepth) ?? "subtle" }
        set { defaults.set(newValue, forKey: Key.spiritualDepth) }
    }

    public var enabledDhikrIds: Set<String> {
        get {
            let stored = defaults.stringArray(forKey: Key.enabledDhikr) ?? []
            return stored.isEmpty ? DhikrLibrary.defaultEnabledIds : Set(stored)
        }
        set { defaults.set(Array(newValue), forKey: Key.enabledDhikr) }
    }

    public var showTranslations: Bool {
        get { defaults.object(forKey: Key.showTranslations) as? Bool ?? true }
        set { defaults.set(newValue, forKey: Key.showTranslations) }
    }

    public var acknowledgementSentence: String {
        get {
            let stored = defaults.string(forKey: Key.acknowledgementSentence) ?? ""
            return stored.isEmpty ? GateConfig.defaultSentence : stored
        }
        set { defaults.set(newValue, forKey: Key.acknowledgementSentence) }
    }

    public var dayResetHour: Int {
        get { defaults.object(forKey: Key.dayResetHour) as? Int ?? DayBoundary.defaultResetHour }
        set { defaults.set(min(max(newValue, 0), 23), forKey: Key.dayResetHour) }
    }

    public var keepWrittenReasons: Bool {
        get { defaults.object(forKey: Key.keepWrittenReasons) as? Bool ?? true }
        set { defaults.set(newValue, forKey: Key.keepWrittenReasons) }
    }

    public var onboardingComplete: Bool {
        get { defaults.bool(forKey: Key.onboardingComplete) }
        set { defaults.set(newValue, forKey: Key.onboardingComplete) }
    }

    public var emergencyPerWeek: Int {
        get { defaults.object(forKey: Key.emergencyPerWeek) as? Int ?? 3 }
        set { defaults.set(newValue, forKey: Key.emergencyPerWeek) }
    }

    public var emergencyUses: [Date] {
        get { decode([Date].self, Key.emergencyUses) ?? [] }
        set { encode(newValue, Key.emergencyUses) }
    }

    public func emergencyUsesThisWeek(now: Date = Date()) -> Int {
        let cutoff = now.addingTimeInterval(-7 * 24 * 60 * 60)
        return emergencyUses.filter { $0 >= cutoff }.count
    }

    // MARK: - The shield handoff

    /// A block the user asked to reflect on, waiting for the app to pick it up.
    ///
    /// The shield extension cannot show the six-phase flow - iOS only gives it
    /// a title, a subtitle and two buttons - so it records the request here and
    /// the app runs the real gate when it next opens.
    public struct PendingGate: Codable, Equatable, Sendable {
        public var appName: String
        public var requestedAt: Date
        /// `ShieldReason.rawValue`, when the shield knew it.
        public var reason: String?
        /// The token key of the shielded app. The shield *action* extension
        /// receives the token even though it is denied the display name, so
        /// this is how the gate knows which limit it is talking about.
        public var appKey: String?

        public init(
            appName: String,
            requestedAt: Date = Date(),
            reason: String? = nil,
            appKey: String? = nil
        ) {
            self.appName = appName
            self.requestedAt = requestedAt
            self.reason = reason
            self.appKey = appKey
        }

        /// Requests go stale: picking up a pause you asked for yesterday would
        /// be confusing rather than helpful.
        public var isFresh: Bool {
            Date().timeIntervalSince(requestedAt) < 15 * 60
        }
    }

    public var pendingGate: PendingGate? {
        get { decode(PendingGate.self, Key.pendingGate) }
        set {
            if let newValue { encode(newValue, Key.pendingGate) }
            else { defaults.removeObject(forKey: Key.pendingGate) }
        }
    }

    /// When the current temporary access ends.
    public var grantExpiry: Date? {
        get { defaults.object(forKey: Key.grantExpiry) as? Date }
        set { defaults.set(newValue, forKey: Key.grantExpiry) }
    }

    /// Which app the live grant belongs to. Nil means "the shield could not
    /// tell us", and the grant then lifts everything.
    public var grantAppKey: String? {
        get { defaults.string(forKey: Key.grantAppKey) }
        set { defaults.set(newValue, forKey: Key.grantAppKey) }
    }

    public var hasLiveGrant: Bool {
        guard let expiry = grantExpiry else { return false }
        return expiry > Date()
    }

    // MARK: - Per-app usage floors

    /// "How many minutes has this app had today", as far as iOS will say.
    ///
    /// Written only by the monitor extension, when a threshold is crossed, and
    /// therefore always a *floor*: the real number is somewhere between this
    /// and the next threshold. Every screen that shows it says "al menos".
    public var usageFloors: [String: Int] {
        get { decode([String: Int].self, Key.usageFloors) ?? [:] }
        set { encode(newValue, Key.usageFloors) }
    }

    public func usageFloor(dayKey: String, appKey: String) -> Int {
        usageFloors[TokenKey.usageKey(dayKey: dayKey, appKey: appKey)] ?? 0
    }

    /// Thresholds can arrive out of order after a device restart, so this only
    /// ever moves the floor up.
    public func recordUsageFloor(dayKey: String, appKey: String, minutes: Int) {
        var all = usageFloors
        let key = TokenKey.usageKey(dayKey: dayKey, appKey: appKey)
        guard minutes > (all[key] ?? 0) else { return }
        all[key] = minutes
        // Two months is more than the 30-day screen needs and keeps the
        // extension's tiny memory budget out of trouble.
        let horizon = Set(DayBoundary.recentDayKeys(count: 60, resetHour: dayResetHour))
        usageFloors = all.filter { entry in
            horizon.contains(String(entry.key.split(separator: "|").first ?? ""))
        }
    }

    public func usageFloorsToday(dayKey: String) -> [String: Int] {
        let prefix = "\(dayKey)|"
        return usageFloors.reduce(into: [String: Int]()) { result, entry in
            guard entry.key.hasPrefix(prefix) else { return }
            result[String(entry.key.dropFirst(prefix.count))] = entry.value
        }
    }

    /// Whether to register the intermediate 50 % and 80 % thresholds.
    ///
    /// Off with many apps selected: `startMonitoring` throws when asked to
    /// watch too much at once, and Apple does not document where the ceiling
    /// is, so the app gives up resolution rather than monitoring.
    public var detailedUsageTracking: Bool {
        get { defaults.object(forKey: Key.detailedUsage) as? Bool ?? true }
        set { defaults.set(newValue, forKey: Key.detailedUsage) }
    }

    /// Token key to display name, on the devices where iOS 26.4 allows it.
    /// Empty everywhere else, and every screen is written to cope with that.
    public var appNames: [String: String] {
        get { decode([String: String].self, Key.appNames) ?? [:] }
        set { encode(newValue, Key.appNames) }
    }

    // MARK: - Why an app is shielded

    /// Written by whoever last applied the shields, read by the gate so it can
    /// say "tu franja de 22:00 a 08:00" instead of a generic sentence.
    public var shieldReasons: [String: String] {
        get { decode([String: String].self, Key.lastShieldReasons) ?? [:] }
        set { encode(newValue, Key.lastShieldReasons) }
    }

    // MARK: - Reminders

    public var reminders: [Reminder] {
        get { decode([Reminder].self, Key.reminders) ?? [] }
        set { encode(newValue, Key.reminders) }
    }

    // MARK: - Discipline mode extras

    /// Opt-in, and off by default: it blocks removing *any* app on the device,
    /// not only the limited ones.
    public var denyAppRemoval: Bool {
        get { defaults.bool(forKey: Key.denyAppRemoval) }
        set { defaults.set(newValue, forKey: Key.denyAppRemoval) }
    }

    // MARK: - Privacy

    public func forgetWrittenReasons() {
        attempts = attempts.map { attempt in
            var copy = attempt
            copy.writtenReason = nil
            return copy
        }
    }

    public func eraseEverything() {
        [
            Key.selection, Key.limits, Key.defaultLimit, Key.goals, Key.windows,
            Key.attempts, Key.attemptCounts, Key.strictMode, Key.spiritualDepth,
            Key.enabledDhikr, Key.showTranslations, Key.acknowledgementSentence,
            Key.dayResetHour, Key.keepWrittenReasons, Key.onboardingComplete,
            Key.pendingGate, Key.grantExpiry, Key.emergencyUses, Key.emergencyPerWeek,
            Key.usageFloors, Key.reminders, Key.denyAppRemoval, Key.lastShieldReasons,
            Key.detailedUsage, Key.grantAppKey, Key.appNames,
        ].forEach(defaults.removeObject(forKey:))
    }
}
