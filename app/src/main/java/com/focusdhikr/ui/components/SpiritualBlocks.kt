package com.focusdhikr.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.focusdhikr.content.Dhikr
import com.focusdhikr.content.HadithCitation
import com.focusdhikr.content.QuranCitation
import com.focusdhikr.ui.Copy
import com.focusdhikr.ui.theme.Spacing

/**
 * A single dhikr, shown large and alone.
 *
 * Arabic is set at a size where its diacritics are actually legible - the usual
 * mistake is to shrink it to body size, where it becomes decoration.
 */
@Composable
fun DhikrBlock(
    dhikr: Dhikr,
    showMeaning: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = dhikr.arabic,
            fontSize = 30.sp,
            lineHeight = 50.sp,
            fontFamily = FontFamily.Serif,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = dhikr.transliteration,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        if (showMeaning) {
            Text(
                text = dhikr.meaningEs,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

/**
 * A Qur'anic citation with its reference always visible.
 *
 * The reference is not fine print here. Requirement 5 asks that nothing appear
 * without surah and ayah, so the reference renders at the same moment as the
 * text, never behind a tap.
 */
@Composable
fun QuranBlock(
    citation: QuranCitation,
    showTranslation: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = citation.arabic,
            fontSize = 22.sp,
            lineHeight = 42.sp,
            fontFamily = FontFamily.Serif,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (showTranslation) {
            Text(
                text = "«${citation.translationEs}»",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.md),
            )
            Text(
                text = Copy.GATE_TRANSLATION_LABEL,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        Text(
            text = citation.fullReference,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

/** A hadith, with collection, number and grading always shown together. */
@Composable
fun HadithBlock(
    citation: HadithCitation,
    showTranslation: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = citation.arabic,
            fontSize = 20.sp,
            lineHeight = 38.sp,
            fontFamily = FontFamily.Serif,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (showTranslation) {
            Text(
                text = "«${citation.translationEs}»",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }
        Text(
            text = "${citation.collection} ${citation.reference} · ${citation.narrator}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Text(
            text = citation.grading,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
    }
}
