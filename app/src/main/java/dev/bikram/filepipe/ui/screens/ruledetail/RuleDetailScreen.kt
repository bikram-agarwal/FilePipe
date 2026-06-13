package dev.bikram.filepipe.ui.screens.ruledetail

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.preferences.FolderAccessMode
import dev.bikram.filepipe.data.preferences.treatAsSafUi
import dev.bikram.filepipe.data.preferences.usesAllFilesPaths
import dev.bikram.filepipe.data.storage.isFilesystemFolderPathAllowedForRules
import dev.bikram.filepipe.data.storage.isFilesystemFolderPathString
import dev.bikram.filepipe.data.storage.isFolderPathAllFilesAccessLocationForRules
import dev.bikram.filepipe.data.storage.normalizeFilesystemFolderPath
import dev.bikram.filepipe.data.storage.safTreeUriToPath
import dev.bikram.filepipe.domain.RuleFolderSeverity
import dev.bikram.filepipe.domain.assessRuleFolderAccess
import dev.bikram.filepipe.domain.model.ConflictPolicy
import dev.bikram.filepipe.domain.model.FolderAccessResult
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.RuleIcon
import dev.bikram.filepipe.domain.model.RuleSchedule
import dev.bikram.filepipe.domain.model.RuleTemplate
import dev.bikram.filepipe.domain.model.ScheduleType
import dev.bikram.filepipe.domain.model.materialSymbolName
import dev.bikram.filepipe.ui.common.AppBottomSheet
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.common.FilePipePredictiveBackHandler
import dev.bikram.filepipe.ui.common.isSmallLandscape
import dev.bikram.filepipe.ui.components.CenteredTooltipText
import dev.bikram.filepipe.ui.components.FileExtensionChips
import dev.bikram.filepipe.ui.components.FilePipeButton
import dev.bikram.filepipe.ui.components.FilePipeDropdownMenuItem
import dev.bikram.filepipe.ui.components.FilePipeElevatedCard
import dev.bikram.filepipe.ui.components.FilePipeFilledTonalButton
import dev.bikram.filepipe.ui.components.FilePipeFilledTonalIconButton
import dev.bikram.filepipe.ui.components.FilePipeIconButton
import dev.bikram.filepipe.ui.components.FilePipeOutlinedButton
import dev.bikram.filepipe.ui.components.FilePipeSurface
import dev.bikram.filepipe.ui.components.FilePipeSwitch
import dev.bikram.filepipe.ui.components.FilePipeConfirmDialog
import dev.bikram.filepipe.ui.components.FilePipeTextButton
import dev.bikram.filepipe.ui.components.FilePipeToggleButton
import dev.bikram.filepipe.ui.components.FilesystemFolderPickerSheetContent
import dev.bikram.filepipe.ui.components.FolderPickerButton
import dev.bikram.filepipe.ui.components.RuleIconEmojiPresets
import dev.bikram.filepipe.ui.components.RuleIconOrEmoji
import dev.bikram.filepipe.ui.components.ScheduleDialog
import dev.bikram.filepipe.ui.components.ToggleLabelHelpDropdown
import dev.bikram.filepipe.ui.components.absoluteStoragePathToOpenTreeInitialUri
import dev.bikram.filepipe.ui.components.displayPath
import dev.bikram.filepipe.ui.components.previewSourceFolderDisplayPath
import dev.bikram.filepipe.ui.feedback.tapSoundClickable
import dev.bikram.filepipe.ui.modifiers.progressiveBlurFullBleedLayer
import dev.bikram.filepipe.ui.theme.LocalProgressiveBlurStyle
import dev.bikram.filepipe.ui.theme.LocalUseGradientBackground
import dev.bikram.filepipe.ui.theme.cardFilledTonalIconButtonColors
import dev.bikram.filepipe.ui.theme.cardIconContainerColor
import dev.bikram.filepipe.ui.theme.compactControlShape
import dev.bikram.filepipe.ui.theme.elevatedCardColors
import dev.bikram.filepipe.ui.theme.gradientOverlayTopAppBarColors
import dev.bikram.filepipe.ui.theme.pillShape
import dev.bikram.filepipe.ui.theme.reducedMotionAwareSpec
import dev.bikram.filepipe.ui.theme.reducedMotionEnterTransition
import dev.bikram.filepipe.ui.theme.reducedMotionExitTransition
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * No-entry when outside allowed volumes, or in selective access when the folder is a location Android
 * typically does not expose as a SAF tree (primary shared root, public Download root). Otherwise warn for
 * lost grants, unreadable trees, or paths fixable by re-picking SAF.
 */
private fun folderAccessIssueEmojiPrefix(
    path: String,
    folderAccessMode: FolderAccessMode,
): String {
    if (isFilesystemFolderPathString(path)) {
        if (!isFilesystemFolderPathAllowedForRules(path)) return "🚫 "
        if (folderAccessMode.treatAsSafUi() && isFolderPathAllFilesAccessLocationForRules(path)) {
            return "🚫 "
        }
        return "⚠️ "
    }
    if (isFolderPathAllFilesAccessLocationForRules(path)) return "🚫 "
    return "⚠️ "
}

private sealed class FolderPickIntent {
    data object AddSource : FolderPickIntent()

    data class ReplaceSource(
        val previousPath: String,
    ) : FolderPickIntent()

    data object SetDestination : FolderPickIntent()
}

private data class PendingFilesystemFolderPick(
    val intent: FolderPickIntent,
    val startDirectory: String,
)

private data class RuleAlertColors(
    val container: Color,
    val content: Color,
    val accent: Color,
)

@Composable
private fun ruleAlertColors(isErrorSeverity: Boolean): RuleAlertColors {
    val scheme = MaterialTheme.colorScheme
    if (isErrorSeverity) {
        return RuleAlertColors(
            container = scheme.errorContainer.copy(alpha = 0.9f),
            content = scheme.onErrorContainer,
            accent = scheme.error,
        )
    }

    return ruleWarningColors()
}

@Composable
private fun ruleWarningColors(): RuleAlertColors {
    val scheme = MaterialTheme.colorScheme
    val darkUi = ColorUtils.calculateLuminance(scheme.background.toArgb()) < 0.35
    return if (darkUi) {
        RuleAlertColors(
            container = Color(0xFF4A3000).copy(alpha = 0.94f),
            content = Color(0xFFFFDFA3),
            accent = Color(0xFFFFB300),
        )
    } else {
        RuleAlertColors(
            container = Color(0xFFFFF1CC).copy(alpha = 0.96f),
            content = Color(0xFF5F3B00),
            accent = Color(0xFFB26A00),
        )
    }
}

@Composable
private fun RuleErrorAlertCard(
    title: String,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    titleTrailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val contentColor = scheme.onErrorContainer
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.errorContainer,
        contentColor = contentColor,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilePipeMaterialRoundedSymbol(
                    name = "error",
                    contentDescription = null,
                    size = 22.dp,
                    tint = contentColor,
                    weight = FontWeight.Medium,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = LocalContentColor.current,
                            modifier = Modifier.weight(1f),
                        )
                        titleTrailing?.invoke()
                    }
                    Spacer(Modifier.height(4.dp))
                    Column(
                        verticalArrangement = verticalArrangement,
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleValidationErrorBar(
    messages: List<String>,
    isErrorSeverity: Boolean,
    onOpenFaq: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (messages.isEmpty()) return

    val alertColors = ruleAlertColors(isErrorSeverity)
    val contentColor = alertColors.content
    val barShape = MaterialTheme.shapes.extraLargeIncreased
    val scope = rememberCoroutineScope()
    var offsetX by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(messages) {
        offsetX = 0f
    }

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .onSizeChanged { size -> widthPx = size.width.toFloat() }
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount ->
                            val dragLimit = widthPx.takeIf { it > 0f } ?: 10_000f
                            offsetX = (offsetX + dragAmount).coerceIn(-dragLimit, dragLimit)
                        },
                        onDragCancel = {
                            scope.launch {
                                val animation = Animatable(offsetX)
                                animation.animateTo(0f) {
                                    offsetX = value
                                }
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                val dismissThreshold = widthPx * 0.28f
                                val shouldDismiss = widthPx > 0f && abs(offsetX) >= dismissThreshold
                                val target =
                                    if (shouldDismiss) {
                                        widthPx * offsetX.sign
                                    } else {
                                        0f
                                    }
                                val animation = Animatable(offsetX)
                                animation.animateTo(target) {
                                    offsetX = value
                                }
                                if (shouldDismiss) onDismiss()
                            }
                        },
                    )
                },
        shape = barShape,
        color = alertColors.container,
        contentColor = contentColor,
        border = BorderStroke(1.dp, alertColors.accent.copy(alpha = 0.48f)),
        shadowElevation = 6.dp,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilePipeMaterialRoundedSymbol(
                    name = if (isErrorSeverity) "error" else "warning",
                    contentDescription = null,
                    size = 26.dp,
                    tint = LocalContentColor.current,
                    weight = FontWeight.Medium,
                )
                RuleValidationMessageList(
                    messages = messages,
                    modifier = Modifier.weight(1f),
                )
                FilePipeFilledTonalButton(
                    onClick = onOpenFaq,
                    shape = pillShape,
                    colors =
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = contentColor.copy(alpha = 0.14f),
                            contentColor = contentColor,
                        ),
                    border = BorderStroke(1.dp, contentColor.copy(alpha = 0.22f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.rule_detail_open_faq),
                        style = MaterialTheme.typography.labelLarge,
                        color = LocalContentColor.current,
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleValidationMessageList(
    messages: List<String>,
    modifier: Modifier = Modifier,
) {
    if (messages.size == 1) {
        Text(
            text = messages.first(),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalContentColor.current,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        messages.forEach { message ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "\u2022",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current,
                    modifier = Modifier.width(14.dp),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ruleIconOptionLabel(icon: RuleIcon): String =
    stringResource(
        when (icon) {
            RuleIcon.DEFAULT -> R.string.rule_icon_label_default
            RuleIcon.IMAGE -> R.string.rule_icon_label_image
            RuleIcon.SCREENSHOT -> R.string.rule_icon_label_screenshot
            RuleIcon.VIDEO -> R.string.rule_icon_label_video
            RuleIcon.MUSIC -> R.string.rule_icon_label_music
            RuleIcon.DOWNLOAD -> R.string.rule_icon_label_download
            RuleIcon.DOCUMENT -> R.string.rule_icon_label_document
            RuleIcon.INSTALLABLE -> R.string.rule_icon_label_installable
        },
    )

@Composable
private fun RuleSectionCard(
    title: String,
    subtitle: String?,
    iconName: String,
    modifier: Modifier = Modifier,
    highlightColor: Color? = null,
    titleTrailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardShape = MaterialTheme.shapes.large
    ElevatedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (highlightColor != null) {
                        Modifier.border(
                            width = 1.dp,
                            color = highlightColor,
                            shape = cardShape,
                        )
                    } else {
                        Modifier
                    },
                ),
        shape = cardShape,
        colors = elevatedCardColors(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilePipeMaterialRoundedSymbol(
                    name = iconName,
                    contentDescription = null,
                    size = 24.dp,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                titleTrailing?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

private fun isScheduleInvalid(schedule: RuleSchedule?): Boolean {
    if (schedule == null) return false
    val interval = schedule.repeatInterval ?: RuleSchedule.DEFAULT_REPEAT_INTERVAL
    if (!RuleSchedule.isRepeatIntervalValid(schedule.type, interval)) return true
    return when (schedule.type) {
        ScheduleType.EVERY_N_HOURS -> {
            schedule.hour !in 0..23 ||
                schedule.minute !in 0..59
        }

        ScheduleType.WEEKLY -> {
            val days = RuleSchedule.bitmaskToDaysOfWeek(schedule.dayOfWeek)
            schedule.dayOfWeek == null ||
                days.isEmpty() ||
                schedule.hour !in 0..23 ||
                schedule.minute !in 0..59
        }

        ScheduleType.DAILY -> {
            schedule.hour !in 0..23 ||
                schedule.minute !in 0..59
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun RuleDetailScreen(
    onNavigateBack: () -> Unit,
    onOpenFaq: () -> Unit,
    onSavedRule: ((Long) -> Unit)? = null,
    showNavigateBack: Boolean = true,
    allowInitialRuleNameFocus: Boolean = true,
    isReadOnly: Boolean = false,
    viewModel: RuleDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isReadOnly = isReadOnly || state.isTrashed
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteForeverConfirm by remember { mutableStateOf(false) }
    var showTemplateSheet by remember { mutableStateOf(viewModel.showInitialTemplatePicker) }
    var showRuleIconSheet by remember { mutableStateOf(false) }
    var customEmojiDraft by remember { mutableStateOf("") }
    val previewSheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    val bookmarkedFolders by viewModel.bookmarkedFolders.collectAsStateWithLifecycle()
    var advancedExpanded by remember { mutableStateOf(false) }
    var ruleNameCanFocus by remember(allowInitialRuleNameFocus) { mutableStateOf(allowInitialRuleNameFocus) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val internalStorageDisplayName = stringResource(R.string.filesystem_folder_picker_internal_storage)

    var pendingFolderPick by remember { mutableStateOf<FolderPickIntent?>(null) }
    var pendingFilesystemFolderPick by remember { mutableStateOf<PendingFilesystemFolderPick?>(null) }
    val folderPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri: Uri? ->
            val pending = pendingFolderPick
            if (uri == null) {
                pendingFolderPick = null
                return@rememberLauncherForActivityResult
            }
            // Persist the grant so it survives reboots
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            val uriString = uri.toString()
            when (pending) {
                FolderPickIntent.AddSource -> {
                    viewModel.addSourceFolder(uriString)
                }

                is FolderPickIntent.ReplaceSource -> {
                    viewModel.replaceSourceFolder(pending.previousPath, uriString)
                }

                FolderPickIntent.SetDestination -> {
                    viewModel.setDestination(uriString)
                }

                null -> {}
            }
            pendingFolderPick = null
            if (pending != null) {
                viewModel.refreshFolderAccessAfterPermissionChange()
            }
        }

    fun externalStorageRootPath(): String =
        runCatching { Environment.getExternalStorageDirectory().canonicalPath }.getOrNull()
            ?: "/storage/emulated/0"

    fun resolveFilesystemPickerStartDirectory(initialPath: String?): String {
        if (initialPath.isNullOrBlank()) return externalStorageRootPath()
        if (initialPath.startsWith("content://")) {
            val pathFromSaf = runCatching { safTreeUriToPath(initialPath.toUri()) }.getOrNull()
            val normalized = pathFromSaf?.let { normalizeFilesystemFolderPath(it) }
            return normalized?.takeIf { candidate ->
                val folder = File(candidate)
                folder.isDirectory && folder.canRead()
            } ?: externalStorageRootPath()
        }
        return normalizeFilesystemFolderPath(initialPath)
            ?.takeIf { candidate ->
                val folder = File(candidate)
                folder.isDirectory && folder.canRead()
            } ?: externalStorageRootPath()
    }

    fun launchFolderPicker(
        intent: FolderPickIntent,
        initialPath: String?,
    ) {
        val useFilesystemPicker =
            !state.folderAccessMode.treatAsSafUi() && state.allFilesAccessGranted
        if (useFilesystemPicker) {
            pendingFilesystemFolderPick =
                PendingFilesystemFolderPick(
                    intent = intent,
                    startDirectory = resolveFilesystemPickerStartDirectory(initialPath),
                )
            return
        }
        pendingFolderPick = intent
        folderPickerLauncher.launch(initialPath?.let { absoluteStoragePathToOpenTreeInitialUri(it) })
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshFolderAccessAfterPermissionChange()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            if (onSavedRule != null) {
                onSavedRule(state.id)
            } else {
                onNavigateBack()
            }
        }
    }

    LaunchedEffect(allowInitialRuleNameFocus) {
        if (!allowInitialRuleNameFocus) {
            withFrameNanos { }
            ruleNameCanFocus = true
        }
    }

    LaunchedEffect(state.removedRedundantFolders) {
        if (state.removedRedundantFolders.isNotEmpty()) {
            val names = state.removedRedundantFolders.joinToString(", ") { it.substringAfterLast('/') }
            snackbarHostState.showSnackbar(resources.getString(R.string.rule_detail_redundant_subfolder_removed, names))
            viewModel.dismissRedundantFolderNotice()
        }
    }

    fun tryNavigateBack() {
        when {
            showDiscardDialog -> showDiscardDialog = false
            isDirty -> showDiscardDialog = true
            else -> onNavigateBack()
        }
    }

    FilePipePredictiveBackHandler(
        enabled =
            isDirty &&
                !showDiscardDialog &&
                !showScheduleDialog &&
                !showTemplateSheet &&
                !showRuleIconSheet &&
                state.previewFiles == null &&
                !state.isPreviewLoading,
    ) {
        showDiscardDialog = true
    }

    val scrollState = rememberScrollState()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topContentPadding = statusTop + 64.dp
    val showValidationErrors = state.errors.isNotEmpty()
    val folderAccessIssues =
        state.inaccessibleSourceIssues.isNotEmpty() || state.destinationFolderAccessIssue != null
    val folderAccessAssessment =
        assessRuleFolderAccess(
            sourceIssues = state.inaccessibleSourceIssues,
            destinationIssue = state.destinationFolderAccessIssue,
            isBlockedLocation = ::isFolderPathAllFilesAccessLocationForRules,
        )
    val hasSuppressibleMissingSourceWarning = folderAccessAssessment.onlySuppressibleSourceWarnings
    val hasOperationalFolderError = folderAccessAssessment.severity == RuleFolderSeverity.ERROR
    val alertIsErrorSeverity = showValidationErrors || hasOperationalFolderError
    val anySourcePermission =
        state.inaccessibleSourceIssues.values.any { it == FolderAccessResult.PermissionDenied }
    val destPermission = state.destinationFolderAccessIssue == FolderAccessResult.PermissionDenied
    val showPermissionHint = anySourcePermission || destPermission
    val hasAllFilesAccessLocationIssue =
        state.inaccessibleSourceIssues.keys.any(::isFolderPathAllFilesAccessLocationForRules) ||
            (
                state.destinationFolderAccessIssue != null &&
                    isFolderPathAllFilesAccessLocationForRules(state.destinationFolderPath)
            )
    val folderAccessSummary =
        if (folderAccessIssues) {
            val usesFilesystemPaths =
                state.inaccessibleSourceIssues.keys.any { !it.startsWith("content://") } ||
                    (
                        state.destinationFolderAccessIssue != null &&
                            state.destinationFolderPath.isNotBlank() &&
                            !state.destinationFolderPath.startsWith("content://")
                    )
            val summaryRes =
                when {
                    hasAllFilesAccessLocationIssue || (showPermissionHint && usesFilesystemPaths) -> {
                        R.string.rule_detail_folder_access_summary_filesystem
                    }

                    showPermissionHint -> {
                        R.string.rule_detail_folder_access_summary_permission
                    }

                    else -> {
                        R.string.rule_detail_folder_access_summary_unavailable
                    }
                }
            stringResource(summaryRes)
        } else {
            null
        }
    val bottomBarMessages =
        buildList {
            addAll(state.errors)
            folderAccessSummary?.let(::add)
        }
    val bottomBarKey =
        remember(
            state.errors,
            state.inaccessibleSourceIssues,
            state.destinationFolderAccessIssue,
            folderAccessSummary,
        ) {
            buildString {
                append(state.errors.joinToString(separator = "\u001F"))
                append("|sources=")
                append(
                    state.inaccessibleSourceIssues.entries
                        .sortedBy { it.key }
                        .joinToString(separator = "\u001F") { "${it.key}:${it.value}" },
                )
                append("|destination=")
                append(state.destinationFolderAccessIssue)
                append("|summary=")
                append(folderAccessSummary.orEmpty())
            }
        }
    var dismissedBottomBarKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(bottomBarMessages.isEmpty()) {
        if (bottomBarMessages.isEmpty()) dismissedBottomBarKey = null
    }
    val isPortrait = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    val showReadOnlyBottomActions = isReadOnly && isPortrait
    val showBottomBar = !isReadOnly && bottomBarMessages.isNotEmpty() && dismissedBottomBarKey != bottomBarKey
    // A pristine new rule hosted in a pane has no back arrow, so it must always offer
    // Cancel/Save; on phone the top-bar back covers cancel until the form is dirty.
    val showBottomActions = isDirty || (viewModel.isNewRule && !showNavigateBack)
    val validationErrorOverlayExtraPadding = if (showBottomBar) 72.dp else 0.dp
    val bottomActionOverlayPadding = if (showBottomActions || showReadOnlyBottomActions) 88.dp else 0.dp
    val bottomOverlayPadding = bottomActionOverlayPadding + validationErrorOverlayExtraPadding
    val bottomContentPadding = navBottom + bottomOverlayPadding
    val density = LocalDensity.current
    val topAlphaMultiplier by remember(scrollState) {
        derivedStateOf {
            if (scrollState.value <= 0) {
                0f
            } else {
                val offsetPx = scrollState.value.toFloat()
                val thresholdPx = with(density) { 24.dp.toPx() }
                (offsetPx / thresholdPx).coerceIn(0f, 1f)
            }
        }
    }
    val fullBleedBlurModifier =
        LocalProgressiveBlurStyle.current?.let { blurStyle ->
            Modifier.progressiveBlurFullBleedLayer(blurStyle, topAlphaMultiplier = topAlphaMultiplier)
        } ?: Modifier
    val alertColors = ruleAlertColors(isErrorSeverity = alertIsErrorSeverity)
    val warningColors = ruleWarningColors()
    val blockingHighlightColor = MaterialTheme.colorScheme.error
    val warningHighlightColor = warningColors.accent
    val sourceAndDestinationSame =
        state.destinationFolderPath.isNotBlank() &&
            state.sourceFolderPaths.any { sourcePath -> sourcePath == state.destinationFolderPath }
    val nameHasError = showValidationErrors && state.name.isBlank()
    val fileTypesHaveError = showValidationErrors && state.fileExtensions.isEmpty()
    val sourceFoldersHaveBlockingError =
        showValidationErrors && (state.sourceFolderPaths.isEmpty() || sourceAndDestinationSame)
    val destinationFolderHasBlockingError =
        showValidationErrors && (state.destinationFolderPath.isBlank() || sourceAndDestinationSame)
    val sourceFolderAccessIsWarningOnly = folderAccessAssessment.sourceIssuesAllSuppressible
    val sourceFolderAccessHighlightColor =
        if (sourceFolderAccessIsWarningOnly) {
            warningHighlightColor
        } else {
            blockingHighlightColor
        }
    val sourceFoldersHighlightColor =
        when {
            sourceFoldersHaveBlockingError -> blockingHighlightColor
            state.inaccessibleSourceIssues.isNotEmpty() -> sourceFolderAccessHighlightColor
            else -> null
        }
    val destinationFolderHighlightColor =
        when {
            destinationFolderHasBlockingError -> blockingHighlightColor
            state.destinationFolderAccessIssue != null -> blockingHighlightColor
            else -> null
        }
    val scheduleHasError = showValidationErrors && isScheduleInvalid(state.schedule)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .then(
                    if (LocalUseGradientBackground.current) {
                        Modifier
                    } else {
                        Modifier.background(MaterialTheme.colorScheme.background)
                    },
                ),
    ) {
        if (state.isLoading) {
            LoadingIndicator(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(48.dp),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(fullBleedBlurModifier),
            ) {
                if (!LocalUseGradientBackground.current) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                }
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .imePadding()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Spacer(Modifier.height(topContentPadding))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TooltipBox(
                            positionProvider =
                                TooltipDefaults.rememberTooltipPositionProvider(
                                    TooltipAnchorPosition.Above,
                                ),
                            tooltip = {
                                PlainTooltip {
                                    CenteredTooltipText(stringResource(R.string.rule_icon_picker_cd))
                                }
                            },
                            state = rememberTooltipState(),
                        ) {
                            FilePipeFilledTonalIconButton(
                                onClick = { if (!isReadOnly) showRuleIconSheet = true },
                                enabled = !isReadOnly,
                                modifier =
                                    Modifier
                                        .padding(top = 4.dp)
                                        .size(56.dp),
                                shape = compactControlShape,
                                colors = cardFilledTonalIconButtonColors(),
                            ) {
                                RuleIconOrEmoji(
                                    iconEmoji = state.iconEmoji,
                                    icon = state.icon,
                                    vectorSize = 34.dp,
                                    emojiFontSize = 32.sp,
                                    tint = if (isReadOnly) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f) else MaterialTheme.colorScheme.primary,
                                    contentDescription = stringResource(R.string.rule_icon_picker_cd),
                                    modifier = Modifier,
                                )
                            }
                        }
                        OutlinedTextField(
                            value = state.name,
                            onValueChange = viewModel::setName,
                            label = { Text(stringResource(R.string.rule_name_label)) },
                            placeholder = { Text(stringResource(R.string.rule_name_placeholder)) },
                            singleLine = true,
                            enabled = !isReadOnly,
                            isError = nameHasError,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .focusProperties {
                                        canFocus = ruleNameCanFocus && !isReadOnly
                                    },
                        )
                    }

                    RuleSectionCard(
                        title = stringResource(R.string.rule_section_extensions_title),
                        subtitle = stringResource(R.string.rule_section_extensions_subtitle),
                        iconName = "extension",
                        highlightColor = if (fileTypesHaveError) blockingHighlightColor else null,
                    ) {
                        FileExtensionChips(
                            extensions = state.fileExtensions,
                            onAdd = viewModel::addExtension,
                            onRemove = viewModel::removeExtension,
                            onUseTemplate = { showTemplateSheet = true },
                            enabled = !isReadOnly,
                        )
                    }

                    RuleSectionCard(
                        title = stringResource(R.string.source_folders_label),
                        subtitle = stringResource(R.string.rule_section_source_subtitle),
                        iconName = "search",
                        highlightColor = sourceFoldersHighlightColor,
                    ) {
                        state.sourceFolderPaths.forEach { path ->
                            key(path) {
                                val isSourceBookmarked = path in bookmarkedFolders
                                val sourceNeedsAccess = path in state.inaccessibleSourceIssues
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text =
                                            buildString {
                                                if (sourceNeedsAccess) {
                                                    append(folderAccessIssueEmojiPrefix(path, state.folderAccessMode))
                                                }
                                                append(displayPath(path, internalStorageDisplayName))
                                            },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color =
                                            if (sourceNeedsAccess) {
                                                sourceFolderAccessHighlightColor
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .tapSoundClickable(enabled = !isReadOnly) {
                                                    launchFolderPicker(FolderPickIntent.ReplaceSource(path), path)
                                                },
                                    )
                                    if (!isReadOnly) {
                                        TooltipBox(
                                            positionProvider =
                                                TooltipDefaults.rememberTooltipPositionProvider(
                                                    TooltipAnchorPosition.Above,
                                                ),
                                            tooltip = {
                                                PlainTooltip {
                                                    CenteredTooltipText(stringResource(R.string.bookmark_toggle_cd))
                                                }
                                            },
                                            state = rememberTooltipState(),
                                        ) {
                                            FilePipeIconButton(onClick = { viewModel.toggleBookmark(path) }) {
                                                FilePipeMaterialRoundedSymbol(
                                                    name = "bookmark",
                                                    contentDescription = stringResource(R.string.bookmark_toggle_cd),
                                                    size = 22.dp,
                                                    filled = isSourceBookmarked,
                                                    tint =
                                                        if (isSourceBookmarked) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        },
                                                )
                                            }
                                        }
                                        TooltipBox(
                                            positionProvider =
                                                TooltipDefaults.rememberTooltipPositionProvider(
                                                    TooltipAnchorPosition.Above,
                                                ),
                                            tooltip = {
                                                PlainTooltip {
                                                    CenteredTooltipText(stringResource(R.string.schedule_remove_short))
                                                }
                                            },
                                            state = rememberTooltipState(),
                                        ) {
                                            FilePipeIconButton(onClick = { viewModel.removeSourceFolder(path) }) {
                                                FilePipeMaterialRoundedSymbol(
                                                    name = "close",
                                                    contentDescription = stringResource(R.string.schedule_remove_short),
                                                    size = 18.dp,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (!isReadOnly) {
                            val unusedBookmarks =
                                bookmarkedFolders.filter {
                                    it.startsWith("content://") && it !in state.sourceFolderPaths
                                }
                            var bookmarkDropdownExpanded by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FolderPickerButton(
                                    label = stringResource(R.string.add_source_folder),
                                    onClick = { launchFolderPicker(FolderPickIntent.AddSource, null) },
                                    modifier = Modifier.weight(1f),
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    FilePipeOutlinedButton(
                                        onClick = { if (unusedBookmarks.isNotEmpty()) bookmarkDropdownExpanded = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = compactControlShape,
                                        enabled = unusedBookmarks.isNotEmpty(),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    ) {
                                        FilePipeMaterialRoundedSymbol(
                                            name = "bookmark",
                                            contentDescription = null,
                                            size = 18.dp,
                                            filled = false,
                                        )
                                        Text(
                                            text = "  ${stringResource(R.string.bookmarks_choose)}",
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = bookmarkDropdownExpanded,
                                        onDismissRequest = { bookmarkDropdownExpanded = false },
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    ) {
                                        unusedBookmarks.forEach { bookmarkPath ->
                                            FilePipeDropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = displayPath(bookmarkPath, internalStorageDisplayName),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.addSourceFolder(bookmarkPath)
                                                    bookmarkDropdownExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            if (state.folderAccessMode.usesAllFilesPaths() &&
                                !state.allFilesAccessGranted
                            ) {
                                FilePipeTextButton(
                                    onClick = {
                                        val allFilesIntent =
                                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                data = "package:${context.packageName}".toUri()
                                            }
                                        context.startActivity(allFilesIntent)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.rule_open_all_files_settings))
                                }
                            }
                        }
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.rule_scan_subdirs_label),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    ToggleLabelHelpDropdown(
                                        tipText = stringResource(R.string.rule_scan_subdirs_support),
                                        contentDescription = stringResource(R.string.rule_toggle_tip_show_help),
                                    )
                                }
                                FilePipeSwitch(
                                    checked = state.scanSubdirectories,
                                    onCheckedChange = viewModel::setScanSubdirectories,
                                    enabled = !isReadOnly,
                                )
                            }
                        }
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.rule_recreate_subfolders_label),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    ToggleLabelHelpDropdown(
                                        tipText = stringResource(R.string.rule_recreate_subfolders_support),
                                        contentDescription = stringResource(R.string.rule_toggle_tip_show_help),
                                    )
                                }
                                FilePipeSwitch(
                                    checked = state.scanSubdirectories && state.recreateDestinationSubfolders,
                                    onCheckedChange = viewModel::setRecreateDestinationSubfolders,
                                    enabled = state.scanSubdirectories && !isReadOnly,
                                )
                            }
                        }
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.rule_suppress_missing_source_card_label),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    ToggleLabelHelpDropdown(
                                        tipText = stringResource(R.string.rule_suppress_missing_source_card_support),
                                        contentDescription = stringResource(R.string.rule_toggle_tip_show_help),
                                    )
                                }
                                FilePipeSwitch(
                                    checked = state.suppressMissingSourceFolderCardWarning,
                                    onCheckedChange = viewModel::setSuppressMissingSourceFolderCardWarning,
                                    enabled = !isReadOnly,
                                )
                            }
                        }
                    }

                    RuleSectionCard(
                        title = stringResource(R.string.destination_label),
                        subtitle = stringResource(R.string.rule_section_destination_subtitle),
                        iconName = "folder_special",
                        highlightColor = destinationFolderHighlightColor,
                    ) {
                        if (state.destinationFolderPath.isNotBlank()) {
                            val isDestBookmarked = state.destinationFolderPath in bookmarkedFolders
                            val destinationAccessIssue = state.destinationFolderAccessIssue
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text =
                                        buildString {
                                            if (destinationAccessIssue != null) {
                                                append(
                                                    folderAccessIssueEmojiPrefix(
                                                        state.destinationFolderPath,
                                                        state.folderAccessMode,
                                                    ),
                                                )
                                            }
                                            append(displayPath(state.destinationFolderPath, internalStorageDisplayName))
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color =
                                        if (destinationAccessIssue != null) {
                                            blockingHighlightColor
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .tapSoundClickable(enabled = !isReadOnly) {
                                                launchFolderPicker(
                                                    FolderPickIntent.SetDestination,
                                                    state.destinationFolderPath,
                                                )
                                            },
                                )
                                if (!isReadOnly) {
                                    TooltipBox(
                                        positionProvider =
                                            TooltipDefaults.rememberTooltipPositionProvider(
                                                TooltipAnchorPosition.Above,
                                            ),
                                        tooltip = {
                                            PlainTooltip {
                                                CenteredTooltipText(stringResource(R.string.bookmark_toggle_cd))
                                            }
                                        },
                                        state = rememberTooltipState(),
                                    ) {
                                        FilePipeIconButton(onClick = { viewModel.toggleBookmark(state.destinationFolderPath) }) {
                                            FilePipeMaterialRoundedSymbol(
                                                name = "bookmark",
                                                contentDescription = stringResource(R.string.bookmark_toggle_cd),
                                                size = 22.dp,
                                                filled = isDestBookmarked,
                                                tint =
                                                    if (isDestBookmarked) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (!isReadOnly) {
                            val unusedDestBookmarks =
                                bookmarkedFolders.filter {
                                    it.startsWith("content://") && it != state.destinationFolderPath
                                }
                            var destBookmarkDropdownExpanded by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FolderPickerButton(
                                    label =
                                        if (state.destinationFolderPath.isBlank()) {
                                            stringResource(R.string.pick_folder)
                                        } else {
                                            stringResource(R.string.change_destination)
                                        },
                                    onClick = {
                                        launchFolderPicker(
                                            FolderPickIntent.SetDestination,
                                            state.destinationFolderPath.takeIf { it.isNotBlank() },
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    FilePipeOutlinedButton(
                                        onClick = { if (unusedDestBookmarks.isNotEmpty()) destBookmarkDropdownExpanded = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = compactControlShape,
                                        enabled = unusedDestBookmarks.isNotEmpty(),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    ) {
                                        FilePipeMaterialRoundedSymbol(
                                            name = "bookmark",
                                            contentDescription = null,
                                            size = 18.dp,
                                            filled = false,
                                        )
                                        Text(
                                            text = "  ${stringResource(R.string.bookmarks_choose)}",
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = destBookmarkDropdownExpanded,
                                        onDismissRequest = { destBookmarkDropdownExpanded = false },
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    ) {
                                        unusedDestBookmarks.forEach { bookmarkPath ->
                                            FilePipeDropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = displayPath(bookmarkPath, internalStorageDisplayName),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.setDestination(bookmarkPath)
                                                    destBookmarkDropdownExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            if (state.folderAccessMode.usesAllFilesPaths() &&
                                !state.allFilesAccessGranted
                            ) {
                                FilePipeTextButton(
                                    onClick = {
                                        val allFilesIntent =
                                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                data = "package:${context.packageName}".toUri()
                                            }
                                        context.startActivity(allFilesIntent)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.rule_open_all_files_settings))
                                }
                            }
                        }
                    }

                    RuleSectionCard(
                        title = stringResource(R.string.rule_section_operation_title),
                        subtitle = null,
                        iconName = "content_copy",
                    ) {
                        Text(
                            text = stringResource(R.string.rule_operation_mode_label),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val operationModes = OperationMode.entries
                        val operationLabels =
                            operationModes.map { mode ->
                                when (mode) {
                                    OperationMode.MOVE -> stringResource(R.string.operation_move)
                                    OperationMode.COPY -> stringResource(R.string.operation_copy)
                                }
                            }
                        val operationShapes =
                            operationModes.mapIndexed { index, _ ->
                                when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    operationModes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                }
                            }
                        ButtonGroup(
                            modifier = Modifier.fillMaxWidth(),
                            overflowIndicator = { menuState ->
                                ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                            },
                        ) {
                            operationModes.forEachIndexed { index, mode ->
                                val label = operationLabels[index]
                                customItem(
                                    buttonGroupContent = {
                                        FilePipeToggleButton(
                                            checked = state.operationMode == mode,
                                            onCheckedChange = { checked -> if (checked) viewModel.setOperationMode(mode) },
                                            enabled = !isReadOnly,
                                            shapes = operationShapes[index],
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(label)
                                        }
                                    },
                                    menuContent = { menuState ->
                                        FilePipeDropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                if (!isReadOnly) {
                                                    viewModel.setOperationMode(mode)
                                                    menuState.dismiss()
                                                }
                                            },
                                        )
                                    },
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = stringResource(R.string.rule_conflict_policy_label),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val conflictPolicies = ConflictPolicy.entries
                        val conflictLabels =
                            conflictPolicies.map { policy ->
                                when (policy) {
                                    ConflictPolicy.SKIP -> stringResource(R.string.conflict_skip)
                                    ConflictPolicy.OVERWRITE -> stringResource(R.string.conflict_overwrite)
                                    ConflictPolicy.RENAME_SUFFIX -> stringResource(R.string.conflict_rename)
                                }
                            }
                        val conflictShapes =
                            conflictPolicies.mapIndexed { index, _ ->
                                when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    conflictPolicies.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                }
                            }
                        ButtonGroup(
                            modifier = Modifier.fillMaxWidth(),
                            overflowIndicator = { menuState ->
                                ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                            },
                        ) {
                            conflictPolicies.forEachIndexed { index, policy ->
                                val label = conflictLabels[index]
                                customItem(
                                    buttonGroupContent = {
                                        FilePipeToggleButton(
                                            checked = state.conflictPolicy == policy,
                                            onCheckedChange = { checked -> if (checked) viewModel.setConflictPolicy(policy) },
                                            enabled = !isReadOnly,
                                            shapes = conflictShapes[index],
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(label)
                                        }
                                    },
                                    menuContent = { menuState ->
                                        FilePipeDropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                if (!isReadOnly) {
                                                    viewModel.setConflictPolicy(policy)
                                                    menuState.dismiss()
                                                }
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }

                    RuleSectionCard(
                        title = stringResource(R.string.schedule_label),
                        subtitle = stringResource(R.string.rule_section_schedule_subtitle),
                        iconName = "calendar_clock",
                        highlightColor = if (scheduleHasError) blockingHighlightColor else null,
                    ) {
                        val schedule = state.schedule
                        if (schedule != null) {
                            val scheduleText = schedule.toReadableString(context)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilePipeOutlinedButton(
                                    onClick = { showScheduleDialog = true },
                                    enabled = !isReadOnly,
                                    modifier = Modifier.weight(1f),
                                    shape = compactControlShape,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                ) {
                                    FilePipeMaterialRoundedSymbol(
                                        name = "calendar_month",
                                        contentDescription = null,
                                        size = 18.dp,
                                    )
                                    Text(
                                        text = "  $scheduleText",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (!isReadOnly) {
                                    TooltipBox(
                                        positionProvider =
                                            TooltipDefaults.rememberTooltipPositionProvider(
                                                TooltipAnchorPosition.Above,
                                            ),
                                        tooltip = {
                                            PlainTooltip {
                                                CenteredTooltipText(stringResource(R.string.remove_schedule))
                                            }
                                        },
                                        state = rememberTooltipState(),
                                    ) {
                                        FilePipeIconButton(onClick = { viewModel.setSchedule(null) }) {
                                            FilePipeMaterialRoundedSymbol(
                                                name = "close",
                                                contentDescription = stringResource(R.string.remove_schedule),
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            FilePipeOutlinedButton(
                                onClick = { showScheduleDialog = true },
                                enabled = !isReadOnly,
                                modifier = Modifier.fillMaxWidth(),
                                shape = compactControlShape,
                            ) {
                                FilePipeMaterialRoundedSymbol(
                                    name = "calendar_month",
                                    contentDescription = null,
                                    size = 18.dp,
                                )
                                Text(text = "  ${stringResource(R.string.add_schedule_chip)}")
                            }
                        }
                    }

                    // Advanced filters section
                    val hasAdvancedFilters =
                        state.filenamePattern.isNotBlank() ||
                            state.minFileSizeMb.isNotBlank() ||
                            state.maxFileSizeMb.isNotBlank() ||
                            state.minAgeDays.isNotBlank() ||
                            state.maxAgeDays.isNotBlank() ||
                            state.excludePatternsText.isNotBlank()

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = elevatedCardColors(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            val advancedHeaderInteractionSource = remember { MutableInteractionSource() }
                            val advancedChevronRotation by animateFloatAsState(
                                targetValue = if (advancedExpanded) 180f else 0f,
                                animationSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<Float>()),
                                label = "advanced_filters_chevron_rotation",
                            )
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .tapSoundClickable(
                                            onClick = { advancedExpanded = !advancedExpanded },
                                            interactionSource = advancedHeaderInteractionSource,
                                            indication = null,
                                        ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                FilePipeMaterialRoundedSymbol(
                                    name = "tune",
                                    contentDescription = null,
                                    size = 24.dp,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.advanced_section_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color =
                                            if (hasAdvancedFilters) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                    )
                                    Text(
                                        text =
                                            if (hasAdvancedFilters && !advancedExpanded) {
                                                stringResource(R.string.advanced_section_filters_active)
                                            } else {
                                                stringResource(R.string.advanced_section_subtitle)
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                FilePipeMaterialRoundedSymbol(
                                    name = "expand_more",
                                    contentDescription = null,
                                    modifier = Modifier.graphicsLayer { rotationZ = advancedChevronRotation },
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            val advancedExpandSpec =
                                reducedMotionAwareSpec(
                                    MaterialTheme.motionScheme.slowSpatialSpec<androidx.compose.ui.unit.IntSize>(),
                                )
                            val advancedFadeInSpec =
                                reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
                            val advancedFadeOutSpec =
                                reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
                            AnimatedVisibility(
                                visible = advancedExpanded,
                                enter =
                                    expandVertically(
                                        animationSpec = advancedExpandSpec,
                                        expandFrom = Alignment.Top,
                                    ) + fadeIn(advancedFadeInSpec),
                                exit =
                                    shrinkVertically(
                                        animationSpec = advancedExpandSpec,
                                        shrinkTowards = Alignment.Top,
                                    ) + fadeOut(advancedFadeOutSpec),
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(top = 16.dp),
                                ) {
                                    OutlinedTextField(
                                        value = state.filenamePattern,
                                        onValueChange = viewModel::setFilenamePattern,
                                        label = { Text(stringResource(R.string.advanced_filename_pattern_label)) },
                                        placeholder = { Text(stringResource(R.string.advanced_filename_pattern_placeholder)) },
                                        singleLine = true,
                                        enabled = !isReadOnly,
                                        modifier = Modifier.fillMaxWidth(),
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedTextField(
                                            value = state.minFileSizeMb,
                                            onValueChange = viewModel::setMinFileSizeMb,
                                            label = { Text(stringResource(R.string.advanced_min_size_label)) },
                                            singleLine = true,
                                            enabled = !isReadOnly,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                        )
                                        OutlinedTextField(
                                            value = state.maxFileSizeMb,
                                            onValueChange = viewModel::setMaxFileSizeMb,
                                            label = { Text(stringResource(R.string.advanced_max_size_label)) },
                                            singleLine = true,
                                            enabled = !isReadOnly,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedTextField(
                                            value = state.minAgeDays,
                                            onValueChange = viewModel::setMinAgeDays,
                                            label = { Text(stringResource(R.string.advanced_min_age_label)) },
                                            singleLine = true,
                                            enabled = !isReadOnly,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                        )
                                        OutlinedTextField(
                                            value = state.maxAgeDays,
                                            onValueChange = viewModel::setMaxAgeDays,
                                            label = { Text(stringResource(R.string.advanced_max_age_label)) },
                                            singleLine = true,
                                            enabled = !isReadOnly,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                        )
                                    }

                                    OutlinedTextField(
                                        value = state.excludePatternsText,
                                        onValueChange = viewModel::setExcludePatternsText,
                                        label = { Text(stringResource(R.string.advanced_exclude_patterns_label)) },
                                        placeholder = { Text(stringResource(R.string.advanced_exclude_placeholder)) },
                                        supportingText = { Text(stringResource(R.string.advanced_csv_hint)) },
                                        singleLine = true,
                                        enabled = !isReadOnly,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }

                    if (isReadOnly && !isPortrait) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            FilePipeButton(
                                onClick = { viewModel.restoreRule() },
                                modifier = Modifier.weight(1f),
                                shape = pillShape,
                            ) {
                                Text(stringResource(R.string.restore))
                            }
                            FilePipeOutlinedButton(
                                onClick = { showDeleteForeverConfirm = true },
                                modifier = Modifier.weight(1f),
                                shape = pillShape,
                                colors =
                                    ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error,
                                    ),
                                border =
                                    BorderStroke(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                    ),
                            ) {
                                Text(stringResource(R.string.delete_forever))
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp + bottomContentPadding))
                }
            }
        }

        TopAppBar(
            modifier = Modifier.align(Alignment.TopCenter),
            title = {
                Text(
                    if (viewModel.isNewRule) {
                        stringResource(R.string.new_rule)
                    } else {
                        stringResource(R.string.rule_details)
                    },
                )
            },
            navigationIcon = {
                if (showNavigateBack) {
                    TooltipBox(
                        positionProvider =
                            TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Above,
                            ),
                        tooltip = {
                            PlainTooltip {
                                CenteredTooltipText(stringResource(R.string.nav_back))
                            }
                        },
                        state = rememberTooltipState(),
                    ) {
                        FilePipeIconButton(onClick = { tryNavigateBack() }) {
                            FilePipeMaterialRoundedSymbol(
                                name = "arrow_back",
                                contentDescription = stringResource(R.string.nav_back),
                                autoMirror = true,
                            )
                        }
                    }
                }
            },
            actions = {
                if (!isReadOnly) {
                    TooltipBox(
                        positionProvider =
                            TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Above,
                            ),
                        tooltip = {
                            PlainTooltip {
                                CenteredTooltipText(stringResource(R.string.preview_rule))
                            }
                        },
                        state = rememberTooltipState(),
                    ) {
                        FilePipeIconButton(
                            onClick = { viewModel.loadPreview() },
                            enabled =
                                state.sourceFolderPaths.isNotEmpty() &&
                                    state.destinationFolderPath.isNotBlank() &&
                                    state.fileExtensions.isNotEmpty(),
                        ) {
                            FilePipeMaterialRoundedSymbol(
                                name = "visibility",
                                contentDescription = stringResource(R.string.preview_rule),
                            )
                        }
                    }
                }
            },
            colors = gradientOverlayTopAppBarColors(),
        )

        if (!state.isLoading && (showBottomBar || showBottomActions || showReadOnlyBottomActions)) {
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                color = Color.Transparent,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val bottomBarSpatialSpec =
                        reducedMotionAwareSpec(
                            MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntSize>(),
                        )
                    val bottomBarFadeInSpec =
                        reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
                    val bottomBarFadeOutSpec =
                        reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
                    AnimatedVisibility(
                        visible = showBottomBar,
                        enter =
                            reducedMotionEnterTransition(
                                fadeIn(animationSpec = bottomBarFadeInSpec) +
                                    expandVertically(
                                        animationSpec = bottomBarSpatialSpec,
                                        clip = false,
                                    ),
                            ),
                        exit =
                            reducedMotionExitTransition(
                                fadeOut(animationSpec = bottomBarFadeOutSpec) +
                                    shrinkVertically(
                                        animationSpec = bottomBarSpatialSpec,
                                        clip = false,
                                    ),
                            ),
                    ) {
                        RuleValidationErrorBar(
                            messages = bottomBarMessages,
                            isErrorSeverity = alertIsErrorSeverity,
                            onOpenFaq = onOpenFaq,
                            onDismiss = { dismissedBottomBarKey = bottomBarKey },
                        )
                    }

                    AnimatedVisibility(
                        visible = showReadOnlyBottomActions,
                        enter =
                            reducedMotionEnterTransition(
                                fadeIn(animationSpec = bottomBarFadeInSpec) +
                                    expandVertically(
                                        animationSpec = bottomBarSpatialSpec,
                                        clip = false,
                                    ),
                            ),
                        exit =
                            reducedMotionExitTransition(
                                fadeOut(animationSpec = bottomBarFadeOutSpec) +
                                    shrinkVertically(
                                        animationSpec = bottomBarSpatialSpec,
                                        clip = false,
                                    ),
                            ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            FilePipeButton(
                                onClick = { viewModel.restoreRule() },
                                modifier = Modifier.weight(1f),
                                shape = pillShape,
                            ) {
                                Text(stringResource(R.string.restore))
                            }
                            FilePipeOutlinedButton(
                                onClick = { showDeleteForeverConfirm = true },
                                modifier = Modifier.weight(1f),
                                shape = pillShape,
                                colors =
                                    ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error,
                                    ),
                                border =
                                    BorderStroke(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                    ),
                            ) {
                                Text(stringResource(R.string.delete_forever))
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showBottomActions,
                        enter =
                            reducedMotionEnterTransition(
                                fadeIn(animationSpec = bottomBarFadeInSpec) +
                                    expandVertically(
                                        animationSpec = bottomBarSpatialSpec,
                                        clip = false,
                                    ),
                            ),
                        exit =
                            reducedMotionExitTransition(
                                fadeOut(animationSpec = bottomBarFadeOutSpec) +
                                    shrinkVertically(
                                        animationSpec = bottomBarSpatialSpec,
                                        clip = false,
                                    ),
                            ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            FilePipeOutlinedButton(
                                onClick = { tryNavigateBack() },
                                modifier = Modifier.weight(1f),
                                shape = pillShape,
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                            FilePipeButton(
                                onClick = {
                                    if (state.errors.isNotEmpty()) dismissedBottomBarKey = null
                                    viewModel.save()
                                },
                                modifier = Modifier.weight(1f),
                                shape = pillShape,
                            ) {
                                Text(stringResource(R.string.save))
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom =
                            if (state.isLoading) {
                                navBottom + 16.dp
                            } else {
                                navBottom + bottomOverlayPadding
                            },
                    ),
        )
    }

    if (showDeleteForeverConfirm) {
        FilePipeConfirmDialog(
            title = stringResource(R.string.history_trash_delete_forever_confirm_title),
            text = stringResource(R.string.history_trash_delete_forever_confirm_message, state.name),
            confirmLabel = stringResource(R.string.delete_forever),
            onConfirm = {
                showDeleteForeverConfirm = false
                viewModel.deleteRuleForever()
            },
            onDismiss = { showDeleteForeverConfirm = false },
            destructive = true,
        )
    }

    if (pendingFilesystemFolderPick != null) {
        val pickRequest = pendingFilesystemFolderPick!!
        val pickedIntent = pickRequest.intent
        val folderPickerSheetState =
            rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            )
        LaunchedEffect(pickRequest) {
            folderPickerSheetState.expand()
        }
        val fontScale = LocalDensity.current.fontScale
        val showTitle = fontScale <= 1.15f && !isSmallLandscape()
        AppBottomSheet(
            title = stringResource(R.string.filesystem_folder_picker_title),
            showTitleBar = showTitle,
            onDismiss = { pendingFilesystemFolderPick = null },
            sheetState = folderPickerSheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrollable = false,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        ) {
            FilesystemFolderPickerSheetContent(
                initialDirectory = pickRequest.startDirectory,
                onDismiss = { pendingFilesystemFolderPick = null },
                onFolderChosen = { absolutePath ->
                    when (pickedIntent) {
                        FolderPickIntent.AddSource -> {
                            viewModel.addSourceFolder(absolutePath)
                        }

                        is FolderPickIntent.ReplaceSource -> {
                            viewModel.replaceSourceFolder(pickedIntent.previousPath, absolutePath)
                        }

                        FolderPickIntent.SetDestination -> {
                            viewModel.setDestination(absolutePath)
                        }
                    }
                    pendingFilesystemFolderPick = null
                    viewModel.refreshFolderAccessAfterPermissionChange()
                },
            )
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier =
                Modifier
                    .widthIn(min = 320.dp, max = 560.dp)
                    .fillMaxWidth(0.9f),
            title = { Text(stringResource(R.string.unsaved_changes_title)) },
            text = { Text(stringResource(R.string.unsaved_changes_message)) },
            confirmButton = {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    // Destructive: low-emphasis, error-colored, kept leftmost — farthest from Save.
                    FilePipeTextButton(
                        onClick = {
                            showDiscardDialog = false
                            viewModel.discardChanges()
                            onNavigateBack()
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.discard_changes),
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                        )
                    }
                    FilePipeTextButton(onClick = { showDiscardDialog = false }) {
                        Text(
                            text = stringResource(R.string.keep_editing),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                        )
                    }
                    // Recommended, non-destructive action: filled emphasis, rightmost.
                    FilePipeButton(
                        onClick = {
                            showDiscardDialog = false
                            if (state.errors.isNotEmpty()) dismissedBottomBarKey = null
                            viewModel.save()
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.save_and_exit),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                        )
                    }
                }
            },
        )
    }

    if (showScheduleDialog) {
        ScheduleDialog(
            initialSchedule = state.schedule,
            onDismiss = { showScheduleDialog = false },
            onSave = { schedule ->
                viewModel.setSchedule(schedule)
                showScheduleDialog = false
            },
        )
    }

    if (showRuleIconSheet) {
        val ruleIconSheetState =
            rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            )
        LaunchedEffect(showRuleIconSheet) {
            if (showRuleIconSheet) {
                customEmojiDraft = state.iconEmoji.orEmpty()
            }
        }
        AppBottomSheet(
            title = stringResource(R.string.rule_icon_sheet_title),
            onDismiss = { showRuleIconSheet = false },
            sheetState = ruleIconSheetState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.rule_icon_preset_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    val ruleIconGridCell = (maxWidth - 48.dp) / 7f
                    FlowRow(
                        maxItemsInEachRow = 7,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RuleIcon.entries.forEach { iconOption ->
                            FilePipeFilledTonalIconButton(
                                onClick = {
                                    viewModel.setIcon(iconOption)
                                    showRuleIconSheet = false
                                },
                                modifier = Modifier.size(ruleIconGridCell),
                                shape = compactControlShape,
                                colors = cardFilledTonalIconButtonColors(),
                            ) {
                                FilePipeMaterialRoundedSymbol(
                                    name = iconOption.materialSymbolName(),
                                    contentDescription = ruleIconOptionLabel(iconOption),
                                    size = 34.dp,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                } // close BoxWithConstraints for icon section
                Text(
                    text = stringResource(R.string.rule_icon_emoji_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                val customEmojiSlotDescription = stringResource(R.string.rule_icon_custom_slot_cd)
                val emojiTextStyle =
                    TextStyle(
                        fontSize = 28.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary,
                    )
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    val emojiCellSize = (maxWidth - 48.dp) / 7f
                    FlowRow(
                        maxItemsInEachRow = 7,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RuleIconEmojiPresets.forEach { emojiPreset ->
                            FilePipeSurface(
                                onClick = {
                                    viewModel.setIconEmoji(emojiPreset)
                                    showRuleIconSheet = false
                                },
                                modifier = Modifier.size(emojiCellSize),
                                shape = compactControlShape,
                                color = cardIconContainerColor(),
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(text = emojiPreset, style = emojiTextStyle)
                                }
                            }
                        }
                        Surface(
                            modifier =
                                Modifier
                                    .size(emojiCellSize)
                                    .semantics { contentDescription = customEmojiSlotDescription },
                            shape = compactControlShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) {
                            BasicTextField(
                                value = customEmojiDraft,
                                onValueChange = { newValue ->
                                    if (newValue.isEmpty()) {
                                        customEmojiDraft = ""
                                    } else {
                                        val boundary = java.text.BreakIterator.getCharacterInstance()
                                        boundary.setText(newValue)
                                        val start = boundary.first()
                                        val end = boundary.next()
                                        customEmojiDraft = newValue.substring(start, end)
                                    }
                                },
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp, vertical = 10.dp),
                                textStyle = emojiTextStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurfaceVariant),
                                singleLine = true,
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        innerTextField()
                                    }
                                },
                            )
                        }
                        val applyEmojiEnabled = customEmojiDraft.isNotBlank()
                        FilePipeSurface(
                            onClick = {
                                viewModel.setIconEmoji(customEmojiDraft)
                                showRuleIconSheet = false
                            },
                            enabled = applyEmojiEnabled,
                            modifier = Modifier.size(emojiCellSize),
                            shape = MaterialShapes.Cookie12Sided.toShape(),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                FilePipeMaterialRoundedSymbol(
                                    name = "check",
                                    contentDescription = stringResource(R.string.rule_icon_apply_emoji_cd),
                                    size = 28.dp,
                                )
                            }
                        }
                    }
                } // close BoxWithConstraints for emoji section
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Template picker
    if (showTemplateSheet) {
        AppBottomSheet(
            title = "",
            onDismiss = { showTemplateSheet = false },
            sheetState =
                rememberBottomSheetState(
                    initialValue = SheetValue.Hidden,
                    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
                ),
            showTitleBar = false,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            RuleTemplate.ALL.forEach { template ->
                FilePipeElevatedCard(
                    onClick = {
                        viewModel.applyTemplate(template)
                        showTemplateSheet = false
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = compactControlShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                FilePipeMaterialRoundedSymbol(
                                    name = template.suggestedIcon.materialSymbolName(),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    size = 32.dp,
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(template.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                template.extensions.map { it.removePrefix(".") }.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // Preview bottom sheet
    if (state.previewFiles != null || state.isPreviewLoading) {
        AppBottomSheet(
            title = stringResource(R.string.preview_title),
            onDismiss = { viewModel.dismissPreview() },
            sheetState = previewSheetState,
            scrollable = false,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        ) {
            Column {
                if (state.isPreviewLoading) {
                    LoadingIndicator(
                        modifier =
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(32.dp)
                                .size(48.dp),
                    )
                } else {
                    val files = state.previewFiles ?: emptyList()
                    if (files.isEmpty()) {
                        Text(
                            text = stringResource(R.string.preview_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    } else {
                        Text(
                            text =
                                pluralStringResource(
                                    when (state.operationMode) {
                                        OperationMode.MOVE -> R.plurals.preview_files_would_move
                                        OperationMode.COPY -> R.plurals.preview_files_would_copy
                                    },
                                    files.size,
                                    files.size,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        val groupedFiles =
                            files.groupBy { file ->
                                previewSourceFolderDisplayPath(
                                    sourcePath = file.sourcePath,
                                    fileName = file.fileName,
                                    internalStorageRootDisplayName = internalStorageDisplayName,
                                )
                            }
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            groupedFiles.forEach { (sourceFolder, sourceFiles) ->
                                item(key = "source_$sourceFolder") {
                                    Text(
                                        text = sourceFolder.trimEnd('/') + "/",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                items(
                                    items = sourceFiles,
                                    key = { file -> file.sourcePath },
                                ) { file ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Text(
                                                text = file.fileName,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            when {
                                                file.wouldSkip -> {
                                                    Text(
                                                        text = stringResource(R.string.preview_would_skip),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }

                                                file.wouldOverwrite -> {
                                                    Text(
                                                        text = stringResource(R.string.preview_would_overwrite),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }

                                                file.renamedTo != null -> {
                                                    Text(
                                                        text =
                                                            stringResource(
                                                                R.string.preview_destination_path,
                                                                displayPath(file.simulatedDestPath, internalStorageDisplayName),
                                                            ),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }
                                        val sizeKb = file.sizeBytes / 1024
                                        Text(
                                            text = if (sizeKb > 1024) "${sizeKb / 1024} MB" else "$sizeKb KB",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
