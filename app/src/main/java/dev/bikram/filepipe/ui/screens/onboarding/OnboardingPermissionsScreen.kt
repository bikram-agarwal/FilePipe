package dev.bikram.filepipe.ui.screens.onboarding

import android.content.Intent
import android.os.Environment
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.preferences.FolderAccessMode
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.components.FilePipeButton
import dev.bikram.filepipe.ui.components.FilePipeOutlinedButton
import dev.bikram.filepipe.ui.components.FilePipeToggleButton
import dev.bikram.filepipe.ui.theme.pillShape
import dev.bikram.filepipe.ui.theme.reducedMotionAwareSpec
import dev.bikram.filepipe.ui.theme.reducedMotionEnterTransition
import dev.bikram.filepipe.ui.theme.reducedMotionExitTransition
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PermissionCardLeadingIconSlotWidth = 44.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingPermissionsScreen(
    onContinue: () -> Unit,
    onOpenStorageAccessFaq: () -> Unit,
    viewModel: OnboardingPermissionsViewModel = hiltViewModel(),
) {
    val selected by viewModel.onboardingFolderAccessSelectionState.collectAsStateWithLifecycle()
    var grantPanelVisible by remember { mutableStateOf(false) }
    var showOpenSettingsInPanel by remember { mutableStateOf(false) }
    var awaitingSettingsReturn by remember { mutableStateOf(false) }
    var showAccessNotGrantedHint by remember { mutableStateOf(false) }
    var hasEnteredAllFilesGrantFlow by remember { mutableStateOf(false) }
    var didAutoAdvanceFromGrant by remember { mutableStateOf(false) }

    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val visibilityFadeInSpec =
        reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val visibilityFadeOutSpec =
        reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())

    val currentSelected by rememberUpdatedState(selected)
    val currentHasEnteredAllFilesGrantFlow by rememberUpdatedState(hasEnteredAllFilesGrantFlow)
    val currentAwaitingSettingsReturn by rememberUpdatedState(awaitingSettingsReturn)
    val currentDidAutoAdvanceFromGrant by rememberUpdatedState(didAutoAdvanceFromGrant)
    val currentOnContinue by rememberUpdatedState(onContinue)

    val needsAllFilesGrant =
        selected == FolderAccessMode.ALL_FILES_PREFERRED &&
            !Environment.isExternalStorageManager()
    val hideBottomPrimaryButton = grantPanelVisible && needsAllFilesGrant

    fun resetGrantFlowUi() {
        grantPanelVisible = false
        showOpenSettingsInPanel = false
        awaitingSettingsReturn = false
        showAccessNotGrantedHint = false
        hasEnteredAllFilesGrantFlow = false
    }

    LaunchedEffect(grantPanelVisible, selected) {
        if (!grantPanelVisible ||
            selected != FolderAccessMode.ALL_FILES_PREFERRED ||
            Environment.isExternalStorageManager()
        ) {
            return@LaunchedEffect
        }
        showOpenSettingsInPanel = false
        delay(2500)
        if (grantPanelVisible &&
            selected == FolderAccessMode.ALL_FILES_PREFERRED &&
            !Environment.isExternalStorageManager()
        ) {
            showOpenSettingsInPanel = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
                if (currentDidAutoAdvanceFromGrant) return@LifecycleEventObserver
                val allFilesSelected = currentSelected == FolderAccessMode.ALL_FILES_PREFERRED
                val manager = Environment.isExternalStorageManager()
                if (allFilesSelected && manager && currentHasEnteredAllFilesGrantFlow) {
                    grantPanelVisible = false
                    showOpenSettingsInPanel = false
                    awaitingSettingsReturn = false
                    showAccessNotGrantedHint = false
                    didAutoAdvanceFromGrant = true
                    viewModel.setFolderAccessMode(FolderAccessMode.ALL_FILES_PREFERRED)
                    currentOnContinue()
                    return@LifecycleEventObserver
                }
                if (allFilesSelected && !manager && currentAwaitingSettingsReturn) {
                    coroutineScope.launch {
                        delay(1000)
                        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            showAccessNotGrantedHint = true
                            grantPanelVisible = true
                            showOpenSettingsInPanel = true
                        }
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
    ) {
        val baseDensity = LocalDensity.current
        val baselineHeight = 980.dp
        val responsiveScale =
            minOf(
                1f,
                maxWidth / 390.dp,
                maxHeight / baselineHeight,
            ).coerceAtLeast(0.76f)
        val responsiveDensity =
            remember(baseDensity, responsiveScale) {
                Density(
                    density = baseDensity.density * responsiveScale,
                    fontScale = baseDensity.fontScale,
                )
            }

        CompositionLocalProvider(LocalDensity provides responsiveDensity) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp)
                            .padding(top = 12.dp, bottom = 8.dp),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                    ) {
                        PermissionsHeroIllustration(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(232.dp),
                        )
                        Spacer(Modifier.height(52.dp))
                        AccessModeSwitcher(
                            selected = selected,
                            onSelectAllFiles = {
                                viewModel.setOnboardingFolderAccessSelection(FolderAccessMode.ALL_FILES_PREFERRED)
                                resetGrantFlowUi()
                            },
                            onSelectSelective = {
                                viewModel.setOnboardingFolderAccessSelection(FolderAccessMode.SAF_ONLY)
                                resetGrantFlowUi()
                            },
                        )
                        Spacer(Modifier.height(32.dp))
                        if (selected == FolderAccessMode.SAF_ONLY) {
                            SelectiveAccessPitch(onLearnMore = onOpenStorageAccessFaq)
                        } else {
                            AllFilesAccessPitch(onLearnMore = onOpenStorageAccessFaq)
                        }
                        AnimatedVisibility(
                            visible = grantPanelVisible,
                            enter = reducedMotionEnterTransition(fadeIn(animationSpec = visibilityFadeInSpec)),
                            exit = reducedMotionExitTransition(fadeOut(animationSpec = visibilityFadeOutSpec)),
                        ) {
                            Column(modifier = Modifier.padding(top = 32.dp)) {
                                AllFilesInstructionPanel(
                                    showOpenSettingsButton = showOpenSettingsInPanel,
                                    showNotGrantedHint = showAccessNotGrantedHint,
                                    onOpenSettings = {
                                        awaitingSettingsReturn = true
                                        showAccessNotGrantedHint = false
                                        val manageIntent =
                                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                data = "package:${context.packageName}".toUri()
                                            }
                                        context.startActivity(manageIntent)
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(120.dp))
                    }
                }

                AnimatedVisibility(
                    visible = !hideBottomPrimaryButton,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = reducedMotionEnterTransition(fadeIn(animationSpec = visibilityFadeInSpec)),
                    exit = reducedMotionExitTransition(fadeOut(animationSpec = visibilityFadeOutSpec)),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 32.dp, end = 32.dp, bottom = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ChangeAnytimeFooter()
                        Spacer(Modifier.height(16.dp))
                        if (selected == FolderAccessMode.SAF_ONLY) {
                            val selectiveActionColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            PrimaryPermissionActionButton(
                                onClick = {
                                    viewModel.setFolderAccessMode(FolderAccessMode.SAF_ONLY)
                                    onContinue()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = stringResource(R.string.onboarding_permissions_use_selective_access),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = selectiveActionColor,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                                fontWeight = FontWeight.Normal,
                            )
                        } else {
                            val allFilesButtonLabel =
                                if (Environment.isExternalStorageManager()) {
                                    stringResource(R.string.onboarding_permissions_allow_all_files)
                                } else {
                                    stringResource(R.string.onboarding_permissions_grant_all_files)
                                }
                            PrimaryPermissionActionButton(
                                onClick = {
                                    when {
                                        Environment.isExternalStorageManager() -> {
                                            viewModel.setFolderAccessMode(FolderAccessMode.ALL_FILES_PREFERRED)
                                            onContinue()
                                        }

                                        else -> {
                                            viewModel.setFolderAccessMode(FolderAccessMode.ALL_FILES_PREFERRED)
                                            hasEnteredAllFilesGrantFlow = true
                                            grantPanelVisible = true
                                            showAccessNotGrantedHint = false
                                            showOpenSettingsInPanel = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = allFilesButtonLabel,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeAnytimeFooter() {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        FilePipeMaterialRoundedSymbol(
            name = "verified_user",
            contentDescription = null,
            size = 18.dp,
            tint = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.onboarding_permissions_change_anytime_footer),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccessModeSwitcher(
    selected: FolderAccessMode,
    onSelectAllFiles: () -> Unit,
    onSelectSelective: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val allFilesSelected = selected == FolderAccessMode.ALL_FILES_PREFERRED
    val stateBAccentColor = scheme.surfaceContainerHighest
    val allFilesActiveFillColor = scheme.primary
    val transparentInactiveColors =
        ToggleButtonDefaults.toggleButtonColors(
            containerColor = Color.Transparent,
            contentColor = scheme.onSurfaceVariant,
        )
    val selectiveActiveColors =
        ToggleButtonDefaults.toggleButtonColors(
            checkedContainerColor = stateBAccentColor,
            checkedContentColor = scheme.onSurface,
        )
    ButtonGroup(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
    ) {
        customItem(
            buttonGroupContent = {
                FilePipeToggleButton(
                    checked = allFilesSelected,
                    onCheckedChange = { checked -> if (checked) onSelectAllFiles() },
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(54.dp)
                            .semantics { role = Role.RadioButton },
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    colors =
                        if (allFilesSelected) {
                            ToggleButtonDefaults.toggleButtonColors()
                        } else {
                            transparentInactiveColors
                        },
                    border =
                        if (allFilesSelected) {
                            null
                        } else {
                            BorderStroke(1.dp, allFilesActiveFillColor.copy(alpha = 0.82f))
                        },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_permissions_mode_all_files),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            menuContent = { menuState ->
                FilePipeOutlinedButton(
                    onClick = {
                        onSelectAllFiles()
                        menuState.dismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.onboarding_permissions_mode_all_files))
                }
            },
        )
        customItem(
            buttonGroupContent = {
                FilePipeToggleButton(
                    checked = !allFilesSelected,
                    onCheckedChange = { checked -> if (checked) onSelectSelective() },
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(54.dp)
                            .semantics { role = Role.RadioButton },
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    colors =
                        if (allFilesSelected) {
                            transparentInactiveColors
                        } else {
                            selectiveActiveColors
                        },
                    border =
                        if (allFilesSelected) {
                            BorderStroke(1.5.dp, stateBAccentColor)
                        } else {
                            null
                        },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_permissions_mode_selective),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            menuContent = { menuState ->
                FilePipeOutlinedButton(
                    onClick = {
                        onSelectSelective()
                        menuState.dismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.onboarding_permissions_mode_selective))
                }
            },
        )
    }
}

@Composable
private fun PrimaryPermissionActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    fontWeight: FontWeight = FontWeight.Bold,
) {
    FilePipeButton(
        onClick = onClick,
        modifier = modifier.height(66.dp),
        shape = pillShape,
        colors = colors,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = fontWeight,
        )
        FilePipeMaterialRoundedSymbol(
            name = "arrow_forward",
            contentDescription = null,
            autoMirror = true,
        )
    }
}

@Composable
private fun AllFilesAccessPitch(onLearnMore: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.onboarding_permissions_all_files_pitch_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = scheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        PitchSubtitleText(
            text = stringResource(R.string.onboarding_permissions_all_files_pitch_subtitle),
            modifier = Modifier.fillMaxWidth(),
            onLearnMore = onLearnMore,
        )
        Spacer(Modifier.height(28.dp))
        CheckCopyLine(
            leading = stringResource(R.string.onboarding_permissions_all_files_hook_once),
            rest = stringResource(R.string.onboarding_permissions_all_files_rest_once),
        )
        Spacer(Modifier.height(20.dp))
        CheckCopyLine(
            leading = stringResource(R.string.onboarding_permissions_all_files_hook_everywhere),
            rest = stringResource(R.string.onboarding_permissions_all_files_rest_everywhere),
        )
        Spacer(Modifier.height(20.dp))
        CheckCopyLine(
            leading = stringResource(R.string.onboarding_permissions_all_files_hook_device),
            rest = stringResource(R.string.onboarding_permissions_all_files_rest_device),
        )
    }
}

@Composable
private fun SelectiveAccessPitch(onLearnMore: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.onboarding_permissions_selective_pitch_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = scheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        PitchSubtitleText(
            text = stringResource(R.string.onboarding_permissions_selective_pitch_subtitle),
            modifier = Modifier.fillMaxWidth(),
            onLearnMore = onLearnMore,
        )
        Spacer(Modifier.height(28.dp))
        CheckCopyLine(
            leading = stringResource(R.string.onboarding_permissions_selective_hook_privacy),
            rest = stringResource(R.string.onboarding_permissions_selective_rest_privacy),
        )
        Spacer(Modifier.height(18.dp))
        CheckCopyLine(
            leading = stringResource(R.string.onboarding_permissions_selective_hook_control),
            rest = stringResource(R.string.onboarding_permissions_selective_rest_control),
        )
        Spacer(Modifier.height(18.dp))
        CopyLine(
            marker = "close",
            leading = stringResource(R.string.onboarding_permissions_selective_hook_limits),
            rest = stringResource(R.string.onboarding_permissions_selective_rest_limits),
        )
    }
}

@Composable
private fun PitchSubtitleText(
    text: String,
    onLearnMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val learnMore = stringResource(R.string.onboarding_permissions_learn_more)
    Text(
        text =
            buildAnnotatedString {
                append(text)
                append(" ")
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "storage_access_faq",
                        styles =
                            TextLinkStyles(
                                style =
                                    SpanStyle(
                                        color = scheme.secondary,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                            ),
                        linkInteractionListener = { onLearnMore() },
                    ),
                ) {
                    append(learnMore)
                }
            },
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Normal,
        color = scheme.onPrimaryContainer.copy(alpha = 0.92f),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun CheckCopyLine(
    leading: String,
    rest: String,
) {
    CopyLine(
        marker = "check",
        leading = leading,
        rest = rest,
    )
}

@Composable
private fun CopyLine(
    marker: String,
    leading: String,
    rest: String,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        FilePipeMaterialRoundedSymbol(
            name = marker,
            contentDescription = null,
            size = 22.dp,
            tint = scheme.primary,
        )
        CopyLineText(
            leading = leading,
            rest = rest,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CopyLineText(
    leading: String,
    rest: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text =
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(leading)
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Normal)) {
                    append(" ")
                    append(rest)
                }
            },
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Normal,
        color = scheme.onPrimaryContainer.copy(alpha = 0.92f),
    )
}

@Composable
private fun AllFilesInstructionPanel(
    showOpenSettingsButton: Boolean,
    showNotGrantedHint: Boolean,
    onOpenSettings: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val visibilityFadeInSpec =
        reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val visibilityFadeOutSpec =
        reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = scheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.onboarding_permissions_instruction_heading),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            PermissionBulletLine(
                text = stringResource(R.string.onboarding_permissions_instruction_step1),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            PermissionBulletLine(
                text = stringResource(R.string.onboarding_permissions_instruction_step2),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            AnimatedVisibility(
                visible = showNotGrantedHint,
                enter = reducedMotionEnterTransition(fadeIn(animationSpec = visibilityFadeInSpec)),
                exit = reducedMotionExitTransition(fadeOut(animationSpec = visibilityFadeOutSpec)),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_permissions_access_not_granted),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            AnimatedVisibility(
                visible = showOpenSettingsButton,
                enter = reducedMotionEnterTransition(fadeIn(animationSpec = visibilityFadeInSpec)),
                exit = reducedMotionExitTransition(fadeOut(animationSpec = visibilityFadeOutSpec)),
            ) {
                FilePipeOutlinedButton(
                    onClick = onOpenSettings,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    shape = pillShape,
                    border = BorderStroke(1.dp, scheme.primary),
                ) {
                    Text(stringResource(R.string.onboarding_permissions_open_settings))
                }
            }
        }
    }
}

@Composable
private fun FullAccessHighlightCard(
    selected: Boolean,
    onSelect: () -> Unit,
    title: String,
    body: String,
) {
    val scheme = MaterialTheme.colorScheme
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.03f else 1f,
        animationSpec =
            reducedMotionAwareSpec(
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
        label = "fullAccessCardScale",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.primary.copy(alpha = 0.55f),
        animationSpec = reducedMotionAwareSpec(spring()),
        label = "fullAccessBorder",
    )
    val surfaceBlend = if (selected) 0.22f else 0.40f
    val containerColor = lerp(scheme.primaryContainer, scheme.surface, surfaceBlend)
    Card(
        onClick = onSelect,
        modifier =
            Modifier
                .fillMaxWidth()
                .scale(scale),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(2.dp, borderColor),
        colors =
            CardDefaults.cardColors(
                containerColor = containerColor,
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
                draggedElevation = 0.dp,
                disabledElevation = 0.dp,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(PermissionCardLeadingIconSlotWidth)
                        .padding(top = 2.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                StarGlowIcon(tint = scheme.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(8.dp))
                PermissionTextWithBullets(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.92f),
                )
            }
            RadioButton(
                selected = selected,
                onClick = onSelect,
                modifier = Modifier.padding(top = 2.dp),
                colors =
                    RadioButtonDefaults.colors(
                        selectedColor = scheme.primary,
                        unselectedColor = scheme.onSurfaceVariant,
                    ),
            )
        }
    }
}

@Composable
private fun StarGlowIcon(tint: Color) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .background(
                        brush =
                            Brush.radialGradient(
                                colors =
                                    listOf(
                                        tint.copy(alpha = 0.45f),
                                        tint.copy(alpha = 0.08f),
                                        Color.Transparent,
                                    ),
                            ),
                        shape = CircleShape,
                    ),
        )
        FilePipeMaterialRoundedSymbol(
            name = "star",
            contentDescription = null,
            size = 26.dp,
            modifier = Modifier.size(26.dp),
            tint = tint,
        )
    }
}

@Composable
private fun SelectFoldersSecondaryCard(
    selected: Boolean,
    onSelect: () -> Unit,
    title: String,
    body: String,
) {
    val scheme = MaterialTheme.colorScheme
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        animationSpec =
            reducedMotionAwareSpec(
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
        label = "selectFoldersCardScale",
    )
    val borderColor by animateColorAsState(
        targetValue =
            when {
                selected -> scheme.primary
                else -> scheme.outlineVariant.copy(alpha = 0.5f)
            },
        animationSpec = reducedMotionAwareSpec(spring()),
        label = "selectFoldersBorder",
    )
    val borderWidth = if (selected) 2.dp else 1.dp
    Card(
        onClick = onSelect,
        modifier =
            Modifier
                .fillMaxWidth()
                .scale(scale),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(borderWidth, borderColor),
        colors =
            CardDefaults.cardColors(
                containerColor = scheme.surfaceContainerHigh,
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = if (selected) 4.dp else 1.dp,
                pressedElevation = 6.dp,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(PermissionCardLeadingIconSlotWidth)
                        .padding(top = 2.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                FilePipeMaterialRoundedSymbol(
                    name = "folder_open",
                    contentDescription = null,
                    filled = false,
                    size = 28.dp,
                    modifier = Modifier.size(28.dp),
                    tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                PermissionTextWithBullets(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            RadioButton(
                selected = selected,
                onClick = onSelect,
                modifier = Modifier.padding(top = 2.dp),
                colors =
                    RadioButtonDefaults.colors(
                        selectedColor = scheme.primary,
                        unselectedColor = scheme.onSurfaceVariant,
                    ),
            )
        }
    }
}

@Composable
private fun PermissionTextWithBullets(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        text.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> {
                    Spacer(Modifier.height(2.dp))
                }

                trimmed.startsWith("\u2022") -> {
                    PermissionBulletLine(
                        text = trimmed.removePrefix("\u2022").trimStart(),
                        style = style,
                        color = color,
                    )
                }

                else -> {
                    Text(
                        text = line,
                        style = style,
                        color = color,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionsHeroIllustration(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier =
            modifier
                .scale(1.08f)
                .drawBehind {
                    // Background concentric rings: outer primary, inner secondary.
                    val baseRingRadius = size.minDimension * 0.42f
                    drawCircle(
                        color = scheme.primary,
                        alpha = 0.18f,
                        radius = baseRingRadius,
                        center = Offset(size.width * 0.50f, size.height * 0.52f),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                    drawCircle(
                        color = scheme.secondary,
                        alpha = 0.22f,
                        radius = baseRingRadius * 0.66f,
                        center = Offset(size.width * 0.56f, size.height * 0.46f),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    // Dashed connector arcs from each chip toward the folder center.
                    // Each arc carries the chip's role color.
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(2.5.dp.toPx(), 4.dp.toPx()), 0f)
                    val arcStrokeWidth = 1.2.dp.toPx()
                    val folderCenter = Offset(size.width * 0.50f, size.height * 0.55f)
                    val chipOffsets =
                        listOf(
                            Offset(size.width * 0.22f, size.height * 0.30f) to scheme.primary,
                            Offset(size.width * 0.78f, size.height * 0.30f) to scheme.tertiary,
                            Offset(size.width * 0.22f, size.height * 0.78f) to scheme.secondary,
                            Offset(size.width * 0.78f, size.height * 0.78f) to scheme.tertiary,
                        )
                    chipOffsets.forEach { (start, color) ->
                        val controlX = (start.x + folderCenter.x) / 2f + (folderCenter.x - start.x) * 0.08f
                        val controlY = (start.y + folderCenter.y) / 2f - 12.dp.toPx()
                        val arcPath =
                            Path().apply {
                                moveTo(start.x, start.y)
                                quadraticTo(controlX, controlY, folderCenter.x, folderCenter.y)
                            }
                        drawPath(
                            path = arcPath,
                            color = color,
                            alpha = 0.28f,
                            style = Stroke(width = arcStrokeWidth, pathEffect = dashEffect),
                        )
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        // Decorative sparkles, cycling through primary / tertiary / secondary so the
        // ornamentation visibly belongs to the whole theme rather than a single accent.
        DecorativeSparkle(
            color = scheme.primary,
            alpha = 0.78f,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 36.dp, y = 22.dp),
        )
        DecorativeSparkle(
            color = scheme.tertiary,
            alpha = 0.78f,
            size = 20.dp,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-32).dp, y = (-26).dp),
        )
        DecorativeSparkle(
            color = scheme.secondary,
            alpha = 0.62f,
            size = 14.dp,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-38).dp, y = 58.dp),
        )
        DecorativeDot(
            color = scheme.tertiary,
            alpha = 0.65f,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 44.dp, y = (-42).dp),
        )
        DecorativeDot(
            color = scheme.primary,
            alpha = 0.55f,
            size = 3.dp,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-62).dp, y = 26.dp),
        )
        DecorativeDot(
            color = scheme.secondary,
            alpha = 0.6f,
            size = 3.dp,
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 30.dp, y = (-4).dp),
        )

        // Center folder badge. Sits a touch below dead center so the chips at the top
        // do not crowd the title that follows.
        FolderBadge(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset(y = 10.dp),
        )

        // Four file-type chips, each pulling a different M3 role so the hero is colored
        // as a full palette rather than a monochrome stack.
        FileTypeChip(
            iconName = "image",
            iconColor = scheme.primary,
            rotationDeg = -8f,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset(x = (-68).dp, y = (-50).dp),
        )
        FileTypeChip(
            iconName = "music_note",
            iconColor = scheme.tertiary,
            rotationDeg = 8f,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset(x = 68.dp, y = (-50).dp),
        )
        FileTypeChip(
            iconName = "description",
            iconColor = scheme.secondary,
            rotationDeg = 6f,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset(x = (-68).dp, y = 60.dp),
        )
        FileTypeChip(
            iconName = "movie",
            iconColor = scheme.tertiary,
            rotationDeg = -6f,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset(x = 68.dp, y = 60.dp),
        )
    }
}

@Composable
private fun FolderBadge(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier.size(width = 96.dp, height = 66.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val cornerRadius = 8.dp.toPx()

            // Folder outline: tab on top-left, body fills the rest. Matches the M3 folder silhouette.
            val bodyTopY = h * 0.22f
            val tabStartX = 0f
            val tabRightX = w * 0.27f
            val bodyTopLeftX = w * 0.38f
            val folderPath =
                Path().apply {
                    // Start just inside the tab's top-left corner.
                    moveTo(tabStartX + cornerRadius, 0f)
                    lineTo(tabRightX, 0f)
                    // Slope from tab corner down to body top.
                    lineTo(bodyTopLeftX, bodyTopY)
                    lineTo(w - cornerRadius, bodyTopY)
                    quadraticTo(w, bodyTopY, w, bodyTopY + cornerRadius)
                    lineTo(w, h - cornerRadius)
                    quadraticTo(w, h, w - cornerRadius, h)
                    lineTo(tabStartX + cornerRadius, h)
                    quadraticTo(tabStartX, h, tabStartX, h - cornerRadius)
                    lineTo(tabStartX, cornerRadius)
                    quadraticTo(tabStartX, 0f, tabStartX + cornerRadius, 0f)
                    close()
                }
            drawPath(folderPath, color = scheme.primaryContainer)

            // Seam line where the tab meets the body, drawn with onPrimaryContainer at low alpha.
            drawLine(
                color = scheme.onPrimaryContainer,
                alpha = 0.30f,
                start = Offset(tabStartX, bodyTopY),
                end = Offset(w, bodyTopY),
                strokeWidth = 1f,
            )
        }
        FilePipeMaterialRoundedSymbol(
            name = "sort",
            contentDescription = null,
            size = 34.dp,
            modifier = Modifier.offset(y = 10.dp),
            tint = scheme.primary,
        )
    }
}

@Composable
private fun FileTypeChip(
    iconName: String,
    iconColor: Color,
    rotationDeg: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(58.dp)
                .graphicsLayer { rotationZ = rotationDeg },
        contentAlignment = Alignment.Center,
    ) {
        FilePipeMaterialRoundedSymbol(
            name = iconName,
            contentDescription = null,
            size = 44.dp,
            tint = iconColor,
            filled = true,
        )
    }
}

@Composable
private fun DecorativeSparkle(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    alpha: Float = 0.82f,
) {
    val sparkleColor = color.copy(alpha = alpha)
    Box(
        modifier =
            modifier
                .size(size)
                .drawBehind {
                    val centerX = this.size.width / 2f
                    val centerY = this.size.height / 2f
                    val radius = this.size.minDimension / 2f
                    val innerRadius = radius * 0.28f
                    val sparklePath =
                        Path().apply {
                            moveTo(centerX, centerY - radius)
                            lineTo(centerX + innerRadius, centerY - innerRadius)
                            lineTo(centerX + radius, centerY)
                            lineTo(centerX + innerRadius, centerY + innerRadius)
                            lineTo(centerX, centerY + radius)
                            lineTo(centerX - innerRadius, centerY + innerRadius)
                            lineTo(centerX - radius, centerY)
                            lineTo(centerX - innerRadius, centerY - innerRadius)
                            close()
                        }
                    drawPath(sparklePath, sparkleColor)
                },
    )
}

@Composable
private fun DecorativeDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 4.dp,
    alpha: Float = 0.72f,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .background(color.copy(alpha = alpha), CircleShape),
    )
}

@Composable
private fun PermissionBulletLine(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "\u2022",
            style = style,
            color = color,
        )
        Text(
            text = text,
            style = style,
            color = color,
            modifier = Modifier.weight(1f),
        )
    }
}
