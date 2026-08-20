package com.focusdhikr.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Runs [block] every time the screen comes back to the foreground.
 *
 * Needed because the permissions this app depends on are granted in system
 * settings, not in a dialog: the only way to know the user actually toggled
 * something is to re-check on resume.
 */
@Composable
fun OnResume(block: () -> Unit) {
    val current by rememberUpdatedState(block)
    val owner = LocalLifecycleOwner.current

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) current()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
