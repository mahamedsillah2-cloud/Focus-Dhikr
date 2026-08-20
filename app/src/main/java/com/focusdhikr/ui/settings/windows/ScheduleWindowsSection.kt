package com.focusdhikr.ui.settings.windows

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import com.focusdhikr.domain.model.ScheduleWindow
import com.focusdhikr.ui.Copy
import com.focusdhikr.ui.components.QuietCard
import com.focusdhikr.ui.components.SectionLabel
import com.focusdhikr.ui.theme.Spacing

private val DAY_LABELS = listOf("L", "M", "X", "J", "V", "S", "D")

@Composable
fun ScheduleWindowsSection(
    windows: List<ScheduleWindow>,
    onSave: (ScheduleWindow) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var editing by remember { mutableStateOf<ScheduleWindow?>(null) }

    Column(Modifier.fillMaxWidth()) {
        SectionLabel(Copy.SETTINGS_WINDOWS)
        QuietCard {
            Text(
                text = Copy.SETTINGS_WINDOWS_BODY,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )

            Spacer(Modifier.height(Spacing.sm))

            windows.forEach { window ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = window }
                        .padding(vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "${formatMinute(window.startMinuteOfDay)} – " +
                                formatMinute(window.endMinuteOfDay),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = formatDays(window.daysMask),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    IconButton(onClick = { onDelete(window.id) }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = Copy.DELETE,
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            TextButton(
                onClick = {
                    editing = ScheduleWindow(
                        packageName = null,
                        startMinuteOfDay = 22 * 60,
                        endMinuteOfDay = 8 * 60,
                        label = "Noche",
                    )
                }
            ) {
                Text(Copy.SETTINGS_ADD_WINDOW, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    editing?.let { window ->
        WindowDialog(
            window = window,
            onDismiss = { editing = null },
            onSave = {
                onSave(it)
                editing = null
            },
        )
    }
}

@Composable
private fun WindowDialog(
    window: ScheduleWindow,
    onDismiss: () -> Unit,
    onSave: (ScheduleWindow) -> Unit,
) {
    var start by remember { mutableIntStateOf(window.startMinuteOfDay) }
    var end by remember { mutableIntStateOf(window.endMinuteOfDay) }
    var days by remember { mutableIntStateOf(window.daysMask) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(Copy.SETTINGS_ADD_WINDOW) },
        text = {
            Column {
                Text(
                    text = "Desde ${formatMinute(start)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Slider(
                    value = start.toFloat(),
                    onValueChange = { start = snapToQuarter(it) },
                    valueRange = 0f..(24 * 60 - 15).toFloat(),
                )

                Text(
                    text = "Hasta ${formatMinute(end)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Slider(
                    value = end.toFloat(),
                    onValueChange = { end = snapToQuarter(it) },
                    valueRange = 0f..(24 * 60 - 15).toFloat(),
                )

                Spacer(Modifier.height(Spacing.sm))

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    DAY_LABELS.forEachIndexed { index, label ->
                        val bit = 1 shl index
                        val on = days and bit != 0
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (on) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            modifier = Modifier
                                .clickable { days = days xor bit }
                                .padding(Spacing.sm),
                        )
                    }
                }

                if (end <= start) {
                    Text(
                        text = "Esta franja cruza la medianoche.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                // A window with no days selected would silently never fire.
                enabled = days != 0 && start != end,
                onClick = {
                    onSave(
                        window.copy(
                            startMinuteOfDay = start,
                            endMinuteOfDay = end,
                            daysMask = days,
                            label = "${formatMinute(start)}–${formatMinute(end)}",
                        )
                    )
                },
            ) { Text(Copy.SAVE) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Copy.CANCEL) }
        },
    )
}

private fun snapToQuarter(raw: Float): Int = ((raw / 15).toInt()) * 15

private fun formatMinute(minuteOfDay: Int): String {
    val h = (minuteOfDay / 60).coerceIn(0, 23)
    val m = minuteOfDay % 60
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}

private fun formatDays(mask: Int): String {
    if (mask == ScheduleWindow.ALL_DAYS) return "Todos los días"
    return DAY_LABELS.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
        .joinToString(" ")
        .ifBlank { "Ningún día" }
}
