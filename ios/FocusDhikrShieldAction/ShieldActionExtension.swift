import ManagedSettings
import UserNotifications
import Foundation

/// Handles the two buttons on the shield.
///
/// iOS gives this extension exactly three possible replies - `.close`, `.defer`
/// and `.none` - and no way to present anything. So:
///
///  - **Dejarlo por ahora** → `.close`, which dismisses the shield and returns
///    the user to the Home screen. That is the whole point of the app, and on
///    iOS it is the one action the system performs cleanly.
///  - **Quiero entrar igualmente** → record the request in the App Group, post
///    a notification the user can tap to open Focus Dhikr, and `.defer` so the
///    shield stays up. The real six-phase pause then runs inside the app.
///
/// The handoff is a tap the user has to make themselves. An extension cannot
/// open its containing app, so this is as seamless as iOS permits.
class ShieldActionExtension: ShieldActionDelegate {

    private let shared = SharedStore.shared

    override func handle(
        action: ShieldAction,
        for application: ApplicationToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        respond(to: action, appName: nil, completionHandler: completionHandler)
    }

    override func handle(
        action: ShieldAction,
        for webDomain: WebDomainToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        respond(to: action, appName: nil, completionHandler: completionHandler)
    }

    override func handle(
        action: ShieldAction,
        for category: ActivityCategoryToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        respond(to: action, appName: nil, completionHandler: completionHandler)
    }

    private func respond(
        to action: ShieldAction,
        appName: String?,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        switch action {
        case .primaryButtonPressed:
            shared.pendingGate = SharedStore.PendingGate(
                appName: appName ?? "esta aplicación"
            )
            postHandoffNotification()
            completionHandler(.defer)

        case .secondaryButtonPressed:
            recordTurnedBack()
            completionHandler(.close)

        @unknown default:
            completionHandler(.close)
        }
    }

    /// Records a turn-back straight from the shield.
    ///
    /// This is the common case on iOS and it never reaches the app, so if it
    /// were not counted here the statistics would only ever show the times the
    /// user gave in - exactly the wrong half.
    private func recordTurnedBack() {
        let now = Date()
        shared.recordAttempt(
            GateAttempt(
                dayKey: DayBoundary.dayKey(for: now, resetHour: shared.dayResetHour),
                startedAt: now,
                endedAt: now,
                reachedPhase: GatePhase.pause.rawValue,
                outcome: GateOutcome.turnedBack.rawValue
            )
        )
    }

    private func postHandoffNotification() {
        let content = UNMutableNotificationContent()
        content.title = "Abre Focus Dhikr"
        content.body = "Tómate un momento antes de entrar."
        content.sound = nil

        let request = UNNotificationRequest(
            identifier: "focusdhikr.handoff",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request, withCompletionHandler: nil)
    }
}
