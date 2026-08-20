package com.focusdhikr.ui.gate

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.focusdhikr.ui.theme.FocusDhikrTheme

/**
 * The pause screen.
 *
 * Launched over the app the user just opened. A few deliberate choices:
 *
 *  - `showWhenLocked` and `turnScreenOn` are NOT set: this should never wake a
 *    sleeping phone.
 *  - Back does not dismiss it. Back would be a one-tap bypass, which would make
 *    the whole thing theatre. Home still works, and is recorded as "abandoned"
 *    rather than as a decision either way.
 *  - `FLAG_SECURE` keeps what the user writes in phase 5 out of screenshots and
 *    the recents thumbnail.
 */
class GateActivity : ComponentActivity() {

    private val viewModel: GateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        viewModel.start(
            packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty(),
            appLabel = intent.getStringExtra(EXTRA_LABEL).orEmpty(),
            usedMillis = intent.getLongExtra(EXTRA_USED, 0),
            limitMillis = intent.getLongExtra(EXTRA_LIMIT, 0),
            strict = intent.getBooleanExtra(EXTRA_STRICT, false),
            reason = intent.getStringExtra(EXTRA_REASON) ?: REASON_LIMIT,
            windowLabel = intent.getStringExtra(EXTRA_WINDOW_LABEL).orEmpty(),
        )

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(enabled = true) {
                // Intentionally inert. See the class comment.
                override fun handleOnBackPressed() = Unit
            },
        )

        setContent {
            FocusDhikrTheme(forceDark = true) {
                GateScreen(
                    viewModel = viewModel,
                    onDismiss = { goHome() },
                    onOpenTargetApp = { packageName -> openTargetApp(packageName) },
                )
            }
        }
    }

    /**
     * The user was already inside the target app when we appeared, so simply
     * finishing puts them straight back into it. Sending them home instead makes
     * "turn back" mean what it says.
     */
    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finishAndRemoveTask()
    }

    private fun openTargetApp(packageName: String) {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
        }
        finishAndRemoveTask()
    }

    override fun onStop() {
        super.onStop()
        // Left without answering (home, power button, a call). Recorded as its
        // own outcome so the statistics do not claim a win that did not happen.
        if (!isFinishing) viewModel.abandon()
    }

    companion object {
        const val REASON_LIMIT = "limit"
        const val REASON_WINDOW = "window"

        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_USED = "used"
        private const val EXTRA_LIMIT = "limit"
        private const val EXTRA_STRICT = "strict"
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_WINDOW_LABEL = "window_label"

        fun intent(
            context: Context,
            packageName: String,
            appLabel: String,
            usedMillis: Long,
            limitMillis: Long,
            strict: Boolean,
            reason: String,
            windowLabel: String,
        ): Intent = Intent(context, GateActivity::class.java).apply {
            putExtra(EXTRA_PACKAGE, packageName)
            putExtra(EXTRA_LABEL, appLabel)
            putExtra(EXTRA_USED, usedMillis)
            putExtra(EXTRA_LIMIT, limitMillis)
            putExtra(EXTRA_STRICT, strict)
            putExtra(EXTRA_REASON, reason)
            putExtra(EXTRA_WINDOW_LABEL, windowLabel)
        }
    }
}
