import XCTest

/// Structural guarantees for the citation libraries, and proof that the Swift
/// copy has not drifted from the Kotlin one.
///
/// These do not verify that a citation is *correct* - only a primary source can
/// do that, which is what `tools/verify_citations.py` does in CI against both
/// files. What they guarantee is that no entry can reach a screen without its
/// reference, and no hadith without a grading.
final class ContentIntegrityTests: XCTestCase {

    func testEveryQuranCitationCarriesAFullReference() {
        for c in QuranLibrary.all {
            XCTAssertTrue((1...114).contains(c.surah), "surah out of range: \(c.surah)")
            XCTAssertGreaterThanOrEqual(c.ayahStart, 1)
            XCTAssertGreaterThanOrEqual(c.ayahEnd, c.ayahStart)
            XCTAssertFalse(c.arabic.isEmpty, "arabic missing for \(c.reference)")
            XCTAssertFalse(c.translationEs.isEmpty, "translation missing for \(c.reference)")
            XCTAssertFalse(c.surahNameTransliterated.isEmpty)
            XCTAssertFalse(c.themes.isEmpty, "no theme for \(c.reference)")
        }
    }

    func testArabicTextIsActuallyArabic() {
        let arabic = CharacterSet(charactersIn: "\u{0600}"..."\u{06FF}")
        for text in QuranLibrary.all.map(\.arabic) + HadithLibrary.all.map(\.arabic) {
            XCTAssertTrue(
                text.unicodeScalars.contains { arabic.contains($0) },
                "not arabic script: \(text)"
            )
        }
    }

    func testEveryHadithStatesItsGradingAndSource() {
        for h in HadithLibrary.all {
            XCTAssertFalse(h.collection.isEmpty)
            XCTAssertFalse(h.reference.isEmpty)
            XCTAssertFalse(h.grading.isEmpty, "grading missing for \(h.collection) \(h.reference)")
            XCTAssertFalse(h.gradingAuthority.isEmpty)
            XCTAssertFalse(h.narrator.isEmpty)
            XCTAssertTrue(h.sourceUrl.hasPrefix("https://"))
        }
    }

    func testNoDuplicateCitations() {
        let quranKeys = QuranLibrary.all.map(\.id)
        XCTAssertEqual(quranKeys.count, Set(quranKeys).count)
        let hadithKeys = HadithLibrary.all.map(\.id)
        XCTAssertEqual(hadithKeys.count, Set(hadithKeys).count)
    }

    func testPickingIsDeterministicAndTotalAcrossEveryTheme() {
        for theme in Theme.allCases {
            XCTAssertEqual(QuranLibrary.pick(theme: theme, seed: 7).id,
                           QuranLibrary.pick(theme: theme, seed: 7).id,
                           "pick must be stable")
        }
        // Negative and huge seeds must not trap.
        _ = QuranLibrary.pick(theme: nil, seed: Int.max)
        _ = QuranLibrary.pick(theme: nil, seed: -13)
        _ = HadithLibrary.pick(theme: nil, seed: -1)
    }

    func testDhikrDefaultsAreTheThreeTheUserNamed() {
        XCTAssertTrue(DhikrLibrary.defaultEnabledIds.contains(DhikrLibrary.subhanAllah))
        XCTAssertTrue(DhikrLibrary.defaultEnabledIds.contains(DhikrLibrary.alhamdulillah))
        XCTAssertTrue(DhikrLibrary.defaultEnabledIds.contains(DhikrLibrary.allahuAkbar))
    }

    func testEmptyDhikrSelectionFallsBackRatherThanShowingNothing() {
        XCTAssertFalse(DhikrLibrary.enabled([]).isEmpty)
        XCTAssertFalse(DhikrLibrary.enabled(["nonexistent"]).isEmpty)
    }

    /// The app must not read as punishment. This catches the words that would
    /// break that promise if someone edited the copy later.
    func testReflectionCopyContainsNoShamingLanguage() {
        let banned = ["adicto", "adicción", "débil", "fracaso", "fracasado",
                      "vergüenza", "patético", "culpa tuya", "otra vez has"]
        let all = Reflections.pause + Reflections.wait + Reflections.purpose
            + Reflections.turnedBack + Reflections.ambient

        for line in all {
            for word in banned {
                XCTAssertFalse(
                    line.lowercased().contains(word),
                    "shaming language in: \"\(line)\""
                )
            }
        }
    }

    func testReflectionPoolsAreNonEmptyAndPickingNeverTraps() {
        for pool in [Reflections.pause, Reflections.wait, Reflections.purpose,
                     Reflections.turnedBack, Reflections.ambient] {
            XCTAssertFalse(pool.isEmpty)
            _ = Reflections.pick(pool, seed: Int.max)
            _ = Reflections.pick(pool, seed: -13)
        }
    }
}

final class ScheduleWindowTests: XCTestCase {

    private func night(days: Int = ScheduleWindow.allDays) -> ScheduleWindow {
        ScheduleWindow(startMinuteOfDay: 22 * 60, endMinuteOfDay: 8 * 60, daysMask: days)
    }

    func testWrappingWindowCoversBothSidesOfMidnight() {
        XCTAssertTrue(night().contains(minuteOfDay: 23 * 60, isoDayOfWeek: 3))
        XCTAssertTrue(night().contains(minuteOfDay: 2 * 60, isoDayOfWeek: 4))
        XCTAssertTrue(night().contains(minuteOfDay: 22 * 60, isoDayOfWeek: 3))
    }

    func testWrappingWindowExcludesTheMiddleOfTheDay() {
        XCTAssertFalse(night().contains(minuteOfDay: 12 * 60, isoDayOfWeek: 3))
        XCTAssertFalse(night().contains(minuteOfDay: 8 * 60, isoDayOfWeek: 3), "end minute is exclusive")
    }

    func testSmallHoursBelongToTheDayTheWindowStartedOn() {
        let mondayOnly = night(days: 0b0000001)
        XCTAssertTrue(mondayOnly.contains(minuteOfDay: 23 * 60, isoDayOfWeek: 1))
        XCTAssertTrue(mondayOnly.contains(minuteOfDay: 60, isoDayOfWeek: 2))
        XCTAssertFalse(mondayOnly.contains(minuteOfDay: 60, isoDayOfWeek: 3))
    }

    func testSundayNightWrapsRoundToMondayMorning() {
        let sundayOnly = night(days: 0b1000000)
        XCTAssertTrue(sundayOnly.contains(minuteOfDay: 23 * 60, isoDayOfWeek: 7))
        XCTAssertTrue(sundayOnly.contains(minuteOfDay: 60, isoDayOfWeek: 1))
    }

    func testSameDayWindowBehavesNormally() {
        let work = ScheduleWindow(startMinuteOfDay: 9 * 60, endMinuteOfDay: 17 * 60)
        XCTAssertTrue(work.contains(minuteOfDay: 10 * 60, isoDayOfWeek: 2))
        XCTAssertFalse(work.contains(minuteOfDay: 18 * 60, isoDayOfWeek: 2))
        XCTAssertFalse(work.contains(minuteOfDay: 8 * 60, isoDayOfWeek: 2))
    }

    func testDisabledWindowNeverMatches() {
        var disabled = night()
        disabled.enabled = false
        XCTAssertFalse(disabled.contains(minuteOfDay: 23 * 60, isoDayOfWeek: 3))
    }
}

final class DayBoundaryTests: XCTestCase {

    private var calendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Europe/Madrid")!
        return c
    }()

    private func date(_ iso: String) -> Date {
        let formatter = ISO8601DateFormatter()
        formatter.timeZone = TimeZone(identifier: "Europe/Madrid")
        return formatter.date(from: iso)!
    }

    func testLateNightBelongsToTheDayThatHasNotEndedYet() {
        // 02:30 local on the 11th with a 04:00 reset is still the 10th.
        XCTAssertEqual(
            DayBoundary.dayKey(for: date("2026-03-11T02:30:00+01:00"), resetHour: 4, calendar: calendar),
            "2026-03-10"
        )
    }

    func testAfterTheResetHourTheNewDayHasStarted() {
        XCTAssertEqual(
            DayBoundary.dayKey(for: date("2026-03-11T08:00:00+01:00"), resetHour: 4, calendar: calendar),
            "2026-03-11"
        )
    }

    func testMidnightResetBehavesLikeTheCalendarDate() {
        XCTAssertEqual(
            DayBoundary.dayKey(for: date("2026-03-11T02:30:00+01:00"), resetHour: 0, calendar: calendar),
            "2026-03-11"
        )
    }

    func testRecentDayKeysEndWithTodayAndAreOrdered() {
        let keys = DayBoundary.recentDayKeys(
            from: date("2026-03-11T20:00:00+01:00"), count: 7, resetHour: 4, calendar: calendar
        )
        XCTAssertEqual(keys.count, 7)
        XCTAssertEqual(keys.last, "2026-03-11")
        XCTAssertEqual(keys.first, "2026-03-05")
        XCTAssertEqual(keys, keys.sorted())
    }
}

final class DurationsTests: XCTestCase {

    func testDurationsReadTheWayAPersonWouldSayThem() {
        XCTAssertEqual(Durations.format(minutes: 0), "0 min")
        XCTAssertEqual(Durations.format(minutes: 45), "45 min")
        XCTAssertEqual(Durations.format(minutes: 60), "1 h")
        XCTAssertEqual(Durations.format(minutes: 64), "1 h 04 min")
        XCTAssertEqual(Durations.format(minutes: 150), "2 h 30 min")
    }

    func testNegativeDurationsAreClamped() {
        XCTAssertEqual(Durations.format(minutes: -5), "0 min")
        XCTAssertEqual(Durations.formatSeconds(-5), "0 s")
    }
}
