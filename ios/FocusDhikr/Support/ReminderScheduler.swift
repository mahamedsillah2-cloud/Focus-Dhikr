import Foundation
import UserNotifications

/// Local notifications for the reminders in the settings screen.
///
/// Everything is scheduled with `UNCalendarNotificationTrigger`, on the device,
/// with no server and no push token. The app never sends a notification it was
/// not explicitly asked for: there is no "we noticed you have been scrolling"
/// nudge, because that is the kind of thing that gets an app deleted.
enum ReminderScheduler {

    private static let prefix = "focusdhikr.reminder."

    static func requestPermission() async -> Bool {
        let center = UNUserNotificationCenter.current()
        return (try? await center.requestAuthorization(options: [.alert, .sound])) ?? false
    }

    /// Rewrites every scheduled reminder from the stored list.
    ///
    /// Called on save and on foreground: the wording is picked fresh each time,
    /// so a reminder that fires every morning does not read like a recording.
    static func reschedule(from store: SharedStore = .shared) async {
        let center = UNUserNotificationCenter.current()
        let stale = await center.pendingNotificationRequests()
            .map(\.identifier)
            .filter { $0.hasPrefix(prefix) }
        center.removePendingNotificationRequests(withIdentifiers: stale)

        let settings = await center.notificationSettings()
        guard settings.authorizationStatus == .authorized
                || settings.authorizationStatus == .provisional
        else { return }

        for reminder in store.reminders where reminder.enabled {
            for isoDay in reminder.isoDays {
                var components = DateComponents()
                // UNCalendarNotificationTrigger wants 1 = Sunday.
                components.weekday = isoDay == 7 ? 1 : isoDay + 1
                components.hour = reminder.minuteOfDay / 60
                components.minute = reminder.minuteOfDay % 60

                let content = UNMutableNotificationContent()
                let body = text(for: reminder.kind, store: store)
                content.title = body.title
                content.body = body.detail
                content.sound = nil
                content.interruptionLevel = .passive

                let request = UNNotificationRequest(
                    identifier: "\(prefix)\(reminder.id.uuidString).\(isoDay)",
                    content: content,
                    trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
                )
                try? await center.add(request)
            }
        }
    }

    static func cancelAll() {
        let center = UNUserNotificationCenter.current()
        center.getPendingNotificationRequests { requests in
            let ids = requests.map(\.identifier).filter { $0.hasPrefix(prefix) }
            center.removePendingNotificationRequests(withIdentifiers: ids)
        }
    }

    // MARK: - Wording

    private static func text(
        for kind: Reminder.Kind,
        store: SharedStore
    ) -> (title: String, detail: String) {
        let seed = Int(Date().timeIntervalSince1970)

        switch kind {
        case .goals:
            let goals = store.activeGoals
            guard let goal = goals.isEmpty ? nil : goals[abs(seed) % goals.count] else {
                return ("Tus objetivos", "Todavía no has escrito ninguno.")
            }
            return ("Recuerda por qué", goal.title)

        case .rest:
            return ("Descansa un momento", "Levanta la vista y mueve el cuerpo.")

        case .dhikr:
            guard store.spiritualDepth != "off",
                  let dhikr = DhikrLibrary.enabled(store.enabledDhikrIds).first
            else {
                return ("Una pausa", "Un momento de calma.")
            }
            return (dhikr.transliteration, store.showTranslations ? dhikr.meaningEs : dhikr.arabic)

        case .reflection:
            return ("Un momento", Reflections.pick(Reflections.ambient, seed: seed))

        case .screenTime:
            return ("Tiempo de pantalla", "Echa un vistazo a cómo ha ido tu día.")
        }
    }
}
