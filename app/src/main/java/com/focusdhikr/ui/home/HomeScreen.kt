package com.focusdhikr.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusdhikr.core.Durations
import com.focusdhikr.ui.Copy
import com.focusdhikr.ui.components.PrimaryAction
import com.focusdhikr.ui.components.QuietCard
import com.focusdhikr.ui.components.SectionLabel
import com.focusdhikr.ui.components.StatTile
import com.focusdhikr.ui.goals.GoalsSection
import com.focusdhikr.ui.theme.Spacing
import com.focusdhikr.ui.util.OnResume

@Composable
fun HomeScreen(
    onPickApps: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    OnResume { viewModel.refresh() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.xl,
            bottom = Spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            Text(
                text = Copy.HOME_GREETING,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (state.settings.strictMode) {
            item {
                Text(
                    text = Copy.HOME_STRICT_ON,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                StatTile(
                    value = Durations.format(state.reclaimedTodayMillis),
                    label = Copy.HOME_RECLAIMED_TODAY,
                    accent = true,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = "${state.turnedBackToday}",
                    label = Copy.HOME_TURNED_BACK,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            StatTile(
                value = Durations.format(state.usedTodayMillis),
                label = Copy.HOME_USED_TODAY,
            )
        }

        if (state.rows.isEmpty() && !state.loading) {
            item { EmptyApps(onPickApps) }
        } else {
            item { SectionLabel(Copy.HOME_BLOCKED_APPS) }
            items(state.rows, key = { it.app.packageName }) { row ->
                AppProgressRow(row)
            }
        }

        item {
            GoalsSection(
                goals = state.goals,
                onSave = viewModel::saveGoal,
                onDelete = viewModel::deleteGoal,
            )
        }

        item {
            Text(
                text = state.ambientLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.lg),
            )
        }
    }
}

@Composable
private fun EmptyApps(onPickApps: () -> Unit) {
    QuietCard {
        Text(
            text = Copy.HOME_NO_APPS_TITLE,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = Copy.HOME_NO_APPS_BODY,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        PrimaryAction(text = Copy.HOME_CHOOSE_APPS, onClick = onPickApps)
    }
}

/**
 * One app, its time and its remaining allowance.
 *
 * The meter is a thin line rather than a filled bar: a bar racing towards full
 * reads as a target to reach. This just marks where you are.
 */
@Composable
private fun AppProgressRow(row: AppRow) {
    QuietCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.app.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (row.limitReached) {
                    Copy.HOME_LIMIT_REACHED
                } else {
                    "${Durations.format(row.remainingMillis)} ${Copy.HOME_REMAINING}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (row.limitReached) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }

        Spacer(Modifier.height(Spacing.md))

        Meter(fraction = row.fraction, reached = row.limitReached)

        Spacer(Modifier.height(Spacing.sm))

        Text(
            text = "${Durations.format(row.usedMillis)} ${Copy.HOME_OF} " +
                Durations.format(row.app.dailyLimitMillis),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Meter(fraction: Float, reached: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.outline)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (reached) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
        )
    }
}
