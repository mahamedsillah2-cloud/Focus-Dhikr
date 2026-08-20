import Foundation

/// Days of the week as a bit field, bit 0 = Monday.
public enum DaysMask {
    public static func describe(_ mask: Int) -> String {
        switch mask {
        case ScheduleWindow.allDays: return "Todos los días"
        case ScheduleWindow.weekdays: return "De lunes a viernes"
        case ScheduleWindow.weekend: return "Fines de semana"
        default:
            let names = ["L", "M", "X", "J", "V", "S", "D"]
            let picked = (0..<7).filter { mask & (1 << $0) != 0 }.map { names[$0] }
            return picked.isEmpty ? "Ningún día" : picked.joined(separator: " · ")
        }
    }
}

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

    /// Apps this window applies to, by token key. Empty means "all of them",
    /// which is both the useful default and what survives the user changing
    /// their selection later.
    public var appKeys: [String]

    public var label: String

    public static let allDays = 0b1111111
    public static let weekdays = 0b0011111
    public static let weekend = 0b1100000

    public init(
        id: UUID = UUID(),
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        daysMask: Int = ScheduleWindow.allDays,
        enabled: Bool = true,
        appKeys: [String] = [],
        label: String = ""
    ) {
        self.id = id
        self.startMinuteOfDay = startMinuteOfDay
        self.endMinuteOfDay = endMinuteOfDay
        self.daysMask = daysMask
        self.enabled = enabled
        self.appKeys = appKeys
        self.label = label
    }

    /// Older stored windows have no `appKeys`/`label`; decoding must not throw
    /// and lose the user's schedule because the app gained a field.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(UUID.self, forKey: .id)
        startMinuteOfDay = try c.decode(Int.self, forKey: .startMinuteOfDay)
        endMinuteOfDay = try c.decode(Int.self, forKey: .endMinuteOfDay)
        daysMask = try c.decode(Int.self, forKey: .daysMask)
        enabled = try c.decode(Bool.self, forKey: .enabled)
        appKeys = try c.decodeIfPresent([String].self, forKey: .appKeys) ?? []
        label = try c.decodeIfPresent(String.self, forKey: .label) ?? ""
    }

    public func covers(key: String) -> Bool {
        appKeys.isEmpty || appKeys.contains(key)
    }

    /// "22:00 - 08:00"
    public var clockDescription: String {
        func hhmm(_ minute: Int) -> String {
            String(format: "%02d:%02d", (minute / 60) % 24, minute % 60)
        }
        return "\(hhmm(startMinuteOfDay)) - \(hhmm(endMinuteOfDay))"
    }

    public var daysDescription: String { DaysMask.describe(daysMask) }

    /// How long the window lasts. The system refuses very short schedules, so
    /// the editor uses this to keep the user out of a state iOS will reject.
    public var durationMinutes: Int {
        endMinuteOfDay > startMinuteOfDay
            ? endMinuteOfDay - startMinuteOfDay
            : (1440 - startMinuteOfDay) + endMinuteOfDay
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

    /// `Calendar.weekday` is 1 = Sunday; every model here uses ISO 1 = Monday.
    public static func isoDayOfWeek(fromCalendarWeekday weekday: Int) -> Int {
        (weekday + 5) % 7 + 1
    }

    /// Minutes since local midnight, and the ISO weekday, for one date.
    public static func clock(
        for date: Date,
        calendar: Calendar = .current
    ) -> (minuteOfDay: Int, isoDayOfWeek: Int) {
        let parts = calendar.dateComponents([.hour, .minute, .weekday], from: date)
        return (
            (parts.hour ?? 0) * 60 + (parts.minute ?? 0),
            isoDayOfWeek(fromCalendarWeekday: parts.weekday ?? 1)
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

/// A configurable nudge. Discreet by design: the user picks the moment, the
/// app never invents one.
public struct Reminder: Identifiable, Codable, Equatable, Sendable {

    public enum Kind: String, Codable, CaseIterable, Sendable {
        case goals
        case rest
        case dhikr
        case reflection
        case screenTime

        public var labelEs: String {
            switch self {
            case .goals: return "Tus objetivos"
            case .rest: return "Descanso"
            case .dhikr: return "Dhikr"
            case .reflection: return "Reflexión"
            case .screenTime: return "Tiempo de pantalla"
            }
        }

        public var symbol: String {
            switch self {
            case .goals: return "target"
            case .rest: return "leaf"
            case .dhikr: return "moon.stars"
            case .reflection: return "text.quote"
            case .screenTime: return "hourglass"
            }
        }
    }

    public var id: UUID
    public var kind: Kind
    public var minuteOfDay: Int
    public var daysMask: Int
    public var enabled: Bool

    public init(
        id: UUID = UUID(),
        kind: Kind,
        minuteOfDay: Int,
        daysMask: Int = ScheduleWindow.allDays,
        enabled: Bool = true
    ) {
        self.id = id
        self.kind = kind
        self.minuteOfDay = minuteOfDay
        self.daysMask = daysMask
        self.enabled = enabled
    }

    public var clockDescription: String {
        String(format: "%02d:%02d", (minuteOfDay / 60) % 24, minuteOfDay % 60)
    }

    public var daysDescription: String { DaysMask.describe(daysMask) }

    /// ISO weekdays this reminder fires on, 1 = Monday ... 7 = Sunday.
    public var isoDays: [Int] {
        (0..<7).filter { daysMask & (1 << $0) != 0 }.map { $0 + 1 }
    }
}

/// Consecutive-day counts, computed from the gate history.
///
/// The definition is deliberately generous: a day counts if you turned back at
/// least once and never pushed through. A day you never opened a limited app at
/// all is neutral - it neither breaks the streak nor extends it, because the
/// app has no way of knowing whether you were disciplined or just busy, and
/// inventing the difference would make the number a lie.
public enum Streaks {

    public struct Result: Equatable, Sendable {
        public var current: Int
        public var best: Int
        public init(current: Int, best: Int) {
            self.current = current
            self.best = best
        }
    }

    public static func compute(attempts: [GateAttempt], dayKeys: [String]) -> Result {
        var current = 0
        var best = 0
        var running = 0

        for key in dayKeys {
            let day = attempts.filter { $0.dayKey == key }
            let turnedBack = day.contains { $0.outcome == GateOutcome.turnedBack.rawValue }
            let pushedThrough = day.contains {
                $0.outcome == GateOutcome.choseToContinue.rawValue
                    || $0.outcome == GateOutcome.emergencyAccess.rawValue
            }

            if pushedThrough {
                running = 0
            } else if turnedBack {
                running += 1
            }
            best = max(best, running)
        }
        // `dayKeys` arrives oldest first, so whatever is running at the end is
        // the streak still alive today.
        current = running
        return Result(current: current, best: best)
    }
}
