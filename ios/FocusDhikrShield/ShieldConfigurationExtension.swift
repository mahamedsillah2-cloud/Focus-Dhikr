import ManagedSettings
import ManagedSettingsUI
import UIKit

/// The blocking screen, as far as iOS will allow.
///
/// This is the honest ceiling of what Apple permits: a background colour, a
/// blur style, an icon, a title, a subtitle and two buttons. No countdown, no
/// text field, no navigation, no second screen. The six-phase flow cannot live
/// here - it lives in the app, and the primary button hands off to it.
///
/// See docs/AUDITORIA_IOS.md, section 3b.
class ShieldConfigurationExtension: ShieldConfigurationDataSource {

    private let shared = SharedStore.shared

    override func configuration(shielding application: Application) -> ShieldConfiguration {
        // Note the asymmetry worth knowing about: here, and only here, iOS does
        // give us the app's display name. The main app never learns it, and
        // neither does the shield *action* extension.
        makeConfiguration(appName: application.localizedDisplayName)
    }

    override func configuration(
        shielding application: Application,
        in category: ActivityCategory
    ) -> ShieldConfiguration {
        makeConfiguration(appName: application.localizedDisplayName)
    }

    override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration {
        makeConfiguration(appName: webDomain.domain)
    }

    override func configuration(
        shielding webDomain: WebDomain,
        in category: ActivityCategory
    ) -> ShieldConfiguration {
        makeConfiguration(appName: webDomain.domain)
    }

    // MARK: -

    private enum Palette {
        static let ink = UIColor(red: 0.07, green: 0.06, blue: 0.05, alpha: 0.94)
        static let text = UIColor(red: 0.91, green: 0.89, blue: 0.85, alpha: 1)
        static let muted = UIColor(red: 0.66, green: 0.63, blue: 0.60, alpha: 1)
        static let gold = UIColor(red: 0.79, green: 0.64, blue: 0.15, alpha: 1)
    }

    private func makeConfiguration(appName: String?) -> ShieldConfiguration {
        let name = appName ?? "esta aplicación"

        return ShieldConfiguration(
            backgroundBlurStyle: .systemUltraThinMaterialDark,
            backgroundColor: Palette.ink,
            icon: nil,
            title: ShieldConfiguration.Label(text: title, color: Palette.text),
            subtitle: ShieldConfiguration.Label(text: subtitle(for: name), color: Palette.muted),
            primaryButtonLabel: ShieldConfiguration.Label(
                text: "Quiero entrar igualmente",
                color: UIColor(red: 0.07, green: 0.06, blue: 0.05, alpha: 1)
            ),
            primaryButtonBackgroundColor: Palette.gold,
            secondaryButtonLabel: ShieldConfiguration.Label(
                text: "Dejarlo por ahora",
                color: Palette.muted
            )
        )
    }

    private var title: String {
        shared.strictMode ? "Pausa · Modo Disciplina" : "Pausa"
    }

    /// Everything the user is told at the moment of the impulse has to fit in
    /// one string. Order matters: the dhikr first when it is on, because that
    /// is the half-second the app is really buying; then what they decided;
    /// then how to continue if they still want to.
    private func subtitle(for name: String) -> String {
        var lines: [String] = []

        if shared.spiritualDepth != "off",
           let dhikr = DhikrLibrary.enabled(shared.enabledDhikrIds).randomElement() {
            lines.append(dhikr.arabic)
            lines.append(shared.showTranslations ? "\(dhikr.transliteration) - \(dhikr.meaningEs)" : dhikr.transliteration)
            lines.append("")
        }

        lines.append(rule(for: name))
        lines.append("")
        lines.append("Toca «Quiero entrar igualmente» y abre Focus Dhikr para decidirlo con calma.")

        return lines.joined(separator: "\n")
    }

    /// Which of the user's own rules is in force. The shield knows the app's
    /// name but not its token, so it cannot look up that one app's reason; what
    /// it can say honestly is which kinds of rule are active right now.
    private func rule(for name: String) -> String {
        let reasons = Set(shared.shieldReasons.values)
        let hasWindow = reasons.contains(ShieldReason.scheduleWindow.rawValue)
            || reasons.contains(ShieldReason.limitAndWindow.rawValue)

        if hasWindow, let window = activeWindow() {
            return "Estás dentro de la franja que fijaste: \(window.clockDescription). Habías decidido limitar \(name)."
        }
        return "Has alcanzado el límite que tú mismo pusiste a \(name)."
    }

    private func activeWindow() -> ScheduleWindow? {
        let clock = DayBoundary.clock(for: Date())
        return shared.scheduleWindows.first {
            $0.contains(minuteOfDay: clock.minuteOfDay, isoDayOfWeek: clock.isoDayOfWeek)
        }
    }
}
