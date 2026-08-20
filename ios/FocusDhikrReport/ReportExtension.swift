import DeviceActivity
import ManagedSettings
import SwiftUI

/// The only place in the whole app where real minutes exist.
///
/// `DeviceActivity` never tells the app "45 minutes today". It tells *this*
/// extension, which runs in a sandbox that - in Apple's words - "prevents your
/// extension from making network requests or moving sensitive content outside
/// the extension's address space".
///
/// So the numbers below can be drawn on screen, inside a `DeviceActivityReport`
/// view the app embeds, and can never be read back by the app, written to the
/// App Group, or used to decide anything. What you see is real; what the app
/// counts is the pause history. Both screens say which is which.
@main
struct FocusDhikrReport: DeviceActivityReportExtension {
    var body: some DeviceActivityReportScene {
        UsageReportScene { summary in
            UsageReportView(summary: summary)
        }
    }
}

/// What the extension managed to work out from the raw results.
struct UsageSummary {
    struct Row: Identifiable {
        let id: String
        let name: String
        let seconds: TimeInterval
        let pickups: Int
    }

    var totalSeconds: TimeInterval = 0
    var pickups: Int = 0
    var rows: [Row] = []
}

struct UsageReportScene: DeviceActivityReportScene {
    /// Must match the context the app asks for.
    let context: DeviceActivityReport.Context = .init("focusdhikr.usage")
    let content: (UsageSummary) -> UsageReportView

    func makeConfiguration(
        representing data: DeviceActivityResults<DeviceActivityData>
    ) async -> UsageSummary {
        var summary = UsageSummary()
        var byApp: [String: UsageSummary.Row] = [:]

        // Everything here is an AsyncSequence, four levels deep, and the whole
        // thing is re-run by the system whenever the filter changes.
        for await result in data {
            for await segment in result.activitySegments {
                summary.totalSeconds += segment.totalActivityDuration
                for await category in segment.categories {
                    for await app in category.applications {
                        let name = app.application.localizedDisplayName
                            ?? app.application.bundleIdentifier
                            ?? "Otra aplicación"
                        summary.pickups += app.numberOfPickups
                        let existing = byApp[name]
                        byApp[name] = UsageSummary.Row(
                            id: name,
                            name: name,
                            seconds: (existing?.seconds ?? 0) + app.totalActivityDuration,
                            pickups: (existing?.pickups ?? 0) + app.numberOfPickups
                        )
                    }
                }
            }
        }

        summary.rows = byApp.values
            .filter { $0.seconds >= 60 }
            .sorted { $0.seconds > $1.seconds }
        return summary
    }
}

struct UsageReportView: View {
    let summary: UsageSummary

    private func minutes(_ seconds: TimeInterval) -> Int { Int(seconds / 60) }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 4) {
                Text(Durations.format(minutes: minutes(summary.totalSeconds)))
                    .font(.system(size: 34, weight: .light, design: .serif))
                    .foregroundStyle(FocusColor.gold)
                Text("en total, según el propio iOS")
                    .font(.footnote)
                    .foregroundStyle(FocusColor.textMuted)
            }

            if summary.rows.isEmpty {
                Text("Todavía no hay actividad registrada en este periodo.")
                    .font(.callout)
                    .foregroundStyle(FocusColor.outline)
            } else {
                ForEach(summary.rows.prefix(8)) { row in
                    HStack(alignment: .firstTextBaseline) {
                        Text(row.name)
                            .font(.callout)
                            .foregroundStyle(FocusColor.textOnInk)
                        Spacer(minLength: 12)
                        Text(Durations.format(minutes: minutes(row.seconds)))
                            .font(.callout.monospacedDigit())
                            .foregroundStyle(FocusColor.textMuted)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
