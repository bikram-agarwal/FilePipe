package dev.bikram.filepipe.ui.screens.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.bikram.filepipe.R

/**
 * Full article (same copy as the former Settings "Learn more" sheet). When [useNestedVerticalScroll]
 * is false, omit the inner scroll so the parent (e.g. FAQ [androidx.compose.foundation.lazy.LazyColumn])
 * can scroll the content.
 */
@Composable
fun FolderAccessLearnMoreArticle(
    modifier: Modifier = Modifier,
    scheme: ColorScheme = MaterialTheme.colorScheme,
    useNestedVerticalScroll: Boolean = true
) {
    val scrollModifier = if (useNestedVerticalScroll) {
        Modifier.verticalScroll(rememberScrollState())
    } else {
        Modifier
    }
    Column(modifier = modifier.then(scrollModifier)) {
        FolderAccessLearnMoreArticleBody(scheme = scheme)
    }
}

@Composable
fun PermissionsLearnMoreSheetContent(scheme: ColorScheme) {
    FolderAccessLearnMoreArticle(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        scheme = scheme,
        useNestedVerticalScroll = true
    )
}

/** "All files access" block from the learn-more article (for FAQ expandable card). */
@Composable
fun FolderAccessLearnMoreFullModeSection(
    scheme: ColorScheme = MaterialTheme.colorScheme,
    modifier: Modifier = Modifier,
    /** When false, omits the large mode title (card header already shows it). */
    showModeTitleInBody: Boolean = true
) {
    Column(modifier = modifier) {
        FolderAccessLearnMoreFullModeBody(
            scheme = scheme,
            showModeTitle = showModeTitleInBody
        )
    }
}

/** "Selective access" block from the learn-more article (for FAQ expandable card). */
@Composable
fun FolderAccessLearnMoreSelectiveModeSection(
    scheme: ColorScheme = MaterialTheme.colorScheme,
    modifier: Modifier = Modifier,
    showModeTitleInBody: Boolean = true
) {
    Column(modifier = modifier) {
        FolderAccessLearnMoreSelectiveModeBody(
            scheme = scheme,
            showModeTitle = showModeTitleInBody
        )
    }
}

@Composable
private fun ColumnScope.FolderAccessLearnMoreArticleBody(scheme: ColorScheme) {
    val titleLargeStyle = MaterialTheme.typography.titleLarge
    val onSurface = scheme.onSurface
    Text(
        text = stringResource(R.string.onboarding_permissions_sheet_title),
        style = titleLargeStyle,
        fontWeight = FontWeight.Bold,
        color = onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
    )
    FolderAccessLearnMoreFullModeBody(scheme = scheme)
    Spacer(Modifier.height(28.dp))
    FolderAccessLearnMoreSelectiveModeBody(scheme = scheme)
    Spacer(Modifier.height(28.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = scheme.tertiaryContainer.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_permissions_sheet_footer_tip),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun ColumnScope.FolderAccessLearnMoreFullModeBody(
    scheme: ColorScheme,
    showModeTitle: Boolean = true
) {
    val titleLargeStyle = MaterialTheme.typography.titleLarge
    val onSurface = scheme.onSurface
    if (showModeTitle) {
        Text(
            text = stringResource(R.string.onboarding_permissions_sheet_full_mode),
            style = titleLargeStyle,
            fontWeight = FontWeight.Bold,
            color = scheme.primary
        )
        Spacer(Modifier.height(6.dp))
    }
    Text(
        text = stringResource(R.string.onboarding_permissions_sheet_full_subtitle),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = onSurface
    )
    Spacer(Modifier.height(14.dp))
    Text(
        text = stringResource(R.string.onboarding_permissions_sheet_full_use_intro),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = onSurface
    )
    LearnMoreBulletLine(stringResource(R.string.onboarding_permissions_sheet_full_bullet1), onSurface)
    LearnMoreBulletLine(stringResource(R.string.onboarding_permissions_sheet_full_bullet2), onSurface)
    LearnMoreBulletLine(stringResource(R.string.onboarding_permissions_sheet_full_bullet3), onSurface)
    Spacer(Modifier.height(14.dp))
    Text(
        text = stringResource(R.string.onboarding_permissions_sheet_full_good_to_know),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = onSurface
    )
    LearnMoreBulletLine(stringResource(R.string.onboarding_permissions_sheet_full_note1), onSurface)
    LearnMoreBulletLine(stringResource(R.string.onboarding_permissions_sheet_full_note2), onSurface)
}

@Composable
private fun ColumnScope.FolderAccessLearnMoreSelectiveModeBody(
    scheme: ColorScheme,
    showModeTitle: Boolean = true
) {
    val titleLargeStyle = MaterialTheme.typography.titleLarge
    val onSurface = scheme.onSurface
    if (showModeTitle) {
        Text(
            text = stringResource(R.string.onboarding_permissions_sheet_select_mode),
            style = titleLargeStyle,
            fontWeight = FontWeight.Bold,
            color = scheme.primary
        )
        Spacer(Modifier.height(6.dp))
    }
    Text(
        text = stringResource(R.string.onboarding_permissions_sheet_select_subtitle),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = onSurface
    )
    Spacer(Modifier.height(14.dp))
    Text(
        text = stringResource(R.string.onboarding_permissions_sheet_select_use_intro),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = onSurface
    )
    LearnMoreBulletLine(stringResource(R.string.onboarding_permissions_sheet_select_bullet1), onSurface)
    LearnMoreBulletLine(stringResource(R.string.onboarding_permissions_sheet_select_bullet2), onSurface)
    Spacer(Modifier.height(14.dp))
    Text(
        text = stringResource(R.string.onboarding_permissions_sheet_select_limitations),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = onSurface
    )
    LearnMoreBulletLine(stringResource(R.string.onboarding_permissions_sheet_select_lim1), onSurface)
    LearnMoreBulletLine(stringResource(R.string.onboarding_permissions_sheet_select_lim2), onSurface)
}

@Composable
private fun LearnMoreBulletLine(text: String, color: Color) {
    Text(
        text = "• $text",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Normal,
        color = color,
        modifier = Modifier.padding(top = 6.dp)
    )
}
