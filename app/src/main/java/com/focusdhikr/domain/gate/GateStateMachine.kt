package com.focusdhikr.domain.gate

import com.focusdhikr.domain.model.GateOutcome
import com.focusdhikr.domain.model.IntentReason

/** Everything the user (or a clock tick) can do inside the gate. */
sealed interface GateEvent {
    /** The forward affordance: "Quiero entrar igualmente" / "Continuar". */
    data object Advance : GateEvent

    data class ChooseIntent(val reason: IntentReason) : GateEvent

    data class AnswerAlignment(val alignsWithToday: Boolean) : GateEvent

    /** One second of the wait elapsed. */
    data object Tick : GateEvent

    data class EditFreeText(val text: String) : GateEvent

    data class EditSentence(val text: String) : GateEvent

    /** The user chose not to go in. The whole app exists for this button. */
    data object TurnBack : GateEvent

    /** The user left the gate without answering (home button, screen off). */
    data object Abandon : GateEvent

    /** The user took the emergency path, which has its own separate friction. */
    data object EmergencyGranted : GateEvent
}

/**
 * Pure reducer for the progressive pause.
 *
 * No Android, no coroutines, no clock: [Tick] is injected from outside. That
 * makes the whole flow - including the escalating waits and the sentence match -
 * unit-testable on the JVM, which matters because this is the one piece that
 * absolutely must not misbehave at the moment the user is most impulsive.
 */
object GateStateMachine {

    fun initial(config: GateConfig): GateState = GateState(config = config)

    fun reduce(state: GateState, event: GateEvent): GateState {
        if (state.isResolved) return state

        return when (event) {
            is GateEvent.TurnBack -> state.copy(
                phase = GatePhase.RESOLVED,
                outcome = GateOutcome.TURNED_BACK,
            )

            is GateEvent.Abandon -> state.copy(
                phase = GatePhase.RESOLVED,
                outcome = GateOutcome.ABANDONED,
            )

            is GateEvent.EmergencyGranted -> state.copy(
                phase = GatePhase.RESOLVED,
                outcome = GateOutcome.EMERGENCY_ACCESS,
            )

            is GateEvent.ChooseIntent -> state.copy(intent = event.reason)

            is GateEvent.AnswerAlignment -> state.copy(alignsWithToday = event.alignsWithToday)

            is GateEvent.Tick ->
                if (state.phase == GatePhase.WAIT) {
                    state.copy(waitRemainingSeconds = (state.waitRemainingSeconds - 1).coerceAtLeast(0))
                } else {
                    state
                }

            is GateEvent.EditFreeText -> state.copy(freeText = event.text)

            is GateEvent.EditSentence -> state.copy(typedSentence = event.text)

            is GateEvent.Advance -> advance(state)
        }
    }

    private fun advance(state: GateState): GateState {
        if (!state.canAdvance) return state

        if (state.phase == GatePhase.DECIDE) {
            return state.copy(
                phase = GatePhase.RESOLVED,
                outcome = GateOutcome.CHOSE_TO_CONTINUE,
            )
        }

        val next = state.activePhases.getOrNull(state.activePhases.indexOf(state.phase) + 1)
            ?: return state.copy(phase = GatePhase.RESOLVED, outcome = GateOutcome.CHOSE_TO_CONTINUE)

        // Entering the wait always restarts its countdown, so backing out and
        // coming round again never shortens it.
        return if (next == GatePhase.WAIT) {
            state.copy(phase = next, waitRemainingSeconds = state.config.waitSeconds)
        } else {
            state.copy(phase = next)
        }
    }
}
