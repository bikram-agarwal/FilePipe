package dev.bikram.filepipe.ui.screens.historydetail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.util.LruCache
import android.util.Size
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bikram.filepipe.R
import dev.bikram.filepipe.domain.model.FileMoved
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.RunHistory
import dev.bikram.filepipe.domain.model.RunStatus
import dev.bikram.filepipe.domain.model.TriggerType
import dev.bikram.filepipe.domain.model.isEffectivelyUndone
import dev.bikram.filepipe.domain.model.isNoChangesRun
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.components.FilePipeButton
import dev.bikram.filepipe.ui.components.FilePipeIconButton
import dev.bikram.filepipe.ui.components.StatusChip
import dev.bikram.filepipe.ui.components.displayPath
import dev.bikram.filepipe.ui.components.formatTime
import dev.bikram.filepipe.ui.feedback.tapSoundClickable
import dev.bikram.filepipe.ui.modifiers.progressiveBlurFullBleedLayer
import dev.bikram.filepipe.ui.theme.LocalProgressiveBlurStyle
import dev.bikram.filepipe.ui.theme.LocalUseGradientBackground
import dev.bikram.filepipe.ui.theme.compactControlShape
import dev.bikram.filepipe.ui.theme.elevatedCardColors
import dev.bikram.filepipe.ui.theme.gradientOverlayTopAppBarColors
import dev.bikram.filepipe.ui.theme.pillShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private const val THUMBNAIL_CACHE_SIZE_KB = 6 * 1024

private val fileThumbnailMemoryCache =
    object : LruCache<String, Bitmap>(THUMBNAIL_CACHE_SIZE_KB) {
        override fun sizeOf(
            key: String,
            value: Bitmap,
        ): Int = (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

private enum class ThumbnailMediaType {
    IMAGE,
    VIDEO,
    AUDIO,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    onNavigateBack: () -> Unit,
    showNavigateBack: Boolean = true,
    viewModel: HistoryDetailViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val topAlphaMultiplier by remember(listState) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                val offsetPx = listState.firstVisibleItemScrollOffset.toFloat()
                val thresholdPx = with(density) { 24.dp.toPx() }
                (offsetPx / thresholdPx).coerceIn(0f, 1f)
            }
        }
    }
    val fullBleedBlurModifier =
        LocalProgressiveBlurStyle.current?.let { blurStyle ->
            Modifier.progressiveBlurFullBleedLayer(blurStyle, topAlphaMultiplier = topAlphaMultiplier)
        } ?: Modifier
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topListPadding = statusTop + 64.dp

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(userMessage) {
        val msg = userMessage ?: return@LaunchedEffect
        try {
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short,
            )
        } finally {
            viewModel.clearUserMessage()
        }
    }
    DisposableEffect(lifecycleOwner, snackbarHostState) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    snackbarHostState.currentSnackbarData?.dismiss()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(fullBleedBlurModifier),
        ) {
            val scheme = MaterialTheme.colorScheme
            if (LocalUseGradientBackground.current) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(scheme.surface)
                        .background(
                            Brush.verticalGradient(
                                colorStops =
                                    arrayOf(
                                        0f to scheme.primaryContainer.copy(alpha = 0.45f),
                                        0.55f to scheme.surface.copy(alpha = 0f),
                                    ),
                            ),
                        ),
                )
            } else {
                Box(Modifier.fillMaxSize().background(scheme.background))
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = topListPadding + 8.dp,
                        bottom = 32.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                history?.let { h ->
                    item {
                        RunSummaryCard(
                            history = h,
                            onUndo = viewModel::undoRun,
                        )
                    }
                    if (files.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.history_detail_files_header, files.size),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                        items(files, key = { it.id }) { file ->
                            FileMovedCard(file, modifier = Modifier.animateItem())
                        }
                    } else {
                        item {
                            Text(
                                stringResource(R.string.history_detail_no_file_records),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        TopAppBar(
            modifier = Modifier.align(Alignment.TopCenter),
            title = {
                Text(
                    history?.ruleName ?: stringResource(R.string.history_detail_title),
                )
            },
            navigationIcon = {
                if (showNavigateBack) {
                    val backLabel = stringResource(R.string.nav_back)
                    FilePipeIconButton(onClick = onNavigateBack) {
                        FilePipeMaterialRoundedSymbol(
                            name = "arrow_back",
                            contentDescription = backLabel,
                            autoMirror = true,
                        )
                    }
                }
            },
            colors = gradientOverlayTopAppBarColors(),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = navBottom + 16.dp),
        )
    }
}

@Composable
private fun RunSummaryCard(
    history: RunHistory,
    onUndo: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = elevatedCardColors(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Run Summary", style = MaterialTheme.typography.titleMedium)
                StatusChip(
                    status = if (history.isEffectivelyUndone()) RunStatus.UNDONE else history.status,
                    noChanges = history.isNoChangesRun(),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            SummaryRow(
                "Trigger",
                when (history.triggeredBy) {
                    TriggerType.MANUAL -> "Manual"
                    TriggerType.SCHEDULED -> "Scheduled"
                },
            )
            val cardContext = LocalContext.current
            SummaryRow("Started", formatTime(cardContext, history.startedAt))
            history.completedAt?.let { completed ->
                SummaryRow("Completed", formatTime(cardContext, completed))
                val durationSec = (completed - history.startedAt) / 1000
                SummaryRow("Duration", "${durationSec}s")
            }
            SummaryRow(
                stringResource(
                    when (history.operationMode) {
                        OperationMode.COPY -> R.string.history_detail_files_copied_label
                        OperationMode.MOVE -> R.string.history_detail_files_moved_label
                    },
                ),
                history.totalFilesMoved.toString(),
            )
            if (history.totalFilesFailed > 0) {
                SummaryRow("Failed", history.totalFilesFailed.toString())
            }
            if (history.cancelledUnprocessedCount > 0) {
                SummaryRow(
                    stringResource(R.string.history_detail_not_processed_cancelled_label),
                    history.cancelledUnprocessedCount.toString(),
                )
            }
            history.errorMessage?.let { msg ->
                SummaryRow("Error", msg)
            }
            if (history.isEffectivelyUndone()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.history_detail_run_undone),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            } else if (
                history.totalFilesMoved > 0 &&
                (history.status == RunStatus.SUCCESS || history.status == RunStatus.CANCELLED)
            ) {
                Spacer(Modifier.height(8.dp))
                FilePipeButton(
                    onClick = onUndo,
                    modifier = Modifier.fillMaxWidth(),
                    shape = pillShape,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                ) {
                    FilePipeMaterialRoundedSymbol(
                        name = "undo",
                        contentDescription = null,
                        size = 20.dp,
                        autoMirror = true,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        pluralStringResource(
                            R.plurals.history_detail_undo_files,
                            history.totalFilesMoved,
                            history.totalFilesMoved,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FileMovedCard(
    file: FileMoved,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val internalStorageDisplayName = stringResource(R.string.filesystem_folder_picker_internal_storage)
    val isSuccess = file.success && !file.skipped
    val iconColor =
        when {
            file.skipped -> MaterialTheme.colorScheme.outline
            file.success -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.error
        }
    val listCardSurface = elevatedCardColors()
    val containerColor =
        when {
            file.skipped -> listCardSurface.containerColor
            !file.success -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else -> listCardSurface.containerColor
        }
    val rowContentColor =
        if (file.success || file.skipped) {
            listCardSurface.contentColor
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (isSuccess) {
                        Modifier.tapSoundClickable {
                            openFileWithDefaultApp(context, file.destinationUri)
                        }
                    } else {
                        Modifier
                    },
                ),
        shape = compactControlShape,
        color = containerColor,
        contentColor = rowContentColor,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FileThumbnailOrIcon(file = file, isSuccess = isSuccess)

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        file.fileName,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    FilePipeMaterialRoundedSymbol(
                        name = if (isSuccess) "check_circle" else "warning",
                        contentDescription = null,
                        size = 16.dp,
                        tint = iconColor,
                    )
                }

                Text(
                    "From: ${displayPath(file.sourceUri, internalStorageDisplayName)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isSuccess) {
                    Text(
                        "To: ${displayPath(file.destinationUri, internalStorageDisplayName)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val sizeKb = file.fileSizeBytes / 1024
                    Text(
                        if (sizeKb > 1024) "${sizeKb / 1024} MB" else "$sizeKb KB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    if (file.skipped) {
                        Text(
                            "skipped",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    file.errorMessage?.let { err ->
                        Text(
                            err,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileThumbnailOrIcon(
    file: FileMoved,
    isSuccess: Boolean,
) {
    val context = LocalContext.current
    val thumbnailSize = 40.dp
    val targetSizePx = with(LocalDensity.current) { thumbnailSize.toPx().roundToInt().coerceAtLeast(1) }
    val uriString = if (isSuccess) file.destinationUri else file.sourceUri
    val thumbnailBitmap by produceState<Bitmap?>(
        initialValue = null,
        uriString,
        file.fileName,
        targetSizePx,
    ) {
        value =
            withContext(Dispatchers.IO) {
                loadCachedFileThumbnail(
                    context = context.applicationContext,
                    uriString = uriString,
                    fileName = file.fileName,
                    targetSizePx = targetSizePx,
                )
            }
    }

    val bitmap = thumbnailBitmap
    if (bitmap != null) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(thumbnailSize),
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        Box(
            modifier =
                Modifier
                    .size(thumbnailSize)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            FilePipeMaterialRoundedSymbol(
                name = fileTypeSymbolName(file.fileName),
                contentDescription = null,
                size = 24.dp,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private fun loadCachedFileThumbnail(
    context: Context,
    uriString: String,
    fileName: String,
    targetSizePx: Int,
): Bitmap? {
    val mediaType = thumbnailMediaType(fileName) ?: return null
    val cacheKey = "$targetSizePx:$uriString"
    val cachedBitmap = fileThumbnailMemoryCache.get(cacheKey)
    if (cachedBitmap != null) {
        return cachedBitmap
    }

    val thumbnailBitmap =
        loadFileThumbnail(
            context = context,
            uriString = uriString,
            mediaType = mediaType,
            targetSize = Size(targetSizePx, targetSizePx),
        ) ?: return null
    fileThumbnailMemoryCache.put(cacheKey, thumbnailBitmap)
    return thumbnailBitmap
}

private fun loadFileThumbnail(
    context: Context,
    uriString: String,
    mediaType: ThumbnailMediaType,
    targetSize: Size,
): Bitmap? {
    if (uriString.isBlank()) {
        return null
    }

    val uri =
        try {
            uriString.toUri()
        } catch (_: RuntimeException) {
            return null
        }

    return when {
        uri.scheme == "content" -> {
            loadContentThumbnail(
                context = context,
                uri = uri,
                mediaType = mediaType,
                targetSize = targetSize,
            )
        }

        uri.scheme == "file" -> {
            val filePath = uri.path ?: return null
            loadFilePathThumbnail(
                context = context,
                file = File(filePath),
                mediaType = mediaType,
                targetSize = targetSize,
            )
        }

        uri.scheme.isNullOrBlank() && uriString.startsWith("/") -> {
            loadFilePathThumbnail(
                context = context,
                file = File(uriString),
                mediaType = mediaType,
                targetSize = targetSize,
            )
        }

        else -> {
            null
        }
    }
}

private fun loadContentThumbnail(
    context: Context,
    uri: Uri,
    mediaType: ThumbnailMediaType,
    targetSize: Size,
): Bitmap? {
    val providerThumbnail =
        runCatching {
            context.contentResolver.loadThumbnail(uri, targetSize, null)
        }.getOrNull()
    if (providerThumbnail != null) {
        return providerThumbnail
    }

    return when (mediaType) {
        ThumbnailMediaType.IMAGE -> {
            loadImageContentThumbnail(context, uri, targetSize)
        }

        ThumbnailMediaType.VIDEO -> {
            loadVideoFrameThumbnail(
                context = context,
                uri = uri,
                file = null,
                targetSize = targetSize,
            )
        }

        ThumbnailMediaType.AUDIO -> {
            loadAudioArtworkThumbnail(
                context = context,
                uri = uri,
                file = null,
                targetSize = targetSize,
            )
        }
    }
}

private fun loadFilePathThumbnail(
    context: Context,
    file: File,
    mediaType: ThumbnailMediaType,
    targetSize: Size,
): Bitmap? {
    if (!file.isFile) {
        return null
    }

    return when (mediaType) {
        ThumbnailMediaType.IMAGE -> {
            loadImageFileThumbnail(file, targetSize)
        }

        ThumbnailMediaType.VIDEO -> {
            runCatching {
                ThumbnailUtils.createVideoThumbnail(file, targetSize, null)
            }.getOrNull()
                ?: loadVideoFrameThumbnail(
                    context = context,
                    uri = null,
                    file = file,
                    targetSize = targetSize,
                )
        }

        ThumbnailMediaType.AUDIO -> {
            loadAudioArtworkThumbnail(
                context = context,
                uri = null,
                file = file,
                targetSize = targetSize,
            )
        }
    }
}

private fun loadImageContentThumbnail(
    context: Context,
    uri: Uri,
    targetSize: Size,
): Bitmap? =
    runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        decodeImageThumbnail(source, targetSize)
    }.getOrNull()

private fun loadImageFileThumbnail(
    file: File,
    targetSize: Size,
): Bitmap? =
    runCatching {
        val source = ImageDecoder.createSource(file)
        decodeImageThumbnail(source, targetSize)
    }.getOrNull()

private fun decodeImageThumbnail(
    source: ImageDecoder.Source,
    targetSize: Size,
): Bitmap =
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        setDecoderTargetSize(decoder, info.size, targetSize)
    }

private fun setDecoderTargetSize(
    decoder: ImageDecoder,
    sourceSize: Size,
    targetSize: Size,
) {
    if (sourceSize.width <= 0 || sourceSize.height <= 0) {
        return
    }

    val scale =
        minOf(
            targetSize.width.toFloat() / sourceSize.width.toFloat(),
            targetSize.height.toFloat() / sourceSize.height.toFloat(),
        ).coerceAtMost(1f)
    if (scale >= 1f) {
        return
    }

    decoder.setTargetSize(
        (sourceSize.width * scale).roundToInt().coerceAtLeast(1),
        (sourceSize.height * scale).roundToInt().coerceAtLeast(1),
    )
}

private fun loadVideoFrameThumbnail(
    context: Context,
    uri: Uri?,
    file: File?,
    targetSize: Size,
): Bitmap? {
    val mediaMetadataRetriever = MediaMetadataRetriever()
    return try {
        setMediaDataSource(mediaMetadataRetriever, context, uri, file)
        mediaMetadataRetriever.getScaledFrameAtTime(
            0,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            targetSize.width,
            targetSize.height,
        )
    } catch (_: RuntimeException) {
        null
    } finally {
        runCatching { mediaMetadataRetriever.release() }
    }
}

private fun loadAudioArtworkThumbnail(
    context: Context,
    uri: Uri?,
    file: File?,
    targetSize: Size,
): Bitmap? {
    val mediaMetadataRetriever = MediaMetadataRetriever()
    return try {
        setMediaDataSource(mediaMetadataRetriever, context, uri, file)
        val embeddedPicture = mediaMetadataRetriever.embeddedPicture ?: return null
        decodeByteArrayThumbnail(embeddedPicture, targetSize)
    } catch (_: RuntimeException) {
        null
    } finally {
        runCatching { mediaMetadataRetriever.release() }
    }
}

private fun setMediaDataSource(
    mediaMetadataRetriever: MediaMetadataRetriever,
    context: Context,
    uri: Uri?,
    file: File?,
) {
    when {
        uri != null -> mediaMetadataRetriever.setDataSource(context, uri)
        file != null -> mediaMetadataRetriever.setDataSource(file.absolutePath)
        else -> throw IllegalArgumentException("Missing media source")
    }
}

private fun decodeByteArrayThumbnail(
    data: ByteArray,
    targetSize: Size,
): Bitmap? {
    val boundsOptions =
        BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
    BitmapFactory.decodeByteArray(data, 0, data.size, boundsOptions)
    if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
        return null
    }

    val decodeOptions =
        BitmapFactory.Options().apply {
            inSampleSize =
                calculateSampleSize(
                    sourceWidth = boundsOptions.outWidth,
                    sourceHeight = boundsOptions.outHeight,
                    targetSize = targetSize,
                )
        }
    return BitmapFactory.decodeByteArray(data, 0, data.size, decodeOptions)
}

private fun calculateSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    targetSize: Size,
): Int {
    var sampleSize = 1
    while (
        sourceWidth / (sampleSize * 2) >= targetSize.width &&
        sourceHeight / (sampleSize * 2) >= targetSize.height
    ) {
        sampleSize *= 2
    }
    return sampleSize
}

private fun thumbnailMediaType(fileName: String): ThumbnailMediaType? {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "gif", "heic", "heif", "webp", "bmp" -> ThumbnailMediaType.IMAGE
        "mp4", "mkv", "avi", "mov", "m4v", "webm", "3gp" -> ThumbnailMediaType.VIDEO
        "mp3", "flac", "aac", "ogg", "m4a", "wav", "opus" -> ThumbnailMediaType.AUDIO
        else -> null
    }
}

private fun openFileWithDefaultApp(
    context: Context,
    uriString: String,
) {
    if (uriString.isBlank()) {
        Toast.makeText(context, context.getString(R.string.history_file_location_unavailable), Toast.LENGTH_SHORT).show()
        return
    }
    val intent =
        try {
            val uri = uriString.toUri()
            val uriForIntent =
                when {
                    uri.scheme == "file" -> {
                        val filePath = uri.path
                        if (filePath.isNullOrBlank()) {
                            val unavailableToast =
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.history_file_location_unavailable),
                                    Toast.LENGTH_SHORT,
                                )
                            unavailableToast.show()
                            return
                        }
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            File(filePath),
                        )
                    }

                    uri.scheme.isNullOrBlank() && uriString.startsWith("/") -> {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            File(uriString),
                        )
                    }

                    else -> {
                        uri
                    }
                }
            val fileName = uriString.substringAfterLast('/')
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uriForIntent, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: RuntimeException) {
            Toast.makeText(context, context.getString(R.string.history_file_open_failed), Toast.LENGTH_SHORT).show()
            return
        }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.history_file_open_no_app), Toast.LENGTH_SHORT).show()
    } catch (_: RuntimeException) {
        Toast.makeText(context, context.getString(R.string.history_file_open_failed), Toast.LENGTH_SHORT).show()
    }
}

private fun fileTypeSymbolName(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "gif", "heic", "webp", "bmp", "svg" -> "image"
        "mp4", "mkv", "avi", "mov", "m4v", "webm" -> "movie"
        "mp3", "flac", "aac", "ogg", "m4a", "wav" -> "audio_file"
        "pdf", "docx", "doc", "xlsx", "xls", "pptx", "ppt", "txt", "odt" -> "description"
        "zip", "rar", "7z", "tar", "gz" -> "folder_zip"
        else -> "draft"
    }
}
