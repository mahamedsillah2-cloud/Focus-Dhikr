import Foundation

/// The short adhkar the user can switch on and off individually.
///
/// These are the standard formulas of remembrance, not narrations, so they
/// carry no chain or grading. They are shown as what they are: words to say,
/// with their meaning next to them.
public enum DhikrLibrary {

    public static let subhanAllah = "subhan_allah"
    public static let alhamdulillah = "alhamdulillah"
    public static let allahuAkbar = "allahu_akbar"
    public static let laIlahaIllaAllah = "la_ilaha_illa_allah"
    public static let astaghfirullah = "astaghfirullah"
    public static let laHawla = "la_hawla"
    public static let subhanAllahWaBihamdih = "subhan_allah_wa_bihamdih"

    public static let all: [Dhikr] = [
        Dhikr(id: subhanAllah, arabic: "سُبْحَانَ اللَّهِ",
              transliteration: "SubhanAllah", meaningEs: "Glorificado sea Allah"),
        Dhikr(id: alhamdulillah, arabic: "الْحَمْدُ لِلَّهِ",
              transliteration: "Alhamdulillah", meaningEs: "Toda alabanza pertenece a Allah"),
        Dhikr(id: allahuAkbar, arabic: "اللَّهُ أَكْبَرُ",
              transliteration: "Allahu Akbar", meaningEs: "Allah es más grande"),
        Dhikr(id: laIlahaIllaAllah, arabic: "لَا إِلَٰهَ إِلَّا اللَّهُ",
              transliteration: "La ilaha illa Allah", meaningEs: "No hay más divinidad que Allah"),
        Dhikr(id: astaghfirullah, arabic: "أَسْتَغْفِرُ اللَّهَ",
              transliteration: "Astaghfirullah", meaningEs: "Pido perdón a Allah"),
        Dhikr(id: laHawla, arabic: "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ",
              transliteration: "La hawla wa la quwwata illa billah",
              meaningEs: "No hay fuerza ni poder sino en Allah"),
        Dhikr(id: subhanAllahWaBihamdih, arabic: "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
              transliteration: "SubhanAllahi wa bihamdih",
              meaningEs: "Glorificado sea Allah y con Su alabanza"),
    ]

    /// What is enabled the first time the app runs: the three the user named.
    public static let defaultEnabledIds: Set<String> = [subhanAllah, alhamdulillah, allahuAkbar]

    public static func byId(_ id: String) -> Dhikr? { all.first { $0.id == id } }

    /// Never returns empty: an empty selection would silently show nothing
    /// rather than reading as "off", which is a separate setting.
    public static func enabled(_ ids: Set<String>) -> [Dhikr] {
        let picked = all.filter { ids.contains($0.id) }
        return picked.isEmpty ? all.filter { defaultEnabledIds.contains($0.id) } : picked
    }
}
