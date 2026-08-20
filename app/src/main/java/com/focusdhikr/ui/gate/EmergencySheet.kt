package com.focusdhikr.ui.gate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focusdhikr.core.Durations
import com.focusdhikr.ui.Copy
import com.focusdhikr.ui.components.CenteredProse
import com.focusdhikr.ui.components.PrimaryAction
import com.focusdhikr.ui.components.QuietAction
import com.focusdhikr.ui.theme.Spacing

/**
 * The way out (requirement 10).
 *
 * A blocker with no escape hatch is not disciplined, it is brittle: the first
 * time it stops something that genuinely mattered, the user uninstalls it and
 * loses everything. So there is always a way through - it just costs a written
 * reason, a wait, and one of a small weekly budget.
 */
@Composable
fun EmergencySheet(viewModel: GateViewModel, state: GateUiState) {
    LaunchedEffect(Unit) { viewModel.beginEmergencyWait() }

    val exhausted = state.emergencyRemaining <= 0
    val waiting = state.emergencyWaitRemaining > 0
    val reasonOk = state.emergencyReason.trim().length >= 5

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.xxl))

        Text(
            text = Copy.GATE_EMERGENCY_TITLE,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.md))

        CenteredProse(
            if (exhausted) {
                Copy.GATE_EMERGENCY_NONE_LEFT
            } else {
                Copy.GATE_EMERGENCY_BODY.format(state.emergencyRemaining)
            }
        )

        if (!exhausted) {
            Spacer(Modifier.height(Spacing.xl))

            OutlinedTextField(
                value = state.emergencyReason,
                onValueChange = viewModel::setEmergencyReason,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(Copy.GATE_EMERGENCY_REASON) },
                minLines = 3,
                shape = RoundedCornerShape(14.dp),
            )

            Spacer(Modifier.height(Spacing.lg))

            if (waiting) {
                Text(
                    text = Durations.formatSeconds(state.emergencyWaitRemaining),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(Spacing.md))
            }

            PrimaryAction(
                text = Copy.GATE_EMERGENCY_CONFIRM,
                enabled = !waiting && reasonOk,
                onClick = viewModel::confirmEmergency,
            )
        }

        QuietAction(text = Copy.BACK, onClick = viewModel::closeEmergency)
    }
}
