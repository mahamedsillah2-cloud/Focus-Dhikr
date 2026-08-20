import Foundation

/// Why a given app is behind the shield right now.
///
/// The app needs the reason, not just the fact: "has llegado a tu límite" and
/// "estás dentro de tu franja de 22:00 a 08:00" are different sentences, and
/// showing the wrong one makes the whole pause feel automated rather than
/// like something the user set up themselves.
public enum ShieldReason: String, Equatable, Sendable {
    case limitReached
    case scheduleWindow
    /// Both at once. The window wins the wording: it is the harder rule.
    case limitAndWindow

    public var isWindow: Bool { self != .limitReached }
}

/// Decides which apps should be shielded, from plain values.
///
/// Deliberately free of FamilyControls types: it takes token *keys* rather than
/// `ApplicationToken`s, so the whole rule set can be unit-tested on a machine
/// with no Screen Time entitlement. `ScreenTimeController` maps keys back to
/// tokens at the edge.
public enum ShieldDecision {

    /// - Parameters:
    ///   - keys: stable key of every app the user chose to limit.
    ///   - limits: per-app daily limit in minutes, keyed like `keys`.
    ///   - usedMinutes: floor of minutes used today, from crossed thresholds.
    ///   - minuteOfDay: minutes since local midnight.
    ///   - isoDayOfWeek: 1 = Monday ... 7 = Sunday.
    ///   - grantedKey: the app the user earned a few minutes on, if known. A
    ///     grant from a shield that could not name its app lifts everything,
    ///     which is the old behaviour and still the safe fallback.
    public static func evaluate(
        keys: [String],
        limits: [String: Int],
        defaultLimit: Int,
        usedMinutes: [String: Int],
        windows: [ScheduleWindow],
        minuteOfDay: Int,
        isoDayOfWeek: Int,
        grantActive: Bool = false,
        grantedKey: String? = nil
    ) -> [String: ShieldReason] {
        // A live grant is the one thing that lifts a shield: the user just
        // spent a minute of their life earning it and the app said yes. It
        // applies to the app they earned it on, not to everything they ever
        // limited - going through the gate for Instagram should not quietly
        // open TikTok too.
        if grantActive, grantedKey == nil { return [:] }

        let active = windows.filter {
            $0.contains(minuteOfDay: minuteOfDay, isoDayOfWeek: isoDayOfWeek)
        }

        var result: [String: ShieldReason] = [:]
        for key in keys {
            if grantActive, key == grantedKey { continue }
            let limit = limits[key] ?? defaultLimit
            let overLimit = limit > 0 && (usedMinutes[key] ?? 0) >= limit
            let inWindow = active.contains { $0.covers(key: key) }

            switch (overLimit, inWindow) {
            case (true, true): result[key] = .limitAndWindow
            case (false, true): result[key] = .scheduleWindow
            case (true, false): result[key] = .limitReached
            case (false, false): break
            }
        }
        return result
    }

    /// Minutes until the app is allowed again, when that is knowable.
    ///
    /// Returns nil for a plain daily limit: the answer is "when your day
    /// resets", which the caller words itself, and pretending to a minute of
    /// precision we do not have would be a lie.
    public static func minutesUntilAllowed(
        key: String,
        windows: [ScheduleWindow],
        minuteOfDay: Int,
        isoDayOfWeek: Int
    ) -> Int? {
        let active = windows
            .filter { $0.covers(key: key) && $0.contains(minuteOfDay: minuteOfDay, isoDayOfWeek: isoDayOfWeek) }
        guard !active.isEmpty else { return nil }

        return active.map { window -> Int in
            let end = window.endMinuteOfDay
            return end > minuteOfDay ? end - minuteOfDay : (1440 - minuteOfDay) + end
        }.max()
    }
}

/// Turns crossed thresholds into an honest "at least N minutes".
///
/// iOS never reports minutes. It reports "the 48-minute threshold was crossed".
/// So every number this app shows is a floor, and the UI says so out loud
/// rather than rounding a guess into something that looks like a measurement.
public enum UsageFloor {

    /// The thresholds registered for a limit, in minutes, ascending.
    ///
    /// Three points is the compromise: enough resolution that the home screen
    /// means something, few enough events that a handful of apps stays well
    /// inside whatever undocumented ceiling `startMonitoring` enforces.
    public static func stages(forLimit limit: Int, detailed: Bool = true) -> [Int] {
        let capped = max(limit, 1)
        guard detailed, capped >= 10 else { return [capped] }
        let half = max(capped / 2, 1)
        let most = max((capped * 4) / 5, half + 1)
        return Array(Set([half, most, capped])).sorted().filter { $0 <= capped }
    }

    /// How the app words a floor. Never "45 min", always "al menos 45 min".
    public static func describe(minutes: Int, limit: Int) -> String {
        guard minutes > 0 else { return "Aún sin datos de hoy" }
        return minutes >= limit
            ? "Has llegado a tu límite de \(Durations.format(minutes: limit))"
            : "Al menos \(Durations.format(minutes: minutes)) de \(Durations.format(minutes: limit))"
    }
}
