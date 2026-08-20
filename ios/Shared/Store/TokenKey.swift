import Foundation

#if canImport(FamilyControls)
import FamilyControls
import DeviceActivity
// ApplicationToken is a ManagedSettings type, not a FamilyControls one. Without
// this import it is simply not in scope in the shield extensions, which do not
// import ManagedSettings on their own account.
import ManagedSettings
#endif

/// The one naming scheme for apps, shared by the app and all four extensions.
///
/// `ApplicationToken` is opaque: it has no name, no bundle id and no stable
/// integer. What it does have is `Codable`, so its encoded bytes are the only
/// identifier available to us, and every process derives the same string from
/// the same token.
///
/// Duplicating this logic per target is how the app and the monitor end up
/// disagreeing about which app a limit belongs to, silently.
public enum TokenKey {

    /// Keeps the key short and free of characters that would need escaping in a
    /// `DeviceActivityEvent.Name` or a defaults key.
    private static func sanitize(_ base64: String) -> String {
        base64
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    #if canImport(FamilyControls)
    public static func key(for token: ApplicationToken) -> String {
        let data = (try? JSONEncoder().encode(token)) ?? Data()
        return String(sanitize(data.base64EncodedString()).prefix(32))
    }

    public static func eventName(key: String, thresholdMinutes: Int) -> DeviceActivityEvent.Name {
        DeviceActivityEvent.Name(eventRawValue(key: key, thresholdMinutes: thresholdMinutes))
    }
    #endif

    /// `<key>#<minutes>`: the monitor extension needs both halves - which app,
    /// and how many minutes that particular threshold stands for - and the name
    /// is the only thing the callback receives.
    public static func eventRawValue(key: String, thresholdMinutes: Int) -> String {
        "\(key)#\(thresholdMinutes)"
    }

    public static func parseEvent(_ rawValue: String) -> (key: String, thresholdMinutes: Int)? {
        let parts = rawValue.split(separator: "#")
        guard parts.count == 2, let minutes = Int(parts[1]) else { return nil }
        return (String(parts[0]), minutes)
    }

    /// The name of the DeviceActivity activity that carries a schedule window.
    public static func windowActivityRawValue(_ id: UUID) -> String {
        "window.\(id.uuidString.prefix(8))"
    }

    public static func parseWindowActivity(_ rawValue: String) -> String? {
        guard rawValue.hasPrefix("window.") else { return nil }
        return String(rawValue.dropFirst("window.".count))
    }

    /// Usage floors are stored per logical day so that yesterday's total never
    /// leaks into today's decision.
    public static func usageKey(dayKey: String, appKey: String) -> String {
        "\(dayKey)|\(appKey)"
    }
}
