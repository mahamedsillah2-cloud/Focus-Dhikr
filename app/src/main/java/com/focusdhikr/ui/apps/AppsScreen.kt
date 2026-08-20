package com.focusdhikr.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusdhikr.core.Durations
import com.focusdhikr.data.repo.InstalledApp
import com.focusdhikr.domain.model.TrackedApp
import com.focusdhikr.ui.Copy
import com.focusdhikr.ui.components.PrimaryAction
import com.focusdhikr.ui.components.QuietCard
import com.focusdhikr.ui.components.SectionLabel
import com.focusdhikr.ui.theme.Spacing

@Composable
fun AppsScreen(viewModel: AppsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Spacing.lg, end = Spacing.lg, top = Spacing.xl, bottom = Spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Text(
                text = Copy.APPS_TITLE,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        item { SectionLabel(Copy.APPS_LIMITED) }

        items(state.tracked, key = { it.packageName }) { app ->
            TrackedAppCard(
                app = app,
                onLimitChange = { viewModel.setLimitMinutes(app.packageName, it) },
                onEnabledChange = { viewModel.setEnabled(app.packageName, it) },
                onStrictChange = { viewModel.setStrict(app.packageName, it) },
                onRemove = { viewModel.untrack(app.packageName) },
            )
        }

        item {
            Spacer(Modifier.height(Spacing.sm))
            PrimaryAction(text = Copy.APPS_ADD, onClick = viewModel::openPicker)
        }
    }

    if (state.picking) {
        AppPickerDialog(
            state = state,
            onQueryChange = viewModel::setQuery,
            onPick = { app, minutes -> viewModel.track(app, minutes) },
            onDismiss = viewModel::closePicker,
        )
    }
}

@Composable
private fun TrackedAppCard(
    app: TrackedApp,
    onLimitChange: (Int) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onStrictChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    var minutes by remember(app.packageName, app.dailyLimitMillis) {
        mutableIntStateOf(Durations.millisToMinutes(app.dailyLimitMillis))
    }

    QuietCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = app.enabled, onCheckedChange = onEnabledChange)
        }

        Spacer(Modifier.height(Spacing.md))

        Text(
            text = "${Copy.APPS_DAILY_LIMIT}: ${Durations.format(minutes * 60_000L)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        // Steps of five minutes below an hour, then coarser: nobody sets a
        // 47-minute Instagram limit, and a finer slider only makes it fiddly.
        Slider(
            value = minutes.toFloat(),
            onValueChange = { minutes = snapMinutes(it) },
            onValueChangeFinished = { onLimitChange(minutes) },
            valueRange = 0f..240f,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = Copy.APPS_STRICT_THIS_APP,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = app.strict, onCheckedChange = onStrictChange)
        }

        TextButton(onClick = onRemove) {
            Text(Copy.APPS_REMOVE, color = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun snapMinutes(raw: Float): Int {
    val value = raw.toInt()
    return when {
        value <= 60 -> (value / 5) * 5
        value <= 120 -> (value / 10) * 10
        else -> (value / 15) * 15
    }
}

@Composable
private fun AppPickerDialog(
    state: AppsUiState,
    onQueryChange: (String) -> Unit,
    onPick: (InstalledApp, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf<InstalledApp?>(null) }
    var minutes by remember { mutableIntStateOf(60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(chosen?.label ?: Copy.APPS_PICK_TITLE)
        },
        text = {
            val picked = chosen
            if (picked == null) {
                Column {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        label = { Text(Copy.APPS_SEARCH) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.sm))

                    if (state.loadingInstalled) {
                        Text(
                            text = "…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }

                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        items(state.filteredInstalled, key = { it.packageName }) { app ->
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { chosen = app }
                                    .padding(vertical = 14.dp),
                            )
                        }
                    }
                }
            } else {
                Column {
                    Text(
                        text = "${Copy.APPS_DAILY_LIMIT}: ${Durations.format(minutes * 60_000L)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Slider(
                        value = minutes.toFloat(),
                        onValueChange = { minutes = snapMinutes(it) },
                        valueRange = 0f..240f,
                    )
                }
            }
        },
        confirmButton = {
            val picked = chosen
            if (picked != null) {
                TextButton(onClick = { onPick(picked, minutes) }) { Text(Copy.SAVE) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Copy.CANCEL) }
        },
    )
}
