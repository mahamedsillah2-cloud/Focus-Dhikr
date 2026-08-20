package com.focusdhikr.ui.gate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusdhikr.AppGraph
import com.focusdhikr.content.Dhikr
import com.focusdhikr.content.DhikrLibrary
import com.focusdhikr.content.HadithCitation
import com.focusdhikr.content.HadithLibrary
import com.focusdhikr.content.QuranCitation
import com.focusdhikr.content.QuranLibrary
import com.focusdhikr.content.Reflections
import com.focusdhikr.content.Theme
import com.focusdhikr.data.prefs.Settings
import com.focusdhikr.data.prefs.SpiritualDepth
import com.focusdhikr.domain.gate.GateConfig
import com.focusdhikr.domain.gate.GateEvent
import com.focusdhikr.domain.gate.GatePhase
import com.focusdhikr.domain.gate.GateState
import com.focusdhikr.domain.gate.GateStateMachine
import com.focusdhikr.domain.model.GateOutcome
import com.focusdhikr.domain.model.Goal
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the gate screen needs, in one snapshot. */
data class GateUiState(
    val loading: Boolean = true,
    val appLabel: String = "",
    val packageName: String = "",
    val usedMillis: Long = 0,
    val limitMillis: Long = 0,
    val reason: String = GateActivity.REASON_LIMIT,
    val windowLabel: String = "",
    val gate: GateState? = null,
    val settings: Settings = Settings(),
    val goal: Goal? = null,
    val dhikr: Dhikr? = null,
    val quran: QuranCitation? = null,
    val hadith: HadithCitation? = null,
    val pauseLine: String = "",
    val waitLine: String = "",
    val purposeLine: String = "",
    val turnedBackLine: String = "",
    val emergencyRemaining: Int = 0,
    val emergencyReason: String = "",
    val emergencyWaitRemaining: Int = 0,
    val showEmergency: Boolean = false,
    /** Set once the run has ended; the Activity reacts and finishes. */
    val resolution: GateOutcome? = null,
    val grantedMillis: Long = 0,
)

/**
 * Drives one pass through the gate.
 *
 * The decision logic itself lives in [GateStateMachine], which is pure and
 * tested. This class only wires it to the clock, the database and the content
 * libraries.
 */
class GateViewModel(application: Application) : AndroidViewModel(application) {

    private val graph = AppGraph.get(application)
    private val repository = graph.repository

    private val _state = MutableStateFlow(GateUiState())
    val state: StateFlow<GateUiState> = _state.asStateFlow()

    private var attemptId: Long = 0

    /** The furthest phase actually reached, for honest statistics. */
    private var deepestPhase: GatePhase = GatePhase.PAUSE
    private var ticker: Job? = null
    private var emergencyTicker: Job? = null

    /** Stable per-attempt seed, so content does not reshuffle on recomposition. */
    private var seed: Int = 0

    fun start(
        packageName: String,
        appLabel: String,
        usedMillis: Long,
        limitMillis: Long,
        strict: Boolean,
        reason: String,
        windowLabel: String,
    ) {
        if (attemptId != 0L) return

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val settings = repository.settingsSnapshot()
            val dayKey = repository.dayKey(now)
            val attemptsToday = repository.attemptsToday(packageName, dayKey)

            seed = (packageName.hashCode() xor now.toInt()) and Int.MAX_VALUE

            val config = GateConfig.forAttempt(
                strict = strict || settings.strictActive(now),
                attemptsToday = attemptsToday,
                inScheduleWindow = reason == GateActivity.REASON_WINDOW,
                sentence = settings.acknowledgementSentence,
            ).copy(grantMillis = settings.grantMinutes * 60_000L)

            attemptId = repository.beginAttempt(
                packageName = packageName,
                dayKey = dayKey,
                startedAt = now,
                initialPhase = GatePhase.PAUSE.name,
            )

            val goals = repository.activeGoals()
            val theme = themeFor(reason)

            _state.update {
                it.copy(
                    loading = false,
                    appLabel = appLabel,
                    packageName = packageName,
                    usedMillis = usedMillis,
                    limitMillis = limitMillis,
                    reason = reason,
                    windowLabel = windowLabel,
                    gate = GateStateMachine.initial(config),
                    settings = settings,
                    goal = goals.randomAt(seed),
                    dhikr = pickDhikr(settings),
                    quran = if (settings.spiritualDepth == SpiritualDepth.FULL) {
                        QuranLibrary.pick(theme, seed)
                    } else {
                        null
                    },
                    hadith = if (settings.spiritualDepth == SpiritualDepth.FULL) {
                        HadithLibrary.pick(theme, seed + 1)
                    } else {
                        null
                    },
                    pauseLine = Reflections.pick(Reflections.pause, seed),
                    waitLine = Reflections.pick(Reflections.wait, seed),
                    purposeLine = Reflections.pick(Reflections.purpose, seed),
                    turnedBackLine = Reflections.pick(Reflections.turnedBack, seed),
                    emergencyRemaining = (settings.emergencyUsesPerWeek -
                        repository.emergencyUsesThisWeek(now)).coerceAtLeast(0),
                )
            }
        }
    }

    fun onEvent(event: GateEvent) {
        val current = _state.value.gate ?: return
        val next = GateStateMachine.reduce(current, event)
        if (next === current) return

        _state.update { it.copy(gate = next) }

        if (next.phase != GatePhase.RESOLVED &&
            next.activePhases.indexOf(next.phase) > next.activePhases.indexOf(deepestPhase)
        ) {
            deepestPhase = next.phase
        }

        if (next.phase == GatePhase.WAIT && current.phase != GatePhase.WAIT) {
            startTicker()
        }
        if (next.phase != GatePhase.WAIT) {
            ticker?.cancel()
        }
        if (next.isResolved) {
            finish(next)
        }
    }

    /** Called when the user leaves without answering: home, back, screen off. */
    fun abandon() {
        val current = _state.value.gate ?: return
        if (current.isResolved) return
        onEvent(GateEvent.Abandon)
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                delay(1_000)
                val gate = _state.value.gate ?: return@launch
                if (gate.phase != GatePhase.WAIT || gate.waitRemainingSeconds <= 0) return@launch
                onEvent(GateEvent.Tick)
            }
        }
    }

    private fun finish(resolved: GateState) {
        ticker?.cancel()
        emergencyTicker?.cancel()

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val outcome = resolved.outcome ?: GateOutcome.ABANDONED

            repository.finishAttempt(
                id = attemptId,
                endedAt = now,
                reachedPhase = deepestPhase.name,
                outcome = outcome,
                intentReason = resolved.intent?.name,
                writtenReason = resolved.freeText,
            )

            val granted = when (outcome) {
                GateOutcome.CHOSE_TO_CONTINUE -> {
                    graph.blockCoordinator.grantAfterGate(_state.value.packageName, now)
                    resolved.config.grantMillis
                }

                GateOutcome.EMERGENCY_ACCESS -> {
                    graph.blockCoordinator.grantAfterGate(
                        _state.value.packageName, now, emergency = true
                    )
                    repository.recordEmergencyUse(
                        _state.value.packageName, now, _state.value.emergencyReason
                    )
                    resolved.config.grantMillis
                }

                else -> 0L
            }

            _state.update { it.copy(resolution = outcome, grantedMillis = granted) }
        }
    }

    // --- emergency path ---------------------------------------------------

    fun openEmergency() {
        _state.update { it.copy(showEmergency = true) }
    }

    fun closeEmergency() {
        emergencyTicker?.cancel()
        _state.update { it.copy(showEmergency = false, emergencyWaitRemaining = 0) }
    }

    fun setEmergencyReason(text: String) {
        _state.update { it.copy(emergencyReason = text) }
    }

    /**
     * Starts the emergency countdown.
     *
     * The emergency path has its own friction on purpose (requirement 10): a
     * written reason, a wait, and a weekly budget. It exists so the system is
     * never dangerously rigid, not as a shortcut around the gate.
     */
    fun beginEmergencyWait() {
        emergencyTicker?.cancel()
        val seconds = _state.value.settings.emergencyWaitSeconds
        _state.update { it.copy(emergencyWaitRemaining = seconds) }
        emergencyTicker = viewModelScope.launch {
            while (_state.value.emergencyWaitRemaining > 0) {
                delay(1_000)
                _state.update { it.copy(emergencyWaitRemaining = it.emergencyWaitRemaining - 1) }
            }
        }
    }

    fun confirmEmergency() {
        if (_state.value.emergencyRemaining <= 0) return
        if (_state.value.emergencyWaitRemaining > 0) return
        if (_state.value.emergencyReason.trim().length < 5) return
        onEvent(GateEvent.EmergencyGranted)
    }

    private fun pickDhikr(settings: Settings): Dhikr? =
        if (settings.spiritualDepth == SpiritualDepth.OFF) {
            null
        } else {
            DhikrLibrary.enabled(settings.enabledDhikrIds).randomAt(seed)
        }

    private fun themeFor(reason: String): Theme = when {
        reason == GateActivity.REASON_WINDOW -> Theme.KEEPING_COMMITMENTS
        else -> listOf(
            Theme.KEEPING_COMMITMENTS,
            Theme.SELF_DISCIPLINE,
            Theme.VALUE_OF_TIME,
            Theme.RESTRAINING_DESIRE,
            Theme.WHAT_BENEFITS_YOU,
        )[Math.floorMod(seed, 5)]
    }

    private fun <T> List<T>.randomAt(seed: Int): T? =
        if (isEmpty()) null else this[Math.floorMod(seed, size)]

    override fun onCleared() {
        ticker?.cancel()
        emergencyTicker?.cancel()
        super.onCleared()
    }
}
