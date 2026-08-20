import Foundation

/// Everything the user (or a clock tick) can do inside the gate.
public enum GateEvent: Equatable, Sendable {
    /// The forward affordance: "Quiero entrar igualmente" / "Continuar".
    case advance
    case chooseIntent(IntentReason)
    case answerAlignment(Bool)
    /// One second of the wait elapsed.
    case tick
    case editFreeText(String)
    case editSentence(String)
    /// The user chose not to go in. The whole app exists for this button.
    case turnBack
    /// The user left the gate without answering.
    case abandon
    /// The user took the emergency path, which has its own separate friction.
    case emergencyGranted
}

/// Pure reducer for the progressive pause.
///
/// No UIKit, no SwiftUI, no clock: `tick` is injected from outside. That makes
/// the whole flow - including the escalating waits and the sentence match -
/// unit-testable, which matters because this is the one piece that absolutely
/// must not misbehave at the moment the user is most impulsive.
///
/// Mirrors `GateStateMachine.kt` in the Android app one-for-one; the shared
/// test suite in `ios/Tests` is a port of the Kotlin one for that reason.
public enum GateStateMachine {

    public static func initial(_ config: GateConfig) -> GateState {
        GateState(config: config)
    }

    public static func reduce(_ state: GateState, _ event: GateEvent) -> GateState {
        guard !state.isResolved else { return state }
        var next = state

        switch event {
        case .turnBack:
            next.phase = .resolved
            next.outcome = .turnedBack

        case .abandon:
            next.phase = .resolved
            next.outcome = .abandoned

        case .emergencyGranted:
            next.phase = .resolved
            next.outcome = .emergencyAccess

        case let .chooseIntent(reason):
            next.intent = reason

        case let .answerAlignment(aligns):
            next.alignsWithToday = aligns

        case .tick:
            guard state.phase == .wait else { return state }
            next.waitRemainingSeconds = max(state.waitRemainingSeconds - 1, 0)

        case let .editFreeText(text):
            next.freeText = text

        case let .editSentence(text):
            next.typedSentence = text

        case .advance:
            return advance(state)
        }

        return next
    }

    private static func advance(_ state: GateState) -> GateState {
        guard state.canAdvance else { return state }
        var next = state

        if state.phase == .decide {
            next.phase = .resolved
            next.outcome = .choseToContinue
            return next
        }

        let phases = state.activePhases
        guard
            let index = phases.firstIndex(of: state.phase),
            index + 1 < phases.count
        else {
            next.phase = .resolved
            next.outcome = .choseToContinue
            return next
        }

        let upcoming = phases[index + 1]
        next.phase = upcoming
        // Entering the wait always restarts its countdown, so backing out and
        // coming round again never shortens it.
        if upcoming == .wait {
            next.waitRemainingSeconds = state.config.waitSeconds
        }
        return next
    }
}
