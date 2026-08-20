import Foundation
import SwiftUI

/// Drives one pass through the progressive pause.
///
/// The decision logic lives in `GateStateMachine`, which is pure and tested.
/// This only wires it to the clock, the shared store and the content libraries.
@MainActor
final class GateViewModel: ObservableObject {

    @Published private(set) var state: GateState
    @Published private(set) var appName: String
    /// What the user has used today and what they set, in their own numbers.
    @Published private(set) var usedLine: String
    /// When this app becomes available again, when iOS lets us know.
    @Published private(set) var availableLine: String
    @Published private(set) var reason: ShieldReason
    @Published private(set) var goal: Goal?
    @Published private(set) var dhikr: Dhikr?
    @Published private(set) var quran: QuranCitation?
    @Published private(set) var pauseLine: String
    @Published private(set) var waitLine: String
    @Published private(set) var purposeLine: String
    @Published var showEmergency = false
    @Published var emergencyReason = ""
    @Published private(set) var emergencyWaitRemaining = 0

    private let store: SharedStore
    /// Which app the earned minutes should apply to, when the shield told us.
    private(set) var grantAppKey: String?
    private var attempt: GateAttempt
    /// The furthest phase actually reached, for honest statistics.
    private var deepestPhase: GatePhase = .pause
    private var timer: Timer?
    private var emergencyTimer: Timer?

    var onResolved: ((GateOutcome, Int) -> Void)?

    convenience init(pending: SharedStore.PendingGate, store: SharedStore = .shared) {
        self.init(
            appName: pending.appName,
            appKey: pending.appKey,
            reason: pending.reason.flatMap(ShieldReason.init(rawValue:)) ?? .limitReached,
            store: store
        )
    }

    init(
        appName: String,
        appKey: String? = nil,
        reason: ShieldReason = .limitReached,
        store: SharedStore = .shared
    ) {
        self.store = store
        // The shield action extension is denied the app's name by iOS. On the
        // devices where the name is knowable at all, use it; elsewhere the
        // neutral wording is the whole truth.
        self.appName = AppNames.name(forKey: appKey, store: store) ?? appName
        self.reason = reason

        let now = Date()
        let dayKey = DayBoundary.dayKey(for: now, resetHour: store.dayResetHour)
        let attemptsToday = store.attemptsToday(dayKey: dayKey)
        let clock = DayBoundary.clock(for: now)

        // Phase 1 promised the user three numbers: what they used, what they
        // set, and when it comes back. iOS gives us the first only as a floor
        // and the third only for schedule windows, so both are worded as what
        // they are rather than dressed up as precision.
        if let appKey {
            let limit = store.limitMinutes[appKey] ?? store.defaultLimitMinutes
            self.usedLine = UsageFloor.describe(
                minutes: store.usageFloor(dayKey: dayKey, appKey: appKey),
                limit: limit
            )
            let minutesLeft = ShieldDecision.minutesUntilAllowed(
                key: appKey,
                windows: store.scheduleWindows,
                minuteOfDay: clock.minuteOfDay,
                isoDayOfWeek: clock.isoDayOfWeek
            )
            self.availableLine = minutesLeft.map {
                "Vuelve a estar disponible dentro de \(Durations.format(minutes: $0))"
            } ?? "Tu día se reinicia a las \(String(format: "%02d:00", store.dayResetHour))"
        } else {
            self.usedLine = ""
            self.availableLine = reason.isWindow
                ? "Estás dentro de una franja que tú fijaste"
                : "Tu día se reinicia a las \(String(format: "%02d:00", store.dayResetHour))"
        }
        self.grantAppKey = appKey
        let seed = abs(appName.hashValue &+ Int(now.timeIntervalSince1970))

        let config = GateConfig.forAttempt(
            strict: store.strictMode,
            attemptsToday: attemptsToday,
            inScheduleWindow: reason.isWindow || Self.insideWindow(store.scheduleWindows, at: now),
            sentence: store.acknowledgementSentence
        )
        self.state = GateStateMachine.initial(config)

        let goals = store.activeGoals
        self.goal = goals.isEmpty ? nil : goals[seed % goals.count]

        let depth = store.spiritualDepth
        self.dhikr = depth == "off" ? nil : {
            let pool = DhikrLibrary.enabled(store.enabledDhikrIds)
            return pool.isEmpty ? nil : pool[seed % pool.count]
        }()
        self.quran = depth == "full" ? QuranLibrary.pick(theme: .valueOfTime, seed: seed) : nil

        self.pauseLine = Reflections.pick(Reflections.pause, seed: seed)
        self.waitLine = Reflections.pick(Reflections.wait, seed: seed)
        self.purposeLine = Reflections.pick(Reflections.purpose, seed: seed)

        self.attempt = GateAttempt(
            dayKey: dayKey,
            startedAt: now,
            reachedPhase: GatePhase.pause.rawValue
        )
        store.recordAttempt(attempt)
    }

    var emergencyRemaining: Int {
        max(store.emergencyPerWeek - store.emergencyUsesThisWeek(), 0)
    }

    var showTranslations: Bool { store.showTranslations }

    func send(_ event: GateEvent) {
        let next = GateStateMachine.reduce(state, event)
        guard next != state else { return }

        let wasWaiting = state.phase == .wait
        state = next

        if let index = next.activePhases.firstIndex(of: next.phase),
           let deepest = next.activePhases.firstIndex(of: deepestPhase),
           index > deepest {
            deepestPhase = next.phase
        }

        if next.phase == .wait && !wasWaiting { startTimer() }
        if next.phase != .wait { stopTimer() }
        if next.isResolved { finish() }
    }

    /// The user left without answering: home, app switcher, a call.
    func abandon() {
        guard !state.isResolved else { return }
        send(.abandon)
    }

    private func startTimer() {
        stopTimer()
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self, self.state.phase == .wait else { return }
                guard self.state.waitRemainingSeconds > 0 else { return self.stopTimer() }
                self.send(.tick)
            }
        }
    }

    private func stopTimer() {
        timer?.invalidate()
        timer = nil
    }

    private func finish() {
        stopTimer()
        emergencyTimer?.invalidate()

        let outcome = state.outcome ?? .abandoned
        attempt.endedAt = Date()
        attempt.reachedPhase = deepestPhase.rawValue
        attempt.outcome = outcome.rawValue
        attempt.intentReason = state.intent?.rawValue
        attempt.writtenReason = store.keepWrittenReasons
            ? state.freeText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : state.freeText
            : nil
        store.recordAttempt(attempt)

        if outcome == .emergencyAccess {
            store.emergencyUses.append(Date())
        }
        store.pendingGate = nil

        onResolved?(outcome, state.config.grantMinutes)
    }

    // MARK: - Emergency path

    /// The way out. A blocker with no escape hatch is not disciplined, it is
    /// brittle: the first time it stops something that genuinely mattered, the
    /// user uninstalls it and loses everything.
    func beginEmergencyWait(seconds: Int = 30) {
        emergencyWaitRemaining = seconds
        emergencyTimer?.invalidate()
        emergencyTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                if self.emergencyWaitRemaining > 0 {
                    self.emergencyWaitRemaining -= 1
                } else {
                    self.emergencyTimer?.invalidate()
                }
            }
        }
    }

    func confirmEmergency() {
        guard emergencyRemaining > 0,
              emergencyWaitRemaining == 0,
              emergencyReason.trimmingCharacters(in: .whitespacesAndNewlines).count >= 5
        else { return }
        send(.emergencyGranted)
    }

    private static func insideWindow(_ windows: [ScheduleWindow], at date: Date) -> Bool {
        let clock = DayBoundary.clock(for: date)
        return windows.contains {
            $0.contains(minuteOfDay: clock.minuteOfDay, isoDayOfWeek: clock.isoDayOfWeek)
        }
    }
}
