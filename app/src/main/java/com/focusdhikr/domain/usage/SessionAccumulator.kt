package com.focusdhikr.domain.usage

/** A foreground transition, decoupled from android.app.usage.UsageEvents. */
data class ForegroundEvent(
    val packageName: String,
    val timestamp: Long,
    val resumed: Boolean,
)

/**
 * Result of folding a batch of events into per-app durations.
 *
 * [openSessions] carries apps that were resumed but not yet paused when the
 * batch ended, so the next batch can continue counting them instead of losing
 * the time or double counting it.
 */
data class AccumulationResult(
    val addedMillis: Map<String, Long>,
    val openSessions: Map<String, Long>,
    val lastEventTimestamp: Long,
)

/**
 * Turns the raw ACTIVITY_RESUMED / ACTIVITY_PAUSED stream into real time per app.
 *
 * Why this exists instead of queryUsageStats().totalTimeInForeground: that field
 * is bucketed per interval, its precision varies by OEM, and it cannot be
 * resumed from a watermark. Folding raw events lets the counter be idempotent -
 * if the phone was off for three hours, the next pass recovers those three hours
 * exactly once.
 *
 * Pure JVM so the accounting can be tested without a device.
 */
object SessionAccumulator {

    /**
     * @param events must be sorted by timestamp ascending.
     * @param until upper bound of the batch. Sessions still open at [until] are
     *   credited up to [until] and reported in [AccumulationResult.openSessions].
     * @param openSessions package -> timestamp at which it was resumed, carried
     *   over from the previous call.
     * @param maxSessionMillis defensive cap. A session longer than this means we
     *   missed a PAUSE (process death, event retention loss) and crediting it
     *   whole would produce absurd numbers.
     */
    fun accumulate(
        events: List<ForegroundEvent>,
        until: Long,
        openSessions: Map<String, Long> = emptyMap(),
        maxSessionMillis: Long = DEFAULT_MAX_SESSION_MILLIS,
    ): AccumulationResult {
        val added = mutableMapOf<String, Long>()
        val open = openSessions.toMutableMap()
        var lastTimestamp = 0L

        for (event in events) {
            lastTimestamp = maxOf(lastTimestamp, event.timestamp)
            if (event.resumed) {
                // A second RESUMED without a PAUSE means we lost the PAUSE; close
                // the previous stretch at this point rather than dropping it.
                open[event.packageName]?.let { startedAt ->
                    credit(added, event.packageName, startedAt, event.timestamp, maxSessionMillis)
                }
                open[event.packageName] = event.timestamp
            } else {
                val startedAt = open.remove(event.packageName) ?: continue
                credit(added, event.packageName, startedAt, event.timestamp, maxSessionMillis)
            }
        }

        // Credit the still-open sessions up to `until`, then re-anchor them there
        // so the next batch does not count the same milliseconds again.
        val carried = mutableMapOf<String, Long>()
        for ((pkg, startedAt) in open) {
            credit(added, pkg, startedAt, until, maxSessionMillis)
            carried[pkg] = until
        }

        return AccumulationResult(
            addedMillis = added,
            openSessions = carried,
            lastEventTimestamp = lastTimestamp,
        )
    }

    private fun credit(
        into: MutableMap<String, Long>,
        packageName: String,
        from: Long,
        to: Long,
        maxSessionMillis: Long,
    ) {
        val delta = (to - from).coerceAtLeast(0).coerceAtMost(maxSessionMillis)
        if (delta > 0) {
            into[packageName] = (into[packageName] ?: 0L) + delta
        }
    }

    /** Six hours. Longer than any plausible uninterrupted single-app session. */
    const val DEFAULT_MAX_SESSION_MILLIS: Long = 6 * 60 * 60 * 1000L
}
