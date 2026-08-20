import ManagedSettings
import UserNotifications
import Foundation

/// Handles the two buttons on the shield.
///
/// iOS gives this extension a token, an action, and three possible replies -
/// `.close`, `.defer` and `.none` - with no way to present anything at all.
/// Apple documents that it deliberately withholds the app's *name* here, even
/// though the shield-configuration extension gets it. So:
///
///  - **Dejarlo por ahora** → `.close`, which dismisses the shield and returns
///    the user to the Home screen. That is the whole point of the app, and on
///    iOS it is the one action the system performs cleanly.
///  - **Quiero entrar igualmente** → record the request in the App Group, post
///    a notification the user can tap to open Focus Dhikr, and `.defer` so the
///    shield stays up. The real six-phase pause then runs inside the app.
///
/// On iOS 26.5 and later there is an official reply for that handoff -
/// `.openParentalControlsApp` - which removes the notification tap entirely.
/// It needs the 26.5 SDK to compile; see `FOCUSDHIKR_MODERN_SHIELD` in
/// `ios/project.yml`.
class ShieldActionExtension: ShieldActionDelegate {

    private let shared = SharedStore.shared

    override func handle(
        action: ShieldAction,
        for application: ApplicationToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        respond(to: action, appKey: TokenKey.key(for: application), completionHandler: completionHandler)
    }

    override func handle(
        action: ShieldAction,
        for webDomain: WebDomainToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        respond(to: action, appKey: nil, completionHandler: completionHandler)
    }

    override func handle(
        action: ShieldAction,
        for category: ActivityCategoryToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        respond(to: action, appKey: nil, completionHandler: completionHandler)
    }

    private func respond(
        to action: ShieldAction,
        appKey: String?,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        switch action {
        case .primaryButtonPressed:
            shared.pendingGate = SharedStore.PendingGate(
                appName: "esta aplicación",
                reason: appKey.flatMap { shared.shieldReasons[$0] },
                appKey: appKey
            )
            #if FOCUSDHIKR_MODERN_SHIELD
            if #available(iOS 26.5, *) {
                completionHandler(.openParentalControlsApp)
                return
            }
            #endif
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
