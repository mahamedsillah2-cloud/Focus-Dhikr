package com.focusdhikr.ui.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.focusdhikr.domain.model.Goal
import com.focusdhikr.ui.Copy
import com.focusdhikr.ui.components.QuietCard
import com.focusdhikr.ui.components.SectionLabel
import com.focusdhikr.ui.theme.Spacing

/**
 * The user's own reasons, in their own words.
 *
 * Shown on the home screen rather than buried in settings, because these are
 * what phase 4 of the gate reads back to them - and something you will be shown
 * at your weakest moment deserves to be edited at your calmest.
 */
@Composable
fun GoalsSection(
    goals: List<Goal>,
    onSave: (Goal) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var editing by remember { mutableStateOf<Goal?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(Copy.GOALS_TITLE)

        QuietCard {
            Text(
                text = Copy.GOALS_SUBTITLE,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.md))

            if (goals.isEmpty()) {
                Text(
                    text = Copy.GOALS_EMPTY,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(Spacing.sm))
                SuggestionRow { suggestion ->
                    onSave(Goal(title = suggestion))
                }
            } else {
                goals.forEach { goal ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editing = goal }
                            .padding(vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = goal.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (goal.note.isNotBlank()) {
                                Text(
                                    text = goal.note,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { onDelete(goal.id) }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = Copy.DELETE,
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            TextButton(onClick = { editing = Goal(title = "") }) {
                Text(Copy.GOALS_ADD, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    editing?.let { goal ->
        GoalDialog(
            goal = goal,
            onDismiss = { editing = null },
            onSave = {
                onSave(it)
                editing = null
            },
        )
    }
}

@Composable
private fun SuggestionRow(onPick: (String) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Copy.GOAL_SUGGESTIONS.forEach { suggestion ->
            Text(
                text = suggestion,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onPick(suggestion) }
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }
    }
}

@Composable
private fun GoalDialog(
    goal: Goal,
    onDismiss: () -> Unit,
    onSave: (Goal) -> Unit,
) {
    var title by remember { mutableStateOf(goal.title) }
    var note by remember { mutableStateOf(goal.note) }
    var active by remember { mutableStateOf(goal.active) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(Copy.GOALS_TITLE_FIELD) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(Copy.GOALS_TITLE_FIELD) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(Copy.GOALS_NOTE_FIELD) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = Copy.GOALS_ACTIVE,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onSave(goal.copy(title = title, note = note, active = active)) },
            ) { Text(Copy.SAVE) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Copy.CANCEL) }
        },
    )
}
