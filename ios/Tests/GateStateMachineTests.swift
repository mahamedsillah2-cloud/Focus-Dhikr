import XCTest

/// The gate is the whole product: everything else is settings around it.
///
/// It also runs at the moment the user is least patient and most impulsive, so
/// every phase transition is asserted here rather than trusted.
final class GateStateMachineTests: XCTestCase {

    private func state(strict: Bool = false, attemptsToday: Int = 0, inWindow: Bool = false) -> GateState {
        GateStateMachine.initial(
            GateConfig.forAttempt(strict: strict, attemptsToday: attemptsToday, inScheduleWindow: inWindow)
        )
    }

    private func run(_ start: GateState, _ events: GateEvent...) -> GateState {
        events.reduce(start) { GateStateMachine.reduce($0, $1) }
    }

    // MARK: - Turning back is always available

    func testTurningBackResolvesFromAnyPhase() {
        for phase in GatePhase.allCases where phase != .resolved {
            var s = state(strict: true)
            s.phase = phase
            let out = GateStateMachine.reduce(s, .turnBack)
            XCTAssertEqual(out.phase, .resolved)
            XCTAssertEqual(out.outcome, .turnedBack)
        }
    }

    func testResolvedGateIgnoresFurtherEvents() {
        let resolved = run(state(), .turnBack)
        let after = run(resolved, .advance, .tick, .editFreeText("x"))
        XCTAssertEqual(resolved, after)
    }

    // MARK: - Phase 1 -> 2

    func testFirstContinueMovesToIntentAndDoesNotOpenTheApp() {
        let s = run(state(), .advance)
        XCTAssertEqual(s.phase, .intent)
        XCTAssertNotEqual(s.outcome, .choseToContinue)
    }

    // MARK: - Phase 2 needs both answers

    func testIntentPhaseNeedsReasonAndAlignment() {
        var s = run(state(), .advance)
        XCTAssertFalse(s.canAdvance)

        s = run(s, .chooseIntent(.bored))
        XCTAssertFalse(s.canAdvance, "reason alone is not enough")

        s = run(s, .answerAlignment(false))
        XCTAssertTrue(s.canAdvance)
    }

    // MARK: - Phase 3 is a real wait

    func testWaitCannotBeSkippedByPressingContinue() {
        var s = run(state(), .advance, .chooseIntent(.habit), .answerAlignment(false), .advance)
        XCTAssertEqual(s.phase, .wait)
        XCTAssertEqual(s.waitRemainingSeconds, GateConfig.baseWaitSeconds)

        for _ in 0..<50 { s = GateStateMachine.reduce(s, .advance) }
        XCTAssertEqual(s.phase, .wait, "still waiting")

        for _ in 0..<GateConfig.baseWaitSeconds { s = GateStateMachine.reduce(s, .tick) }
        XCTAssertEqual(s.waitRemainingSeconds, 0)
        XCTAssertTrue(s.canAdvance)
    }

    func testTicksOutsideTheWaitDoNothing() {
        let s = state()
        XCTAssertEqual(s, GateStateMachine.reduce(s, .tick))
    }

    func testWaitNeverGoesNegative() {
        var s = run(state(), .advance, .chooseIntent(.bored), .answerAlignment(true), .advance)
        for _ in 0..<200 { s = GateStateMachine.reduce(s, .tick) }
        XCTAssertEqual(s.waitRemainingSeconds, 0)
    }

    // MARK: - Friction escalates with insistence

    func testRepeatAttemptsGetLongerWaits() {
        let first = GateConfig.forAttempt(strict: false, attemptsToday: 0)
        let fourth = GateConfig.forAttempt(strict: false, attemptsToday: 3)
        XCTAssertGreaterThan(fourth.waitSeconds, first.waitSeconds)
    }

    func testWaitIsCappedSoTheAppNeverBecomesAWall() {
        let absurd = GateConfig.forAttempt(strict: true, attemptsToday: 500)
        XCTAssertEqual(absurd.waitSeconds, GateConfig.maxWaitSeconds)
    }

    func testSingleRelaxedVisitIsShortStrictIsNot() {
        let relaxed = GateConfig.forAttempt(strict: false, attemptsToday: 0)
        XCTAssertFalse(relaxed.requirePurpose)
        XCTAssertFalse(relaxed.requireWriting)

        let strict = GateConfig.forAttempt(strict: true, attemptsToday: 0)
        XCTAssertTrue(strict.requirePurpose)
        XCTAssertTrue(strict.requireWriting)
    }

    func testScheduleWindowIsTreatedAsStrict() {
        let windowed = GateConfig.forAttempt(strict: false, attemptsToday: 0, inScheduleWindow: true)
        XCTAssertTrue(windowed.strict)
        XCTAssertTrue(windowed.requireWriting)
    }

    // MARK: - The whole strict run

    func testStrictModeWalksAllSixPhasesThenRespectsTheDecision() {
        let config = GateConfig.forAttempt(strict: true, attemptsToday: 0)
        var s = GateStateMachine.initial(config)

        XCTAssertEqual(s.activePhases, [.pause, .intent, .wait, .purpose, .write, .decide])
        XCTAssertEqual(s.stepCount, 6)

        s = run(s, .advance)
        XCTAssertEqual(s.phase, .intent)

        s = run(s, .chooseIntent(.seekingDistraction), .answerAlignment(false), .advance)
        XCTAssertEqual(s.phase, .wait)

        for _ in 0..<config.waitSeconds { s = GateStateMachine.reduce(s, .tick) }
        s = run(s, .advance)
        XCTAssertEqual(s.phase, .purpose)

        s = run(s, .advance)
        XCTAssertEqual(s.phase, .write)

        s = run(s, .editFreeText("corto"))
        XCTAssertFalse(s.canAdvance, "too short to count as writing")

        s = run(s, .editFreeText("Quiero ver si alguien ha respondido a mi mensaje de esta mañana."))
        XCTAssertTrue(s.canAdvance)

        s = run(s, .advance)
        XCTAssertEqual(s.phase, .decide)

        s = run(s, .editSentence("no es la frase"))
        XCTAssertFalse(s.canAdvance)

        s = run(s, .editSentence(config.acknowledgementSentence))
        XCTAssertTrue(s.canAdvance)

        s = run(s, .advance)
        XCTAssertEqual(s.phase, .resolved)
        XCTAssertEqual(s.outcome, .choseToContinue, "after all of it, the user's choice stands")
    }

    func testReEnteringTheWaitRestartsTheFullCountdown() {
        let config = GateConfig.forAttempt(strict: true, attemptsToday: 0)
        var s = GateStateMachine.initial(config)
        s = run(s, .advance, .chooseIntent(.bored), .answerAlignment(false), .advance)
        for _ in 0..<3 { s = GateStateMachine.reduce(s, .tick) }
        XCTAssertEqual(s.waitRemainingSeconds, config.waitSeconds - 3)

        s.phase = .intent
        s = GateStateMachine.reduce(s, .advance)
        XCTAssertEqual(s.phase, .wait)
        XCTAssertEqual(s.waitRemainingSeconds, config.waitSeconds)
    }

    func testAbandoningIsRecordedSeparatelyFromTurningBack() {
        let s = run(state(), .abandon)
        XCTAssertEqual(s.outcome, .abandoned)
    }

    // MARK: - The sentence check is friction, not a spelling exam

    func testSentenceMatchingForgivesAccentsCaseAndPunctuation() {
        let expected = GateConfig.defaultSentence
        XCTAssertTrue(SentenceMatcher.matches(expected, expected))
        XCTAssertTrue(SentenceMatcher.matches(
            "reconozco que estoy eligiendo dedicar este tiempo a una distraccion que yo mismo habia decidido limitar",
            expected
        ))
        XCTAssertTrue(SentenceMatcher.matches("  \(expected)  ", expected))
    }

    func testSentenceMatchingDoesNotForgiveMissingOrWrongWords() {
        let expected = GateConfig.defaultSentence
        XCTAssertFalse(SentenceMatcher.matches("Reconozco que estoy eligiendo", expected))
        XCTAssertFalse(SentenceMatcher.matches("", expected))
        XCTAssertFalse(SentenceMatcher.matches(
            expected.replacingOccurrences(of: "limitar", with: "ampliar"),
            expected
        ))
    }

    func testSentenceProgressGrowsAsTheUserTypes() {
        let expected = GateConfig.defaultSentence
        XCTAssertEqual(SentenceMatcher.progress("", expected), 0, accuracy: 0.001)
        let partial = SentenceMatcher.progress("Reconozco que estoy", expected)
        XCTAssertGreaterThan(partial, 0)
        XCTAssertLessThan(partial, 1)
        XCTAssertEqual(SentenceMatcher.progress(expected, expected), 1, accuracy: 0.001)
    }
}
