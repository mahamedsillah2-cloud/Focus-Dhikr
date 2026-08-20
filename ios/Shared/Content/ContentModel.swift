import Foundation

/// Themes the user asked for. Used to pick something relevant to the moment
/// instead of a random verse.
public enum Theme: String, CaseIterable, Sendable {
    case keepingCommitments
    case selfDiscipline
    case remembrance
    case valueOfTime
    case patience
    case restrainingDesire
    case whatBenefitsYou
}

/// A Qur'anic citation.
///
/// Every field is mandatory on purpose. Nothing goes into this app without
/// surah, ayah, Arabic and a translation of the meaning.
///
/// - Parameter partial: true when `arabic` is an excerpt of a longer ayah. The
///   CI validator checks whole ayat for equality and excerpts for containment.
///   See `tools/verify_citations.py`.
public struct QuranCitation: Identifiable, Sendable {
    public let surah: Int
    public let ayahStart: Int
    public let ayahEnd: Int
    public let surahNameEs: String
    public let surahNameTransliterated: String
    public let arabic: String
    /// Translation of the meaning into Spanish. Not a substitute for the Arabic.
    public let translationEs: String
    public let partial: Bool
    public let themes: Set<Theme>

    public var id: String { "\(surah):\(ayahStart)-\(ayahEnd)" }

    public var reference: String {
        ayahStart == ayahEnd ? "Corán \(surah):\(ayahStart)" : "Corán \(surah):\(ayahStart)-\(ayahEnd)"
    }

    public var fullReference: String {
        "\(reference) — Sura \(surahNameTransliterated) (\(surahNameEs))" + (partial ? " · fragmento" : "")
    }

    public init(
        surah: Int,
        ayahStart: Int,
        ayahEnd: Int? = nil,
        surahNameEs: String,
        surahNameTransliterated: String,
        arabic: String,
        translationEs: String,
        partial: Bool = false,
        themes: Set<Theme>
    ) {
        self.surah = surah
        self.ayahStart = ayahStart
        self.ayahEnd = ayahEnd ?? ayahStart
        self.surahNameEs = surahNameEs
        self.surahNameTransliterated = surahNameTransliterated
        self.arabic = arabic
        self.translationEs = translationEs
        self.partial = partial
        self.themes = themes
    }
}

/// A hadith citation.
///
/// `grading` is never omitted and never guessed. If the authenticity of a
/// report could not be stated plainly, the report is not in this file.
public struct HadithCitation: Identifiable, Sendable {
    public let collection: String
    public let reference: String
    public let narrator: String
    public let arabic: String
    public let translationEs: String
    public let grading: String
    public let gradingAuthority: String
    public let sourceUrl: String
    public let partial: Bool
    public let themes: Set<Theme>

    public var id: String { "\(collection):\(reference)" }

    public var fullReference: String { "\(collection) \(reference) · \(grading) (\(gradingAuthority))" }

    public init(
        collection: String,
        reference: String,
        narrator: String,
        arabic: String,
        translationEs: String,
        grading: String,
        gradingAuthority: String,
        sourceUrl: String,
        partial: Bool = false,
        themes: Set<Theme>
    ) {
        self.collection = collection
        self.reference = reference
        self.narrator = narrator
        self.arabic = arabic
        self.translationEs = translationEs
        self.grading = grading
        self.gradingAuthority = gradingAuthority
        self.sourceUrl = sourceUrl
        self.partial = partial
        self.themes = themes
    }
}

/// A short remembrance the user can switch on or off individually.
public struct Dhikr: Identifiable, Sendable {
    public let id: String
    public let arabic: String
    public let transliteration: String
    public let meaningEs: String

    public init(id: String, arabic: String, transliteration: String, meaningEs: String) {
        self.id = id
        self.arabic = arabic
        self.transliteration = transliteration
        self.meaningEs = meaningEs
    }
}
