import ManagedSettings
import ManagedSettingsUI
import UIKit

/// The blocking screen, as far as iOS will allow.
///
/// This is the honest ceiling of what Apple permits: a background colour, a
/// blur style, an icon, a title, a subtitle and two buttons. No countdown, no
/// text field, no navigation, no second screen. The six-phase flow the user
/// asked for cannot live here - it lives in the app, and the primary button
/// hands off to it.
///
/// See docs/LIMITES_PLATAFORMA.md, section I2.
class ShieldConfigurationExtension: ShieldConfigurationDataSource {

    private let shared = SharedStore.shared

    override func configuration(shielding application: Application) -> ShieldConfiguration {
        // Note the asymmetry worth knowing about: here, and only here, iOS does
        // give us the app's display name. The main app never learns it.
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

    private func makeConfiguration(appName: String?) -> ShieldConfiguration {
        let dhikr = DhikrLibrary.enabled(shared.enabledDhikrIds).first
        let showDhikr = shared.spiritualDepth != "off"

        let name = appName ?? "esta aplicación"
        var subtitle = "Habías decidido limitar \(name).\n\nToca «Quiero entrar igualmente» y abre Focus Dhikr para decidirlo con calma."

        if showDhikr, let dhikr {
            subtitle = "\(dhikr.arabic)\n\(dhikr.transliteration)\n\n" + subtitle
        }

        return ShieldConfiguration(
            backgroundBlurStyle: .systemUltraThinMaterialDark,
            backgroundColor: UIColor(red: 0.07, green: 0.06, blue: 0.05, alpha: 0.94),
            icon: nil,
            title: ShieldConfiguration.Label(
                text: "Pausa",
                color: UIColor(red: 0.91, green: 0.89, blue: 0.85, alpha: 1)
            ),
            subtitle: ShieldConfiguration.Label(
                text: subtitle,
                color: UIColor(red: 0.66, green: 0.63, blue: 0.60, alpha: 1)
            ),
            primaryButtonLabel: ShieldConfiguration.Label(
                text: "Quiero entrar igualmente",
                color: UIColor(red: 0.07, green: 0.06, blue: 0.05, alpha: 1)
            ),
            primaryButtonBackgroundColor: UIColor(red: 0.79, green: 0.64, blue: 0.15, alpha: 1),
            secondaryButtonLabel: ShieldConfiguration.Label(
                text: "Dejarlo por ahora",
                color: UIColor(red: 0.66, green: 0.63, blue: 0.60, alpha: 1)
            )
        )
    }
}
