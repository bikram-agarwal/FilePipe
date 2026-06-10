package dev.bikram.filepipe.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bikram.filepipe.R
import dev.bikram.filepipe.domain.model.RuleTemplate
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.components.FilePipeElevatedCard
import dev.bikram.filepipe.ui.components.FilePipeOutlinedButton
import dev.bikram.filepipe.ui.components.RuleIconOrEmoji
import dev.bikram.filepipe.ui.theme.pillShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingRuleWizardScreen(
    onBackToPermissions: () -> Unit,
    onUseTemplate: (templateIndex: Int) -> Unit,
    onStartBlank: () -> Unit,
    onSkip: () -> Unit,
) {
    val header: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.onboarding_wizard_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_wizard_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                textAlign = TextAlign.Center,
            )
        }
    }

    val actionButtons: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val onboardingCtaPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
            FilePipeOutlinedButton(
                onClick = onBackToPermissions,
                modifier = Modifier.weight(1f),
                shape = pillShape,
                contentPadding = onboardingCtaPadding,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilePipeMaterialRoundedSymbol(
                        name = "arrow_back",
                        contentDescription = null,
                        size = 24.dp,
                        autoMirror = true,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.onboarding_wizard_back_to_permissions),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.size(24.dp))
                }
            }
            FilePipeOutlinedButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f),
                shape = pillShape,
                contentPadding = onboardingCtaPadding,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.size(24.dp))
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.onboarding_wizard_skip),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    FilePipeMaterialRoundedSymbol(
                        name = "arrow_forward",
                        contentDescription = null,
                        size = 24.dp,
                        autoMirror = true,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
    ) {
        val templateColumns = if (maxWidth >= 600.dp) 2 else 1
        if (maxHeight < 560.dp) {
            // Short screens (e.g. landscape): scroll the header, templates and actions together.
            // A pinned header/footer would overlap the grid, and the translucent action buttons
            // would let template cards show through them.
            LazyVerticalGrid(
                columns = GridCells.Fixed(templateColumns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        header()
                        Spacer(Modifier.height(16.dp))
                    }
                }
                itemsIndexed(RuleTemplate.ALL) { index, template ->
                    TemplateCard(
                        template = template,
                        onUseTemplate = { onUseTemplate(index) },
                    )
                }
                item {
                    StartBlankCard(onStartBlank = onStartBlank)
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OnboardingBottomActions(modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                        actionButtons()
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                Spacer(Modifier.height(28.dp))
                header()
                Spacer(Modifier.height(28.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(templateColumns),
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(RuleTemplate.ALL) { index, template ->
                        TemplateCard(
                            template = template,
                            onUseTemplate = { onUseTemplate(index) },
                        )
                    }
                    item {
                        StartBlankCard(onStartBlank = onStartBlank)
                    }
                }
            }
            OnboardingBottomActions(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp),
            ) {
                actionButtons()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateCard(
    template: RuleTemplate,
    onUseTemplate: () -> Unit,
) {
    FilePipeElevatedCard(
        onClick = onUseTemplate,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            RuleIconOrEmoji(
                iconEmoji = null,
                icon = template.suggestedIcon,
                vectorSize = 28.dp,
                emojiFontSize = 24.sp,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = template.extensions.map { it.removePrefix(".") }.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            FilePipeMaterialRoundedSymbol(
                name = "chevron_right",
                contentDescription = null,
                size = 24.dp,
                autoMirror = true,
                modifier =
                    Modifier
                        .padding(top = 2.dp)
                        .size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartBlankCard(onStartBlank: () -> Unit) {
    FilePipeElevatedCard(
        onClick = onStartBlank,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            FilePipeMaterialRoundedSymbol(
                name = "edit",
                contentDescription = null,
                size = 28.dp,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.onboarding_wizard_start_blank),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.onboarding_wizard_start_blank_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            FilePipeMaterialRoundedSymbol(
                name = "chevron_right",
                contentDescription = null,
                size = 24.dp,
                autoMirror = true,
                modifier =
                    Modifier
                        .padding(top = 2.dp)
                        .size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
