package com.focusdhikr.ui.gate

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusdhikr.core.Durations
import com.focusdhikr.data.prefs.SpiritualDepth
import com.focusdhikr.domain.gate.GateEvent
import com.focusdhikr.domain.gate.GatePhase
import com.focusdhikr.domain.gate.SentenceMatcher
import com.focusdhikr.domain.model.GateOutcome
import com.focusdhikr.domain.model.IntentReason
import com.focusdhikr.ui.Copy
import com.focusdhikr.ui.components.CenteredProse
import com.focusdhikr.ui.components.DhikrBlock
import com.focusdhikr.ui.components.HadithBlock
import com.focusdhikr.ui.components.PrimaryAction
import com.focusdhikr.ui.components.QuietAction
import com.focusdhikr.ui.components.QuranBlock
import com.focusdhikr.ui.components.StepDots
import com.focusdhikr.ui.theme.Spacing

@Composable
fun GateScreen(
    viewModel: GateViewModel,
    onDismiss: () -> Unit,
    onOpenTargetApp: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.resolution) {
        when (state.resolution) {
            GateOutcome.CHOSE_TO_CONTINUE,
            GateOutcome.EMERGENCY_ACCESS -> onOpenTargetApp(state.packageName)

            GateOutcome.TURNED_BACK,
            GateOutcome.ABANDONED -> onDismiss()

            null -> Unit
        }
    }

    val gate = state.gate
    if (state.loading || gate == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    if (state.showEmergency) {
        EmergencySheet(viewModel = viewModel, state = state)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.appLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StepDots(current = gate.stepNumber, total = gate.stepCount)
        }

        Spacer(Modifier.height(Spacing.xxl))

        AnimatedContent(
            targetState = gate.phase,
            transitionSpec = {
                fadeIn(tween(320)) togetherWith fadeOut(tween(180))
            },
            label = "gate-phase",
        ) { phase ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (phase) {
                    GatePhase.PAUSE -> PausePhase(state)
                    GatePhase.INTENT -> IntentPhase(state, viewModel)
                    GatePhase.WAIT -> WaitPhase(state)
                    GatePhase.PURPOSE -> PurposePhase(state)
                    GatePhase.WRITE -> WritePhase(state, viewModel)
                    GatePhase.DECIDE -> DecidePhase(state, viewModel)
                    GatePhase.RESOLVED -> Unit
                }
            }
        }

        Spacer(Modifier.height(Spacing.xl))

        val forwardLabel = when (gate.phase) {
            GatePhase.PAUSE -> Copy.GATE_ENTER_ANYWAY
            GatePhase.DECIDE -> Copy.GATE_OPEN_APP
            else -> Copy.CONTINUE
        }

        PrimaryAction(
            text = forwardLabel,
            enabled = gate.canAdvance,
            onClick = { viewModel.onEvent(GateEvent.Advance) },
        )

        QuietAction(
            text = if (gate.phase == GatePhase.PAUSE) Copy.GATE_TURN_BACK else Copy.GATE_NOT_NOW,
            onClick = { viewModel.onEvent(GateEvent.TurnBack) },
        )

        if (state.settings.emergencyUsesPerWeek > 0) {
            TextButton(onClick = { viewModel.openEmergency() }) {
                Text(
                    text = Copy.GATE_EMERGENCY,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))
    }
}

// --- phase 1 --------------------------------------------------------------

@Composable
private fun PausePhase(state: GateUiState) {
    val isWindow = state.reason == GateActivity.REASON_WINDOW

    Text(
        text = if (isWindow) Copy.GATE_WINDOW_TITLE else Copy.GATE_LIMIT_TITLE,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(Spacing.xl))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NumberBlock(Durations.format(state.usedMillis), Copy.GATE_USED_TODAY)
        if (!isWindow) {
            NumberBlock(Durations.format(state.limitMillis), Copy.GATE_YOUR_LIMIT, accent = true)
        }
    }

    Spacer(Modifier.height(Spacing.lg))

    Text(
        text = if (isWindow && state.windowLabel.isNotBlank()) {
            state.windowLabel
        } else {
            "${Copy.GATE_AVAILABLE_IN} ${Copy.HOME_GREETING.lowercase()}"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(Spacing.xl))

    CenteredProse(state.pauseLine)
}

@Composable
private fun NumberBlock(value: String, label: String, accent: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = if (accent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

// --- phase 2 --------------------------------------------------------------

@Composable
private fun IntentPhase(state: GateUiState, viewModel: GateViewModel) {
    val gate = state.gate ?: return

    Text(
        text = Copy.GATE_INTENT_QUESTION,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(Spacing.lg))

    IntentReason.entries.forEach { reason ->
        ChoiceRow(
            text = Copy.intentLabel(reason),
            selected = gate.intent == reason,
            onClick = { viewModel.onEvent(GateEvent.ChooseIntent(reason)) },
        )
        Spacer(Modifier.height(Spacing.sm))
    }

    Spacer(Modifier.height(Spacing.lg))

    Text(
        text = Copy.GATE_ALIGNMENT_QUESTION,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(Spacing.md))

    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ChoicePill(
            text = Copy.GATE_ALIGNMENT_YES,
            selected = gate.alignsWithToday == true,
            onClick = { viewModel.onEvent(GateEvent.AnswerAlignment(true)) },
        )
        ChoicePill(
            text = Copy.GATE_ALIGNMENT_NO,
            selected = gate.alignsWithToday == false,
            onClick = { viewModel.onEvent(GateEvent.AnswerAlignment(false)) },
        )
    }
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.background
                }
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = 14.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun ChoicePill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

// --- phase 3 --------------------------------------------------------------

@Composable
private fun WaitPhase(state: GateUiState) {
    val gate = state.gate ?: return

    Text(
        text = Copy.GATE_WAIT_TITLE,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(Spacing.xl))

    Text(
        text = Durations.formatSeconds(gate.waitRemainingSeconds),
        fontSize = 56.sp,
        fontFamily = FontFamily.Serif,
        color = MaterialTheme.colorScheme.primary,
    )

    Spacer(Modifier.height(Spacing.xl))

    state.dhikr?.let { dhikr ->
        DhikrBlock(dhikr = dhikr, showMeaning = state.settings.showTranslations)
        Spacer(Modifier.height(Spacing.xl))
    }

    CenteredProse(state.waitLine)

    if (gate.waitRemainingSeconds <= 0) {
        Spacer(Modifier.height(Spacing.lg))
        CenteredProse(
            text = Copy.GATE_WAIT_QUESTION,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

// --- phase 4 --------------------------------------------------------------

@Composable
private fun PurposePhase(state: GateUiState) {
    Text(
        text = state.purposeLine,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(Spacing.xl))

    val goal = state.goal
    if (goal != null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(18.dp),
                )
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = goal.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            if (goal.note.isNotBlank()) {
                Text(
                    text = goal.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    } else {
        CenteredProse(Copy.GATE_PURPOSE_NO_GOALS)
    }

    if (state.settings.spiritualDepth == SpiritualDepth.FULL) {
        Spacer(Modifier.height(Spacing.xxl))
        state.quran?.let {
            QuranBlock(citation = it, showTranslation = state.settings.showTranslations)
        } ?: state.hadith?.let {
            HadithBlock(citation = it, showTranslation = state.settings.showTranslations)
        }
    }
}

// --- phase 5 --------------------------------------------------------------

@Composable
private fun WritePhase(state: GateUiState, viewModel: GateViewModel) {
    val gate = state.gate ?: return

    Text(
        text = Copy.GATE_WRITE_TITLE,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(Spacing.lg))

    OutlinedTextField(
        value = gate.freeText,
        onValueChange = { viewModel.onEvent(GateEvent.EditFreeText(it)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(Copy.GATE_WRITE_PLACEHOLDER) },
        minLines = 4,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
    )

    Spacer(Modifier.height(Spacing.sm))

    Text(
        text = Copy.GATE_WRITE_MIN.format(gate.config.minFreeTextChars),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
    )
}

// --- phase 6 --------------------------------------------------------------

@Composable
private fun DecidePhase(state: GateUiState, viewModel: GateViewModel) {
    val gate = state.gate ?: return
    val sentence = gate.config.acknowledgementSentence
    val progress = SentenceMatcher.progress(gate.typedSentence, sentence)

    Text(
        text = Copy.GATE_DECIDE_TITLE,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(Spacing.lg))

    Text(
        text = Copy.GATE_DECIDE_INSTRUCTION,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
    )

    Spacer(Modifier.height(Spacing.sm))

    Text(
        text = sentence,
        style = MaterialTheme.typography.bodyLarge,
        fontFamily = FontFamily.Serif,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(Spacing.md),
    )

    Spacer(Modifier.height(Spacing.lg))

    OutlinedTextField(
        value = gate.typedSentence,
        onValueChange = { viewModel.onEvent(GateEvent.EditSentence(it)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(Copy.GATE_DECIDE_PLACEHOLDER) },
        minLines = 3,
        shape = RoundedCornerShape(14.dp),
    )

    Spacer(Modifier.height(Spacing.sm))

    // A hint, not a red error: the point is friction, not a spelling exam.
    Text(
        text = "${(progress * 100).toInt()} %",
        style = MaterialTheme.typography.labelSmall,
        color = if (gate.canAdvance) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        },
    )

    Spacer(Modifier.height(Spacing.md))

    CenteredProse(
        Copy.GATE_GRANTED.format(Durations.format(gate.config.grantMillis))
    )
}
