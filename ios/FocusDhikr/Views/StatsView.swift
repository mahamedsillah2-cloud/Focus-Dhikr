import SwiftUI

#if canImport(FamilyControls)
import FamilyControls
import DeviceActivity
#endif

/// The numbers, framed positively and split by how much we actually know.
///
/// Two sections, and the difference between them is the whole iOS story:
///
///  - **Tu tiempo real** is drawn by the report extension. Those minutes come
///    from iOS itself and are exact - and the app cannot read them, only show
///    them, because the extension runs in a sandbox that forbids passing them
///    out.
///  - **Tus pausas** is what the app counts by itself: how many times you
///    stopped. Exact, and ours.
///
/// The headline is turn-backs, not time wasted. Same data, and the app is not
/// trying to make anyone feel bad about it.
struct StatsView: View {
    @EnvironmentObject private var appState: AppState
    @State private var days = 7

    private var store: SharedStore { appState.store }

    private var dayKeys: [String] {
        DayBoundary.recentDayKeys(count: days, resetHour: store.dayResetHour)
    }

    private var scoped: [GateAttempt] {
        let keys = Set(dayKeys)
        return store.attempts.filter { keys.contains($0.dayKey) }
    }

    private var turnedBack: Int {
        scoped.filter { $0.outcome == GateOutcome.turnedBack.rawValue }.count
    }

    private var continued: Int {
        scoped.filter { $0.outcome == GateOutcome.choseToContinue.rawValue }.count
    }

    private var streaks: Streaks.Result {
        Streaks.compute(
            attempts: store.attempts,
            dayKeys: DayBoundary.recentDayKeys(count: 60, resetHour: store.dayResetHour)
        )
    }

    /// Ten minutes per avoided session: a plausible short scroll, and a
    /// conservative estimate. iOS will not tell us the real figure, so the
    /// screen says "estimado" every single time it shows this.
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

                    realUsageSection
                    pauseSection
                    streakSection
                    dailyBars
                }
                .padding(Spacing.lg)
            }
            .background(FocusColor.ink)
            .navigationTitle("Historial")
        }
    }

    // MARK: - Real minutes, drawn by iOS

    private var realUsageSection: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text("TU TIEMPO REAL")
                .font(.focusCaption)
                .foregroundStyle(FocusColor.textMuted)

            QuietCard {
                #if canImport(FamilyControls)
                if #available(iOS 16.0, *), appState.authorized {
                    DeviceActivityReport(
                        DeviceActivityReport.Context("focusdhikr.usage"),
                        filter: usageFilter
                    )
                    .frame(minHeight: 160)
                } else {
                    Text("Necesita el permiso de Tiempo de uso.")
                        .font(.focusBody)
                        .foregroundStyle(FocusColor.textMuted)
                }
                #else
                Text("Solo disponible en un iPhone.")
                    .font(.focusBody)
                    .foregroundStyle(FocusColor.textMuted)
                #endif

                Text("Lo dibuja una extensión del sistema. Estos minutos son exactos y la app no puede leerlos: iOS se lo impide a propósito.")
                    .font(.focusCaption)
                    .foregroundStyle(FocusColor.outline)
                    .padding(.top, Spacing.sm)
            }
        }
    }

    #if canImport(FamilyControls)
    @available(iOS 16.0, *)
    private var usageFilter: DeviceActivityFilter {
        let end = Date()
        let start = Calendar.current.date(byAdding: .day, value: -(days - 1), to: end) ?? end
        let interval = DateInterval(
            start: Calendar.current.startOfDay(for: start),
            end: end
        )
        let selection = ScreenTimeController.shared.loadSelection()
        return DeviceActivityFilter(
            segment: .daily(during: interval),
            users: .all,
            devices: .all,
            applications: selection.applicationTokens,
            categories: selection.categoryTokens
        )
    }
    #endif

    // MARK: - What the app itself counts

    private var pauseSection: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text("TUS PAUSAS")
                .font(.focusCaption)
                .foregroundStyle(FocusColor.textMuted)

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
                    Text("Una estimación conservadora: diez minutos por cada vez que diste la vuelta.")
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
    }

    private var streakSection: some View {
        QuietCard {
            HStack(spacing: Spacing.lg) {
                StatTile(value: "\(streaks.current)", label: "Días seguidos", accent: streaks.current > 0)
                StatTile(value: "\(streaks.best)", label: "Tu mejor racha")
            }
            Text("Un día cuenta si diste la vuelta al menos una vez y no forzaste ninguna entrada. Los días en que no abriste nada limitado no rompen la racha ni la alargan.")
                .font(.focusCaption)
                .foregroundStyle(FocusColor.outline)
                .padding(.top, Spacing.sm)
        }
    }

    /// One column per day. No axis, no grid, no numbers on top: it is there to
    /// show a shape, not to be read to the minute.
    private var dailyBars: some View {
        let counts = dayKeys.map { key in
            store.attempts.filter {
                $0.dayKey == key && $0.outcome == GateOutcome.turnedBack.rawValue
            }.count
        }
        let peak = max(counts.max() ?? 0, 1)

        return QuietCard {
            Text("VECES QUE DISTE LA VUELTA")
                .font(.focusCaption)
                .foregroundStyle(FocusColor.textMuted)

            HStack(alignment: .bottom, spacing: days > 7 ? 2 : 6) {
                ForEach(Array(counts.enumerated()), id: \.offset) { _, count in
                    RoundedRectangle(cornerRadius: 3, style: .continuous)
                        .fill(count > 0 ? FocusColor.gold.opacity(0.85) : FocusColor.outline)
                        .frame(height: max(CGFloat(count) / CGFloat(peak) * 70, 3))
                        .frame(maxWidth: .infinity)
                }
            }
            .frame(height: 80, alignment: .bottom)
            .padding(.top, Spacing.sm)
        }
    }
}
