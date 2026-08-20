package com.focusdhikr.data.repo

import com.focusdhikr.data.db.BlockAttemptEntity
import com.focusdhikr.data.db.GoalEntity
import com.focusdhikr.data.db.ScheduleWindowEntity
import com.focusdhikr.data.db.TrackedAppEntity
import com.focusdhikr.data.db.UsageDayEntity
import com.focusdhikr.domain.model.BlockAttempt
import com.focusdhikr.domain.model.GateOutcome
import com.focusdhikr.domain.model.Goal
import com.focusdhikr.domain.model.IntentReason
import com.focusdhikr.domain.model.ScheduleWindow
import com.focusdhikr.domain.model.TrackedApp
import com.focusdhikr.domain.model.UsageDay

internal fun TrackedAppEntity.toDomain() = TrackedApp(
    packageName = packageName,
    label = label,
    dailyLimitMillis = dailyLimitMillis,
    enabled = enabled,
    strict = strict,
)

internal fun TrackedApp.toEntity(createdAt: Long) = TrackedAppEntity(
    packageName = packageName,
    label = label,
    dailyLimitMillis = dailyLimitMillis,
    enabled = enabled,
    strict = strict,
    createdAt = createdAt,
)

internal fun UsageDayEntity.toDomain() = UsageDay(packageName, dayKey, millis)

internal fun GoalEntity.toDomain() = Goal(id = id, title = title, note = note, active = active)

internal fun ScheduleWindowEntity.toDomain() = ScheduleWindow(
    id = id,
    packageName = packageName,
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
    daysMask = daysMask,
    enabled = enabled,
    label = label,
)

internal fun ScheduleWindow.toEntity() = ScheduleWindowEntity(
    id = id,
    packageName = packageName,
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
    daysMask = daysMask,
    enabled = enabled,
    label = label,
)

internal fun BlockAttemptEntity.toDomain() = BlockAttempt(
    id = id,
    packageName = packageName,
    dayKey = dayKey,
    startedAt = startedAt,
    endedAt = endedAt,
    reachedPhase = reachedPhase,
    outcome = outcome?.let { name -> runCatching { GateOutcome.valueOf(name) }.getOrNull() },
    intentReason = intentReason?.let { name -> runCatching { IntentReason.valueOf(name) }.getOrNull() },
)
