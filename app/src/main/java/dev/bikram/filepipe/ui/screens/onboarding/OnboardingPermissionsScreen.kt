package dev.bikram.filepipe.ui.screens.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.preferences.FolderAccessMode
import kotlinx.coroutines.delay

private val PermissionCardLeadingIconSlotWidth = 44.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingPermissionsScreen(
    onContinue: () -> Unit,
    onOpenStorageAccessFaq: () -> Unit,
    viewModel: OnboardingPermissionsViewModel = hiltViewModel()
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

    val needsAllFilesGrant =
        selected == FolderAccessMode.ALL_FILES_PREFERRED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
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

    DisposableEffect(lifecycleOwner, selected, hasEnteredAllFilesGrantFlow, awaitingSettingsReturn) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            if (didAutoAdvanceFromGrant) return@LifecycleEventObserver
            val allFilesSelected = selected == FolderAccessMode.ALL_FILES_PREFERRED
            val manager = Environment.isExternalStorageManager()
            if (allFilesSelected && manager && hasEnteredAllFilesGrantFlow) {
                grantPanelVisible = false
                showOpenSettingsInPanel = false
                awaitingSettingsReturn = false
                showAccessNotGrantedHint = false
                didAutoAdvanceFromGrant = true
                viewModel.setFolderAccessMode(FolderAccessMode.ALL_FILES_PREFERRED)
                onContinue()
                return@LifecycleEventObserver
            }
            if (allFilesSelected && !manager && awaitingSettingsReturn) {
                showAccessNotGrantedHint = true
                grantPanelVisible = true
                showOpenSettingsInPanel = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.surface)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to scheme.primaryContainer.copy(alpha = 0.45f),
                        0.55f to scheme.surface.copy(alpha = 0f)
                    )
                )
            )
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.onboarding_permissions_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_permissions_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
                FullAccessHighlightCard(
                    selected = selected == FolderAccessMode.ALL_FILES_PREFERRED,
                    onSelect = {
                        viewModel.setOnboardingFolderAccessSelection(FolderAccessMode.ALL_FILES_PREFERRED)
                    },
                    title = stringResource(R.string.onboarding_permissions_all_files_title),
                    body = stringResource(R.string.onboarding_permissions_all_files_body)
                )
                Spacer(Modifier.height(14.dp))
                SelectFoldersSecondaryCard(
                    selected = selected == FolderAccessMode.SAF_ONLY,
                    onSelect = {
                        viewModel.setOnboardingFolderAccessSelection(FolderAccessMode.SAF_ONLY)
                        resetGrantFlowUi()
                    },
                    title = stringResource(R.string.onboarding_permissions_saf_title),
                    body = stringResource(R.string.onboarding_permissions_saf_body)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.onboarding_permissions_change_anytime_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onOpenStorageAccessFaq,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.onboarding_permissions_learn_more))
                }
                AnimatedVisibility(visible = grantPanelVisible) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        AllFilesInstructionPanel(
                            showOpenSettingsButton = showOpenSettingsInPanel,
                            showNotGrantedHint = showAccessNotGrantedHint,
                            onOpenSettings = {
                                awaitingSettingsReturn = true
                                showAccessNotGrantedHint = false
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val manageIntent =
                                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                    context.startActivity(manageIntent)
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(120.dp))
            }
        }

        AnimatedVisibility(
            visible = !hideBottomPrimaryButton,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val primaryLabel = when {
                selected == FolderAccessMode.SAF_ONLY ->
                    stringResource(R.string.onboarding_permissions_continue)
                needsAllFilesGrant ->
                    stringResource(R.string.onboarding_permissions_grant_access)
                else ->
                    stringResource(R.string.onboarding_permissions_continue)
            }
            Button(
                onClick = {
                    when {
                        selected == FolderAccessMode.SAF_ONLY -> {
                            viewModel.setFolderAccessMode(FolderAccessMode.SAF_ONLY)
                            onContinue()
                        }
                        selected == FolderAccessMode.ALL_FILES_PREFERRED &&
                            Environment.isExternalStorageManager() -> {
                            viewModel.setFolderAccessMode(FolderAccessMode.ALL_FILES_PREFERRED)
                            onContinue()
                        }
                        selected == FolderAccessMode.ALL_FILES_PREFERRED &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                            !Environment.isExternalStorageManager() -> {
                            viewModel.setFolderAccessMode(FolderAccessMode.ALL_FILES_PREFERRED)
                            hasEnteredAllFilesGrantFlow = true
                            grantPanelVisible = true
                            showAccessNotGrantedHint = false
                            showOpenSettingsInPanel = false
                        }
                        else -> {
                            viewModel.setFolderAccessMode(FolderAccessMode.ALL_FILES_PREFERRED)
                            onContinue()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 32.dp, bottom = 40.dp),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = primaryLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
private fun AllFilesInstructionPanel(
    showOpenSettingsButton: Boolean,
    showNotGrantedHint: Boolean,
    onOpenSettings: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.onboarding_permissions_instruction_heading),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "• ${stringResource(R.string.onboarding_permissions_instruction_step1)}",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "• ${stringResource(R.string.onboarding_permissions_instruction_step2)}",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
            AnimatedVisibility(visible = showNotGrantedHint) {
                Text(
                    text = stringResource(R.string.onboarding_permissions_access_not_granted),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            AnimatedVisibility(visible = showOpenSettingsButton) {
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(50)
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
    body: String
) {
    val scheme = MaterialTheme.colorScheme
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.03f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "fullAccessCardScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.primary.copy(alpha = 0.55f),
        label = "fullAccessBorder"
    )
    val surfaceBlend = if (selected) 0.22f else 0.40f
    val containerColor = lerp(scheme.primaryContainer, scheme.surface, surfaceBlend)
    Card(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(2.dp, borderColor),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            draggedElevation = 0.dp,
            disabledElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(PermissionCardLeadingIconSlotWidth)
                    .padding(top = 2.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                StarGlowIcon(tint = scheme.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onPrimaryContainer
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.92f)
                )
            }
            RadioButton(
                selected = selected,
                onClick = onSelect,
                modifier = Modifier.padding(top = 2.dp),
                colors = RadioButtonDefaults.colors(
                    selectedColor = scheme.primary,
                    unselectedColor = scheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun StarGlowIcon(tint: Color) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tint.copy(alpha = 0.45f),
                            tint.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            tint = tint
        )
    }
}

@Composable
private fun SelectFoldersSecondaryCard(
    selected: Boolean,
    onSelect: () -> Unit,
    title: String,
    body: String
) {
    val scheme = MaterialTheme.colorScheme
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "selectFoldersCardScale"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> scheme.primary
            else -> scheme.outlineVariant.copy(alpha = 0.5f)
        },
        label = "selectFoldersBorder"
    )
    val borderWidth = if (selected) 2.dp else 1.dp
    Card(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(borderWidth, borderColor),
        colors = CardDefaults.cardColors(
            containerColor = scheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (selected) 4.dp else 1.dp,
            pressedElevation = 6.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(PermissionCardLeadingIconSlotWidth)
                    .padding(top = 2.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (selected) scheme.primary else scheme.onSurfaceVariant
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            RadioButton(
                selected = selected,
                onClick = onSelect,
                modifier = Modifier.padding(top = 2.dp),
                colors = RadioButtonDefaults.colors(
                    selectedColor = scheme.primary,
                    unselectedColor = scheme.onSurfaceVariant
                )
            )
        }
    }
}
