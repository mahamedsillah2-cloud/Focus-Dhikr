import SwiftUI

/// A quiet palette.
///
/// Warm near-black and parchment rather than pure black and white, one muted
/// gold accent, and nothing else. No progress bars racing to full, no confetti,
/// no streak flames. Closer to a well-set book than to a game.
enum FocusColor {
    static let ink = Color(red: 0.07, green: 0.06, blue: 0.05)
    static let inkElevated = Color(red: 0.11, green: 0.10, blue: 0.09)
    static let textOnInk = Color(red: 0.91, green: 0.89, blue: 0.85)
    static let textMuted = Color(red: 0.66, green: 0.63, blue: 0.60)
    static let outline = Color(red: 0.16, green: 0.15, blue: 0.14)
    /// The single accent. Used sparingly: remaining time, the active step, dhikr.
    static let gold = Color(red: 0.79, green: 0.64, blue: 0.15)
    /// Never red-for-shame. This is the "you reached your limit" tone.
    static let clay = Color(red: 0.69, green: 0.44, blue: 0.34)
}

enum Spacing {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 16
    static let lg: CGFloat = 24
    static let xl: CGFloat = 32
    static let xxl: CGFloat = 48
}

/// Serif for anything meant to be read slowly, sans for anything scanned.
/// Both are system faces, so the app ships no font binaries.
extension Font {
    static let focusDisplay = Font.system(size: 32, weight: .regular, design: .serif)
    static let focusHeadline = Font.system(size: 24, weight: .regular, design: .serif)
    static let focusTitle = Font.system(size: 17, weight: .medium)
    static let focusBody = Font.system(size: 16)
    static let focusCaption = Font.system(size: 12, weight: .medium)
}

/// The only container shape in the app. One radius, one border, no shadows.
struct QuietCard<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            content
        }
        .padding(Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(FocusColor.inkElevated)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(FocusColor.outline, lineWidth: 1)
        )
    }
}

struct PrimaryAction: View {
    let title: String
    var enabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.focusTitle)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
        }
        .disabled(!enabled)
        .background(enabled ? FocusColor.textOnInk : FocusColor.outline)
        .foregroundStyle(enabled ? FocusColor.ink : FocusColor.textMuted)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

/// The "turn back" affordance.
///
/// Deliberately given equal visual weight to continuing, never made harder to
/// find or smaller. The app does not trick the user in either direction.
struct QuietAction: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.focusTitle)
                .foregroundStyle(FocusColor.textMuted)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
        }
    }
}

/// Dots marking progress through the gate.
///
/// Not a progress bar: a bar racing to 100% invites you to sprint. Dots just
/// say where you are.
struct StepDots: View {
    let current: Int
    let total: Int

    var body: some View {
        HStack(spacing: 6) {
            ForEach(0..<max(total, 1), id: \.self) { index in
                Circle()
                    .fill(index < current ? FocusColor.gold : FocusColor.outline)
                    .frame(width: index == current - 1 ? 7 : 5,
                           height: index == current - 1 ? 7 : 5)
            }
        }
    }
}

struct StatTile: View {
    let value: String
    let label: String
    var accent: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(value)
                .font(.focusHeadline)
                .foregroundStyle(accent ? FocusColor.gold : FocusColor.textOnInk)
            Text(label)
                .font(.focusBody)
                .foregroundStyle(FocusColor.textMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
