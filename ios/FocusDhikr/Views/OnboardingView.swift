import SwiftUI

#if canImport(FamilyControls)
import FamilyControls
#endif

/// Four screens: the idea, the honest limits, privacy, and permission.
///
/// The "what this cannot do" screen comes before the permission request on
/// purpose. A tool like this only works if you trust it, and trust starts with
/// it not overselling itself on the first screen.
struct OnboardingView: View {
    @EnvironmentObject private var appState: AppState
    @State private var page = 0
    @State private var requesting = false
    @State private var errorText: String?

    var body: some View {
        ZStack {
            FocusColor.ink.ignoresSafeArea()

            ScrollView {
                VStack(spacing: Spacing.lg) {
                    Spacer(minLength: Spacing.xxl)

                    switch page {
                    case 0:
                        intro(
                            "Recupera tu atención",
                            "Tú decides los límites. Cuando llegues a ellos, esta aplicación te dará unos segundos para pensar antes de entrar."
                        )
                    case 1:
                        intro(
                            "Lo que iOS permite y lo que no",
                            """
                            Apple dibuja la pantalla de bloqueo, no esta aplicación. Ahí solo caben un título, un subtítulo y dos botones.

                            Por eso la reflexión completa ocurre aquí dentro: el botón «Quiero entrar igualmente» te trae a esta app, y es aquí donde están la espera, tus objetivos y la frase escrita.

                            Es un toque más de lo que debería. Es lo que el sistema deja hacer.
                            """
                        )
                    case 2:
                        intro(
                            "Todo se queda en este iPhone",
                            """
                            No hay cuentas, ni analítica, ni anuncios, ni servidores.

                            iOS ni siquiera permite a esta aplicación saber qué apps has elegido: los identificadores que recibe son opacos a propósito.
                            """
                        )
                    default:
                        permission
                    }

                    Spacer(minLength: Spacing.xxl)

                    if page < 3 {
                        PrimaryAction(title: "Continuar") { page += 1 }
                    } else {
                        PrimaryAction(title: "Empezar", enabled: appState.authorized) {
                            appState.store.onboardingComplete = true
                            appState.refresh()
                        }
                    }

                    if page > 0 {
                        QuietAction(title: "Atrás") { page -= 1 }
                    }
                }
                .padding(Spacing.lg)
            }
        }
    }

    private func intro(_ title: String, _ body: String) -> some View {
        VStack(spacing: Spacing.lg) {
            Text(title)
                .font(.focusDisplay)
                .foregroundStyle(FocusColor.textOnInk)
                .multilineTextAlignment(.center)
            Text(body)
                .font(.focusBody)
                .foregroundStyle(FocusColor.textMuted)
                .multilineTextAlignment(.center)
        }
    }

    private var permission: some View {
        VStack(spacing: Spacing.lg) {
            Text("Permiso de Tiempo de uso")
                .font(.focusDisplay)
                .foregroundStyle(FocusColor.textOnInk)
                .multilineTextAlignment(.center)

            Text("Sin él, iOS no deja a ninguna aplicación aplicar límites. Es el único permiso que se pide.")
                .font(.focusBody)
                .foregroundStyle(FocusColor.textMuted)
                .multilineTextAlignment(.center)

            if appState.authorized {
                Text("Concedido")
                    .font(.focusTitle)
                    .foregroundStyle(FocusColor.gold)
            } else {
                Button(requesting ? "Pidiendo…" : "Conceder") { request() }
                    .font(.focusTitle)
                    .foregroundStyle(FocusColor.gold)
                    .disabled(requesting)
            }

            if let errorText {
                Text(errorText)
                    .font(.focusCaption)
                    .foregroundStyle(FocusColor.clay)
                    .multilineTextAlignment(.center)
            }
        }
    }

    private func request() {
        #if canImport(FamilyControls)
        guard #available(iOS 16.0, *) else { return }
        requesting = true
        errorText = nil
        Task {
            do {
                try await ScreenTimeController.shared.requestAuthorization()
            } catch {
                // Most often: the entitlement is missing, or Screen Time is
                // disabled in Settings. Say so instead of failing silently.
                errorText = "No se pudo obtener el permiso. Comprueba que Tiempo de uso está activado en Ajustes."
            }
            requesting = false
            appState.refresh()
        }
        #endif
    }
}
