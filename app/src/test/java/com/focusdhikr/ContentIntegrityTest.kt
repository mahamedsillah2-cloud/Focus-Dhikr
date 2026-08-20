package com.focusdhikr

import com.focusdhikr.content.DhikrLibrary
import com.focusdhikr.content.HadithLibrary
import com.focusdhikr.content.QuranLibrary
import com.focusdhikr.content.Reflections
import com.focusdhikr.content.Theme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural guarantees for the citation libraries.
 *
 * These do not verify that a citation is *correct* - only a primary source can
 * do that, which is what tools/verify_citations.py does in CI. What they do
 * guarantee is that no entry can ever reach a screen without its reference,
 * and that no hadith can appear without a grading.
 */
class ContentIntegrityTest {

    @Test
    fun `every quran citation carries a full reference`() {
        QuranLibrary.all.forEach { c ->
            assertTrue("surah out of range: ${c.surah}", c.surah in 1..114)
            assertTrue("ayah must be positive", c.ayahStart >= 1)
            assertTrue("ayah range must be ordered", c.ayahEnd >= c.ayahStart)
            assertFalse("arabic missing for ${c.reference}", c.arabic.isBlank())
            assertFalse("translation missing for ${c.reference}", c.translationEs.isBlank())
            assertFalse("surah name missing for ${c.reference}", c.surahNameTransliterated.isBlank())
            assertTrue("no theme for ${c.reference}", c.themes.isNotEmpty())
        }
    }

    @Test
    fun `arabic text is actually arabic`() {
        val arabicRange = '؀'..'ۿ'
        (QuranLibrary.all.map { it.arabic } + HadithLibrary.all.map { it.arabic })
            .forEach { text ->
                assertTrue(
                    "not arabic script: $text",
                    text.any { it in arabicRange },
                )
            }
    }

    @Test
    fun `every hadith states its grading and where it came from`() {
        HadithLibrary.all.forEach { h ->
            assertFalse("collection missing", h.collection.isBlank())
            assertFalse("reference missing for ${h.collection}", h.reference.isBlank())
            assertFalse("grading missing for ${h.collection} ${h.reference}", h.grading.isBlank())
            assertFalse("grading authority missing", h.gradingAuthority.isBlank())
            assertFalse("narrator missing", h.narrator.isBlank())
            assertTrue("source url missing", h.sourceUrl.startsWith("https://"))
        }
    }

    @Test
    fun `no duplicate citations`() {
        val quranKeys = QuranLibrary.all.map { "${it.surah}:${it.ayahStart}-${it.ayahEnd}" }
        assertTrue("duplicate quran citation", quranKeys.size == quranKeys.toSet().size)

        val hadithKeys = HadithLibrary.all.map { "${it.collection}:${it.reference}" }
        assertTrue("duplicate hadith citation", hadithKeys.size == hadithKeys.toSet().size)
    }

    @Test
    fun `picking is deterministic and total across every theme`() {
        Theme.entries.forEach { theme ->
            val first = QuranLibrary.pick(theme, 7)
            assertTrue("pick must be stable", first == QuranLibrary.pick(theme, 7))
        }
        // Negative and huge seeds must not throw.
        QuranLibrary.pick(null, Int.MIN_VALUE)
        QuranLibrary.pick(null, Int.MAX_VALUE)
        HadithLibrary.pick(null, -1)
    }

    @Test
    fun `dhikr defaults are the three the user named`() {
        assertTrue(DhikrLibrary.SUBHAN_ALLAH in DhikrLibrary.defaultEnabledIds)
        assertTrue(DhikrLibrary.ALHAMDULILLAH in DhikrLibrary.defaultEnabledIds)
        assertTrue(DhikrLibrary.ALLAHU_AKBAR in DhikrLibrary.defaultEnabledIds)
    }

    @Test
    fun `an empty dhikr selection falls back rather than showing nothing`() {
        assertTrue(DhikrLibrary.enabled(emptySet()).isNotEmpty())
        assertTrue(DhikrLibrary.enabled(setOf("nonexistent")).isNotEmpty())
    }

    @Test
    fun `reflection pools are non-empty and picking never throws`() {
        listOf(
            Reflections.pause,
            Reflections.wait,
            Reflections.purpose,
            Reflections.decide,
            Reflections.turnedBack,
            Reflections.ambient,
        ).forEach { pool ->
            assertTrue(pool.isNotEmpty())
            Reflections.pick(pool, Int.MIN_VALUE)
            Reflections.pick(pool, -13)
        }
    }

    /**
     * Requirement 15: the app must not read as punishment. This catches the
     * words that would break that promise if someone edited the copy later.
     */
    @Test
    fun `reflection copy contains no shaming language`() {
        val banned = listOf(
            "adicto", "adicción", "débil", "fracaso", "fracasado",
            "vergüenza", "patético", "culpa tuya", "otra vez has",
        )
        val all = Reflections.pause + Reflections.wait + Reflections.purpose +
            Reflections.decide + Reflections.turnedBack + Reflections.ambient

        all.forEach { line ->
            banned.forEach { word ->
                assertFalse(
                    "shaming language in: \"$line\"",
                    line.lowercase().contains(word),
                )
            }
        }
    }
}
