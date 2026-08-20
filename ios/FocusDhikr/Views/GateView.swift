import SwiftUI

/// The progressive pause, running inside the app.
///
/// On Android this screen appears over the app you just opened. On iOS the
/// system shield appears there instead, and hands off to here - the one place
/// iOS lets us draw a countdown, a menu and a text field.
struct GateView: View {
    @StateObject private var model: GateViewModel
    @Environment(\.scenePhase) private var scenePhase
    let onDone: () -> Void

    init(appName: String, onDone: @escaping () -> Void) {
        _model = StateObject(wrappedValue: GateViewModel(appName: appName))
        self.onDone = onDone
    }

    var body: some View {
        ZStack {
            FocusColor.ink.ignoresSafeArea()

            if model.showEmergency {
                EmergencyView(model: model)
            } else {
                content
            }
        }
        .preferredColorScheme(.dark)
        .onAppear {
            model.onResolved = { outcome, grantMinutes in
                applyOutcome(outcome, grantMinutes: grantMinutes)
                onDone()
            }
        }
        .onChange(of: scenePhase) { phase in
            // Leaving without answering is recorded as its own outcome, so the
            // statistics never claim a win that did not happen.
            if phase == .background { model.abandon() }
        }
    }

    private var content: some View {
        ScrollView {
            VStack(spacing: Spacing.lg) {
                HStack {
                    Text(model.appName.uppercased())
                        .font(.focusCaption)
                        .foregroundStyle(FocusColor.textMuted)
                    Spacer()
                    StepDots(current: model.state.stepNumber, total: model.state.stepCount)
                }

                Spacer(minLength: Spacing.xl)

                phaseContent
                    .animation(.easeInOut(duration: 0.3), value: model.state.phase)

                Spacer(minLength: Spacing.xl)

                PrimaryAction(title: forwardLabel, enabled: model.state.canAdvance) {
                    model.send(.advance)
                }

                QuietAction(title: model.state.phase == .pause ? "Dejarlo por ahora" : "Ahora no") {
                    model.send(.turnBack)
                }

                if model.emergencyRemaining > 0 {
                    Button("Lo necesito por trabajo") {
                        model.showEmergency = true
                        model.beginEmergencyWait()
                    }
                    .font(.focusCaption)
                    .foregroundStyle(FocusColor.outline)
                }
            }
            .padding(Spacing.lg)
        }
    }

    private var forwardLabel: String {
        switch model.state.phase {
        case .pause: return "Quiero entrar igualmente"
        case .decide: return "Abrir de todos modos"
        default: return "Continuar"
        }
    }

    @ViewBuilder
    private var phaseContent: some View {
        switch model.state.phase {
        case .pause: PausePhase(model: model)
        case .intent: IntentPhase(model: model)
        case .wait: WaitPhase(model: model)
        case .purpose: PurposePhase(model: model)
        case .write: WritePhase(model: model)
        case .decide: DecidePhase(model: model)
        case .resolved: EmptyView()
        }
    }

    private func applyOutcome(_ outcome: GateOutcome, grantMinutes: Int) {
        #if canImport(FamilyControls)
        if #available(iOS 16.0, *) {
            switch outcome {
            case .choseToContinue, .emergencyAccess:
                // The decision is respected. This is the promise the whole app
                // rests on: after all the friction, you still get to choose.
                ScreenTimeController.shared.grantTemporaryAccess(minutes: grantMinutes)
            case .turnedBack, .abandoned:
                ScreenTimeController.shared.shieldAll()
            }
        }
        #endif
    }
}

// MARK: - Phase 1

private struct PausePhase: View {
    @ObservedObject var model: GateViewModel

    var body: some View {
        VStack(spacing: Spacing.lg) {
            Text("Has alcanzado el tiempo que tú mismo decidiste para esta aplicación.")
                .font(.focusHeadline)
                .foregroundStyle(FocusColor.textOnInk)
                .multilineTextAlignment(.center)

            Text(model.pauseLine)
                .font(.focusBody)
                .foregroundStyle(FocusColor.textMuted)
                .multilineTextAlignment(.center)
                .padding(.top, Spacing.md)
        }
    }
}

// MARK: - Phase 2

private struct IntentPhase: View {
    @ObservedObject var model: GateViewModel

    var body: some View {
        VStack(spacing: Spacing.md) {
            Text("¿Qué ibas a hacer exactamente?")
                .font(.focusHeadline)
                .foregroundStyle(FocusColor.textOnInk)
                .multilineTextAlignment(.center)

            ForEach(IntentReason.allCases) { reason in
                Button {
                    model.send(.chooseIntent(reason))
                } label: {
                    Text(reason.labelEs)
                        .font(.focusBody)
                        .foregroundStyle(FocusColor.textOnInk)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(Spacing.md)
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(
                                    model.state.intent == reason ? FocusColor.gold : FocusColor.outline,
                                    lineWidth: 1
                                )
                        )
                }
            }

            Text("¿Esto te acerca a lo que quieres conseguir hoy?")
                .font(.focusTitle)
                .foregroundStyle(FocusColor.textOnInk)
                .multilineTextAlignment(.center)
                .padding(.top, Spacing.md)

            HStack(spacing: Spacing.sm) {
                ForEach([true, false], id: \.self) { answer in
                    Button(answer ? "Sí" : "No") {
                        model.send(.answerAlignment(answer))
                    }
                    .font(.focusTitle)
                    .foregroundStyle(
                        model.state.alignsWithToday == answer ? FocusColor.gold : FocusColor.textMuted
                    )
                    .padding(.horizontal, Spacing.lg)
                    .padding(.vertical, Spacing.sm)
                    .overlay(
                        Capsule().stroke(
                            model.state.alignsWithToday == answer ? FocusColor.gold : FocusColor.outline,
                            lineWidth: 1
                        )
                    )
                }
            }
        }
    }
}

// MARK: - Phase 3

private struct WaitPhase: View {
    @ObservedObject var model: GateViewModel

    var body: some View {
        VStack(spacing: Spacing.lg) {
            Text("Espera unos segundos antes de continuar.")
                .font(.focusHeadline)
                .foregroundStyle(FocusColor.textOnInk)
                .multilineTextAlignment(.center)

            Text(Durations.formatSeconds(model.state.waitRemainingSeconds))
                .font(.system(size: 56, weight: .regular, design: .serif))
                .foregroundStyle(FocusColor.gold)
                .monospacedDigit()

            if let dhikr = model.dhikr {
                VStack(spacing: Spacing.sm) {
                    Text(dhikr.arabic)
                        .font(.system(size: 30, design: .serif))
                        .foregroundStyle(FocusColor.gold)
                    Text(dhikr.transliteration)
                        .font(.focusBody)
                        .foregroundStyle(FocusColor.textOnInk)
                    if model.showTranslations {
                        Text(dhikr.meaningEs)
                            .font(.focusBody)
                            .foregroundStyle(FocusColor.textMuted)
                    }
                }
                .multilineTextAlignment(.center)
            }

            Text(model.waitLine)
                .font(.focusBody)
                .foregroundStyle(FocusColor.textMuted)
                .multilineTextAlignment(.center)
        }
    }
}

// MARK: - Phase 4

private struct PurposePhase: View {
    @ObservedObject var model: GateViewModel

    var body: some View {
        VStack(spacing: Spacing.lg) {
            Text(model.purposeLine)
                .font(.focusHeadline)
                .foregroundStyle(FocusColor.textOnInk)
                .multilineTextAlignment(.center)

            if let goal = model.goal {
                VStack(spacing: Spacing.sm) {
                    Text(goal.title)
                        .font(.focusHeadline)
                        .foregroundStyle(FocusColor.gold)
                    if !goal.note.isEmpty {
                        Text(goal.note)
                            .font(.focusBody)
                            .foregroundStyle(FocusColor.textMuted)
                    }
                }
                .multilineTextAlignment(.center)
                .padding(Spacing.lg)
                .frame(maxWidth: .infinity)
                .overlay(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .stroke(FocusColor.gold, lineWidth: 1)
                )
            } else {
                Text("Todavía no has escrito ningún objetivo. Puedes añadirlos en la pestaña Hoy.")
                    .font(.focusBody)
                    .foregroundStyle(FocusColor.textMuted)
                    .multilineTextAlignment(.center)
            }

            if let citation = model.quran {
                VStack(spacing: Spacing.sm) {
                    Text(citation.arabic)
                        .font(.system(size: 22, design: .serif))
                        .foregroundStyle(FocusColor.textOnInk)
                    if model.showTranslations {
                        Text("«\(citation.translationEs)»")
                            .font(.focusBody)
                            .foregroundStyle(FocusColor.textMuted)
                        Text("Traducción del significado")
                            .font(.focusCaption)
                            .foregroundStyle(FocusColor.outline)
                    }
                    // The reference is never fine print and never behind a tap.
                    Text(citation.fullReference)
                        .font(.focusCaption)
                        .foregroundStyle(FocusColor.gold)
                }
                .multilineTextAlignment(.center)
                .padding(.top, Spacing.lg)
            }
        }
    }
}

// MARK: - Phase 5

private struct WritePhase: View {
    @ObservedObject var model: GateViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text("Escribe con tus propias palabras por qué quieres entrar.")
                .font(.focusHeadline)
                .foregroundStyle(FocusColor.textOnInk)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)

            TextEditor(text: Binding(
                get: { model.state.freeText },
                set: { model.send(.editFreeText($0)) }
            ))
            .frame(minHeight: 120)
            .padding(Spacing.sm)
            .scrollContentBackground(.hidden)
            .background(FocusColor.inkElevated)
            .foregroundStyle(FocusColor.textOnInk)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))

            Text("Al menos \(model.state.config.minFreeTextChars) caracteres")
                .font(.focusCaption)
                .foregroundStyle(FocusColor.outline)
        }
    }
}

// MARK: - Phase 6

private struct DecidePhase: View {
    @ObservedObject var model: GateViewModel

    private var progress: Double {
        SentenceMatcher.progress(model.state.typedSentence, model.state.config.acknowledgementSentence)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text("Estás eligiendo conscientemente usar esta aplicación aunque hayas alcanzado tu límite.")
                .font(.focusHeadline)
                .foregroundStyle(FocusColor.textOnInk)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)

            Text("Para continuar, copia esta frase:")
                .font(.focusCaption)
                .foregroundStyle(FocusColor.outline)

            Text(model.state.config.acknowledgementSentence)
                .font(.system(size: 16, design: .serif))
                .foregroundStyle(FocusColor.gold)
                .padding(Spacing.md)
                .frame(maxWidth: .infinity)
                .background(FocusColor.inkElevated)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))

            TextEditor(text: Binding(
                get: { model.state.typedSentence },
                set: { model.send(.editSentence($0)) }
            ))
            .frame(minHeight: 100)
            .padding(Spacing.sm)
            .scrollContentBackground(.hidden)
            .background(FocusColor.inkElevated)
            .foregroundStyle(FocusColor.textOnInk)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))

            // A hint, not a red error: the point is friction, not a spelling exam.
            Text("\(Int(progress * 100)) %")
                .font(.focusCaption)
                .foregroundStyle(model.state.canAdvance ? FocusColor.gold : FocusColor.outline)
        }
    }
}

// MARK: - Emergency

private struct EmergencyView: View {
    @ObservedObject var model: GateViewModel

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.lg) {
                Text("Acceso de emergencia")
                    .font(.focusDisplay)
                    .foregroundStyle(FocusColor.textOnInk)

                Text("Esto es para cuando de verdad lo necesitas. Te quedan \(model.emergencyRemaining) esta semana.")
                    .font(.focusBody)
                    .foregroundStyle(FocusColor.textMuted)
                    .multilineTextAlignment(.center)

                TextEditor(text: $model.emergencyReason)
                    .frame(minHeight: 100)
                    .padding(Spacing.sm)
                    .scrollContentBackground(.hidden)
                    .background(FocusColor.inkElevated)
                    .foregroundStyle(FocusColor.textOnInk)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))

                if model.emergencyWaitRemaining > 0 {
                    Text(Durations.formatSeconds(model.emergencyWaitRemaining))
                        .font(.focusHeadline)
                        .foregroundStyle(FocusColor.gold)
                        .monospacedDigit()
                }

                PrimaryAction(
                    title: "Conceder acceso",
                    enabled: model.emergencyWaitRemaining == 0
                        && model.emergencyReason.trimmingCharacters(in: .whitespacesAndNewlines).count >= 5
                ) {
                    model.confirmEmergency()
                }

                QuietAction(title: "Atrás") { model.showEmergency = false }
            }
            .padding(Spacing.lg)
        }
    }
}
