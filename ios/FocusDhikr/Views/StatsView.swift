import SwiftUI

/// The numbers, framed positively.
///
/// The headline is turn-backs, not time wasted. Same data, and the app is not
/// trying to make anyone feel bad about it.
struct StatsView: View {
    @EnvironmentObject private var appState: AppState
    @State private var days = 7

    private var store: SharedStore { appState.store }

    private var keys: Set<String> {
        Set(DayBoundary.recentDayKeys(count: days, resetHour: store.dayResetHour))
    }

    private var scoped: [GateAttempt] {
        store.attempts.filter { keys.contains($0.dayKey) }
    }

    private var turnedBack: Int {
        scoped.filter { $0.outcome == GateOutcome.turnedBack.rawValue }.count
    }

    private var continued: Int {
        scoped.filter { $0.outcome == GateOutcome.choseToContinue.rawValue }.count
    }

    /// Ten minutes per avoided session: a plausible short scroll, and a
    /// conservative estimate. iOS will not tell us the real figure.
    private var reclaimedMinutes: Int { turnedBack * 10 }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    Picker("Periodo", selection: $days) {
                        Text("Hoy").tag(1)
                        Text("7 días").tag(7)
                        Text("30 días").tag(30)
                    }
                    .pickerStyle(.segmented)

                    if scoped.isEmpty {
                        QuietCard {
                            Text("Aún no hay suficiente historial. Vuelve en unos días.")
                                .font(.focusBody)
                                .foregroundStyle(FocusColor.textMuted)
                        }
                    } else {
                        QuietCard {
                            StatTile(
                                value: Durations.format(minutes: reclaimedMinutes),
                                label: "Tiempo recuperado (estimado)",
                                accent: true
                            )
                            Text("iOS no deja leer tus minutos reales, así que esto es una estimación conservadora: diez minutos por cada vez que diste la vuelta.")
                                .font(.focusCaption)
                                .foregroundStyle(FocusColor.outline)
                                .padding(.top, Spacing.sm)
                        }

                        HStack(spacing: Spacing.lg) {
                            StatTile(value: "\(turnedBack)", label: "Has dado la vuelta")
                            StatTile(value: "\(continued)", label: "Decidiste entrar")
                        }
                    }
                }
                .padding(Spacing.lg)
            }
            .background(FocusColor.ink)
            .navigationTitle("Historial")
        }
    }
}
