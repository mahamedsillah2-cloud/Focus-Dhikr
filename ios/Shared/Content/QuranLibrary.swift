import Foundation

/// Verified Qur'anic citations.
///
/// RULES FOR THIS FILE - read before adding anything:
///
///  1. Nothing is written from memory. Every entry must be checked against a
///     primary source before it lands here.
///  2. `tools/verify_citations.py` runs in CI, downloads every ayah below from
///     the Quran.com API and compares the Arabic character by character after
///     removing diacritics and presentation marks. A mismatch fails the build.
///     This file is the only source of citations, so it is the one CI reads.
///  3. `translationEs` is a translation of the meaning, not a replacement for
///     the Arabic, and the UI labels it as such.
///  4. If you are not certain about a citation, do not add it.
public enum QuranLibrary {

    public static let all: [QuranCitation] = [
        QuranCitation(
            surah: 5,
            ayahStart: 1,
            surahNameEs: "La Mesa Servida",
            surahNameTransliterated: "Al-Ma'ida",
            arabic: "يَا أَيُّهَا الَّذِينَ آمَنُوا أَوْفُوا بِالْعُقُودِ",
            translationEs: "¡Vosotros que creéis! Cumplid vuestros compromisos.",
            partial: true,
            themes: [.keepingCommitments]
        ),
        QuranCitation(
            surah: 17,
            ayahStart: 34,
            surahNameEs: "El Viaje Nocturno",
            surahNameTransliterated: "Al-Isra",
            arabic: "وَأَوْفُوا بِالْعَهْدِ ۖ إِنَّ الْعَهْدَ كَانَ مَسْئُولًا",
            translationEs: "Y cumplid el compromiso: en verdad, se responderá del compromiso.",
            partial: true,
            themes: [.keepingCommitments, .selfDiscipline]
        ),
        QuranCitation(
            surah: 103,
            ayahStart: 1,
            ayahEnd: 3,
            surahNameEs: "El Tiempo",
            surahNameTransliterated: "Al-'Asr",
            arabic: "وَالْعَصْرِ إِنَّ الْإِنسَانَ لَفِي خُسْرٍ إِلَّا الَّذِينَ آمَنُوا وَعَمِلُوا الصَّالِحَاتِ وَتَوَاصَوْا بِالْحَقِّ وَتَوَاصَوْا بِالصَّبْرِ",
            translationEs: "Por el tiempo. En verdad, el ser humano está en pérdida, salvo quienes creen, obran rectamente, se aconsejan mutuamente la verdad y se aconsejan mutuamente la paciencia.",
            themes: [.valueOfTime, .patience]
        ),
        QuranCitation(
            surah: 13,
            ayahStart: 28,
            surahNameEs: "El Trueno",
            surahNameTransliterated: "Ar-Ra'd",
            arabic: "أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ",
            translationEs: "Ciertamente, con el recuerdo de Allah se sosiegan los corazones.",
            partial: true,
            themes: [.remembrance]
        ),
        QuranCitation(
            surah: 79,
            ayahStart: 40,
            ayahEnd: 41,
            surahNameEs: "Los que Arrancan",
            surahNameTransliterated: "An-Nazi'at",
            arabic: "وَأَمَّا مَنْ خَافَ مَقَامَ رَبِّهِ وَنَهَى النَّفْسَ عَنِ الْهَوَىٰ فَإِنَّ الْجَنَّةَ هِيَ الْمَأْوَىٰ",
            translationEs: "Y en cuanto a quien temió la comparecencia ante su Señor y apartó al alma del deseo, el Jardín será su morada.",
            themes: [.restrainingDesire, .selfDiscipline]
        ),
        QuranCitation(
            surah: 2,
            ayahStart: 153,
            surahNameEs: "La Vaca",
            surahNameTransliterated: "Al-Baqara",
            arabic: "يَا أَيُّهَا الَّذِينَ آمَنُوا اسْتَعِينُوا بِالصَّبْرِ وَالصَّلَاةِ ۚ إِنَّ اللَّهَ مَعَ الصَّابِرِينَ",
            translationEs: "¡Vosotros que creéis! Buscad ayuda en la paciencia y en la oración. En verdad, Allah está con los pacientes.",
            partial: true,
            themes: [.patience]
        ),
        QuranCitation(
            surah: 75,
            ayahStart: 36,
            surahNameEs: "La Resurrección",
            surahNameTransliterated: "Al-Qiyama",
            arabic: "أَيَحْسَبُ الْإِنسَانُ أَن يُتْرَكَ سُدًى",
            translationEs: "¿Acaso cree el ser humano que va a quedar sin propósito?",
            themes: [.valueOfTime, .whatBenefitsYou]
        ),
        QuranCitation(
            surah: 33,
            ayahStart: 41,
            surahNameEs: "Los Coligados",
            surahNameTransliterated: "Al-Ahzab",
            arabic: "يَا أَيُّهَا الَّذِينَ آمَنُوا اذْكُرُوا اللَّهَ ذِكْرًا كَثِيرًا",
            translationEs: "¡Vosotros que creéis! Recordad a Allah con un recuerdo abundante.",
            themes: [.remembrance]
        ),
        QuranCitation(
            surah: 57,
            ayahStart: 16,
            surahNameEs: "El Hierro",
            surahNameTransliterated: "Al-Hadid",
            arabic: "أَلَمْ يَأْنِ لِلَّذِينَ آمَنُوا أَن تَخْشَعَ قُلُوبُهُمْ لِذِكْرِ اللَّهِ",
            translationEs: "¿Es que no ha llegado ya el momento de que los corazones de los creyentes se humillen ante el recuerdo de Allah?",
            partial: true,
            themes: [.remembrance, .valueOfTime]
        ),
        QuranCitation(
            surah: 29,
            ayahStart: 69,
            surahNameEs: "La Araña",
            surahNameTransliterated: "Al-'Ankabut",
            arabic: "وَالَّذِينَ جَاهَدُوا فِينَا لَنَهْدِيَنَّهُمْ سُبُلَنَا",
            translationEs: "Y a quienes se esfuerzan por Nosotros, ciertamente los guiaremos por Nuestros caminos.",
            partial: true,
            themes: [.selfDiscipline, .patience]
        ),
        QuranCitation(
            surah: 61,
            ayahStart: 2,
            ayahEnd: 3,
            surahNameEs: "Las Filas",
            surahNameTransliterated: "As-Saff",
            arabic: "يَا أَيُّهَا الَّذِينَ آمَنُوا لِمَ تَقُولُونَ مَا لَا تَفْعَلُونَ كَبُرَ مَقْتًا عِندَ اللَّهِ أَن تَقُولُوا مَا لَا تَفْعَلُونَ",
            translationEs: "¡Vosotros que creéis! ¿Por qué decís lo que no hacéis? Grande es, ante Allah, el desagrado de que digáis lo que no hacéis.",
            themes: [.keepingCommitments]
        ),
        QuranCitation(
            surah: 23,
            ayahStart: 1,
            ayahEnd: 3,
            surahNameEs: "Los Creyentes",
            surahNameTransliterated: "Al-Mu'minun",
            arabic: "قَدْ أَفْلَحَ الْمُؤْمِنُونَ الَّذِينَ هُمْ فِي صَلَاتِهِمْ خَاشِعُونَ وَالَّذِينَ هُمْ عَنِ اللَّغْوِ مُعْرِضُونَ",
            translationEs: "Han triunfado los creyentes: los que en su oración están con humildad y los que se apartan de lo vano.",
            themes: [.whatBenefitsYou, .selfDiscipline]
        ),
        QuranCitation(
            surah: 94,
            ayahStart: 5,
            ayahEnd: 6,
            surahNameEs: "La Apertura",
            surahNameTransliterated: "Ash-Sharh",
            arabic: "فَإِنَّ مَعَ الْعُسْرِ يُسْرًا إِنَّ مَعَ الْعُسْرِ يُسْرًا",
            translationEs: "Ciertamente, junto a la dificultad hay facilidad. Ciertamente, junto a la dificultad hay facilidad.",
            themes: [.patience]
        ),
        QuranCitation(
            surah: 8,
            ayahStart: 27,
            surahNameEs: "Los Botines",
            surahNameTransliterated: "Al-Anfal",
            arabic: "يَا أَيُّهَا الَّذِينَ آمَنُوا لَا تَخُونُوا اللَّهَ وَالرَّسُولَ وَتَخُونُوا أَمَانَاتِكُمْ وَأَنتُمْ تَعْلَمُونَ",
            translationEs: "¡Vosotros que creéis! No traicionéis a Allah ni al Mensajero, ni traicionéis, a sabiendas, lo que se os ha confiado.",
            themes: [.keepingCommitments]
        ),
        QuranCitation(
            surah: 2,
            ayahStart: 286,
            surahNameEs: "La Vaca",
            surahNameTransliterated: "Al-Baqara",
            arabic: "لَا يُكَلِّفُ اللَّهُ نَفْسًا إِلَّا وُسْعَهَا",
            translationEs: "Allah no impone a nadie sino lo que puede soportar.",
            partial: true,
            themes: [.patience, .whatBenefitsYou]
        ),
        QuranCitation(
            surah: 20,
            ayahStart: 14,
            surahNameEs: "Ta-Ha",
            surahNameTransliterated: "Ta-Ha",
            arabic: "وَأَقِمِ الصَّلَاةَ لِذِكْرِي",
            translationEs: "Y establece la oración para Mi recuerdo.",
            partial: true,
            themes: [.remembrance]
        ),
    ]

    public static func byTheme(_ theme: Theme) -> [QuranCitation] {
        all.filter { $0.themes.contains(theme) }
    }

    /// Deterministic pick, so the same moment does not flicker between verses.
    public static func pick(theme: Theme?, seed: Int) -> QuranCitation {
        let pool = theme.map(byTheme).flatMap { $0.isEmpty ? nil : $0 } ?? all
        return pool[abs(seed) % pool.count]
    }
}
