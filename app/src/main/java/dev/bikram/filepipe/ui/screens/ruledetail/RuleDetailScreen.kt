package dev.bikram.filepipe.ui.screens.ruledetail

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bikram.filepipe.R
import dev.bikram.filepipe.ui.modifiers.applyToFullBleedLayer
import dev.bikram.filepipe.ui.theme.LocalProgressiveBlurStyle
import dev.bikram.filepipe.ui.theme.LocalUseGradientBackground
import dev.bikram.filepipe.ui.theme.elevatedCardColors
import dev.bikram.filepipe.domain.model.ConflictPolicy
import dev.bikram.filepipe.domain.model.FolderAccessResult
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.RuleIcon
import dev.bikram.filepipe.domain.model.RuleTemplate
import dev.bikram.filepipe.domain.model.ScheduleType
import dev.bikram.filepipe.ui.components.FileExtensionChips
import dev.bikram.filepipe.ui.components.FilesystemFolderPickerSheetContent
import dev.bikram.filepipe.ui.components.FolderPickerButton
import dev.bikram.filepipe.ui.components.RuleIconEmojiPresets
import dev.bikram.filepipe.ui.components.RuleIconOrEmoji
import dev.bikram.filepipe.ui.components.ScheduleDialog
import dev.bikram.filepipe.ui.components.toImageVector
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import dev.bikram.filepipe.data.preferences.treatAsSafUi
import dev.bikram.filepipe.data.preferences.usesAllFilesPaths
import dev.bikram.filepipe.data.storage.normalizeFilesystemFolderPath
import dev.bikram.filepipe.data.storage.safTreeUriToPath
import dev.bikram.filepipe.ui.components.absoluteStoragePathToOpenTreeInitialUri
import dev.bikram.filepipe.ui.components.displayPath
import java.io.File
import dev.bikram.filepipe.ui.feedback.rememberPlayTapSound
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.ui.text.input.KeyboardType

private val SectionButtonShape = RoundedCornerShape(12.dp)

private sealed class FolderPickIntent {
    data object AddSource : FolderPickIntent()
    data class ReplaceSource(val previousPath: String) : FolderPickIntent()
    data object SetDestination : FolderPickIntent()
}

private data class PendingFilesystemFolderPick(
    val intent: FolderPickIntent,
    val startDirectory: String
)
private val PillShape = RoundedCornerShape(50)

/** ObtainX-style large faded icon at bottom-right of error cards (~alpha 28/255). */
private val RuleErrorCardWatermarkAlpha = 28f / 255f

@Composable
private fun RuleErrorAlertCard(
    title: String,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    titleTrailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val onErrorContainer = MaterialTheme.colorScheme.onErrorContainer
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SectionButtonShape,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = onErrorContainer
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PaddingValues(top = 8.dp, start = 12.dp, end = 12.dp, bottom = 12.dp)),
                verticalArrangement = verticalArrangement
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    titleTrailing?.invoke(this)
                }
                content()
            }
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 4.dp)
                    .size(52.dp),
                tint = onErrorContainer.copy(alpha = RuleErrorCardWatermarkAlpha)
            )
        }
    }
}

@Composable
private fun ruleIconOptionLabel(icon: RuleIcon): String = stringResource(
    when (icon) {
        RuleIcon.DEFAULT -> R.string.rule_icon_label_default
        RuleIcon.IMAGE -> R.string.rule_icon_label_image
        RuleIcon.SCREENSHOT -> R.string.rule_icon_label_screenshot
        RuleIcon.VIDEO -> R.string.rule_icon_label_video
        RuleIcon.MUSIC -> R.string.rule_icon_label_music
        RuleIcon.DOWNLOAD -> R.string.rule_icon_label_download
        RuleIcon.DOCUMENT -> R.string.rule_icon_label_document
        RuleIcon.INSTALLABLE -> R.string.rule_icon_label_installable
    }
)

@Composable
private fun RuleSectionCard(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    titleTrailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = elevatedCardColors()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                titleTrailing?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun RuleDetailScreen(
    onNavigateBack: () -> Unit,
    onOpenFaq: () -> Unit,
    viewModel: RuleDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showTemplateSheet by remember { mutableStateOf(viewModel.showInitialTemplatePicker) }
    var showRuleIconSheet by remember { mutableStateOf(false) }
    var customEmojiDraft by remember { mutableStateOf("") }
    val previewSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val playTap = rememberPlayTapSound()
    val bookmarkedFolders by viewModel.bookmarkedFolders.collectAsStateWithLifecycle()
    var advancedExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var pendingFolderPick by remember { mutableStateOf<FolderPickIntent?>(null) }
    var pendingFilesystemFolderPick by remember { mutableStateOf<PendingFilesystemFolderPick?>(null) }
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        val pending = pendingFolderPick
        if (uri == null) {
            pendingFolderPick = null
            return@rememberLauncherForActivityResult
        }
        // Persist the grant so it survives reboots
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val uriString = uri.toString()
        when (pending) {
            FolderPickIntent.AddSource -> viewModel.addSourceFolder(uriString)
            is FolderPickIntent.ReplaceSource ->
                viewModel.replaceSourceFolder(pending.previousPath, uriString)
            FolderPickIntent.SetDestination -> viewModel.setDestination(uriString)
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
            val pathFromSaf = runCatching { safTreeUriToPath(Uri.parse(initialPath)) }.getOrNull()
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

    fun launchFolderPicker(intent: FolderPickIntent, initialPath: String?) {
        val useFilesystemPicker =
            !state.folderAccessMode.treatAsSafUi() && state.allFilesAccessGranted
        if (useFilesystemPicker) {
            pendingFilesystemFolderPick = PendingFilesystemFolderPick(
                intent = intent,
                startDirectory = resolveFilesystemPickerStartDirectory(initialPath)
            )
            return
        }
        pendingFolderPick = intent
        folderPickerLauncher.launch(initialPath?.let { absoluteStoragePathToOpenTreeInitialUri(it) })
    }

    fun withTapSound(action: () -> Unit) {
        playTap()
        action()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshFolderAccessAfterPermissionChange()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onNavigateBack()
    }

    LaunchedEffect(state.removedRedundantFolders) {
        if (state.removedRedundantFolders.isNotEmpty()) {
            val names = state.removedRedundantFolders.joinToString(", ") { it.substringAfterLast('/') }
            snackbarHostState.showSnackbar(context.getString(R.string.rule_detail_redundant_subfolder_removed, names))
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

    BackHandler(onBack = ::tryNavigateBack)

    val scrollState = rememberScrollState()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topContentPadding = statusTop + 64.dp
    val bottomContentPadding = navBottom + 88.dp
    val fullBleedBlurModifier = LocalProgressiveBlurStyle.current?.applyToFullBleedLayer() ?: Modifier

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (LocalUseGradientBackground.current) Modifier
                else Modifier.background(MaterialTheme.colorScheme.background)
            )
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(fullBleedBlurModifier)
            ) {
                val scheme = MaterialTheme.colorScheme
                if (LocalUseGradientBackground.current) {
                    Box(
                        Modifier
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
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(scheme.background))
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
            Spacer(Modifier.height(topContentPadding))
            AnimatedVisibility(
                visible = state.errors.isNotEmpty(),
                enter = fadeIn() + expandVertically(clip = false),
                exit = fadeOut() + shrinkVertically(clip = false)
            ) {
                RuleErrorAlertCard(
                    title = stringResource(R.string.rule_detail_validation_errors_title),
                    titleTrailing = {
                        Text(
                            text = stringResource(R.string.rule_detail_open_faq),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clickable { withTapSound(onOpenFaq) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                ) {
                    Text(
                        text = stringResource(R.string.rule_detail_validation_errors_summary),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        state.errors.forEach { errorLine ->
                            Text(
                                text = "\u2022 $errorLine",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            val folderAccessIssues =
                state.inaccessibleSourceIssues.isNotEmpty() || state.destinationFolderAccessIssue != null
            if (folderAccessIssues) {
                val anySourceUnavailable =
                    state.inaccessibleSourceIssues.values.any { it == FolderAccessResult.Unavailable }
                val anySourcePermission =
                    state.inaccessibleSourceIssues.values.any { it == FolderAccessResult.PermissionDenied }
                val destUnavailable = state.destinationFolderAccessIssue == FolderAccessResult.Unavailable
                val destPermission = state.destinationFolderAccessIssue == FolderAccessResult.PermissionDenied
                val showUnavailableHint = anySourceUnavailable || destUnavailable
                val showPermissionHint = anySourcePermission || destPermission
                RuleErrorAlertCard(
                    title = stringResource(R.string.rule_detail_folder_access_title),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    titleTrailing = {
                        Text(
                            text = stringResource(R.string.rule_detail_open_faq),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clickable { withTapSound(onOpenFaq) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                ) {
                    val usesFilesystemPaths =
                        state.sourceFolderPaths.any { path -> !path.startsWith("content://") } ||
                            (state.destinationFolderPath.isNotBlank() &&
                                !state.destinationFolderPath.startsWith("content://"))
                    val summaryRes = when {
                        showPermissionHint && usesFilesystemPaths ->
                            R.string.rule_detail_folder_access_summary_filesystem
                        showPermissionHint ->
                            R.string.rule_detail_folder_access_summary_permission
                        else ->
                            R.string.rule_detail_folder_access_summary_unavailable
                    }
                    Text(
                        text = stringResource(summaryRes),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalIconButton(
                    onClick = { withTapSound { showRuleIconSheet = true } },
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(56.dp),
                    shape = SectionButtonShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    RuleIconOrEmoji(
                        iconEmoji = state.iconEmoji,
                        icon = state.icon,
                        vectorSize = 34.dp,
                        emojiFontSize = 32.sp,
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = stringResource(R.string.rule_icon_picker_cd),
                        modifier = Modifier
                    )
                }
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text(stringResource(R.string.rule_name_label)) },
                    placeholder = { Text(stringResource(R.string.rule_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            RuleSectionCard(
                title = stringResource(R.string.rule_section_extensions_title),
                subtitle = stringResource(R.string.rule_section_extensions_subtitle),
                icon = Icons.Filled.Extension
            ) {
                FileExtensionChips(
                    extensions = state.fileExtensions,
                    onAdd = viewModel::addExtension,
                    onAddGroup = viewModel::addExtensions,
                    onRemove = viewModel::removeExtension
                )
            }

            RuleSectionCard(
                title = stringResource(R.string.source_folders_label),
                subtitle = stringResource(R.string.rule_section_source_subtitle),
                icon = Icons.Filled.Search
            ) {
                state.sourceFolderPaths.forEach { path ->
                    val isSourceBookmarked = path in bookmarkedFolders
                    val sourceNeedsAccess = path in state.inaccessibleSourceIssues
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = displayPath(path),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (sourceNeedsAccess) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    withTapSound {
                                        launchFolderPicker(FolderPickIntent.ReplaceSource(path), path)
                                    }
                                }
                        )
                        IconButton(onClick = { withTapSound { viewModel.toggleBookmark(path) } }) {
                            Icon(
                                imageVector = if (isSourceBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = stringResource(R.string.bookmark_toggle_cd),
                                modifier = Modifier.size(22.dp),
                                tint = if (isSourceBookmarked) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { withTapSound { viewModel.removeSourceFolder(path) } }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.schedule_remove_short), modifier = Modifier.size(18.dp))
                        }
                    }
                }
                val unusedBookmarks = bookmarkedFolders.filter {
                    it.startsWith("content://") && it !in state.sourceFolderPaths
                }
                var bookmarkDropdownExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FolderPickerButton(
                        label = stringResource(R.string.add_source_folder),
                        onClick = { launchFolderPicker(FolderPickIntent.AddSource, null) },
                        modifier = Modifier.weight(1f)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { if (unusedBookmarks.isNotEmpty()) bookmarkDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = SectionButtonShape,
                            enabled = unusedBookmarks.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Bookmark,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("  ${stringResource(R.string.bookmarks_choose)}")
                        }
                        DropdownMenu(
                            expanded = bookmarkDropdownExpanded,
                            onDismissRequest = { bookmarkDropdownExpanded = false }
                        ) {
                            unusedBookmarks.forEach { bookmarkPath ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = displayPath(bookmarkPath),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    onClick = {
                                        withTapSound {
                                            viewModel.addSourceFolder(bookmarkPath)
                                            bookmarkDropdownExpanded = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                if (state.folderAccessMode.usesAllFilesPaths() && !state.allFilesAccessGranted &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ) {
                    TextButton(
                        onClick = {
                            withTapSound {
                                val allFilesIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(allFilesIntent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.rule_open_all_files_settings))
                    }
                }
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.rule_scan_subdirs_label),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Switch(
                            checked = state.scanSubdirectories,
                            onCheckedChange = { enabled ->
                                withTapSound { viewModel.setScanSubdirectories(enabled) }
                            }
                        )
                    }
                }
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.rule_suppress_missing_source_card_label),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Switch(
                            checked = state.suppressMissingSourceFolderCardWarning,
                            onCheckedChange = { enabled ->
                                withTapSound { viewModel.setSuppressMissingSourceFolderCardWarning(enabled) }
                            }
                        )
                    }
                }
            }

            RuleSectionCard(
                title = stringResource(R.string.destination_label),
                subtitle = stringResource(R.string.rule_section_destination_subtitle),
                icon = Icons.Filled.FolderSpecial
            ) {
                if (state.destinationFolderPath.isNotBlank()) {
                    val isDestBookmarked = state.destinationFolderPath in bookmarkedFolders
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = displayPath(state.destinationFolderPath),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.destinationFolderAccessIssue != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    withTapSound {
                                        launchFolderPicker(
                                            FolderPickIntent.SetDestination,
                                            state.destinationFolderPath
                                        )
                                    }
                                }
                        )
                        IconButton(onClick = { withTapSound { viewModel.toggleBookmark(state.destinationFolderPath) } }) {
                            Icon(
                                imageVector = if (isDestBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = stringResource(R.string.bookmark_toggle_cd),
                                modifier = Modifier.size(22.dp),
                                tint = if (isDestBookmarked) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                val unusedDestBookmarks = bookmarkedFolders.filter {
                    it.startsWith("content://") && it != state.destinationFolderPath
                }
                var destBookmarkDropdownExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FolderPickerButton(
                        label = if (state.destinationFolderPath.isBlank()) {
                            stringResource(R.string.pick_folder)
                        } else {
                            stringResource(R.string.change_destination)
                        },
                        onClick = {
                            launchFolderPicker(
                                FolderPickIntent.SetDestination,
                                state.destinationFolderPath.takeIf { it.isNotBlank() }
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { if (unusedDestBookmarks.isNotEmpty()) destBookmarkDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = SectionButtonShape,
                            enabled = unusedDestBookmarks.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Bookmark,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("  ${stringResource(R.string.bookmarks_choose)}")
                        }
                        DropdownMenu(
                            expanded = destBookmarkDropdownExpanded,
                            onDismissRequest = { destBookmarkDropdownExpanded = false }
                        ) {
                            unusedDestBookmarks.forEach { bookmarkPath ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = displayPath(bookmarkPath),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    onClick = {
                                        withTapSound {
                                            viewModel.setDestination(bookmarkPath)
                                            destBookmarkDropdownExpanded = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                if (state.folderAccessMode.usesAllFilesPaths() && !state.allFilesAccessGranted &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ) {
                    TextButton(
                        onClick = {
                            withTapSound {
                                val allFilesIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(allFilesIntent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.rule_open_all_files_settings))
                    }
                }
            }

            RuleSectionCard(
                title = stringResource(R.string.rule_section_operation_title),
                subtitle = null,
                icon = Icons.Filled.ContentCopy
            ) {
                Text(
                    text = stringResource(R.string.rule_operation_mode_label),
                    style = MaterialTheme.typography.bodyMedium
                )
                @Suppress("DEPRECATION")
                ButtonGroup(modifier = Modifier.fillMaxWidth()) {
                    OperationMode.entries.forEachIndexed { index, mode ->
                        val shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            OperationMode.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                        ToggleButton(
                            checked = state.operationMode == mode,
                            onCheckedChange = { withTapSound { viewModel.setOperationMode(mode) } },
                            shapes = shapes,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                when (mode) {
                                    OperationMode.MOVE -> stringResource(R.string.operation_move)
                                    OperationMode.COPY -> stringResource(R.string.operation_copy)
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.rule_conflict_policy_label),
                    style = MaterialTheme.typography.bodyMedium
                )
                @Suppress("DEPRECATION")
                ButtonGroup(modifier = Modifier.fillMaxWidth()) {
                    ConflictPolicy.entries.forEachIndexed { index, policy ->
                        val shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            ConflictPolicy.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                        ToggleButton(
                            checked = state.conflictPolicy == policy,
                            onCheckedChange = { withTapSound { viewModel.setConflictPolicy(policy) } },
                            shapes = shapes,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                when (policy) {
                                    ConflictPolicy.SKIP -> stringResource(R.string.conflict_skip)
                                    ConflictPolicy.OVERWRITE -> stringResource(R.string.conflict_overwrite)
                                    ConflictPolicy.RENAME_SUFFIX -> stringResource(R.string.conflict_rename)
                                }
                            )
                        }
                    }
                }
            }

            RuleSectionCard(
                title = stringResource(R.string.schedule_label),
                subtitle = stringResource(R.string.rule_section_schedule_subtitle),
                icon = Icons.Default.DateRange
            ) {
                val schedule = state.schedule
                if (schedule != null) {
                    val scheduleText = when (schedule.type) {
                        ScheduleType.DAILY -> "Daily at %02d:%02d".format(schedule.hour, schedule.minute)
                        ScheduleType.WEEKLY -> {
                            val days = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                            val dayName = schedule.dayOfWeek?.let { days.getOrNull(it - 2) } ?: "?"
                            "Weekly ($dayName) at %02d:%02d".format(schedule.hour, schedule.minute)
                        }
                        ScheduleType.EVERY_N_HOURS ->
                            "Every ${schedule.intervalHours ?: 1}h"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { withTapSound { showScheduleDialog = true } },
                            modifier = Modifier.weight(1f),
                            shape = SectionButtonShape
                        ) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(text = "  $scheduleText")
                        }
                        IconButton(onClick = { withTapSound { viewModel.setSchedule(null) } }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_schedule))
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { withTapSound { showScheduleDialog = true } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = SectionButtonShape
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(text = "  ${stringResource(R.string.add_schedule_chip)}")
                    }
                }
            }

            // Advanced filters section
            val hasAdvancedFilters = state.filenamePattern.isNotBlank() ||
                state.minFileSizeMb.isNotBlank() || state.maxFileSizeMb.isNotBlank() ||
                state.minAgeDays.isNotBlank() || state.maxAgeDays.isNotBlank() ||
                state.excludePatternsText.isNotBlank()

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = elevatedCardColors()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { advancedExpanded = !advancedExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (hasAdvancedFilters) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.advanced_section_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (hasAdvancedFilters) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (hasAdvancedFilters && !advancedExpanded)
                                    stringResource(R.string.advanced_section_filters_active)
                                else
                                    stringResource(R.string.advanced_section_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (advancedExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val advancedExpandSpec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntSize>()
                    AnimatedVisibility(
                        visible = advancedExpanded,
                        enter = expandVertically(animationSpec = advancedExpandSpec) + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            OutlinedTextField(
                                value = state.filenamePattern,
                                onValueChange = viewModel::setFilenamePattern,
                                label = { Text(stringResource(R.string.advanced_filename_pattern_label)) },
                                placeholder = { Text(stringResource(R.string.advanced_filename_pattern_placeholder)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = state.minFileSizeMb,
                                    onValueChange = viewModel::setMinFileSizeMb,
                                    label = { Text(stringResource(R.string.advanced_min_size_label)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = state.maxFileSizeMb,
                                    onValueChange = viewModel::setMaxFileSizeMb,
                                    label = { Text(stringResource(R.string.advanced_max_size_label)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = state.minAgeDays,
                                    onValueChange = viewModel::setMinAgeDays,
                                    label = { Text(stringResource(R.string.advanced_min_age_label)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = state.maxAgeDays,
                                    onValueChange = viewModel::setMaxAgeDays,
                                    label = { Text(stringResource(R.string.advanced_max_age_label)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            OutlinedTextField(
                                value = state.excludePatternsText,
                                onValueChange = viewModel::setExcludePatternsText,
                                label = { Text(stringResource(R.string.advanced_exclude_patterns_label)) },
                                placeholder = { Text(stringResource(R.string.advanced_exclude_placeholder)) },
                                supportingText = { Text(stringResource(R.string.advanced_csv_hint)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp + bottomContentPadding))
                }
            }
        }

        TopAppBar(
            modifier = Modifier.align(Alignment.TopCenter),
            title = { Text(if (viewModel.isNewRule) stringResource(R.string.new_rule) else stringResource(R.string.edit_rule)) },
            navigationIcon = {
                IconButton(onClick = { withTapSound(::tryNavigateBack) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                }
            },
            actions = {
                IconButton(
                    onClick = { withTapSound { viewModel.loadPreview() } },
                    enabled = state.sourceFolderPaths.isNotEmpty() && state.fileExtensions.isNotEmpty()
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = stringResource(R.string.preview_rule))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            )
        )

        if (!state.isLoading) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = Color.Transparent,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { withTapSound(::tryNavigateBack) },
                        modifier = Modifier.weight(1f),
                        shape = PillShape
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = { withTapSound { viewModel.save() } },
                        modifier = Modifier.weight(1f),
                        shape = PillShape
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = if (state.isLoading) {
                        navBottom + 16.dp
                    } else {
                        navBottom + 88.dp
                    }
                )
        )
    }

    if (pendingFilesystemFolderPick != null) {
        val pickRequest = pendingFilesystemFolderPick!!
        val pickedIntent = pickRequest.intent
        ModalBottomSheet(
            onDismissRequest = { pendingFilesystemFolderPick = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.filesystem_folder_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                FilesystemFolderPickerSheetContent(
                    initialDirectory = pickRequest.startDirectory,
                    onDismiss = { pendingFilesystemFolderPick = null },
                    onFolderChosen = { absolutePath ->
                        when (pickedIntent) {
                            FolderPickIntent.AddSource -> viewModel.addSourceFolder(absolutePath)
                            is FolderPickIntent.ReplaceSource ->
                                viewModel.replaceSourceFolder(pickedIntent.previousPath, absolutePath)
                            FolderPickIntent.SetDestination -> viewModel.setDestination(absolutePath)
                        }
                        pendingFilesystemFolderPick = null
                        viewModel.refreshFolderAccessAfterPermissionChange()
                    }
                )
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.unsaved_changes_title)) },
            text = { Text(stringResource(R.string.unsaved_changes_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        withTapSound {
                            showDiscardDialog = false
                            onNavigateBack()
                        }
                    }
                ) {
                    Text(stringResource(R.string.discard_changes))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { withTapSound { showDiscardDialog = false } }) {
                        Text(stringResource(R.string.keep_editing))
                    }
                    TextButton(
                        onClick = {
                            withTapSound {
                                showDiscardDialog = false
                                viewModel.save()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.save_and_exit))
                    }
                }
            }
        )
    }

    if (showScheduleDialog) {
        ScheduleDialog(
            initialSchedule = state.schedule,
            onDismiss = { showScheduleDialog = false },
            onSave = { schedule ->
                viewModel.setSchedule(schedule)
                showScheduleDialog = false
            }
        )
    }

    if (showRuleIconSheet) {
        val ruleIconSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        LaunchedEffect(showRuleIconSheet) {
            if (showRuleIconSheet) {
                customEmojiDraft = state.iconEmoji.orEmpty()
            }
        }
        ModalBottomSheet(
            onDismissRequest = { showRuleIconSheet = false },
            sheetState = ruleIconSheetState
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = stringResource(R.string.rule_icon_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = stringResource(R.string.rule_icon_preset_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                val ruleIconGridCell = (maxWidth - 48.dp) / 7f
                FlowRow(
                    maxItemsInEachRow = 7,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RuleIcon.entries.forEach { iconOption ->
                        FilledTonalIconButton(
                            onClick = {
                                withTapSound {
                                    viewModel.setIcon(iconOption)
                                    showRuleIconSheet = false
                                }
                            },
                            modifier = Modifier.size(ruleIconGridCell),
                            shape = SectionButtonShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = iconOption.toImageVector(),
                                contentDescription = ruleIconOptionLabel(iconOption),
                                modifier = Modifier.size(34.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                } // close BoxWithConstraints for icon section
                Text(
                    text = stringResource(R.string.rule_icon_emoji_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                val customEmojiSlotDescription = stringResource(R.string.rule_icon_custom_slot_cd)
                val emojiTextStyle = TextStyle(
                    fontSize = 28.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                val emojiCellSize = (maxWidth - 48.dp) / 7f
                FlowRow(
                    maxItemsInEachRow = 7,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RuleIconEmojiPresets.forEach { emojiPreset ->
                        Surface(
                            onClick = {
                                withTapSound {
                                    viewModel.setIconEmoji(emojiPreset)
                                    showRuleIconSheet = false
                                }
                            },
                            modifier = Modifier.size(emojiCellSize),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emojiPreset, style = emojiTextStyle)
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .size(emojiCellSize)
                            .semantics { contentDescription = customEmojiSlotDescription },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                            textStyle = emojiTextStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurfaceVariant),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    innerTextField()
                                }
                            }
                        )
                    }
                    val applyEmojiEnabled = customEmojiDraft.isNotBlank()
                    Surface(
                        onClick = {
                            if (applyEmojiEnabled) {
                                withTapSound {
                                    viewModel.setIconEmoji(customEmojiDraft)
                                    showRuleIconSheet = false
                                }
                            }
                        },
                        enabled = applyEmojiEnabled,
                        modifier = Modifier.size(emojiCellSize),
                        shape = MaterialShapes.Cookie12Sided.toShape(),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.rule_icon_apply_emoji_cd),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
                } // close BoxWithConstraints for emoji section
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Template picker — shown only for new rules, once
    if (showTemplateSheet && viewModel.isNewRule) {
        ModalBottomSheet(
            onDismissRequest = { showTemplateSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(R.string.template_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = stringResource(R.string.template_picker_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                RuleTemplate.ALL.forEach { template ->
                    ElevatedCard(
                        onClick = {
                            withTapSound {
                                viewModel.applyTemplate(template)
                                showTemplateSheet = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = template.suggestedIcon.toImageVector(),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(template.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    template.extensions.take(5).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                TextButton(
                    onClick = { withTapSound { showTemplateSheet = false } },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = PillShape
                ) {
                    Text(stringResource(R.string.onboarding_wizard_start_blank))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // Preview bottom sheet
    if (state.previewFiles != null || state.isPreviewLoading) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissPreview() },
            sheetState = previewSheetState
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(R.string.preview_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (state.isPreviewLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(32.dp))
                } else {
                    val files = state.previewFiles ?: emptyList()
                    if (files.isEmpty()) {
                        Text(
                            text = stringResource(R.string.preview_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.preview_count, files.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(files) { file ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    val sizeKb = file.size / 1024
                                    Text(
                                        text = if (sizeKb > 1024) "${sizeKb / 1024} MB" else "$sizeKb KB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
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
