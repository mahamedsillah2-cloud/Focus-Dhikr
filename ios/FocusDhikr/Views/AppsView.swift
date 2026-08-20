import SwiftUI

#if canImport(FamilyControls)
import FamilyControls
import ManagedSettings
#endif

/// Choosing the apps, and giving each one its own limit.
///
/// The honest caveat lives on this screen, not buried in a document: iOS hands
/// the app opaque tokens, so it cannot write "Instagram" itself. It renders
/// `Label(token)`, which the *system* draws, and it keys every limit by the
/// token rather than by a name it never learns.
struct AppsView: View {
    @EnvironmentObject private var appState: AppState
    @State private var showPicker = false
    @State private var defaultMinutes: Int = 60
    @State private var limits: [String: Int] = [:]
    @State private var monitoringError: String?

    #if canImport(FamilyControls)
    @State private var selection = FamilyActivitySelection()
    #endif

    private var store: SharedStore { appState.store }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    appsCard
                    defaultLimitCard
                    windowsCard
                    if let monitoringError {
                        QuietCard {
                            Text("iOS ha rechazado parte de la vigilancia")
                                .font(.focusTitle)
                                .foregroundStyle(FocusColor.clay)
                            Text(monitoringError)
                                .font(.focusCaption)
                                .foregroundStyle(FocusColor.textMuted)
                        }
                    }
                    footnote
                }
                .padding(Spacing.lg)
            }
            .background(FocusColor.ink)
            .navigationTitle("Aplicaciones")
        }
        .onAppear(perform: load)
        #if canImport(FamilyControls)
        .familyActivityPicker(isPresented: $showPicker, selection: $selection)
        .onChange(of: selection) { _ in saveSelection() }
        #endif
    }

    // MARK: - Cards

    private var appsCard: some View {
        QuietCard {
            Text("Aplicaciones limitadas")
                .font(.focusTitle)
                .foregroundStyle(FocusColor.textOnInk)

            #if canImport(FamilyControls)
            if #available(iOS 16.0, *) {
                if selection.applicationTokens.isEmpty && selection.categoryTokens.isEmpty {
                    Text("Todavía no has elegido ninguna.")
                        .font(.focusBody)
                        .foregroundStyle(FocusColor.outline)
                } else {
                    ForEach(Array(selection.applicationTokens), id: \.self) { token in
                        appRow(token)
                    }
                    if !selection.categoryTokens.isEmpty {
                        Divider().overlay(FocusColor.outline)
                        Text("\(selection.categoryTokens.count) categoría(s), con el límite general")
                            .font(.focusCaption)
                            .foregroundStyle(FocusColor.textMuted)
                    }
                }
            }
            #endif

            Button("Elegir aplicaciones") { showPicker = true }
                .font(.focusTitle)
                .foregroundStyle(FocusColor.gold)
                .padding(.top, Spacing.sm)
        }
    }

    #if canImport(FamilyControls)
    @available(iOS 16.0, *)
    @ViewBuilder
    private func appRow(_ token: ApplicationToken) -> some View {
        let key = TokenKey.key(for: token)
        let minutes = limits[key] ?? defaultMinutes

        NavigationLink {
            AppLimitEditor(
                key: key,
                token: token,
                minutes: Binding(
                    get: { limits[key] ?? defaultMinutes },
                    set: { limits[key] = $0 }
                ),
                onCommit: commitLimits
            )
        } label: {
            HStack {
                // The only way to show an app: the system renders this, we
                // never see the name.
                Label(token)
                    .labelStyle(.titleAndIcon)
                    .foregroundStyle(FocusColor.textOnInk)
                Spacer(minLength: Spacing.sm)
                Text(Durations.format(minutes: minutes))
                    .font(.focusCaption)
                    .foregroundStyle(FocusColor.gold)
                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundStyle(FocusColor.outline)
            }
            .padding(.vertical, Spacing.xs)
        }
    }
    #endif

    private var defaultLimitCard: some View {
        QuietCard {
            Text("Límite general: \(Durations.format(minutes: defaultMinutes))")
                .font(.focusTitle)
                .foregroundStyle(FocusColor.gold)

            Slider(
                value: Binding(
                    get: { Double(defaultMinutes) },
                    set: { defaultMinutes = Limits.snap(Int($0)) }
                ),
                in: 5...240,
                step: 5
            ) { editing in
                if !editing {
                    store.defaultLimitMinutes = defaultMinutes
                    commitLimits()
                }
            }

            Text("El que se aplica a las categorías y a cada aplicación nueva que elijas, hasta que le pongas el suyo.")
                .font(.focusCaption)
                .foregroundStyle(FocusColor.outline)
        }
    }

    private var windowsCard: some View {
        NavigationLink {
            SchedulesView()
        } label: {
            QuietCard {
                HStack {
                    VStack(alignment: .leading, spacing: Spacing.xs) {
                        Text("Franjas horarias")
                            .font(.focusTitle)
                            .foregroundStyle(FocusColor.textOnInk)
                        Text(windowsSummary)
                            .font(.focusCaption)
                            .foregroundStyle(FocusColor.textMuted)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.caption)
                        .foregroundStyle(FocusColor.outline)
                }
            }
        }
    }

    private var windowsSummary: String {
        let active = store.scheduleWindows.filter(\.enabled)
        guard let first = active.first else {
            return "Ninguna. Por ejemplo: de 22:00 a 08:00."
        }
        return active.count == 1
            ? "\(first.clockDescription) · \(first.daysDescription)"
            : "\(active.count) franjas activas"
    }

    private var footnote: some View {
        Text(footnoteText)
            .font(.focusCaption)
            .foregroundStyle(FocusColor.outline)
            .padding(.horizontal, Spacing.xs)
    }

    private var footnoteText: String {
        #if canImport(FamilyControls)
        if #available(iOS 16.0, *), selection.applicationTokens.count > ScreenTimeController.detailedTrackingAppLimit {
            return "Con más de \(ScreenTimeController.detailedTrackingAppLimit) aplicaciones, iOS deja de admitir tantos avisos intermedios: el bloqueo sigue funcionando igual, pero «Hoy» solo sabrá si has llegado al límite o no."
        }
        #endif
        return "iOS no permite leer tus minutos exactos. El sistema avisa al cruzar un umbral, así que la app muestra siempre «al menos», nunca una cifra que no tiene."
    }

    // MARK: - Persistence

    private func load() {
        defaultMinutes = store.defaultLimitMinutes
        limits = store.limitMinutes
        #if canImport(FamilyControls)
        if #available(iOS 16.0, *) {
            selection = ScreenTimeController.shared.loadSelection()
        }
        #endif
    }

    private func saveSelection() {
        #if canImport(FamilyControls)
        if #available(iOS 16.0, *) {
            ScreenTimeController.shared.saveSelection(selection)
            limits = store.limitMinutes
            restartMonitoring()
        }
        #endif
    }

    private func commitLimits() {
        store.limitMinutes = limits
        restartMonitoring()
    }

    private func restartMonitoring() {
        #if canImport(FamilyControls)
        if #available(iOS 16.0, *) {
            do {
                try ScreenTimeController.shared.startMonitoring()
                monitoringError = nil
            } catch {
                // Apple documents that this throws when asked to watch too
                // much at once, without saying where the line is. Saying so is
                // better than a screen that silently stops enforcing anything.
                monitoringError = "\(error.localizedDescription) Prueba a limitar menos aplicaciones o a quitar alguna franja."
            }
        }
        #endif
    }
}

enum Limits {
    /// Coarser steps as the number grows: nobody needs 5-minute precision on a
    /// four-hour limit, and the slider becomes unusable.
    static func snap(_ raw: Int) -> Int {
        raw <= 60 ? (raw / 5) * 5 : raw <= 120 ? (raw / 10) * 10 : (raw / 15) * 15
    }
}

#if canImport(FamilyControls)
@available(iOS 16.0, *)
struct AppLimitEditor: View {
    let key: String
    let token: ApplicationToken
    @Binding var minutes: Int
    let onCommit: () -> Void

    @EnvironmentObject private var appState: AppState
    @Environment(\.dismiss) private var dismiss
    @State private var windows: [ScheduleWindow] = []

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                QuietCard {
                    Label(token)
                        .labelStyle(.titleAndIcon)
                        .font(.focusHeadline)
                        .foregroundStyle(FocusColor.textOnInk)

                    Text(Durations.format(minutes: minutes) + " al día")
                        .font(.focusTitle)
                        .foregroundStyle(FocusColor.gold)
                        .padding(.top, Spacing.sm)

                    Slider(
                        value: Binding(
                            get: { Double(minutes) },
                            set: { minutes = Limits.snap(Int($0)) }
                        ),
                        in: 5...240,
                        step: 5
                    )

                    HStack(spacing: Spacing.sm) {
                        ForEach([15, 30, 45, 60, 90], id: \.self) { preset in
                            Button("\(preset)m") { minutes = preset }
                                .font(.focusCaption)
                                .foregroundStyle(minutes == preset ? FocusColor.gold : FocusColor.textMuted)
                                .padding(.horizontal, Spacing.sm)
                                .padding(.vertical, Spacing.xs)
                                .overlay(
                                    Capsule().stroke(
                                        minutes == preset ? FocusColor.gold : FocusColor.outline,
                                        lineWidth: 1
                                    )
                                )
                        }
                    }
                }

                if !windows.isEmpty {
                    QuietCard {
                        Text("FRANJAS QUE LE AFECTAN")
                            .font(.focusCaption)
                            .foregroundStyle(FocusColor.textMuted)

                        ForEach($windows) { $window in
                            Toggle(isOn: Binding(
                                get: { window.covers(key: key) },
                                set: { on in
                                    var keys = Set(window.appKeys)
                                    if on {
                                        // An empty list means "every app", so
                                        // opting one app out has to make the
                                        // list explicit first.
                                        keys.insert(key)
                                    } else if window.appKeys.isEmpty {
                                        keys = Set(appState.allAppKeys())
                                        keys.remove(key)
                                    } else {
                                        keys.remove(key)
                                    }
                                    window.appKeys = Array(keys)
                                }
                            )) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(window.clockDescription)
                                        .foregroundStyle(FocusColor.textOnInk)
                                    Text(window.daysDescription)
                                        .font(.focusCaption)
                                        .foregroundStyle(FocusColor.textMuted)
                                }
                            }
                            .tint(FocusColor.gold)
                        }
                    }
                }
            }
            .padding(Spacing.lg)
        }
        .background(FocusColor.ink)
        .navigationTitle("Límite")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { windows = appState.store.scheduleWindows }
        .onDisappear {
            appState.store.scheduleWindows = windows
            onCommit()
        }
    }
}
#endif
