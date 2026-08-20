package com.focusdhikr.domain.usage

import com.focusdhikr.domain.model.BlockAttempt
import com.focusdhikr.domain.model.GateOutcome
import com.focusdhikr.domain.model.UsageDay

data class PeriodStats(
    val dayKeys: List<String>,
    val usedMillis: Long,
    val reclaimedMillis: Long,
    val turnedBackCount: Int,
    val continuedCount: Int,
    val emergencyCount: Int,
    val perApp: Map<String, Long>,
    val perDay: Map<String, Long>,
) {
    /** Turn-backs as a share of all decisions. Null when there is nothing to divide. */
    val turnBackRate: Float?
        get() {
            val decisions = turnedBackCount + continuedCount + emergencyCount
            return if (decisions == 0) null else turnedBackCount.toFloat() / decisions
        }
}

/**
 * Turns raw rows into the numbers the user sees.
 *
 * The framing is deliberate (requirement 7): the headline number is time
 * *reclaimed*, not time wasted. Same data, and the app is not trying to make
 * anyone feel bad about it.
 */
object StatsCalculator {

    /**
     * How long a session avoided is assumed to have lasted.
     *
     * Estimated from the user's own average session rather than a made-up
     * constant, so "you reclaimed 4 h 32 min" is grounded in their behaviour.
     * Falls back to [FALLBACK_SESSION_MILLIS] until there is enough history.
     */
    fun averageSessionMillis(usage: List<UsageDay>, attempts: List<BlockAttempt>): Long {
        val totalUsed = usage.sumOf { it.millis }
        val sessions = attempts.count { it.outcome != GateOutcome.TURNED_BACK }
        if (totalUsed <= 0 || sessions <= 0) return FALLBACK_SESSION_MILLIS
        return (totalUsed / sessions).coerceIn(MIN_SESSION_MILLIS, MAX_SESSION_MILLIS)
    }

    fun compute(
        dayKeys: List<String>,
        usage: List<UsageDay>,
        attempts: List<BlockAttempt>,
    ): PeriodStats {
        val keys = dayKeys.toSet()
        val scopedUsage = usage.filter { it.dayKey in keys }
        val scopedAttempts = attempts.filter { it.dayKey in keys }

        val turnedBack = scopedAttempts.count { it.outcome == GateOutcome.TURNED_BACK }
        val continued = scopedAttempts.count { it.outcome == GateOutcome.CHOSE_TO_CONTINUE }
        val emergency = scopedAttempts.count { it.outcome == GateOutcome.EMERGENCY_ACCESS }

        val perSession = averageSessionMillis(scopedUsage, scopedAttempts)

        return PeriodStats(
            dayKeys = dayKeys,
            usedMillis = scopedUsage.sumOf { it.millis },
            reclaimedMillis = turnedBack * perSession,
            turnedBackCount = turnedBack,
            continuedCount = continued,
            emergencyCount = emergency,
            perApp = scopedUsage
                .groupBy { it.packageName }
                .mapValues { (_, rows) -> rows.sumOf { it.millis } },
            perDay = dayKeys.associateWith { key ->
                scopedUsage.filter { it.dayKey == key }.sumOf { it.millis }
            },
        )
    }

    /** Ten minutes: a plausible short scroll, and a conservative estimate. */
    const val FALLBACK_SESSION_MILLIS: Long = 10 * 60_000L
    private const val MIN_SESSION_MILLIS: Long = 2 * 60_000L
    private const val MAX_SESSION_MILLIS: Long = 45 * 60_000L
}
