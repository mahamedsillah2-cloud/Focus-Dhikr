import Foundation

/// The phases of the progressive pause, in order.
///
/// A direct port of the Kotlin `GatePhase` in the Android app, so the two
/// platforms behave identically. Every phase has a way forward: the app adds
/// seconds and attention, never a dead end.
public enum GatePhase: String, CaseIterable, Sendable {
    /// 1. What you decided, and what you have used.
    case pause
    /// 2. What were you about to do, and does it match today.
    case intent
    /// 3. A short wait with a dhikr.
    case wait
    /// 4. One of your own goals, in your own words.
    case purpose
    /// 5. Write, by hand, why.
    case write
    /// 6. Your decision, acknowledged and respected.
    case decide
    /// Terminal.
    case resolved
}

/// Why the user said they were opening the app, in phase 2.
public enum IntentReason: String, CaseIterable, Identifiable, Sendable {
    case concreteReason
    case bored
    case seekingDistraction
    case habit
    case dontKnow

    public var id: String { rawValue }

    public var labelEs: String {
        switch self {
        case .concreteReason: return "Tengo una razón concreta"
        case .bored: return "Estoy aburrido"
        case .seekingDistraction: return "Estoy buscando una distracción"
        case .habit: return "Lo he abierto por costumbre"
        case .dontKnow: return "No sé por qué lo he abierto"
        }
    }
}

/// How a run through the gate ended.
public enum GateOutcome: String, Sendable {
    /// The user turned back. This is the number the home screen celebrates.
    case turnedBack
    /// The user went through every phase and consciously chose to continue.
    case choseToContinue
    /// The user used the emergency path.
    case emergencyAccess
    /// The gate was shown but the user left without answering.
    case abandoned
}

/// How hard this particular pass through the gate should be.
public struct GateConfig: Equatable, Sendable {
    public var strict: Bool
    public var waitSeconds: Int
    public var requireIntent: Bool
    public var requirePurpose: Bool
    public var requireWriting: Bool
    public var minFreeTextChars: Int
    public var acknowledgementSentence: String
    public var grantMinutes: Int

    public static let defaultSentence =
        "Reconozco que estoy eligiendo dedicar este tiempo a una distracción que yo mismo había decidido limitar."

    public static let baseWaitSeconds = 10
    public static let strictBaseWaitSeconds = 25
    public static let maxWaitSeconds = 90

    public init(
        strict: Bool,
        waitSeconds: Int,
        requireIntent: Bool,
        requirePurpose: Bool,
        requireWriting: Bool,
        minFreeTextChars: Int,
        acknowledgementSentence: String,
        grantMinutes: Int
    ) {
        self.strict = strict
        self.waitSeconds = waitSeconds
        self.requireIntent = requireIntent
        self.requirePurpose = requirePurpose
        self.requireWriting = requireWriting
        self.minFreeTextChars = minFreeTextChars
        self.acknowledgementSentence = acknowledgementSentence
        self.grantMinutes = grantMinutes
    }

    /// Friction scales with how many times you have already been here today,
    /// rather than being the same wall every time.
    ///
    /// - Parameters:
    ///   - attemptsToday: passes through the gate for this app earlier today.
    ///   - inScheduleWindow: a window like 22:00-08:00 is active.
    public static func forAttempt(
        strict: Bool,
        attemptsToday: Int,
        inScheduleWindow: Bool = false,
        sentence: String = defaultSentence
    ) -> GateConfig {
        let hard = strict || inScheduleWindow
        let base = hard ? strictBaseWaitSeconds : baseWaitSeconds
        let step = hard ? 10 : 5
        let wait = min(base + step * max(attemptsToday, 0), maxWaitSeconds)

        return GateConfig(
            strict: hard,
            waitSeconds: wait,
            requireIntent: true,
            // In the relaxed mode the goal reminder only appears once you are
            // clearly insisting, so a single deliberate visit stays short.
            requirePurpose: hard || attemptsToday >= 1,
            requireWriting: hard || attemptsToday >= 2,
            minFreeTextChars: hard ? 25 : 12,
            acknowledgementSentence: sentence,
            grantMinutes: hard ? 2 : 5
        )
    }
}

/// Immutable state of one pass through the gate.
public struct GateState: Equatable, Sendable {
    public var config: GateConfig
    public var phase: GatePhase
    public var intent: IntentReason?
    /// Answer to "does this bring you closer to what you want today?"
    public var alignsWithToday: Bool?
    public var waitRemainingSeconds: Int
    public var freeText: String
    public var typedSentence: String
    public var outcome: GateOutcome?

    public init(config: GateConfig) {
        self.config = config
        self.phase = .pause
        self.intent = nil
        self.alignsWithToday = nil
        self.waitRemainingSeconds = config.waitSeconds
        self.freeText = ""
        self.typedSentence = ""
        self.outcome = nil
    }

    /// Phases actually used for this attempt, in order.
    public var activePhases: [GatePhase] {
        var phases: [GatePhase] = [.pause]
        if config.requireIntent { phases.append(.intent) }
        phases.append(.wait)
        if config.requirePurpose { phases.append(.purpose) }
        if config.requireWriting { phases.append(.write) }
        phases.append(.decide)
        return phases
    }

    public var stepNumber: Int {
        max((activePhases.firstIndex(of: phase).map { $0 + 1 }) ?? 1, 1)
    }

    public var stepCount: Int { activePhases.count }

    public var isResolved: Bool { phase == .resolved }

    /// Whether the forward affordance should be enabled right now.
    public var canAdvance: Bool {
        switch phase {
        case .pause:
            return true
        case .intent:
            return intent != nil && alignsWithToday != nil
        case .wait:
            return waitRemainingSeconds <= 0
        case .purpose:
            return true
        case .write:
            return freeText.trimmingCharacters(in: .whitespacesAndNewlines).count >= config.minFreeTextChars
        case .decide:
            return SentenceMatcher.matches(typedSentence, config.acknowledgementSentence)
        case .resolved:
            return false
        }
    }
}

/// Compares what the user typed against the acknowledgement sentence.
///
/// The point is to make you slow down and read your own words, not to fight a
/// phone keyboard. Accents, case, punctuation and repeated whitespace are all
/// forgiven; the words themselves are not.
public enum SentenceMatcher {

    public static func normalize(_ input: String) -> String {
        let folded = input.folding(
            options: [.diacriticInsensitive, .caseInsensitive],
            locale: Locale(identifier: "es_ES")
        )
        let stripped = folded.unicodeScalars
            .map { CharacterSet.punctuationCharacters.contains($0) || $0 == "¿" || $0 == "¡" ? " " : Character($0) }
        return String(stripped)
            .split(whereSeparator: { $0.isWhitespace })
            .joined(separator: " ")
    }

    public static func matches(_ typed: String, _ expected: String) -> Bool {
        normalize(typed) == normalize(expected)
    }

    /// 0...1, how much of `expected` has been typed correctly so far.
    public static func progress(_ typed: String, _ expected: String) -> Double {
        let target = Array(normalize(expected))
        guard !target.isEmpty else { return 1 }
        let current = Array(normalize(typed))
        var shared = 0
        while shared < current.count, shared < target.count, current[shared] == target[shared] {
            shared += 1
        }
        return min(max(Double(shared) / Double(target.count), 0), 1)
    }
}
