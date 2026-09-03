package dev.bikram.filepipe.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.PaneExpansionStateKey
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.EntryPointAccessors
import dev.bikram.filepipe.di.SettingsDependenciesEntryPoint
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.common.isSmallLandscape
import dev.bikram.filepipe.ui.feedback.appClickable
import dev.bikram.filepipe.ui.screens.devoptions.DevOptionsScreen
import dev.bikram.filepipe.ui.screens.settings.FilePipeUpdateViewModel
import dev.bikram.filepipe.ui.screens.settings.SettingsScreen
import dev.bikram.filepipe.ui.screens.settings.SettingsSectionKey
import dev.bikram.filepipe.ui.screens.settings.SettingsViewModel
import dev.bikram.filepipe.ui.screens.settings.settingsPaneSections
import dev.bikram.filepipe.ui.screens.settings.settingsSectionKeyForHighlight
import dev.bikram.filepipe.ui.theme.reducedMotionAwareSpec
import kotlinx.coroutines.launch

@Composable
fun SettingsSectionListPane(
    contentPadding: PaddingValues,
    selectedSectionKey: SettingsSectionKey,
    developerOptionsEnabled: Boolean,
    onSectionSelected: (SettingsSectionKey) -> Unit,
    modifier: Modifier = Modifier,
    showSelectedState: Boolean = true,
    startPadding: Dp = 16.dp,
    endPadding: Dp = 16.dp,
    extraTopPadding: Dp = 16.dp,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = startPadding,
                end = endPadding,
                top = contentPadding.calculateTopPadding() + extraTopPadding,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = settingsPaneSections,
            key = { sectionKey -> sectionKey.routeKey },
        ) { sectionKey ->
            SettingsSectionListRow(
                iconName = sectionKey.iconName,
                titleRes = sectionKey.titleRes,
                selected = showSelectedState && sectionKey == selectedSectionKey,
                onClick = { onSectionSelected(sectionKey) },
            )
        }
        if (developerOptionsEnabled) {
            item(key = "developer_options") {
                SettingsSectionListRow(
                    iconName = SettingsSectionKey.DeveloperOptions.iconName,
                    titleRes = SettingsSectionKey.DeveloperOptions.titleRes,
                    selected = showSelectedState && selectedSectionKey == SettingsSectionKey.DeveloperOptions,
                    onClick = { onSectionSelected(SettingsSectionKey.DeveloperOptions) },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun SettingsSectionListRow(
    iconName: String,
    @StringRes titleRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    trailingIconName: String = "chevron_right",
) {
    val title = stringResource(titleRes)
    val colorSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<androidx.compose.ui.graphics.Color>())
    val containerColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        animationSpec = colorSpec,
        label = "settings_section_list_container",
    )
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .appClickable(
                    onClick = onClick,
                    indication = null,
                ),
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(MaterialTheme.shapes.extraExtraLarge)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                FilePipeMaterialRoundedSymbol(
                    name = iconName,
                    contentDescription = null,
                    size = 21.dp,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            FilePipeMaterialRoundedSymbol(
                name = trailingIconName,
                contentDescription = null,
                size = 20.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                autoMirror = trailingIconName == "chevron_right",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsTwoPaneRoute(
    contentPadding: PaddingValues,
    onOpenIntro: () -> Unit,
    onOpenFaqStorageSection: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenDevOptions: () -> Unit,
    paneFabContent: (@Composable () -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
    updateVm: FilePipeUpdateViewModel = hiltViewModel(),
    onUpdateCheckStarted: () -> Unit = {},
    highlightSectionKey: String? = null,
    onHighlightHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val settingsDependencies =
        remember(context) {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                SettingsDependenciesEntryPoint::class.java,
            )
        }
    val developerOptionsEnabled by
        settingsDependencies
            .userPreferencesRepository()
            .developerOptionsEnabledFlow
            .collectAsStateWithLifecycle(initialValue = false)
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val paneContentPadding =
        PaddingValues(
            bottom = contentPadding.calculateBottomPadding(),
        )
    val detailPaneContentPadding =
        PaddingValues(
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
        )
    val scope = rememberCoroutineScope()
    var selectedSectionKey by rememberSaveable { mutableStateOf(SettingsSectionKey.Appearance) }
    val showDetailNavigateBack = navigator.scaffoldDirective.maxHorizontalPartitions <= 1
    val showPaneSelectionState = !showDetailNavigateBack

    fun showSettingsSection(sectionKey: SettingsSectionKey) {
        selectedSectionKey = sectionKey
        scope.launch {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, sectionKey.routeKey)
        }
    }

    LaunchedEffect(highlightSectionKey, showPaneSelectionState) {
        if (!showPaneSelectionState) return@LaunchedEffect
        val highlightedSectionKey = settingsSectionKeyForHighlight(highlightSectionKey) ?: return@LaunchedEffect
        showSettingsSection(highlightedSectionKey)
    }

    LaunchedEffect(developerOptionsEnabled, selectedSectionKey) {
        if (!developerOptionsEnabled && selectedSectionKey == SettingsSectionKey.DeveloperOptions) {
            showSettingsSection(SettingsSectionKey.About)
        }
    }

    if (!showPaneSelectionState) {
        Box(Modifier.fillMaxSize()) {
            SettingsScreen(
                contentPadding = contentPadding,
                onOpenIntro = onOpenIntro,
                onOpenFaqStorageSection = onOpenFaqStorageSection,
                onOpenHelp = onOpenHelp,
                onOpenDevOptions = onOpenDevOptions,
                viewModel = viewModel,
                updateVm = updateVm,
                onUpdateCheckStarted = onUpdateCheckStarted,
                highlightSectionKey = highlightSectionKey,
                onHighlightHandled = onHighlightHandled,
            )
            if (paneFabContent != null) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(end = 24.dp, bottom = compactLandscapeFabBottomPadding()),
                ) {
                    paneFabContent()
                }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        val balancedPaneExpansionState =
            rememberFlatScreenBalancedPaneExpansionState(
                directive = navigator.scaffoldDirective,
            )
        ListDetailPaneScaffold(
            directive = navigator.scaffoldDirective,
            scaffoldState = navigator.scaffoldState,
            listPane = {
                AnimatedPane {
                    TwoPaneListPaneWithFab(fabContent = paneFabContent) {
                        SettingsSectionListPane(
                            contentPadding = paneContentPadding,
                            selectedSectionKey = selectedSectionKey,
                            developerOptionsEnabled = developerOptionsEnabled,
                            onSectionSelected = ::showSettingsSection,
                            showSelectedState = showPaneSelectionState,
                            extraTopPadding = 52.dp,
                        )
                    }
                }
            },
            detailPane = {
                AnimatedPane {
                    if (selectedSectionKey == SettingsSectionKey.DeveloperOptions) {
                        DevOptionsScreen(
                            contentPadding = detailPaneContentPadding,
                            onNavigateBack = { showSettingsSection(SettingsSectionKey.About) },
                            updateVm = updateVm,
                            showNavigateBack = showDetailNavigateBack,
                            suppressBlur = true,
                        )
                    } else {
                        SettingsScreen(
                            contentPadding = detailPaneContentPadding,
                            onOpenIntro = onOpenIntro,
                            onOpenFaqStorageSection = onOpenFaqStorageSection,
                            onOpenHelp = onOpenHelp,
                            onOpenDevOptions = { showSettingsSection(SettingsSectionKey.DeveloperOptions) },
                            viewModel = viewModel,
                            updateVm = updateVm,
                            onUpdateCheckStarted = onUpdateCheckStarted,
                            highlightSectionKey = highlightSectionKey,
                            onHighlightHandled = onHighlightHandled,
                            selectedSectionKey = selectedSectionKey,
                            showTopBar = false,
                            showSectionHeaders = false,
                            suppressBlur = true,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            paneExpansionState = balancedPaneExpansionState,
        )
    }
}

@Composable
internal fun TwoPaneListPaneWithFab(
    fabContent: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val centeredContentModifier =
            if (maxWidth > 720.dp) {
                Modifier
                    .width(720.dp)
                    .fillMaxHeight()
            } else {
                Modifier.fillMaxSize()
            }
        Box(centeredContentModifier) {
            content()
            if (fabContent != null) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(end = 24.dp, bottom = compactLandscapeFabBottomPadding()),
                ) {
                    fabContent()
                }
            }
        }
    }
}

@Composable
internal fun compactLandscapeFabBottomPadding(): Dp =
    if (isSmallLandscape()) {
        12.dp
    } else {
        24.dp
    }

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun rememberFlatScreenBalancedPaneExpansionState(
    directive: PaneScaffoldDirective,
): PaneExpansionState {
    val targetFirstPaneProportion: Float? =
        if (directive.excludedBounds.isEmpty() && directive.maxHorizontalPartitions > 1) {
            0.4f
        } else {
            null
        }
    val paneExpansionAnchors =
        remember(targetFirstPaneProportion) {
            if (targetFirstPaneProportion == null) {
                emptyList()
            } else {
                listOf(PaneExpansionAnchor.Proportion(targetFirstPaneProportion))
            }
        }
    val paneExpansionState =
        rememberPaneExpansionState(
            key = PaneExpansionStateKey.Default,
            anchors = paneExpansionAnchors,
        )
    LaunchedEffect(paneExpansionState, targetFirstPaneProportion) {
        if (targetFirstPaneProportion == null) {
            paneExpansionState.clear()
        } else {
            paneExpansionState.setFirstPaneProportion(targetFirstPaneProportion)
        }
    }
    return paneExpansionState
}
