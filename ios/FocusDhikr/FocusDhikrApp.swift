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
            // `.approved` and, on iOS 26.4+ in the EU, `.approvedWithDataAccess`
            // both mean the app may do its job.
            authorized = AuthorizationCenter.shared.authorizationStatus != .notDetermined
                && AuthorizationCenter.shared.authorizationStatus != .denied
            ScreenTimeController.shared.clearGrantIfExpired()
            // A shield that outlived the rule that justified it is the single
            // most confusing thing this app could do, so every foreground
            // recomputes them from the rules.
            ScreenTimeController.shared.applyShields()
        }
        #endif

        // Wording is chosen when a reminder is scheduled, so rewriting them on
        // every foreground is what keeps a daily nudge from reading like a
        // recording of itself.
        Task { await ReminderScheduler.reschedule(from: store) }
        Task { await AppNames.refresh(store: store) }

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

    /// Every app the user is currently limiting, by token key.
    func allAppKeys() -> [String] {
        #if canImport(FamilyControls)
        if #available(iOS 16.0, *) {
            return Array(ScreenTimeController.shared.tokensByKey().keys)
        }
        #endif
        return []
    }

    /// Recomputes the shields after a rule changed.
    func applyShields() {
        #if canImport(FamilyControls)
        if #available(iOS 16.0, *) {
            ScreenTimeController.shared.applyShields()
        }
        #endif
    }

    /// Re-registers everything with DeviceActivity after a rule changed.
    @discardableResult
    func restartMonitoring() -> String? {
        #if canImport(FamilyControls)
        if #available(iOS 16.0, *) {
            do {
                try ScreenTimeController.shared.startMonitoring()
                return nil
            } catch {
                return error.localizedDescription
            }
        }
        #endif
        return nil
    }
}

struct RootView: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        Group {
            if let pending = appState.pendingGate {
                // A pause the user asked for takes priority over everything.
                GateView(pending: pending) { appState.finishGate() }
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
