package com.focusdhikr.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusdhikr.core.Durations
import com.focusdhikr.ui.Copy
import com.focusdhikr.ui.components.QuietCard
import com.focusdhikr.ui.components.SectionLabel
import com.focusdhikr.ui.components.StatTile
import com.focusdhikr.ui.theme.Spacing
import com.focusdhikr.ui.util.OnResume

@Composable
fun StatsScreen(viewModel: StatsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    OnResume { viewModel.load(state.period) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Spacing.lg, end = Spacing.lg, top = Spacing.xl, bottom = Spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            Text(
                text = Copy.STATS_TITLE,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatsPeriod.entries.forEach { period ->
                    PeriodPill(
                        text = period.labelEs,
                        selected = state.period == period,
                        onClick = { viewModel.load(period) },
                    )
                }
            }
        }

        val stats = state.stats
        if (stats == null || (stats.usedMillis == 0L && stats.turnedBackCount == 0)) {
            item {
                QuietCard {
                    Text(
                        text = Copy.STATS_EMPTY,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@LazyColumn
        }

        item {
            QuietCard {
                StatTile(
                    value = Durations.format(stats.reclaimedMillis),
                    label = Copy.STATS_RECLAIMED,
                    accent = true,
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = Copy.STATS_RECLAIMED_EXPLAIN,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                StatTile(
                    value = "${stats.turnedBackCount}",
                    label = "${Copy.STATS_TURNED_BACK} (${Copy.STATS_TIMES})",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = Durations.format(stats.usedMillis),
                    label = Copy.STATS_USED,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (state.period != StatsPeriod.TODAY) {
            item {
                SectionLabel(Copy.STATS_TITLE)
                DayChart(stats.perDay, stats.dayKeys)
            }
        }

        item { SectionLabel(Copy.STATS_BY_APP) }

        stats.perApp.entries
            .sortedByDescending { it.value }
            .forEach { (packageName, millis) ->
                item(key = packageName) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = state.labels[packageName] ?: packageName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = Durations.format(millis),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
    }
}

@Composable
private fun PeriodPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.background
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
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

/**
 * A bare column chart, drawn with boxes.
 *
 * No axes, no gridlines, no library. At this data density a shape is enough to
 * see the trend, and anything more would be decoration.
 */
@Composable
private fun DayChart(perDay: Map<String, Long>, dayKeys: List<String>) {
    val max = (perDay.values.maxOrNull() ?: 0L).coerceAtLeast(1L)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        dayKeys.forEach { key ->
            val value = perDay[key] ?: 0L
            val fraction = (value.toFloat() / max).coerceIn(0.02f, 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(
                        if (value == 0L) {
                            MaterialTheme.colorScheme.outline
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
            )
        }
    }
}
