import XCTest

/// The rules that decide whether an app is behind the shield right now.
///
/// This is the part that has to be right when nobody is watching: it runs in a
/// background extension, minutes after midnight, on a phone that may have been
/// switched off since the last callback. It is written as pure functions over
/// plain values precisely so it can be tested here rather than by installing
/// the app and waiting an hour.
final class EnforcementTests: XCTestCase {

    private let instagram = "app.instagram"
    private let tiktok = "app.tiktok"

    private func night() -> ScheduleWindow {
        ScheduleWindow(startMinuteOfDay: 22 * 60, endMinuteOfDay: 8 * 60)
    }

    // MARK: - Daily limits

    func testAppUnderItsLimitIsNotShielded() {
        let result = ShieldDecision.evaluate(
            keys: [instagram],
            limits: [instagram: 60],
            defaultLimit: 60,
            usedMinutes: [instagram: 48],
            windows: [],
            minuteOfDay: 12 * 60,
            isoDayOfWeek: 3
        )
        XCTAssertTrue(result.isEmpty)
    }

    func testReachingTheLimitShieldsOnlyThatApp() {
        let result = ShieldDecision.evaluate(
            keys: [instagram, tiktok],
            limits: [instagram: 60, tiktok: 30],
            defaultLimit: 60,
            usedMinutes: [instagram: 60, tiktok: 15],
            windows: [],
            minuteOfDay: 12 * 60,
            isoDayOfWeek: 3
        )
        XCTAssertEqual(result[instagram], .limitReached)
        XCTAssertNil(result[tiktok])
    }

    func testEachAppKeepsItsOwnLimit() {
        // The whole point of per-app limits: 40 minutes is over TikTok's half
        // hour and well under Instagram's hour.
        let result = ShieldDecision.evaluate(
            keys: [instagram, tiktok],
            limits: [instagram: 60, tiktok: 30],
            defaultLimit: 60,
            usedMinutes: [instagram: 40, tiktok: 40],
            windows: [],
            minuteOfDay: 12 * 60,
            isoDayOfWeek: 3
        )
        XCTAssertNil(result[instagram])
        XCTAssertEqual(result[tiktok], .limitReached)
    }

    func testAppWithNoLimitOfItsOwnFallsBackToTheDefault() {
        let result = ShieldDecision.evaluate(
            keys: [tiktok],
            limits: [:],
            defaultLimit: 30,
            usedMinutes: [tiktok: 30],
            windows: [],
            minuteOfDay: 12 * 60,
            isoDayOfWeek: 3
        )
        XCTAssertEqual(result[tiktok], .limitReached)
    }

    // MARK: - Schedule windows

    func testWindowShieldsEvenWithTimeLeft() {
        let result = ShieldDecision.evaluate(
            keys: [instagram],
            limits: [instagram: 60],
            defaultLimit: 60,
            usedMinutes: [instagram: 0],
            windows: [night()],
            minuteOfDay: 23 * 60,
            isoDayOfWeek: 3
        )
        XCTAssertEqual(result[instagram], .scheduleWindow)
    }

    func testWindowCoversTheSmallHoursOfTheNextDay() {
        // 01:00 on Tuesday belongs to Monday's 22:00-08:00 window.
        let monday = ScheduleWindow(
            startMinuteOfDay: 22 * 60,
            endMinuteOfDay: 8 * 60,
            daysMask: 0b0000001
        )
        let result = ShieldDecision.evaluate(
            keys: [instagram],
            limits: [:],
            defaultLimit: 60,
            usedMinutes: [:],
            windows: [monday],
            minuteOfDay: 60,
            isoDayOfWeek: 2
        )
        XCTAssertEqual(result[instagram], .scheduleWindow)
    }

    func testWindowIgnoresDaysItWasNotAskedFor() {
        // iOS schedules have no weekday, so this check is the only thing making
        // "weekdays only" mean anything at all.
        let weekdays = ScheduleWindow(
            startMinuteOfDay: 9 * 60,
            endMinuteOfDay: 17 * 60,
            daysMask: ScheduleWindow.weekdays
        )
        let saturday = ShieldDecision.evaluate(
            keys: [instagram],
            limits: [:],
            defaultLimit: 60,
            usedMinutes: [:],
            windows: [weekdays],
            minuteOfDay: 12 * 60,
            isoDayOfWeek: 6
        )
        XCTAssertTrue(saturday.isEmpty)
    }

    func testWindowAppliesOnlyToTheAppsItNames() {
        let onlyInstagram = ScheduleWindow(
            startMinuteOfDay: 22 * 60,
            endMinuteOfDay: 8 * 60,
            appKeys: [instagram]
        )
        let result = ShieldDecision.evaluate(
            keys: [instagram, tiktok],
            limits: [:],
            defaultLimit: 60,
            usedMinutes: [:],
            windows: [onlyInstagram],
            minuteOfDay: 23 * 60,
            isoDayOfWeek: 3
        )
        XCTAssertEqual(result[instagram], .scheduleWindow)
        XCTAssertNil(result[tiktok])
    }

    func testDisabledWindowDoesNothing() {
        var window = night()
        window.enabled = false
        let result = ShieldDecision.evaluate(
            keys: [instagram],
            limits: [:],
            defaultLimit: 60,
            usedMinutes: [:],
            windows: [window],
            minuteOfDay: 23 * 60,
            isoDayOfWeek: 3
        )
        XCTAssertTrue(result.isEmpty)
    }

    func testBothRulesAtOnceAreReportedAsSuch() {
        let result = ShieldDecision.evaluate(
            keys: [instagram],
            limits: [instagram: 60],
            defaultLimit: 60,
            usedMinutes: [instagram: 90],
            windows: [night()],
            minuteOfDay: 23 * 60,
            isoDayOfWeek: 3
        )
        XCTAssertEqual(result[instagram], .limitAndWindow)
        XCTAssertTrue(result[instagram]?.isWindow == true)
    }

    // MARK: - Grants

    func testGrantLiftsOnlyTheAppItWasEarnedOn() {
        let result = ShieldDecision.evaluate(
            keys: [instagram, tiktok],
            limits: [instagram: 60, tiktok: 30],
            defaultLimit: 60,
            usedMinutes: [instagram: 90, tiktok: 90],
            windows: [],
            minuteOfDay: 12 * 60,
            isoDayOfWeek: 3,
            grantActive: true,
            grantedKey: instagram
        )
        XCTAssertNil(result[instagram])
        XCTAssertEqual(result[tiktok], .limitReached)
    }

    func testGrantFromAnUnnamedShieldLiftsEverything() {
        let result = ShieldDecision.evaluate(
            keys: [instagram, tiktok],
            limits: [:],
            defaultLimit: 1,
            usedMinutes: [instagram: 90, tiktok: 90],
            windows: [night()],
            minuteOfDay: 23 * 60,
            isoDayOfWeek: 3,
            grantActive: true,
            grantedKey: nil
        )
        XCTAssertTrue(result.isEmpty)
    }

    // MARK: - When does it come back

    func testMinutesUntilAllowedCrossesMidnight() {
        let minutes = ShieldDecision.minutesUntilAllowed(
            key: instagram,
            windows: [night()],
            minuteOfDay: 23 * 60,
            isoDayOfWeek: 3
        )
        // 23:00 to 08:00 is nine hours.
        XCTAssertEqual(minutes, 9 * 60)
    }

    func testMinutesUntilAllowedIsUnknownForAPlainLimit() {
        XCTAssertNil(
            ShieldDecision.minutesUntilAllowed(
                key: instagram,
                windows: [],
                minuteOfDay: 12 * 60,
                isoDayOfWeek: 3
            )
        )
    }
}

/// Thresholds are the only usage signal iOS gives us, so how they are laid out
/// decides how honest the home screen can be.
final class UsageFloorTests: XCTestCase {

    func testStagesAreAscendingAndEndAtTheLimit() {
        let stages = UsageFloor.stages(forLimit: 60)
        XCTAssertEqual(stages, stages.sorted())
        XCTAssertEqual(stages.last, 60)
        XCTAssertEqual(stages.count, 3)
    }

    func testShortLimitsGetASingleThreshold() {
        // Splitting five minutes three ways buys no resolution and costs
        // events, which iOS rations without saying how many.
        XCTAssertEqual(UsageFloor.stages(forLimit: 5), [5])
    }

    func testDetailedTrackingCanBeTurnedOff() {
        XCTAssertEqual(UsageFloor.stages(forLimit: 60, detailed: false), [60])
    }

    func testStagesNeverExceedTheLimit() {
        for limit in 1...240 {
            let stages = UsageFloor.stages(forLimit: limit)
            XCTAssertFalse(stages.isEmpty, "limit \(limit)")
            XCTAssertEqual(Set(stages).count, stages.count, "limit \(limit) repeated a threshold")
            XCTAssertLessThanOrEqual(stages.max() ?? 0, limit, "limit \(limit)")
            XCTAssertGreaterThan(stages.min() ?? 0, 0, "limit \(limit)")
        }
    }

    func testWordingNeverClaimsPrecisionItDoesNotHave() {
        XCTAssertTrue(UsageFloor.describe(minutes: 48, limit: 60).hasPrefix("Al menos"))
        XCTAssertTrue(UsageFloor.describe(minutes: 60, limit: 60).contains("límite"))
        XCTAssertEqual(UsageFloor.describe(minutes: 0, limit: 60), "Aún sin datos de hoy")
    }
}

/// The event name is the only channel between the app and the monitor
/// extension: the callback carries a name and nothing else.
final class TokenKeyTests: XCTestCase {

    func testEventNameRoundTrips() {
        let raw = TokenKey.eventRawValue(key: "abc-_123", thresholdMinutes: 48)
        let parsed = TokenKey.parseEvent(raw)
        XCTAssertEqual(parsed?.key, "abc-_123")
        XCTAssertEqual(parsed?.thresholdMinutes, 48)
    }

    func testGarbageEventNameIsRejectedRatherThanGuessed() {
        XCTAssertNil(TokenKey.parseEvent("nonsense"))
        XCTAssertNil(TokenKey.parseEvent("key#notanumber"))
    }

    func testWindowActivityNameRoundTrips() {
        let id = UUID()
        let raw = TokenKey.windowActivityRawValue(id)
        XCTAssertEqual(TokenKey.parseWindowActivity(raw), String(id.uuidString.prefix(8)))
        XCTAssertNil(TokenKey.parseWindowActivity("focusdhikr.daily"))
    }
}

/// Streaks, defined so they cannot flatter the user.
final class StreakTests: XCTestCase {

    private func attempt(_ day: String, _ outcome: GateOutcome) -> GateAttempt {
        GateAttempt(
            dayKey: day,
            startedAt: Date(),
            reachedPhase: GatePhase.pause.rawValue,
            outcome: outcome.rawValue
        )
    }

    func testConsecutiveTurnBacksBuildAStreak() {
        let days = ["2026-08-01", "2026-08-02", "2026-08-03"]
        let result = Streaks.compute(
            attempts: days.map { attempt($0, .turnedBack) },
            dayKeys: days
        )
        XCTAssertEqual(result.current, 3)
        XCTAssertEqual(result.best, 3)
    }

    func testPushingThroughBreaksTheStreakButKeepsTheBest() {
        let days = ["2026-08-01", "2026-08-02", "2026-08-03"]
        let result = Streaks.compute(
            attempts: [
                attempt(days[0], .turnedBack),
                attempt(days[1], .turnedBack),
                attempt(days[2], .choseToContinue),
            ],
            dayKeys: days
        )
        XCTAssertEqual(result.current, 0)
        XCTAssertEqual(result.best, 2)
    }

    func testADayWithNothingRecordedNeitherBreaksNorExtends() {
        let days = ["2026-08-01", "2026-08-02", "2026-08-03"]
        let result = Streaks.compute(
            attempts: [attempt(days[0], .turnedBack), attempt(days[2], .turnedBack)],
            dayKeys: days
        )
        XCTAssertEqual(result.current, 2)
    }

    func testEmergencyAccessCountsAsPushingThrough() {
        let days = ["2026-08-01", "2026-08-02"]
        let result = Streaks.compute(
            attempts: [attempt(days[0], .turnedBack), attempt(days[1], .emergencyAccess)],
            dayKeys: days
        )
        XCTAssertEqual(result.current, 0)
    }
}

/// Windows are stored, so they have to survive the app gaining fields.
final class ScheduleWindowTests: XCTestCase {

    func testDecodingAWindowSavedBeforeAppKeysExisted() throws {
        let legacy = """
        {"id":"11111111-1111-1111-1111-111111111111","startMinuteOfDay":1320,\
        "endMinuteOfDay":480,"daysMask":127,"enabled":true}
        """
        let window = try JSONDecoder().decode(ScheduleWindow.self, from: Data(legacy.utf8))
        XCTAssertEqual(window.startMinuteOfDay, 1320)
        XCTAssertTrue(window.appKeys.isEmpty)
        XCTAssertTrue(window.covers(key: "anything"))
    }

    func testDurationCrossesMidnight() {
        XCTAssertEqual(
            ScheduleWindow(startMinuteOfDay: 22 * 60, endMinuteOfDay: 8 * 60).durationMinutes,
            10 * 60
        )
        XCTAssertEqual(
            ScheduleWindow(startMinuteOfDay: 9 * 60, endMinuteOfDay: 17 * 60).durationMinutes,
            8 * 60
        )
    }

    func testClockAndDayDescriptions() {
        let window = ScheduleWindow(
            startMinuteOfDay: 22 * 60,
            endMinuteOfDay: 8 * 60,
            daysMask: ScheduleWindow.weekdays
        )
        XCTAssertEqual(window.clockDescription, "22:00 - 08:00")
        XCTAssertEqual(window.daysDescription, "De lunes a viernes")
    }
}
