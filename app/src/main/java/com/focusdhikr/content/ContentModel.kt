package com.focusdhikr.content

/**
 * Themes the user asked for in requirement 5. Used to pick something relevant to
 * the moment instead of a random verse.
 */
enum class Theme {
    KEEPING_COMMITMENTS,
    SELF_DISCIPLINE,
    REMEMBRANCE,
    VALUE_OF_TIME,
    PATIENCE,
    RESTRAINING_DESIRE,
    WHAT_BENEFITS_YOU,
}

/**
 * A Qur'anic citation.
 *
 * Every field is mandatory on purpose. Nothing goes into this app without surah,
 * ayah, Arabic and a translation of the meaning.
 *
 * @param partial true when [arabic] is an excerpt of a longer ayah rather than
 *   the whole of it. The CI validator checks whole ayat for equality and
 *   excerpts for containment. See tools/verify_citations.py.
 */
data class QuranCitation(
    val surah: Int,
    val ayahStart: Int,
    val ayahEnd: Int = ayahStart,
    val surahNameEs: String,
    val surahNameTransliterated: String,
    val arabic: String,
    /** Translation of the meaning into Spanish. Not a substitute for the Arabic. */
    val translationEs: String,
    val partial: Boolean = false,
    val themes: Set<Theme>,
) {
    val reference: String
        get() = if (ayahStart == ayahEnd) {
            "Corán $surah:$ayahStart"
        } else {
            "Corán $surah:$ayahStart-$ayahEnd"
        }

    val fullReference: String
        get() = "$reference — Sura $surahNameTransliterated ($surahNameEs)" +
            if (partial) " · fragmento" else ""
}

/**
 * A hadith citation.
 *
 * [grading] is never omitted and never guessed. If the authenticity of a report
 * could not be stated plainly, the report is not in this file.
 */
data class HadithCitation(
    val collection: String,
    /** Canonical numbering, e.g. "6412". */
    val reference: String,
    val narrator: String,
    val arabic: String,
    val translationEs: String,
    /** e.g. "Sahih (auténtico)", "Hasan (bueno)". */
    val grading: String,
    val gradingAuthority: String,
    val sourceUrl: String,
    val partial: Boolean = false,
    val themes: Set<Theme>,
) {
    val fullReference: String get() = "$collection $reference · $grading ($gradingAuthority)"
}

/** A short remembrance the user can switch on or off individually. */
data class Dhikr(
    val id: String,
    val arabic: String,
    val transliteration: String,
    val meaningEs: String,
)
