import Foundation

/// Verified hadith citations.
///
/// Same rules as `QuranLibrary`, plus one more: the grading is stated in the UI
/// every single time, with the authority who gave it. A report whose
/// authenticity could not be stated plainly is simply not here.
///
/// Generated from the Android `HadithLibrary.kt`; CI verifies both copies.
public enum HadithLibrary {

    public static let all: [HadithCitation] = [
        HadithCitation(
            collection: "Sahih al-Bujari",
            reference: "6412",
            narrator: "Ibn 'Abbas",
            arabic: "نِعْمَتَانِ مَغْبُونٌ فِيهِمَا كَثِيرٌ مِنَ النَّاسِ الصِّحَّةُ وَالْفَرَاغُ",
            translationEs: "Hay dos bendiciones en las que muchas personas salen perdiendo: la salud y el tiempo libre.",
            grading: "Sahih (auténtico)",
            gradingAuthority: "Al-Bujari",
            sourceUrl: "https://sunnah.com/bukhari:6412",
            partial: true,
            themes: [.valueOfTime]
        ),
        HadithCitation(
            collection: "Yami' at-Tirmidhi",
            reference: "2417",
            narrator: "Abu Barza al-Aslami",
            arabic: "لَا تَزُولُ قَدَمَا عَبْدٍ يَوْمَ الْقِيَامَةِ حَتَّى يُسْأَلَ عَنْ عُمُرِهِ فِيمَا أَفْنَاهُ",
            translationEs: "Los pies de un siervo no se moverán el Día de la Resurrección hasta que se le pregunte por su vida y en qué la consumió.",
            grading: "Hasan sahih (bueno y auténtico)",
            gradingAuthority: "At-Tirmidhi",
            sourceUrl: "https://sunnah.com/tirmidhi:2417",
            partial: true,
            themes: [.valueOfTime, .whatBenefitsYou]
        ),
        HadithCitation(
            collection: "Yami' at-Tirmidhi",
            reference: "2317",
            narrator: "Abu Huraira",
            arabic: "مِنْ حُسْنِ إِسْلَامِ الْمَرْءِ تَرْكُهُ مَا لَا يَعْنِيهِ",
            translationEs: "Parte de la excelencia del islam de una persona es que deje aquello que no le concierne.",
            grading: "Hasan (bueno). At-Tirmidhi lo calificó de garib por esta vía",
            gradingAuthority: "An-Nawawi (Los Cuarenta Hadices, n.º 12)",
            sourceUrl: "https://sunnah.com/tirmidhi:2317",
            partial: true,
            themes: [.whatBenefitsYou, .selfDiscipline]
        ),
        HadithCitation(
            collection: "Sahih al-Bujari",
            reference: "6464",
            narrator: "'A'isha",
            arabic: "وَأَنَّ أَحَبَّ الأَعْمَالِ أَدْوَمُهَا إِلَى اللَّهِ وَإِنْ قَلَّ",
            translationEs: "Y ciertamente, las obras más amadas por Allah son las más constantes, aunque sean pocas.",
            grading: "Sahih (auténtico)",
            gradingAuthority: "Al-Bujari",
            sourceUrl: "https://sunnah.com/bukhari:6464",
            partial: true,
            themes: [.selfDiscipline, .patience]
        ),
        HadithCitation(
            collection: "Sahih al-Bujari",
            reference: "6682",
            narrator: "Abu Huraira",
            arabic: "كَلِمَتَانِ خَفِيفَتَانِ عَلَى اللِّسَانِ ثَقِيلَتَانِ فِي الْمِيزَانِ حَبِيبَتَانِ إِلَى الرَّحْمَنِ سُبْحَانَ اللَّهِ وَبِحَمْدِهِ سُبْحَانَ اللَّهِ الْعَظِيمِ",
            translationEs: "Dos palabras ligeras en la lengua, pesadas en la balanza y amadas por el Misericordioso: SubhanAllahi wa bihamdih, SubhanAllahil-'Adhim.",
            grading: "Sahih (auténtico). También en Sahih Muslim 2694",
            gradingAuthority: "Al-Bujari y Muslim",
            sourceUrl: "https://sunnah.com/bukhari:6682",
            partial: true,
            themes: [.remembrance]
        ),
        HadithCitation(
            collection: "Al-Mustadrak de Al-Hakim",
            reference: "7846",
            narrator: "Ibn 'Abbas",
            arabic: "اغْتَنِمْ خَمْسًا قَبْلَ خَمْسٍ",
            translationEs: "Aprovecha cinco cosas antes de otras cinco: tu juventud antes de tu vejez, tu salud antes de tu enfermedad, tu riqueza antes de tu pobreza, tu tiempo libre antes de tu ocupación y tu vida antes de tu muerte.",
            grading: "Sahih (auténtico) según Al-Hakim; hasan según Al-'Iraqi. Recogido también por Al-Bayhaqi en Shu'ab al-Iman",
            gradingAuthority: "Al-Hakim, corroborado por Al-Albani",
            sourceUrl: "https://sunnah.com/search?q=%D8%A7%D8%BA%D8%AA%D9%86%D9%85+%D8%AE%D9%85%D8%B3%D8%A7+%D9%82%D8%A8%D9%84+%D8%AE%D9%85%D8%B3",
            partial: true,
            themes: [.valueOfTime]
        ),
        HadithCitation(
            collection: "Sahih al-Bujari",
            reference: "6114",
            narrator: "Abu Huraira",
            arabic: "لَيْسَ الشَّدِيدُ بِالصُّرَعَةِ إِنَّمَا الشَّدِيدُ الَّذِي يَمْلِكُ نَفْسَهُ عِنْدَ الْغَضَبِ",
            translationEs: "El fuerte no es el que vence en la lucha; el fuerte es el que se domina a sí mismo cuando se enfada.",
            grading: "Sahih (auténtico). También en Sahih Muslim 2609",
            gradingAuthority: "Al-Bujari y Muslim",
            sourceUrl: "https://sunnah.com/bukhari:6114",
            partial: true,
            themes: [.selfDiscipline, .restrainingDesire]
        ),
    ]

    public static func byTheme(_ theme: Theme) -> [HadithCitation] {
        all.filter { $0.themes.contains(theme) }
    }

    public static func pick(theme: Theme?, seed: Int) -> HadithCitation {
        let pool = theme.map(byTheme).flatMap { $0.isEmpty ? nil : $0 } ?? all
        return pool[abs(seed) % pool.count]
    }
}
