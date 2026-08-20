package com.focusdhikr.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.focusdhikr.core.Permissions

/**
 * Last-resort blocker.
 *
 * Some OEM builds (MIUI, EMUI and friends) throttle background Activity starts
 * beyond what AOSP documents, so [BlockCoordinator] can fail to open the real
 * gate. Rather than silently letting the app through, we draw a plain overlay on
 * top of it: the user is still interrupted, and is told to open Focus Dhikr.
 *
 * Deliberately built with plain views, not Compose, so it has no lifecycle
 * owner requirements and can be shown from any context.
 */
object BlockOverlay {

    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null

    fun show(context: Context, appLabel: String, autoDismissMillis: Long = 6_000L) {
        if (!Permissions.canDrawOverlays(context)) return

        main.post {
            if (view != null) return@post
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return@post

            val content = buildView(context, appLabel)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.CENTER }

            runCatching { wm.addView(content, params) }
                .onSuccess { view = content }

            main.postDelayed({ hide(context) }, autoDismissMillis)
        }
    }

    fun hide(context: Context) {
        main.post {
            val current = view ?: return@post
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            runCatching { wm?.removeView(current) }
            view = null
        }
    }

    private fun buildView(context: Context, appLabel: String): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F2100E0C"))
            val pad = dp(context, 32)
            setPadding(pad, pad, pad, pad)

            addView(
                TextView(context).apply {
                    text = "Pausa"
                    setTextColor(Color.parseColor("#E8E3D8"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
                    gravity = Gravity.CENTER
                }
            )
            addView(
                TextView(context).apply {
                    text = "Habías decidido limitar $appLabel.\n\n" +
                        "Abre Focus Dhikr para continuar."
                    setTextColor(Color.parseColor("#A8A199"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(context, 16), 0, 0)
                }
            )
        }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
