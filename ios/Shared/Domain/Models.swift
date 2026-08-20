import Foundation

/// Something the user actually wants their time to go to.
public struct Goal: Identifiable, Codable, Equatable, Sendable {
    public var id: UUID
    public var title: String
    public var note: String
    public var active: Bool

    public init(id: UUID = UUID(), title: String, note: String = "", active: Bool = true) {
        self.id = id
        self.title = title
        self.note = note
        self.active = active
    }
}

/// A recurring window during which apps are off-limits regardless of remaining
/// time, e.g. 22:00-08:00.
public struct ScheduleWindow: Identifiable, Codable, Equatable, Sendable {
    public var id: UUID
    public var startMinuteOfDay: Int
    public var endMinuteOfDay: Int
    /// Bit 0 = Monday ... bit 6 = Sunday.
    public var daysMask: Int
    public var enabled: Bool

    public static let allDays = 0b1111111

    public init(
        id: UUID = UUID(),
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        daysMask: Int = ScheduleWindow.allDays,
        enabled: Bool = true
    ) {
        self.id = id
        self.startMinuteOfDay = startMinuteOfDay
        self.endMinuteOfDay = endMinuteOfDay
        self.daysMask = daysMask
        self.enabled = enabled
    }

    /// - Parameters:
    ///   - minuteOfDay: minutes since local midnight.
    ///   - isoDayOfWeek: 1 = Monday ... 7 = Sunday.
    public func contains(minuteOfDay: Int, isoDayOfWeek: Int) -> Bool {
        guard enabled else { return false }
        let wraps = endMinuteOfDay <= startMinuteOfDay
        let inClockRange = wraps
            ? (minuteOfDay >= startMinuteOfDay || minuteOfDay < endMinuteOfDay)
            : (minuteOfDay >= startMinuteOfDay && minuteOfDay < endMinuteOfDay)
        guard inClockRange else { return false }

        // For a wrapping window the small-hours half belongs to the day the
        // window *started* on, so 01:00 on Tuesday is covered by a Monday window.
        let effectiveDay: Int
        if wraps && minuteOfDay < endMinuteOfDay {
            effectiveDay = isoDayOfWeek == 1 ? 7 : isoDayOfWeek - 1
        } else {
            effectiveDay = isoDayOfWeek
        }
        return daysMask & (1 << (effectiveDay - 1)) != 0
    }
}

/// One recorded pass through the gate. Local only, never leaves the device.
public struct GateAttempt: Identifiable, Codable, Equatable, Sendable {
    public var id: UUID
    public var dayKey: String
    public var startedAt: Date
    public var endedAt: Date?
    public var reachedPhase: String
    public var outcome: String?
    public var intentReason: String?
    /// Nil when the "keep my written reasons" setting is off.
    public var writtenReason: String?

    public init(
        id: UUID = UUID(),
        dayKey: String,
        startedAt: Date,
        endedAt: Date? = nil,
        reachedPhase: String,
        outcome: String? = nil,
        intentReason: String? = nil,
        writtenReason: String? = nil
    ) {
        self.id = id
        self.dayKey = dayKey
        self.startedAt = startedAt
        self.endedAt = endedAt
        self.reachedPhase = reachedPhase
        self.outcome = outcome
        self.intentReason = intentReason
        self.writtenReason = writtenReason
    }
}

/// A "day" does not necessarily start at midnight.
///
/// If you go to bed at 02:00, a midnight reset splits your night in two and
/// hands back a fresh allowance at exactly the worst moment.
public enum DayBoundary {

    public static let defaultResetHour = 4

    public static func dayKey(
        for date: Date,
        resetHour: Int = defaultResetHour,
        calendar: Calendar = .current
    ) -> String {
        let shifted = calendar.date(byAdding: .hour, value: -resetHour, to: date) ?? date
        let parts = calendar.dateComponents([.year, .month, .day], from: shifted)
        return String(
            format: "%04d-%02d-%02d",
            parts.year ?? 0, parts.month ?? 0, parts.day ?? 0
        )
    }

    /// The `count` most recent logical day keys, oldest first, ending today.
    public static func recentDayKeys(
        from date: Date = Date(),
        count: Int,
        resetHour: Int = defaultResetHour,
        calendar: Calendar = .current
    ) -> [String] {
        precondition(count > 0, "count must be positive")
        return (0..<count).reversed().compactMap { offset in
            calendar.date(byAdding: .day, value: -offset, to: date).map {
                dayKey(for: $0, resetHour: resetHour, calendar: calendar)
            }
        }
    }
}

/// Human formatting for durations.
///
/// Deliberately terse and neutral: "1 h 04 min", never "you wasted 1 h 04 min".
public enum Durations {

    public static func format(minutes: Int) -> String {
        let safe = max(minutes, 0)
        let hours = safe / 60
        let mins = safe % 60
        switch (hours, mins) {
        case let (h, m) where h > 0 && m > 0:
            return "\(h) h \(String(format: "%02d", m)) min"
        case let (h, _) where h > 0:
            return "\(h) h"
        default:
            return "\(mins) min"
        }
    }

    public static func formatSeconds(_ seconds: Int) -> String {
        let safe = max(seconds, 0)
        return safe >= 60
            ? "\(safe / 60):\(String(format: "%02d", safe % 60))"
            : "\(safe) s"
    }
}
