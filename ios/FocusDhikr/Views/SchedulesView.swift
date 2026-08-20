import SwiftUI

/// Windows in which an app is off-limits regardless of how much time is left,
/// e.g. 22:00-08:00, weekdays only.
///
/// Each window becomes its own `DeviceActivitySchedule`, because a schedule is
/// a single interval and the daily allowance already occupies one. The days of
/// the week are *not* part of the schedule - iOS has no such concept - so the
/// monitor extension checks them when the system wakes it.
struct SchedulesView: View {
    @EnvironmentObject private var appState: AppState
    @State private var windows: [ScheduleWindow] = []
    @State private var editing: ScheduleWindow?

    private var store: SharedStore { appState.store }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                if windows.isEmpty {
                    QuietCard {
                        Text("Sin franjas")
                            .font(.focusTitle)
                            .foregroundStyle(FocusColor.textOnInk)
                        Text("Una franja bloquea la aplicación aunque te quede tiempo. La más útil suele ser la de la noche.")
                            .font(.focusBody)
                            .foregroundStyle(FocusColor.textMuted)
                    }
                }

                ForEach(windows) { window in
                    Button {
                        editing = window
                    } label: {
                        QuietCard {
                            HStack {
                                VStack(alignment: .leading, spacing: Spacing.xs) {
                                    Text(window.label.isEmpty ? window.clockDescription : window.label)
                                        .font(.focusHeadline)
                                        .foregroundStyle(window.enabled ? FocusColor.textOnInk : FocusColor.textMuted)
                                    Text("\(window.clockDescription) · \(window.daysDescription)")
                                        .font(.focusCaption)
                                        .foregroundStyle(FocusColor.textMuted)
                                    Text(window.appKeys.isEmpty
                                         ? "Todas las aplicaciones limitadas"
                                         : "\(window.appKeys.count) aplicación(es)")
                                        .font(.focusCaption)
                                        .foregroundStyle(FocusColor.outline)
                                }
                                Spacer()
                                if !window.enabled {
                                    Text("PAUSADA")
                                        .font(.focusCaption)
                                        .foregroundStyle(FocusColor.outline)
                                }
                            }
                        }
                    }
                }

                Button("Añadir franja") {
                    editing = ScheduleWindow(
                        startMinuteOfDay: 22 * 60,
                        endMinuteOfDay: 8 * 60,
                        label: "Noche"
                    )
                }
                .font(.focusTitle)
                .foregroundStyle(FocusColor.gold)

                Text("iOS rechaza las franjas de menos de 15 minutos, así que la app no deja crearlas.")
                    .font(.focusCaption)
                    .foregroundStyle(FocusColor.outline)
            }
            .padding(Spacing.lg)
        }
        .background(FocusColor.ink)
        .navigationTitle("Franjas horarias")
        .onAppear { windows = store.scheduleWindows }
        .sheet(item: $editing) { window in
            ScheduleEditor(window: window) { saved in
                var all = windows
                if let index = all.firstIndex(where: { $0.id == saved.id }) {
                    all[index] = saved
                } else {
                    all.append(saved)
                }
                persist(all)
            } onDelete: { removed in
                persist(windows.filter { $0.id != removed.id })
            }
        }
    }

    private func persist(_ next: [ScheduleWindow]) {
        windows = next.sorted { $0.startMinuteOfDay < $1.startMinuteOfDay }
        store.scheduleWindows = windows
        editing = nil
        appState.restartMonitoring()
    }
}

struct ScheduleEditor: View {
    @State private var draft: ScheduleWindow
    @State private var isNew: Bool
    let onSave: (ScheduleWindow) -> Void
    let onDelete: (ScheduleWindow) -> Void
    @Environment(\.dismiss) private var dismiss

    init(
        window: ScheduleWindow,
        onSave: @escaping (ScheduleWindow) -> Void,
        onDelete: @escaping (ScheduleWindow) -> Void
    ) {
        _draft = State(initialValue: window)
        _isNew = State(initialValue: !SharedStore.shared.scheduleWindows.contains { $0.id == window.id })
        self.onSave = onSave
        self.onDelete = onDelete
    }

    private static let dayNames = ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"]

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Nombre (opcional)", text: $draft.label)
                    DatePicker(
                        "Empieza",
                        selection: Binding(
                            get: { Self.date(from: draft.startMinuteOfDay) },
                            set: { draft.startMinuteOfDay = Self.minutes(from: $0) }
                        ),
                        displayedComponents: .hourAndMinute
                    )
                    DatePicker(
                        "Termina",
                        selection: Binding(
                            get: { Self.date(from: draft.endMinuteOfDay) },
                            set: { draft.endMinuteOfDay = Self.minutes(from: $0) }
                        ),
                        displayedComponents: .hourAndMinute
                    )
                } header: {
                    Text("Horario")
                } footer: {
                    Text(draft.durationMinutes >= 15
                         ? "Dura \(Durations.format(minutes: draft.durationMinutes))."
                         : "Demasiado corta: iOS no admite menos de 15 minutos.")
                }

                Section("Días") {
                    ForEach(0..<7, id: \.self) { index in
                        Toggle(Self.dayNames[index], isOn: Binding(
                            get: { draft.daysMask & (1 << index) != 0 },
                            set: { on in
                                if on { draft.daysMask |= (1 << index) }
                                else { draft.daysMask &= ~(1 << index) }
                            }
                        ))
                    }
                    HStack {
                        Button("Todos") { draft.daysMask = ScheduleWindow.allDays }
                        Spacer()
                        Button("L-V") { draft.daysMask = ScheduleWindow.weekdays }
                        Spacer()
                        Button("Fin de semana") { draft.daysMask = ScheduleWindow.weekend }
                    }
                    .font(.focusCaption)
                    .foregroundStyle(FocusColor.gold)
                }

                Section {
                    Toggle("Activa", isOn: $draft.enabled)
                } footer: {
                    Text("Se aplica a las aplicaciones que elijas en cada una desde su pantalla de límite. Por defecto, a todas.")
                }

                if !isNew {
                    Section {
                        Button("Eliminar franja", role: .destructive) {
                            onDelete(draft)
                            dismiss()
                        }
                    }
                }
            }
            .navigationTitle(isNew ? "Nueva franja" : "Franja")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Guardar") {
                        onSave(draft)
                        dismiss()
                    }
                    .disabled(draft.durationMinutes < 15 || draft.daysMask == 0)
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { dismiss() }
                }
            }
        }
    }

    private static func date(from minuteOfDay: Int) -> Date {
        Calendar.current.date(
            bySettingHour: (minuteOfDay / 60) % 24,
            minute: minuteOfDay % 60,
            second: 0,
            of: Date()
        ) ?? Date()
    }

    private static func minutes(from date: Date) -> Int {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
        return (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
    }
}
