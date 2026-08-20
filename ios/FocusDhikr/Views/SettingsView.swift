import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var appState: AppState

    @State private var strictMode = false
    @State private var spiritualDepth = "subtle"
    @State private var enabledDhikr: Set<String> = DhikrLibrary.defaultEnabledIds
    @State private var showTranslations = true
    @State private var sentence = GateConfig.defaultSentence
    @State private var dayResetHour = DayBoundary.defaultResetHour
    @State private var keepWrittenReasons = true
    @State private var emergencyPerWeek = 3
    @State private var confirmErase = false

    private var store: SharedStore { appState.store }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Toggle("Activar modo estricto", isOn: $strictMode)
                        .onChange(of: strictMode) { store.strictMode = $0 }
                } header: {
                    Text("Modo «no me dejes entrar»")
                } footer: {
                    Text("Todas las fases, esperas más largas y confirmación escrita siempre.")
                }

                Section {
                    Picker("Presencia en las pausas", selection: $spiritualDepth) {
                        Text("Desactivado").tag("off")
                        Text("Discreto").tag("subtle")
                        Text("Completo").tag("full")
                    }
                    .onChange(of: spiritualDepth) { store.spiritualDepth = $0 }

                    if spiritualDepth != "off" {
                        ForEach(DhikrLibrary.all) { dhikr in
                            Toggle(isOn: Binding(
                                get: { enabledDhikr.contains(dhikr.id) },
                                set: { on in
                                    var next = enabledDhikr
                                    if on { next.insert(dhikr.id) } else { next.remove(dhikr.id) }
                                    // Never end up with none enabled while the
                                    // spiritual layer is on: an empty set would
                                    // silently show nothing rather than "off".
                                    guard !next.isEmpty else { return }
                                    enabledDhikr = next
                                    store.enabledDhikrIds = next
                                }
                            )) {
                                VStack(alignment: .leading) {
                                    Text(dhikr.transliteration)
                                    Text(dhikr.meaningEs)
                                        .font(.focusCaption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }

                        Toggle("Mostrar traducción", isOn: $showTranslations)
                            .onChange(of: showTranslations) { store.showTranslations = $0 }
                    }
                } header: {
                    Text("Recordatorios islámicos")
                }

                Section {
                    Stepper(
                        "El día empieza a las \(String(format: "%02d", dayResetHour)):00",
                        value: $dayResetHour, in: 0...12
                    )
                    .onChange(of: dayResetHour) { store.dayResetHour = $0 }

                    NavigationLink("Frase de reconocimiento") {
                        SentenceEditor(sentence: $sentence) { store.acknowledgementSentence = $0 }
                    }
                } header: {
                    Text("La pausa")
                } footer: {
                    Text("Si trasnochas, medianoche corta tu noche por la mitad y te devuelve el límite en el peor momento.")
                }

                Section {
                    Stepper("Accesos por semana: \(emergencyPerWeek)", value: $emergencyPerWeek, in: 0...10)
                        .onChange(of: emergencyPerWeek) { store.emergencyPerWeek = $0 }
                } header: {
                    Text("Emergencias")
                }

                Section {
                    Toggle("Guardar lo que escribo", isOn: $keepWrittenReasons)
                        .onChange(of: keepWrittenReasons) { value in
                            store.keepWrittenReasons = value
                            if !value { store.forgetWrittenReasons() }
                        }

                    Button("Olvidar lo que he escrito") { store.forgetWrittenReasons() }

                    Button("Borrar todos mis datos", role: .destructive) { confirmErase = true }
                } header: {
                    Text("Privacidad")
                } footer: {
                    Text("Esta aplicación no tiene servidores ni cuentas. Nada de lo que hay aquí sale del iPhone.")
                }

                Section {
                    Text("Cada aleya lleva su sura y número. Cada hadiz, su colección, su número y su grado de autenticidad. Un validador comprueba cada cita contra su fuente antes de cada versión.")
                        .font(.focusCaption)
                        .foregroundStyle(.secondary)
                } header: {
                    Text("Fuentes de las citas")
                }
            }
            .navigationTitle("Ajustes")
            .alert("Borrar todos mis datos", isPresented: $confirmErase) {
                Button("Cancelar", role: .cancel) {}
                Button("Borrar", role: .destructive) {
                    store.eraseEverything()
                    load()
                }
            } message: {
                Text("Se borrará el historial, las estadísticas y los ajustes de este iPhone. No se puede deshacer.")
            }
        }
        .onAppear(perform: load)
    }

    private func load() {
        strictMode = store.strictMode
        spiritualDepth = store.spiritualDepth
        enabledDhikr = store.enabledDhikrIds
        showTranslations = store.showTranslations
        sentence = store.acknowledgementSentence
        dayResetHour = store.dayResetHour
        keepWrittenReasons = store.keepWrittenReasons
        emergencyPerWeek = store.emergencyPerWeek
    }
}

struct SentenceEditor: View {
    @Binding var sentence: String
    let onSave: (String) -> Void

    var body: some View {
        Form {
            Section {
                TextEditor(text: $sentence)
                    .frame(minHeight: 140)
            } footer: {
                Text("La que tendrás que copiar en el último paso.")
            }
        }
        .navigationTitle("Frase")
        .onDisappear {
            let trimmed = sentence.trimmingCharacters(in: .whitespacesAndNewlines)
            onSave(trimmed.count >= 10 ? trimmed : GateConfig.defaultSentence)
        }
    }
}
