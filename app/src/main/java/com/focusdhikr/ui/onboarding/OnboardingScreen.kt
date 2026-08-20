package com.focusdhikr.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusdhikr.core.Permissions
import com.focusdhikr.ui.Copy
import com.focusdhikr.ui.components.PrimaryAction
import com.focusdhikr.ui.components.QuietCard
import com.focusdhikr.ui.theme.Spacing
import com.focusdhikr.ui.util.OnResume

/**
 * Four screens: the idea, the honest limits, privacy, and permissions.
 *
 * The "what this cannot do" screen comes before the permission requests on
 * purpose. A tool like this only works if you trust it, and trust starts with
 * it not overselling itself on the first screen.
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var page by remember { mutableIntStateOf(0) }

    OnResume { viewModel.refresh() }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refresh() }

    LaunchedEffect(page) {
        if (page == 3 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.xxl))

        when (page) {
            0 -> Intro(Copy.ONBOARD_TITLE, Copy.ONBOARD_SUBTITLE)
            1 -> Intro(Copy.ONBOARD_HONEST_TITLE, Copy.ONBOARD_HONEST_BODY)
            2 -> Intro(Copy.ONBOARD_PRIVACY_TITLE, Copy.ONBOARD_PRIVACY_BODY)
            else -> PermissionsPage(state, context, viewModel::refresh)
        }

        Spacer(Modifier.height(Spacing.xxl))

        if (page < 3) {
            PrimaryAction(text = Copy.CONTINUE, onClick = { page++ })
        } else {
            PrimaryAction(
                text = Copy.ONBOARD_START,
                enabled = state.usageAccess && state.overlay,
                onClick = viewModel::complete,
            )
            if (!state.usageAccess || !state.overlay) {
                Text(
                    text = "Concede al menos los dos primeros para empezar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }

        if (page > 0) {
            TextButton(onClick = { page-- }) {
                Text(Copy.BACK, color = MaterialTheme.colorScheme.outline)
            }
        }

        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
private fun Intro(title: String, body: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(Spacing.lg))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun PermissionsPage(
    state: OnboardingUiState,
    context: Context,
    onChanged: () -> Unit,
) {
    Text(
        text = Copy.PERM_TITLE,
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(Spacing.lg))

    QuietCard {
        Perm(
            Copy.PERM_USAGE_TITLE,
            Copy.PERM_USAGE_BODY,
            state.usageAccess,
        ) { context.open(Permissions.usageAccessIntent(), onChanged) }

        Perm(
            Copy.PERM_OVERLAY_TITLE,
            Copy.PERM_OVERLAY_BODY,
            state.overlay,
        ) { context.open(Permissions.overlayIntent(context), onChanged) }

        Perm(
            Copy.PERM_ACCESSIBILITY_TITLE,
            Copy.PERM_ACCESSIBILITY_BODY,
            state.accessibility,
        ) { context.open(Permissions.accessibilityIntent(), onChanged) }

        Perm(
            Copy.PERM_BATTERY_TITLE,
            Copy.PERM_BATTERY_BODY,
            state.battery,
        ) { context.open(Permissions.batteryOptimizationIntent(context), onChanged) }
    }
}

@Composable
private fun Perm(title: String, body: String, granted: Boolean, onGrant: () -> Unit) {
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

private fun Context.open(intent: Intent, onChanged: () -> Unit) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    onChanged()
}
