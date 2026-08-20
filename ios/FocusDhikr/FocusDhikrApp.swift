import SwiftUI

#if canImport(FamilyControls)
import FamilyControls
#endif

@main
struct FocusDhikrApp: App {
    @StateObject private var appState = AppState()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(appState)
                .preferredColorScheme(.dark)
                .tint(FocusColor.gold)
        }
    }
}

/// App-wide state, kept deliberately small.
@MainActor
final class AppState: ObservableObject {
    @Published var authorized = false
    @Published var pendingGate: SharedStore.PendingGate?

    let store = SharedStore.shared

    init() {
        refresh()
    }

    /// Picks up a pause the shield asked for, and clears an expired grant.
    ///
    /// Called on every foreground because the handoff happens in another
    /// process: the shield extension writes the request and the user then
    /// switches over here.
    func refresh() {
        #if canImport(FamilyControls)
        if #available(iOS 16.0, *) {
            authorized = AuthorizationCenter.shared.authorizationStatus == .approved
            ScreenTimeController.shared.clearGrantIfExpired()
        }
        #endif

        if let pending = store.pendingGate, pending.isFresh {
            pendingGate = pending
        } else {
            store.pendingGate = nil
            pendingGate = nil
        }
    }

    func finishGate() {
        store.pendingGate = nil
        pendingGate = nil
    }
}

struct RootView: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        Group {
            if let pending = appState.pendingGate {
                // A pause the user asked for takes priority over everything.
                GateView(appName: pending.appName) { appState.finishGate() }
            } else if !appState.store.onboardingComplete {
                OnboardingView()
            } else {
                MainTabView()
            }
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active { appState.refresh() }
        }
    }
}

struct MainTabView: View {
    var body: some View {
        TabView {
            HomeView()
                .tabItem { Label("Hoy", systemImage: "sun.max") }
            AppsView()
                .tabItem { Label("Aplicaciones", systemImage: "square.grid.2x2") }
            StatsView()
                .tabItem { Label("Historial", systemImage: "chart.bar") }
            SettingsView()
                .tabItem { Label("Ajustes", systemImage: "gearshape") }
        }
    }
}
