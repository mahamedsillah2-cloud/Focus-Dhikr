import Foundation

#if canImport(FamilyControls)
import FamilyControls
import ManagedSettings
#endif

/// Turning opaque tokens back into "Instagram", where Apple allows it.
///
/// Everywhere else in this app, an `ApplicationToken` has no name: that is the
/// deal Apple makes in exchange for letting any app see Screen Time at all.
/// Since iOS 26.4 there is one documented exception -
/// `FamilyActivityData.installedApplications` returns each app's real bundle
/// identifier alongside its token - and it comes with hard conditions:
///
///  - the `com.apple.developer.family-controls.app-and-website-usage`
///    entitlement,
///  - authorization status `.approvedWithDataAccess`, which the user grants
///    separately and **only one app on the device can hold at a time**,
///  - and, for anyone who is not the developer, a device in the EU signed in
///    with an EU Apple Account.
///
/// So it is a bonus, never a requirement: every screen still works when this
/// returns nothing. It needs the iOS 26.4 SDK to compile, which is why it sits
/// behind `FOCUSDHIKR_MODERN_SHIELD` - see `ios/project.yml`.
enum AppNames {

    /// Whether the device is in the state where names are available at all.
    static var isAvailable: Bool {
        #if FOCUSDHIKR_MODERN_SHIELD
        if #available(iOS 26.4, *) {
            return AuthorizationCenter.shared.authorizationStatus == .approvedWithDataAccess
        }
        #endif
        return false
    }

    /// Fills the shared name cache, if the system is willing.
    ///
    /// Names are cached rather than looked up on demand because the extensions
    /// - which have no way to make this call - read the same cache.
    static func refresh(store: SharedStore = .shared) async {
        #if FOCUSDHIKR_MODERN_SHIELD
        guard #available(iOS 26.4, *), isAvailable else { return }
        guard let apps = try? await FamilyActivityData.shared.installedApplications else { return }

        var names: [String: String] = [:]
        for app in apps {
            guard let token = app.token else { continue }
            let key = TokenKey.key(for: token)
            names[key] = app.localizedDisplayName
                ?? app.bundleIdentifier
                ?? "Aplicación"
        }
        store.appNames = names
        #endif
    }

    /// The user-facing name of an app, or a neutral fallback. The fallback is
    /// the normal case, not an error state.
    static func name(forKey key: String?, store: SharedStore = .shared) -> String? {
        guard let key else { return nil }
        return store.appNames[key]
    }
}
