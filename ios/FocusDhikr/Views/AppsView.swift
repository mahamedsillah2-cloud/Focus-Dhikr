import SwiftUI

#if canImport(FamilyControls)
import FamilyControls
#endif

/// Choosing the apps and their limits.
///
/// The honest caveat lives on this screen, not buried in a document: iOS hands
/// the app opaque tokens, so it cannot show "Instagram — 1 h". It can only
/// render `Label(token)`, which the system draws, and it cannot group limits by
/// a name it never learns.
struct AppsView: View {
    @EnvironmentObject private var appState: AppState
    @State private var showPicker = false
    @State private var defaultMinutes: Int = 60

    #if canImport(FamilyControls)
    @State private var selection = FamilyActivitySelection()
    #endif

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.lg) {
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
                                    // Label(token) is the only way to show an app:
                                    // the system renders it, we never see the name.
                                    Label(token)
                                        .labelStyle(.titleAndIcon)
                                        .foregroundStyle(FocusColor.textOnInk)
                                        .padding(.vertical, 2)
                                }
                                if !selection.categoryTokens.isEmpty {
                                    Text("\(selection.categoryTokens.count) categoría(s)")
                                        .font(.focusBody)
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

                    QuietCard {
                        Text("Límite diario: \(Durations.format(minutes: defaultMinutes))")
                            .font(.focusTitle)
                            .foregroundStyle(FocusColor.gold)

                        Slider(
                            value: Binding(
                                get: { Double(defaultMinutes) },
                                set: { defaultMinutes = snap(Int($0)) }
                            ),
                            in: 5...240,
                            step: 5
                        ) { editing in
                            if !editing { saveLimit() }
                        }

                        Text("iOS no permite leer tus minutos usados, así que el límite se aplica por umbral: el sistema avisa al cruzarlo y ahí aparece la pausa.")
                            .font(.focusCaption)
                            .foregroundStyle(FocusColor.outline)
                    }
                }
                .padding(Spacing.lg)
            }
            .background(FocusColor.ink)
            .navigationTitle("Aplicaciones")
        }
        .onAppear(perform: load)
        #if canImport(FamilyControls)
        .familyActivityPicker(isPresented: $showPicker, selection: $selection)
        .onChange(of: selection) { _ in save() }
        #endif
    }

    private func snap(_ raw: Int) -> Int {
        raw <= 60 ? (raw / 5) * 5 : raw <= 120 ? (raw / 10) * 10 : (raw / 15) * 15
    }

    private func load() {
        defaultMinutes = appState.store.defaultLimitMinutes
        #if canImport(FamilyControls)
        if #available(iOS 16.0, *) {
            selection = ScreenTimeController.shared.loadSelection()
        }
        #endif
    }

    private func save() {
        #if canImport(FamilyControls)
        if #available(iOS 16.0, *) {
            ScreenTimeController.shared.saveSelection(selection)
            try? ScreenTimeController.shared.startMonitoring()
        }
        #endif
    }

    private func saveLimit() {
        appState.store.defaultLimitMinutes = defaultMinutes
        save()
    }
}
