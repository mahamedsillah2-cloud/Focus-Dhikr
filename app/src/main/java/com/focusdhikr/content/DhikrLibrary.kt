package com.focusdhikr.content

/**
 * The short adhkar the user can switch on and off individually (requirement 4).
 *
 * These are the standard formulas of remembrance, not narrations, so they carry
 * no chain or grading. They are shown as what they are: words to say, with their
 * meaning next to them.
 */
object DhikrLibrary {

    const val SUBHAN_ALLAH = "subhan_allah"
    const val ALHAMDULILLAH = "alhamdulillah"
    const val ALLAHU_AKBAR = "allahu_akbar"
    const val LA_ILAHA_ILLA_ALLAH = "la_ilaha_illa_allah"
    const val ASTAGHFIRULLAH = "astaghfirullah"
    const val LA_HAWLA = "la_hawla"
    const val SUBHAN_ALLAH_WA_BIHAMDIH = "subhan_allah_wa_bihamdih"

    val all: List<Dhikr> = listOf(
        Dhikr(
            id = SUBHAN_ALLAH,
            arabic = "سُبْحَانَ اللَّهِ",
            transliteration = "SubhanAllah",
            meaningEs = "Glorificado sea Allah",
        ),
        Dhikr(
            id = ALHAMDULILLAH,
            arabic = "الْحَمْدُ لِلَّهِ",
            transliteration = "Alhamdulillah",
            meaningEs = "Toda alabanza pertenece a Allah",
        ),
        Dhikr(
            id = ALLAHU_AKBAR,
            arabic = "اللَّهُ أَكْبَرُ",
            transliteration = "Allahu Akbar",
            meaningEs = "Allah es más grande",
        ),
        Dhikr(
            id = LA_ILAHA_ILLA_ALLAH,
            arabic = "لَا إِلَٰهَ إِلَّا اللَّهُ",
            transliteration = "La ilaha illa Allah",
            meaningEs = "No hay más divinidad que Allah",
        ),
        Dhikr(
            id = ASTAGHFIRULLAH,
            arabic = "أَسْتَغْفِرُ اللَّهَ",
            transliteration = "Astaghfirullah",
            meaningEs = "Pido perdón a Allah",
        ),
        Dhikr(
            id = LA_HAWLA,
            arabic = "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ",
            transliteration = "La hawla wa la quwwata illa billah",
            meaningEs = "No hay fuerza ni poder sino en Allah",
        ),
        Dhikr(
            id = SUBHAN_ALLAH_WA_BIHAMDIH,
            arabic = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
            transliteration = "SubhanAllahi wa bihamdih",
            meaningEs = "Glorificado sea Allah y con Su alabanza",
        ),
    )

    /** What is enabled the first time the app runs: the three the user named. */
    val defaultEnabledIds: Set<String> = setOf(SUBHAN_ALLAH, ALHAMDULILLAH, ALLAHU_AKBAR)

    fun byId(id: String): Dhikr? = all.firstOrNull { it.id == id }

    fun enabled(ids: Set<String>): List<Dhikr> =
        all.filter { it.id in ids }.ifEmpty { all.filter { it.id in defaultEnabledIds } }
}
