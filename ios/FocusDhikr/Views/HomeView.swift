import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var appState: AppState
    @State private var goals: [Goal] = []
    @State private var editing: Goal?

    private var store: SharedStore { appState.store }

    private var todayKey: String {
        DayBoundary.dayKey(for: Date(), resetHour: store.dayResetHour)
    }

    private var turnedBackToday: Int {
        store.attempts.filter {
            $0.dayKey == todayKey && $0.outcome == GateOutcome.turnedBack.rawValue
        }.count
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
                            value: "\(store.attempts.filter { $0.dayKey == todayKey }.count)",
                            label: "Pausas hoy"
                        )
                    }

                    if store.strictMode {
                        Text("MODO «NO ME DEJES ENTRAR» ACTIVO")
                            .font(.focusCaption)
                            .foregroundStyle(FocusColor.gold)
                    }

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
        .onAppear { goals = store.goals }
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
