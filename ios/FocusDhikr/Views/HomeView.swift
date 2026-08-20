import SwiftUI

#if canImport(FamilyControls)
import FamilyControls
import ManagedSettings
#endif

/// Today, at a glance.
///
/// The order is deliberate: what you chose to do (turn-backs) before what the
/// system did to you (blocks), and your own goals above both. Nothing on this
/// screen counts anything you should feel bad about.
struct HomeView: View {
    @EnvironmentObject private var appState: AppState
    @State private var goals: [Goal] = []
    @State private var editing: Goal?
    @State private var refreshToken = 0

    private var store: SharedStore { appState.store }

    private var todayKey: String {
        DayBoundary.dayKey(for: Date(), resetHour: store.dayResetHour)
    }

    private var todaysAttempts: [GateAttempt] {
        store.attempts.filter { $0.dayKey == todayKey }
    }

    private var turnedBackToday: Int {
        todaysAttempts.filter { $0.outcome == GateOutcome.turnedBack.rawValue }.count
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    HStack(spacing: Spacing.lg) {
                        StatTile(
                            value: "\(turnedBackToday)",
                            label: "Veces que has dado la vuelta",
                            accent: true
                        )
                        StatTile(
                            value: "\(todaysAttempts.count)",
                            label: "Intentos de abrir algo limitado"
                        )
                    }

                    if store.strictMode {
                        Text("MODO DISCIPLINA ACTIVO")
                            .font(.focusCaption)
                            .foregroundStyle(FocusColor.gold)
                    }

                    if let grantExpiry = store.grantExpiry, grantExpiry > Date() {
                        QuietCard {
                            Text("Tienes acceso concedido hasta las \(Self.time(grantExpiry))")
                                .font(.focusBody)
                                .foregroundStyle(FocusColor.clay)
                            Text("Lo decidiste tú hace un momento. Cuando termine, el límite vuelve solo.")
                                .font(.focusCaption)
                                .foregroundStyle(FocusColor.textMuted)
                        }
                    }

                    todaySection
                    goalsSection

                    Text(Reflections.pick(Reflections.ambient, seed: todayKey.hashValue))
                        .font(.focusBody)
                        .foregroundStyle(FocusColor.outline)
                        .frame(maxWidth: .infinity)
                        .multilineTextAlignment(.center)
                        .padding(.top, Spacing.lg)
                }
                .padding(Spacing.lg)
            }
            .background(FocusColor.ink)
            .navigationTitle("Hoy")
        }
        .onAppear {
            goals = store.goals
            refreshToken += 1
        }
        .sheet(item: $editing) { goal in
            GoalEditor(goal: goal) { saved in
                var all = store.goals
                if let index = all.firstIndex(where: { $0.id == saved.id }) {
                    all[index] = saved
                } else {
                    all.append(saved)
                }
                store.goals = all
                goals = all
                editing = nil
            }
        }
    }

    private static func time(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: date)
    }

    // MARK: - Per-app state

    /// What iOS has told us about today, app by app.
    ///
    /// Every figure here is a floor - "al menos 48 min" - because thresholds
    /// are the only thing DeviceActivity reports. The exact minutes live on the
    /// Historial screen, drawn by the system itself.
    private var todaySection: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text("TUS LÍMITES DE HOY")
                .font(.focusCaption)
                .foregroundStyle(FocusColor.textMuted)

            QuietCard {
                #if canImport(FamilyControls)
                if #available(iOS 16.0, *) {
                    let rows = limitRows()
                    if rows.isEmpty {
                        Text("Todavía no has elegido ninguna aplicación.")
                            .font(.focusBody)
                            .foregroundStyle(FocusColor.outline)
                    } else {
                        ForEach(rows) { row in
                            LimitRow(row: row)
                        }
                    }
                }
                #else
                Text("Solo disponible en un iPhone.")
                    .font(.focusBody)
                    .foregroundStyle(FocusColor.outline)
                #endif
            }
            .id(refreshToken)
        }
    }

    #if canImport(FamilyControls)
    @available(iOS 16.0, *)
    private func limitRows() -> [LimitRowData] {
        let floors = store.usageFloorsToday(dayKey: todayKey)
        let reasons = store.shieldReasons
        return ScreenTimeController.shared.tokensByKey()
            .map { key, token in
                LimitRowData(
                    id: key,
                    token: token,
                    limit: store.limitMinutes[key] ?? store.defaultLimitMinutes,
                    usedFloor: floors[key] ?? 0,
                    reason: reasons[key].flatMap(ShieldReason.init(rawValue:))
                )
            }
            .sorted { $0.id < $1.id }
    }
    #endif

    /// The user's own reasons, in their own words.
    ///
    /// On the home screen rather than buried in settings, because these are what
    /// phase 4 reads back to them - and something you will be shown at your
    /// weakest moment deserves to be edited at your calmest.
    private var goalsSection: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text("TUS OBJETIVOS")
                .font(.focusCaption)
                .foregroundStyle(FocusColor.textMuted)

            QuietCard {
                if goals.isEmpty {
                    Text("Todavía no has escrito ninguno.")
                        .font(.focusBody)
                        .foregroundStyle(FocusColor.outline)
                } else {
                    ForEach(goals) { goal in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(goal.title)
                                    .font(.focusBody)
                                    .foregroundStyle(FocusColor.textOnInk)
                                if !goal.note.isEmpty {
                                    Text(goal.note)
                                        .font(.focusCaption)
                                        .foregroundStyle(FocusColor.textMuted)
                                }
                            }
                            Spacer()
                            Button {
                                store.goals.removeAll { $0.id == goal.id }
                                goals = store.goals
                            } label: {
                                Image(systemName: "xmark")
                                    .foregroundStyle(FocusColor.outline)
                            }
                        }
                        .padding(.vertical, Spacing.xs)
                    }
                }

                Button("Añadir objetivo") { editing = Goal(title: "") }
                    .font(.focusTitle)
                    .foregroundStyle(FocusColor.gold)
                    .padding(.top, Spacing.sm)
            }
        }
    }
}

struct GoalEditor: View {
    @State private var draft: Goal
    let onSave: (Goal) -> Void
    @Environment(\.dismiss) private var dismiss

    init(goal: Goal, onSave: @escaping (Goal) -> Void) {
        _draft = State(initialValue: goal)
        self.onSave = onSave
    }

    private static let suggestions = [
        "Estudiar", "Trabajar", "Entrenar", "Leer",
        "Pasar tiempo con mi familia", "Aprender algo nuevo",
        "Dormir mejor", "Mejorar mi disciplina", "Dedicar tiempo a mi religión",
    ]

    var body: some View {
        NavigationStack {
            Form {
                Section("Objetivo") {
                    TextField("Objetivo", text: $draft.title)
                    TextField("Detalle (opcional)", text: $draft.note)
                    Toggle("Recordármelo en las pausas", isOn: $draft.active)
                }
                Section("Sugerencias") {
                    ForEach(Self.suggestions, id: \.self) { suggestion in
                        Button(suggestion) { draft.title = suggestion }
                            .foregroundStyle(FocusColor.gold)
                    }
                }
            }
            .navigationTitle("Objetivo")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Guardar") { onSave(draft) }
                        .disabled(draft.title.trimmingCharacters(in: .whitespaces).isEmpty)
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { dismiss() }
                }
            }
        }
    }
}

#if canImport(FamilyControls)
@available(iOS 16.0, *)
struct LimitRowData: Identifiable {
    let id: String
    let token: ApplicationToken
    let limit: Int
    let usedFloor: Int
    let reason: ShieldReason?
}

/// One app's line on the home screen.
///
/// `Label(token)` is the only way to name an app: the system draws it, and the
/// app itself never learns which one it is.
@available(iOS 16.0, *)
struct LimitRow: View {
    let row: LimitRowData

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Label(row.token)
                .labelStyle(.titleAndIcon)
                .foregroundStyle(row.reason == nil ? FocusColor.textOnInk : FocusColor.textMuted)
            Spacer(minLength: Spacing.sm)
            VStack(alignment: .trailing, spacing: 2) {
                Text(UsageFloor.describe(minutes: row.usedFloor, limit: row.limit))
                    .font(.focusCaption)
                    .foregroundStyle(row.reason == nil ? FocusColor.textMuted : FocusColor.clay)
                if let reason = row.reason {
                    Text(reason.isWindow ? "En tu franja" : "Bloqueada")
                        .font(.focusCaption)
                        .foregroundStyle(FocusColor.clay)
                }
            }
        }
        .padding(.vertical, Spacing.xs)
    }
}
#endif
