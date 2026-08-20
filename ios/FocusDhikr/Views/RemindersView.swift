import SwiftUI

/// Reminders, deliberately hard to over-use.
///
/// You add them one at a time, each with its own hour and days, and the app
/// refuses to add more than a few: a wellbeing app that notifies you all day is
/// the problem wearing the costume of the solution.
struct RemindersView: View {
    @EnvironmentObject private var appState: AppState
    @State private var reminders: [Reminder] = []
    @State private var editing: Reminder?
    @State private var permissionDenied = false

    static let maxReminders = 6

    private var store: SharedStore { appState.store }

    var body: some View {
        Form {
            if permissionDenied {
                Section {
                    Text("iOS tiene las notificaciones desactivadas para esta app. Actívalas en Ajustes si quieres recordatorios.")
                        .font(.focusCaption)
                        .foregroundStyle(FocusColor.clay)
                }
            }

            Section {
                if reminders.isEmpty {
                    Text("Ninguno. La app no te escribirá salvo que se lo pidas.")
                        .foregroundStyle(.secondary)
                }
                ForEach(reminders) { reminder in
                    Button {
                        editing = reminder
                    } label: {
                        HStack {
                            Image(systemName: reminder.kind.symbol)
                                .foregroundStyle(FocusColor.gold)
                                .frame(width: 24)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(reminder.kind.labelEs)
                                    .foregroundStyle(reminder.enabled ? .primary : .secondary)
                                Text("\(reminder.clockDescription) · \(reminder.daysDescription)")
                                    .font(.focusCaption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            if !reminder.enabled {
                                Text("off").font(.focusCaption).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                .onDelete { offsets in
                    var next = reminders
                    next.remove(atOffsets: offsets)
                    persist(next)
                }
            } header: {
                Text("Recordatorios")
            } footer: {
                Text("Llegan en silencio, sin sonido y sin insistir. Máximo \(Self.maxReminders).")
            }

            if reminders.count < Self.maxReminders {
                Section {
                    ForEach(Reminder.Kind.allCases, id: \.self) { kind in
                        Button {
                            editing = Reminder(kind: kind, minuteOfDay: 9 * 60)
                        } label: {
                            Label(kind.labelEs, systemImage: kind.symbol)
                        }
                    }
                } header: {
                    Text("Añadir")
                }
            }
        }
        .navigationTitle("Recordatorios")
        .onAppear {
            reminders = store.reminders
            Task { permissionDenied = !(await ReminderScheduler.requestPermission()) }
        }
        .sheet(item: $editing) { reminder in
            ReminderEditor(reminder: reminder) { saved in
                var next = reminders
                if let index = next.firstIndex(where: { $0.id == saved.id }) {
                    next[index] = saved
                } else {
                    next.append(saved)
                }
                persist(next)
            }
        }
    }

    private func persist(_ next: [Reminder]) {
        reminders = next.sorted { $0.minuteOfDay < $1.minuteOfDay }
        store.reminders = reminders
        editing = nil
        Task { await ReminderScheduler.reschedule(from: store) }
    }
}

struct ReminderEditor: View {
    @State private var draft: Reminder
    let onSave: (Reminder) -> Void
    @Environment(\.dismiss) private var dismiss

    init(reminder: Reminder, onSave: @escaping (Reminder) -> Void) {
        _draft = State(initialValue: reminder)
        self.onSave = onSave
    }

    private static let dayNames = ["L", "M", "X", "J", "V", "S", "D"]

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    DatePicker(
                        "Hora",
                        selection: Binding(
                            get: {
                                Calendar.current.date(
                                    bySettingHour: draft.minuteOfDay / 60,
                                    minute: draft.minuteOfDay % 60,
                                    second: 0,
                                    of: Date()
                                ) ?? Date()
                            },
                            set: {
                                let parts = Calendar.current.dateComponents([.hour, .minute], from: $0)
                                draft.minuteOfDay = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
                            }
                        ),
                        displayedComponents: .hourAndMinute
                    )
                    Toggle("Activo", isOn: $draft.enabled)
                } header: {
                    Text(draft.kind.labelEs)
                }

                Section("Días") {
                    HStack(spacing: Spacing.sm) {
                        ForEach(0..<7, id: \.self) { index in
                            let on = draft.daysMask & (1 << index) != 0
                            Button(Self.dayNames[index]) {
                                if on { draft.daysMask &= ~(1 << index) }
                                else { draft.daysMask |= (1 << index) }
                            }
                            .font(.focusCaption)
                            .frame(width: 32, height: 32)
                            .foregroundStyle(on ? FocusColor.ink : FocusColor.textMuted)
                            .background(on ? FocusColor.gold : Color.clear)
                            .clipShape(Circle())
                            .overlay(Circle().stroke(FocusColor.outline, lineWidth: on ? 0 : 1))
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .navigationTitle("Recordatorio")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Guardar") { onSave(draft); dismiss() }
                        .disabled(draft.daysMask == 0)
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { dismiss() }
                }
            }
        }
    }
}
