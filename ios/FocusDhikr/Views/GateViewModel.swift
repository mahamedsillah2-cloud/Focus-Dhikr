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
    private var attempt: GateAttempt
    /// The furthest phase actually reached, for honest statistics.
    private var deepestPhase: GatePhase = .pause
    private var timer: Timer?
    private var emergencyTimer: Timer?

    var onResolved: ((GateOutcome, Int) -> Void)?

    init(appName: String, store: SharedStore = .shared) {
        self.store = store
        self.appName = appName

        let now = Date()
        let dayKey = DayBoundary.dayKey(for: now, resetHour: store.dayResetHour)
        let attemptsToday = store.attemptsToday(dayKey: dayKey)
        let seed = abs(appName.hashValue &+ Int(now.timeIntervalSince1970))

        let config = GateConfig.forAttempt(
            strict: store.strictMode,
            attemptsToday: attemptsToday,
            inScheduleWindow: Self.insideWindow(store.scheduleWindows, at: now),
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
        let parts = Calendar.current.dateComponents([.hour, .minute, .weekday], from: date)
        let minuteOfDay = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
        // Calendar.weekday is 1 = Sunday; the model uses ISO 1 = Monday.
        let iso = ((parts.weekday ?? 1) + 5) % 7 + 1
        return windows.contains { $0.contains(minuteOfDay: minuteOfDay, isoDayOfWeek: iso) }
    }
}
