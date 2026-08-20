package com.focusdhikr.ui.settings

import android.content.Context
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusdhikr.content.DhikrLibrary
import com.focusdhikr.core.Durations
import com.focusdhikr.core.Permissions
import com.focusdhikr.data.prefs.ReminderFrequency
import com.focusdhikr.data.prefs.SpiritualDepth
import com.focusdhikr.service.FocusDeviceAdminReceiver
import com.focusdhikr.ui.Copy
import com.focusdhikr.ui.components.QuietCard
import com.focusdhikr.ui.components.SectionLabel
import com.focusdhikr.ui.settings.windows.ScheduleWindowsSection
import com.focusdhikr.ui.theme.Spacing
import com.focusdhikr.ui.util.OnResume

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    OnResume { viewModel.refreshPermissions() }

    var confirmErase by remember { mutableStateOf(false) }
    var editingSentence by remember { mutableStateOf(false) }

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
                text = Copy.SETTINGS_TITLE,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // --- strict mode --------------------------------------------------

        item {
            SectionLabel(Copy.SETTINGS_SECTION_STRICT)
            QuietCard {
                ToggleRow(
                    title = Copy.SETTINGS_STRICT_TOGGLE,
                    body = Copy.SETTINGS_STRICT_BODY,
                    checked = state.settings.strictMode,
                    onCheckedChange = viewModel::setStrictMode,
                )

                Spacer(Modifier.height(Spacing.md))

                Text(
                    text = "${Copy.SETTINGS_STRICT_COOLDOWN}: " +
                        if (state.settings.strictModeCooldownMinutes == 0) {
                            "sin retardo"
                        } else {
                            Durations.format(state.settings.strictModeCooldownMinutes * 60_000L)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = Copy.SETTINGS_STRICT_COOLDOWN_BODY,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                StepSlider(
                    value = state.settings.strictModeCooldownMinutes.toFloat(),
                    range = 0f..720f,
                    onChange = { viewModel.setStrictCooldown(((it / 15).toInt()) * 15) },
                )
            }
        }

        item {
            ScheduleWindowsSection(
                windows = state.windows,
                onSave = viewModel::saveWindow,
                onDelete = viewModel::deleteWindow,
            )
        }

        // --- spiritual ----------------------------------------------------

        item {
            SectionLabel(Copy.SETTINGS_SECTION_SPIRITUAL)
            QuietCard {
                Text(
                    text = Copy.SETTINGS_SPIRITUAL_DEPTH,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SpiritualDepth.entries.forEach { depth ->
                        Pill(
                            text = depth.labelEs,
                            selected = state.settings.spiritualDepth == depth,
                            onClick = { viewModel.setSpiritualDepth(depth) },
                        )
                    }
                }

                if (state.settings.spiritualDepth != SpiritualDepth.OFF) {
                    Spacer(Modifier.height(Spacing.lg))
                    Text(
                        text = Copy.SETTINGS_DHIKR_PICK,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    DhikrLibrary.all.forEach { dhikr ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = dhikr.transliteration,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = dhikr.meaningEs,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            Switch(
                                checked = dhikr.id in state.settings.enabledDhikrIds,
                                onCheckedChange = { viewModel.toggleDhikr(dhikr.id) },
                            )
                        }
                    }

                    Spacer(Modifier.height(Spacing.sm))
                    ToggleRow(
                        title = Copy.SETTINGS_SHOW_TRANSLATIONS,
                        body = "",
                        checked = state.settings.showTranslations,
                        onCheckedChange = viewModel::setShowTranslations,
                    )
                }

                Spacer(Modifier.height(Spacing.lg))
                Text(
                    text = Copy.SETTINGS_REMINDERS,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ReminderFrequency.entries.forEach { frequency ->
                        Pill(
                            text = frequency.labelEs,
                            selected = state.settings.reminderFrequency == frequency,
                            onClick = { viewModel.setReminderFrequency(frequency) },
                        )
                    }
                }
            }
        }

        // --- the gate -----------------------------------------------------

        item {
            SectionLabel(Copy.SETTINGS_SECTION_GATE)
            QuietCard {
                Text(
                    text = "${Copy.SETTINGS_GRANT_MINUTES}: " +
                        Durations.format(state.settings.grantMinutes * 60_000L),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                StepSlider(
                    value = state.settings.grantMinutes.toFloat(),
                    range = 1f..30f,
                    onChange = { viewModel.setGrantMinutes(it.toInt()) },
                )

                Spacer(Modifier.height(Spacing.md))

                Text(
                    text = Copy.SETTINGS_SENTENCE,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = Copy.SETTINGS_SENTENCE_BODY,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = state.settings.acknowledgementSentence,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { editingSentence = true },
                )

                Spacer(Modifier.height(Spacing.lg))

                Text(
                    text = "${Copy.SETTINGS_DAY_RESET} " +
                        "${state.settings.dayResetHour.toString().padStart(2, '0')}:00",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = Copy.SETTINGS_DAY_RESET_BODY,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                StepSlider(
                    value = state.settings.dayResetHour.toFloat(),
                    range = 0f..12f,
                    onChange = { viewModel.setDayResetHour(it.toInt()) },
                )
            }
        }

        // --- emergency ----------------------------------------------------

        item {
            SectionLabel(Copy.SETTINGS_SECTION_EMERGENCY)
            QuietCard {
                Text(
                    text = "${Copy.SETTINGS_EMERGENCY_USES}: ${state.settings.emergencyUsesPerWeek}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                StepSlider(
                    value = state.settings.emergencyUsesPerWeek.toFloat(),
                    range = 0f..10f,
                    onChange = { viewModel.setEmergencyUses(it.toInt()) },
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = "${Copy.SETTINGS_EMERGENCY_WAIT}: " +
                        Durations.formatSeconds(state.settings.emergencyWaitSeconds),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                StepSlider(
                    value = state.settings.emergencyWaitSeconds.toFloat(),
                    range = 0f..120f,
                    onChange = { viewModel.setEmergencyWait(((it / 5).toInt()) * 5) },
                )
            }
        }

        // --- permissions --------------------------------------------------

        item {
            SectionLabel(Copy.SETTINGS_SECTION_PERMISSIONS)
            QuietCard {
                PermissionRow(
                    title = Copy.PERM_USAGE_TITLE,
                    body = Copy.PERM_USAGE_BODY,
                    granted = state.permissions.usageAccess,
                    onGrant = { context.startActivitySafely(Permissions.usageAccessIntent()) },
                )
                PermissionRow(
                    title = Copy.PERM_OVERLAY_TITLE,
                    body = Copy.PERM_OVERLAY_BODY,
                    granted = state.permissions.overlay,
                    onGrant = { context.startActivitySafely(Permissions.overlayIntent(context)) },
                )
                PermissionRow(
                    title = Copy.PERM_ACCESSIBILITY_TITLE,
                    body = Copy.PERM_ACCESSIBILITY_BODY,
                    granted = state.permissions.accessibility,
                    onGrant = { context.startActivitySafely(Permissions.accessibilityIntent()) },
                )
                PermissionRow(
                    title = Copy.PERM_BATTERY_TITLE,
                    body = Copy.PERM_BATTERY_BODY,
                    granted = state.permissions.batteryUnrestricted,
                    onGrant = {
                        context.startActivitySafely(Permissions.batteryOptimizationIntent(context))
                    },
                )
            }
        }

        // --- uninstall friction -------------------------------------------

        item {
            SectionLabel(Copy.SETTINGS_SECTION_ADMIN)
            QuietCard {
                Text(
                    text = Copy.SETTINGS_ADMIN_BODY,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
                TextButton(
                    onClick = {
                        if (state.permissions.deviceAdminActive) {
                            FocusDeviceAdminReceiver.disable(context)
                            viewModel.onDeviceAdminChanged(false)
                        } else {
                            context.startActivitySafely(
                                FocusDeviceAdminReceiver.enableIntent(
                                    context,
                                    Copy.SETTINGS_ADMIN_BODY,
                                )
                            )
                        }
                    }
                ) {
                    Text(
                        text = if (state.permissions.deviceAdminActive) {
                            Copy.SETTINGS_ADMIN_DISABLE
                        } else {
                            Copy.SETTINGS_ADMIN_ENABLE
                        },
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // --- privacy ------------------------------------------------------

        item {
            SectionLabel(Copy.SETTINGS_SECTION_PRIVACY)
            QuietCard {
                Text(
                    text = Copy.SETTINGS_NO_INTERNET,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(Modifier.height(Spacing.md))

                ToggleRow(
                    title = Copy.SETTINGS_KEEP_REASONS,
                    body = Copy.SETTINGS_KEEP_REASONS_BODY,
                    checked = state.settings.keepWrittenReasons,
                    onCheckedChange = viewModel::setKeepWrittenReasons,
                )

                TextButton(onClick = viewModel::forgetWrittenReasons) {
                    Text(Copy.SETTINGS_FORGET_REASONS, color = MaterialTheme.colorScheme.outline)
                }

                Spacer(Modifier.height(Spacing.sm))

                Text(
                    text = "${Copy.SETTINGS_RETENTION}: ${state.settings.historyRetentionDays} días",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                StepSlider(
                    value = state.settings.historyRetentionDays.toFloat(),
                    range = 7f..365f,
                    onChange = { viewModel.setRetentionDays(it.toInt()) },
                )

                TextButton(onClick = { confirmErase = true }) {
                    Text(Copy.SETTINGS_ERASE, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        item {
            SectionLabel(Copy.SETTINGS_SECTION_ABOUT)
            QuietCard {
                Text(
                    text = Copy.SETTINGS_SOURCES,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = Copy.SETTINGS_SOURCES_BODY,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(Copy.SETTINGS_ERASE) },
            text = { Text(Copy.SETTINGS_ERASE_CONFIRM) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.eraseEverything()
                    confirmErase = false
                }) {
                    Text(Copy.DELETE, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmErase = false }) { Text(Copy.CANCEL) }
            },
        )
    }

    if (editingSentence) {
        var draft by remember { mutableStateOf(state.settings.acknowledgementSentence) }
        AlertDialog(
            onDismissRequest = { editingSentence = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(Copy.SETTINGS_SENTENCE) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = draft.trim().length >= 10,
                    onClick = {
                        viewModel.setSentence(draft)
                        editingSentence = false
                    },
                ) { Text(Copy.SAVE) }
            },
            dismissButton = {
                TextButton(onClick = { editingSentence = false }) { Text(Copy.CANCEL) }
            },
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (body.isNotBlank()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PermissionRow(
    title: String,
    body: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        if (granted) {
            Text(
                text = Copy.PERM_GRANTED,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            TextButton(onClick = onGrant) {
                Text(Copy.PERM_GRANT, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    )
}

@Composable
private fun StepSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    var local by remember(value) { mutableStateOf(value) }
    Slider(
        value = local,
        onValueChange = { local = it },
        onValueChangeFinished = { onChange(local) },
        valueRange = range,
    )
}

/**
 * Every one of these targets a system settings screen that may simply not exist
 * on a given OEM build. Failing silently beats crashing the settings screen.
 */
private fun Context.startActivitySafely(intent: android.content.Intent) {
    runCatching {
        startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
