package dev.bikram.filepipe.ui.screens.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Single shared max width so onboarding content and actions line up across the whole flow. */
internal val OnboardingMaxContentWidth = 600.dp

/**
 * Wraps the primary action(s) of an onboarding screen so the button width is identical on the
 * title, permissions and rule-wizard screens regardless of how each screen is laid out: capped at
 * [OnboardingMaxContentWidth], centered, with a consistent horizontal inset. Pass screen-specific
 * placement (e.g. bottom alignment / bottom padding) via [modifier].
 */
@Composable
internal fun OnboardingBottomActions(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier =
                Modifier
                    .widthIn(max = OnboardingMaxContentWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
        ) {
            content()
        }
    }
}
