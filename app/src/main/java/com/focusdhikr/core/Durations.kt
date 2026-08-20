package com.focusdhikr.core

import kotlin.math.roundToInt

/**
 * Human formatting for durations.
 *
 * Deliberately terse and neutral: "1 h 04 min", never "you wasted 1 h 04 min".
 * The app reports, it does not scold. See docs/ARQUITECTURA.md section 4.
 */
object Durations {

    fun format(millis: Long): String {
        val safe = millis.coerceAtLeast(0)
        val totalMinutes = safe / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "$hours h ${minutes.toString().padStart(2, '0')} min"
            hours > 0 -> "$hours h"
            totalMinutes > 0 -> "$minutes min"
            safe > 0 -> "menos de 1 min"
            else -> "0 min"
        }
    }

    /** Compact form for dense rows: "1:04", "0:12". */
    fun formatCompact(millis: Long): String {
        val totalMinutes = millis.coerceAtLeast(0) / 60_000
        return "${totalMinutes / 60}:${(totalMinutes % 60).toString().padStart(2, '0')}"
    }

    fun formatSeconds(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        return if (safe >= 60) "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}" else "$safe s"
    }

    fun minutesToMillis(minutes: Int): Long = minutes * 60_000L

    fun millisToMinutes(millis: Long): Int = (millis / 60_000.0).roundToInt()
}
