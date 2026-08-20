import DeviceActivity
import FamilyControls
import ManagedSettings
import Foundation

/// Applies the shield when a limit is reached or a window opens.
///
/// This runs in its own process, woken by the system, with a very small memory
/// budget and no UI whatsoever. So it does two things and nothing else: write
/// down what the system just told us, and recompute the shields. Everything the
/// user sees afterwards is the system's shield, and everything they *do*
/// happens back in the app.
class DeviceActivityMonitorExtension: DeviceActivityMonitor {

    private let shared = SharedStore.shared
    private lazy var controller = ScreenTimeController(shared: shared)

    // MARK: - Thresholds

    /// A threshold was crossed. The event's name carries both which app and how
    /// many minutes it stands for, because the callback gives us nothing else.
    override func eventDidReachThreshold(
        _ event: DeviceActivityEvent.Name,
        activity: DeviceActivityName
    ) {
        super.eventDidReachThreshold(event, activity: activity)

        guard let parsed = TokenKey.parseEvent(event.rawValue) else { return }

        let dayKey = DayBoundary.dayKey(for: Date(), resetHour: shared.dayResetHour)
        shared.recordUsageFloor(
            dayKey: dayKey,
            appKey: parsed.key,
            minutes: parsed.thresholdMinutes
        )

        // Categories are shielded as a group: iOS reports them as one, so
        // there is no per-app number to act on.
        if parsed.key == "categories" {
            shieldCategories()
            return
        }

        // `applyShields` decides on its own whether this floor has reached the
        // app's limit, so an intermediate 50 % threshold updates the number the
        // home screen shows without shielding anything.
        controller.applyShields()
    }

    private func shieldCategories() {
        guard !shared.hasLiveGrant,
              let data = shared.selectionData,
              let selection = try? JSONDecoder().decode(FamilyActivitySelection.self, from: data),
              !selection.categoryTokens.isEmpty
        else { return }

        ManagedSettingsStore(named: ScreenTimeController.storeName)
            .shield.applicationCategories = .specific(selection.categoryTokens)
    }

    // MARK: - Intervals

    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)

        if activity == ScreenTimeController.dailyActivity {
            // A new logical day: yesterday's floors no longer apply, so the
            // shields they justified come off.
            shared.grantExpiry = nil
        }
        // A window opening needs no special case: `applyShields` asks the
        // windows themselves whether today is one of their days, which is how
        // "only Monday to Friday" works at all - a DeviceActivitySchedule has
        // no concept of a weekday.
        controller.applyShields()
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)

        if activity == ScreenTimeController.dailyActivity {
            shared.grantExpiry = nil
        }
        controller.applyShields()
    }

    /// The system offers a warning before a window starts. Used only to keep
    /// the shields honest if a callback was missed while the device was off.
    override func intervalWillStartWarning(for activity: DeviceActivityName) {
        super.intervalWillStartWarning(for: activity)
        controller.applyShields()
    }
}
