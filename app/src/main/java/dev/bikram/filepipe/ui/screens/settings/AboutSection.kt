package dev.bikram.filepipe.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dev.bikram.filepipe.BuildConfig
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.preferences.AppPreferences
import dev.bikram.filepipe.diagnostics.DiagnosticLog
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.common.isLandscape
import dev.bikram.filepipe.ui.common.isSmallLandscape
import dev.bikram.filepipe.ui.components.AboutAuthorPhoto
import dev.bikram.filepipe.ui.components.AppIconImage
import dev.bikram.filepipe.ui.components.FilePipeIconButton
import dev.bikram.filepipe.ui.components.containers.GroupPosition
import dev.bikram.filepipe.ui.components.containers.GroupedListColumn
import dev.bikram.filepipe.ui.components.containers.GroupedListItem
import dev.bikram.filepipe.ui.feedback.appClickable
import dev.bikram.filepipe.ui.feedback.appCombinedClickable
import dev.bikram.filepipe.ui.theme.compactControlShape
import dev.bikram.filepipe.ui.theme.pillShape
import kotlinx.coroutines.launch

private const val REMEMBER_FDROID_PACKAGE_ID = "dev.bikram.remember.gh"
private const val OBTAINX_FDROID_PACKAGE_ID = "dev.bikram.obtainx"
private const val DEVELOPER_OPTIONS_UNLOCK_TAPS = 7

private data class AboutAppRoute(
    val packageId: String,
    val portfolioUrl: String,
    val playStoreUrl: String = "",
) {
    val copyUrl: String
        get() =
            when (BuildConfig.FLAVOR) {
                "fdroid" -> "fdroid.app:$packageId"
                "playstore" -> playStoreUrl.ifBlank { portfolioUrl }
                else -> portfolioUrl
            }
}

@Composable
private fun AboutPlayStoreIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(R.drawable.ic_google_play_mark),
        contentDescription = null,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
private fun AboutOtherAppsAndLinks(
    context: Context,
    copyLinkToClipboard: (String) -> Unit,
    isSmallLandscape: Boolean,
) {
    val useGithubLikeAboutLinks =
        BuildConfig.FLAVOR == "github" ||
            BuildConfig.FLAVOR == "fdroid" ||
            BuildConfig.FLAVOR == "offline"
    val rememberRoute =
        AboutAppRoute(
            packageId = REMEMBER_FDROID_PACKAGE_ID,
            portfolioUrl = stringResource(R.string.settings_about_remember_website_url),
            playStoreUrl = stringResource(R.string.settings_about_remember_play_store_url),
        )
    val obtainXRoute =
        AboutAppRoute(
            packageId = OBTAINX_FDROID_PACKAGE_ID,
            portfolioUrl = stringResource(R.string.settings_about_obtainx_website_url),
        )
    val websiteUrl = stringResource(R.string.settings_about_filepipe_website_url)
    val privacyUrl = stringResource(R.string.settings_about_filepipe_privacy_url)
    val termsUrl = stringResource(R.string.settings_about_filepipe_terms_url)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_about_other_apps),
            style = if (isSmallLandscape) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(if (isSmallLandscape) 6.dp else 8.dp))
        AboutAppStoreButton(
            iconResId = R.drawable.logo_remember,
            name = stringResource(R.string.settings_about_remember_name),
            tagline = stringResource(R.string.settings_about_remember_tagline),
            route = rememberRoute,
            accentColor = Color(0xFF4F7D43),
            context = context,
            copyLinkToClipboard = copyLinkToClipboard,
            isSmallLandscape = isSmallLandscape,
        )
        if (useGithubLikeAboutLinks) {
            Spacer(Modifier.height(if (isSmallLandscape) 6.dp else 8.dp))
            AboutAppStoreButton(
                iconResId = R.drawable.logo_obtainx,
                name = stringResource(R.string.settings_about_obtainx_name),
                tagline = stringResource(R.string.settings_about_obtainx_tagline),
                route = obtainXRoute,
                accentColor = Color(0xFF7C55D9),
                context = context,
                copyLinkToClipboard = copyLinkToClipboard,
                isSmallLandscape = isSmallLandscape,
            )
        }
        Spacer(Modifier.height(if (isSmallLandscape) 12.dp else 14.dp))
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(if (isSmallLandscape) 6.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AboutTextLink(
                label = stringResource(R.string.settings_about_website),
                url = websiteUrl,
                context = context,
                copyLinkToClipboard = copyLinkToClipboard,
                isSmallLandscape = isSmallLandscape,
            )
            AboutLinkSeparator()
            AboutTextLink(
                label = stringResource(R.string.settings_about_privacy_policy),
                url = privacyUrl,
                context = context,
                copyLinkToClipboard = copyLinkToClipboard,
                isSmallLandscape = isSmallLandscape,
            )
            AboutLinkSeparator()
            AboutTextLink(
                label = stringResource(R.string.settings_about_terms),
                url = termsUrl,
                context = context,
                copyLinkToClipboard = copyLinkToClipboard,
                isSmallLandscape = isSmallLandscape,
            )
        }
    }
}

@Composable
private fun AboutAppStoreButton(
    iconResId: Int,
    name: String,
    tagline: String,
    route: AboutAppRoute,
    accentColor: Color,
    context: Context,
    copyLinkToClipboard: (String) -> Unit,
    isSmallLandscape: Boolean,
) {
    val shape = MaterialTheme.shapes.large
    Surface(
        shape = shape,
        color = accentColor.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.16f)),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .appCombinedClickable(
                    onClick = { openAboutAppRoute(context, route) },
                    onLongClick = { copyLinkToClipboard(route.copyUrl) },
                    role = Role.Button,
                ),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = if (isSmallLandscape) 12.dp else 12.dp,
                    vertical = if (isSmallLandscape) 8.dp else 10.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(iconResId),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(if (isSmallLandscape) 36.dp else 40.dp)
                        .clip(compactControlShape),
            )
            Spacer(Modifier.width(if (isSmallLandscape) 8.dp else 10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = if (isSmallLandscape) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (isSmallLandscape) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            FilePipeMaterialRoundedSymbol(
                name = "chevron_right",
                contentDescription = null,
                size = if (isSmallLandscape) 18.dp else 20.dp,
                tint = accentColor.copy(alpha = 0.86f),
            )
        }
    }
}

@Composable
private fun AboutLinkSeparator() {
    Text(
        text = "•",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
    )
}

@Composable
private fun AboutTextLink(
    label: String,
    url: String,
    context: Context,
    copyLinkToClipboard: (String) -> Unit,
    isSmallLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        modifier =
            modifier
                .appCombinedClickable(
                    onClick = { openAboutUrl(context, url) },
                    onLongClick = { copyLinkToClipboard(url) },
                    role = Role.Button,
                ).padding(horizontal = 4.dp, vertical = 2.dp),
        style = if (isSmallLandscape) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun openAboutUrl(
    context: Context,
    url: String,
) {
    runCatching {
        context.startActivity(aboutViewIntent(url))
    }
}

private fun openAboutAppRoute(
    context: Context,
    route: AboutAppRoute,
) {
    when (BuildConfig.FLAVOR) {
        "fdroid" -> {
            try {
                context.startActivity(aboutViewIntent("fdroid.app:${route.packageId}"))
            } catch (_: ActivityNotFoundException) {
                context.startActivity(aboutViewIntent(route.portfolioUrl))
            }
        }

        "playstore" -> {
            val targetUrl = route.playStoreUrl.ifBlank { route.portfolioUrl }
            context.startActivity(aboutViewIntent(targetUrl))
        }

        else -> {
            context.startActivity(aboutViewIntent(route.portfolioUrl))
        }
    }
}

// Every About link opens with NEW_TASK, so the store client or browser gets its own task instead of
// being stacked inside FilePipe's, where it would show up as FilePipe in Recents and Back would
// return here. PARITY: Remember's AboutSection does the same.
private fun aboutViewIntent(uri: String): Intent = Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/**
 * Opens the system share sheet with the same link and message as the Settings About section used for sharing.
 */
fun launchAppShareChooser(context: Context) {
    val githubRepoForSourceLink = BuildConfig.GITHUB_REPO.trim()
    val portfolioUrl = context.getString(R.string.settings_about_filepipe_website_url)
    val playStoreListingUrl = BuildConfig.PLAY_STORE_URL
    val shareUrl =
        when {
            BuildConfig.FLAVOR == "playstore" -> {
                playStoreListingUrl
            }

            BuildConfig.FLAVOR == "fdroid" ||
                BuildConfig.FLAVOR == "github" ||
                BuildConfig.FLAVOR == "offline" -> {
                portfolioUrl
            }

            githubRepoForSourceLink.isNotEmpty() -> {
                "https://github.com/$githubRepoForSourceLink/releases/latest"
            }

            else -> {
                ""
            }
        }
    if (shareUrl.isEmpty()) return
    val message =
        context.getString(
            R.string.about_share_text,
            context.getString(R.string.app_name),
            shareUrl,
        )
    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name))
        }
    runCatching {
        context.startActivity(
            Intent.createChooser(sendIntent, context.getString(R.string.settings_share_app)),
        )
    }
}

@Composable
private fun rememberDiagnosticsShareAction(
    context: Context,
    chooserTitle: String,
    preferences: AppPreferences,
): () -> Unit {
    val scope = rememberCoroutineScope()
    return remember(context, chooserTitle, preferences, scope) {
        {
            scope.launch {
                runCatching {
                    DiagnosticLog.record(context, "Diagnostic log shared from Settings")
                    val diagnosticsFile = DiagnosticLog.createShareFile(context, preferences)
                    val uri =
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            diagnosticsFile,
                        )
                    val sendIntent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.settings_share_diagnostics_subject))
                            putExtra(Intent.EXTRA_TITLE, context.getString(R.string.settings_share_diagnostics))
                            clipData = ClipData.newUri(context.contentResolver, diagnosticsFile.name, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    context.startActivity(Intent.createChooser(sendIntent, chooserTitle))
                }.onFailure { error ->
                    DiagnosticLog.record(context, "Diagnostic log share failed", error)
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.settings_share_diagnostics_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun AboutSection(
    onOpenIntro: () -> Unit,
    onOpenDevOptions: () -> Unit,
    onDeveloperOptionsUnlocked: () -> Unit,
    onLaunchPlayReview: (onFlowFinished: () -> Unit) -> Unit,
    developerOptionsEnabled: Boolean,
    preferences: AppPreferences,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
) {
    val isLandscape = isLandscape()
    val isSmallLandscape = isSmallLandscape()
    val aboutContext = LocalContext.current
    val aboutResources = LocalResources.current
    val diagnosticsChooserTitle = stringResource(R.string.settings_share_diagnostics_chooser)
    val diagnosticsTooltip = stringResource(R.string.settings_share_diagnostics)
    val shareDiagnostics =
        rememberDiagnosticsShareAction(
            context = aboutContext,
            chooserTitle = diagnosticsChooserTitle,
            preferences = preferences,
        )
    Column(modifier = modifier) {
        if (showHeader) {
            SettingsSectionHeader(
                iconName = SettingsSectionKey.About.iconName,
                title = stringResource(R.string.settings_about_section),
                trailingContent =
                    if (!isLandscape) {
                        {
                            FilePipeIconButton(
                                onClick = shareDiagnostics,
                                modifier = Modifier.size(40.dp),
                                tooltipLabel = diagnosticsTooltip,
                            ) {
                                FilePipeMaterialRoundedSymbol(
                                    name = "bug_report",
                                    contentDescription = diagnosticsTooltip,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    } else {
                        null
                    },
            )
            Spacer(Modifier.height(8.dp))
        }
        AboutSettingsBlock(
            onOpenIntro = onOpenIntro,
            onOpenDevOptions = onOpenDevOptions,
            onDeveloperOptionsUnlocked = onDeveloperOptionsUnlocked,
            onLaunchPlayReview = onLaunchPlayReview,
            developerOptionsEnabled = developerOptionsEnabled,
            isLandscape = isLandscape,
            isSmallLandscape = isSmallLandscape,
            shareDiagnostics = shareDiagnostics,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun AboutSettingsBlock(
    onOpenIntro: () -> Unit,
    onOpenDevOptions: () -> Unit,
    onDeveloperOptionsUnlocked: () -> Unit,
    onLaunchPlayReview: (onFlowFinished: () -> Unit) -> Unit,
    developerOptionsEnabled: Boolean,
    isLandscape: Boolean,
    isSmallLandscape: Boolean,
    shareDiagnostics: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val githubRepoForSourceLink = BuildConfig.GITHUB_REPO.trim()
    val useGithubLikeAboutLinks =
        BuildConfig.FLAVOR == "github" ||
            BuildConfig.FLAVOR == "fdroid" ||
            BuildConfig.FLAVOR == "offline"
    val playStoreListingUrl = BuildConfig.PLAY_STORE_URL
    val buildFlavorLabel =
        when (BuildConfig.FLAVOR) {
            "github" -> stringResource(R.string.build_flavor_github)
            "fdroid" -> stringResource(R.string.build_flavor_fdroid)
            "playstore" -> stringResource(R.string.build_flavor_playstore)
            "offline" -> stringResource(R.string.build_flavor_offline)
            else -> BuildConfig.FLAVOR
        }
    val buildTypeLabel =
        when (BuildConfig.BUILD_TYPE) {
            "debug" -> stringResource(R.string.build_type_debug)
            "devRelease" -> stringResource(R.string.build_type_dev_release)
            "release" -> stringResource(R.string.build_type_release)
            else -> BuildConfig.BUILD_TYPE
        }
    val buildVariantToastText = stringResource(R.string.about_build_variant_format, buildFlavorLabel, buildTypeLabel)
    val developerOptionsUnlockedToast = stringResource(R.string.settings_developer_options_unlocked)
    val diagnosticsTooltip = stringResource(R.string.settings_share_diagnostics)
    val aboutLinkCopiedToast = stringResource(R.string.toast_about_link_copied)
    val authorGithubProfileUrl = stringResource(R.string.about_author_github_profile_url)
    val copyAboutLink =
        remember(context, aboutLinkCopiedToast) {
            { url: String ->
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("link", url))
                Toast
                    .makeText(
                        context,
                        aboutLinkCopiedToast,
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }
    var playStoreAboutUsesListingOnly by remember { mutableStateOf(false) }
    var developerOptionsTapCount by rememberSaveable { mutableIntStateOf(0) }
    val pillPadding = if (isSmallLandscape) PaddingValues(horizontal = 12.dp, vertical = 6.dp) else ButtonDefaults.ContentPadding
    val pillTextStyle = if (isSmallLandscape) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
    val pillIconSize = if (isSmallLandscape) 18.dp else 20.dp
    val pillIconSpacer = if (isSmallLandscape) 6.dp else 8.dp
    val hostActivity = context as? ComponentActivity
    val aboutPillShape = pillShape
    GroupedListColumn {
        GroupedListItem(position = GroupPosition.ONLY) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (isLandscape) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = if (isSmallLandscape) 4.dp else 8.dp, end = if (isSmallLandscape) 4.dp else 8.dp),
                    ) {
                        FilePipeIconButton(
                            onClick = shareDiagnostics,
                            modifier = Modifier.size(40.dp),
                            tooltipLabel = diagnosticsTooltip,
                        ) {
                            FilePipeMaterialRoundedSymbol(
                                name = "bug_report",
                                contentDescription = diagnosticsTooltip,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isSmallLandscape) 16.dp else 20.dp)
                            .padding(top = if (isSmallLandscape) 20.dp else 24.dp, bottom = if (isSmallLandscape) 16.dp else 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.app_version_format,
                                stringResource(R.string.app_name),
                                BuildConfig.VERSION_NAME,
                            ),
                        modifier =
                            Modifier.combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (developerOptionsEnabled) {
                                        onOpenDevOptions()
                                        return@combinedClickable
                                    }
                                    developerOptionsTapCount += 1
                                    val remaining = DEVELOPER_OPTIONS_UNLOCK_TAPS - developerOptionsTapCount
                                    if (remaining > 0) {
                                        Toast
                                            .makeText(
                                                context,
                                                resources.getQuantityString(
                                                    R.plurals.settings_developer_options_taps_remaining,
                                                    remaining,
                                                    remaining,
                                                ),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    } else {
                                        developerOptionsTapCount = 0
                                        onDeveloperOptionsUnlocked()
                                        Toast
                                            .makeText(
                                                context,
                                                developerOptionsUnlockedToast,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        onOpenDevOptions()
                                    }
                                },
                                onLongClick = {
                                    Toast
                                        .makeText(context, buildVariantToastText, Toast.LENGTH_SHORT)
                                        .show()
                                },
                            ),
                        style = if (isSmallLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(if (isSmallLandscape) 8.dp else 10.dp))
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = if (isSmallLandscape) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(if (isSmallLandscape) 12.dp else 20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIconImage(
                            modifier =
                                Modifier
                                    .size(if (isSmallLandscape) 64.dp else 84.dp)
                                    .clip(RoundedCornerShape(percent = 25))
                                    .appClickable(onClick = onOpenIntro),
                        )
                        Spacer(Modifier.width(if (isSmallLandscape) 16.dp else 20.dp))
                        AboutAuthorPhoto(
                            modifier =
                                Modifier
                                    .size(if (isSmallLandscape) 64.dp else 84.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .appClickable { openAboutUrl(context, authorGithubProfileUrl) },
                        )
                    }
                    Spacer(Modifier.height(if (isSmallLandscape) 12.dp else 20.dp))
                    Text(
                        text = stringResource(R.string.settings_byline),
                        style = if (isSmallLandscape) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(if (isSmallLandscape) 14.dp else 24.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.spacedBy(if (isSmallLandscape) 8.dp else 12.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        val hostActivity = context as? ComponentActivity
                        val aboutPillShape = pillShape
                        if (useGithubLikeAboutLinks) {
                            Surface(
                                shape = aboutPillShape,
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier =
                                    Modifier
                                        .clip(aboutPillShape)
                                        .appCombinedClickable(
                                            onClick = { openAboutUrl(context, playStoreListingUrl) },
                                            onLongClick = {
                                                copyAboutLink(playStoreListingUrl)
                                            },
                                            role = Role.Button,
                                        ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(pillPadding),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    AboutPlayStoreIcon(
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(pillIconSize),
                                    )
                                    Spacer(Modifier.width(pillIconSpacer))
                                    Text(
                                        text = stringResource(R.string.settings_rate_on_play_store),
                                        style = pillTextStyle,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false,
                                    )
                                }
                            }
                            Spacer(Modifier.width(if (isSmallLandscape) 10.dp else 12.dp))
                            Surface(
                                shape = aboutPillShape,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier =
                                    Modifier
                                        .clip(aboutPillShape)
                                        .appCombinedClickable(
                                            onClick = {
                                                openAboutUrl(context, "https://github.com/$githubRepoForSourceLink")
                                            },
                                            onLongClick = {
                                                copyAboutLink(
                                                    "https://github.com/$githubRepoForSourceLink",
                                                )
                                            },
                                            role = Role.Button,
                                        ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(pillPadding),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_github_mark),
                                        contentDescription = null,
                                        modifier = Modifier.size(pillIconSize),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Spacer(Modifier.width(pillIconSpacer))
                                    Text(
                                        text = stringResource(R.string.settings_star_on_github),
                                        style = pillTextStyle,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false,
                                    )
                                }
                            }
                        } else {
                            if (playStoreAboutUsesListingOnly) {
                                Surface(
                                    shape = aboutPillShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    modifier =
                                        Modifier
                                            .clip(aboutPillShape)
                                            .appCombinedClickable(
                                                onClick = { openAboutUrl(context, playStoreListingUrl) },
                                                onLongClick = {
                                                    copyAboutLink(playStoreListingUrl)
                                                },
                                                role = Role.Button,
                                            ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(pillPadding),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        AboutPlayStoreIcon(
                                            tint = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(pillIconSize),
                                        )
                                        Spacer(Modifier.width(pillIconSpacer))
                                        Text(
                                            text = stringResource(R.string.settings_rate_on_play_store),
                                            style = pillTextStyle,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            softWrap = false,
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = aboutPillShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    modifier =
                                        Modifier
                                            .clip(aboutPillShape)
                                            .appCombinedClickable(
                                                onClick = {
                                                    if (hostActivity != null) {
                                                        onLaunchPlayReview { playStoreAboutUsesListingOnly = true }
                                                    }
                                                },
                                                onLongClick = {
                                                    copyAboutLink(playStoreListingUrl)
                                                },
                                                role = Role.Button,
                                            ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(pillPadding),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        AboutPlayStoreIcon(
                                            tint = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(pillIconSize),
                                        )
                                        Spacer(Modifier.width(pillIconSpacer))
                                        Text(
                                            text = stringResource(R.string.settings_rate_on_play_store),
                                            style = pillTextStyle,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            softWrap = false,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(if (isSmallLandscape) 10.dp else 12.dp))
                            Surface(
                                shape = aboutPillShape,
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier =
                                    Modifier
                                        .clip(aboutPillShape)
                                        .appCombinedClickable(
                                            onClick = {
                                                openAboutUrl(context, "https://github.com/$githubRepoForSourceLink")
                                            },
                                            onLongClick = {
                                                copyAboutLink(
                                                    "https://github.com/$githubRepoForSourceLink",
                                                )
                                            },
                                            role = Role.Button,
                                        ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(pillPadding),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_github_mark),
                                        contentDescription = null,
                                        modifier = Modifier.size(pillIconSize),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(pillIconSpacer))
                                    Text(
                                        text = stringResource(R.string.settings_star_on_github),
                                        style = pillTextStyle,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(if (isSmallLandscape) 16.dp else 24.dp))
                    AboutOtherAppsAndLinks(
                        context = context,
                        copyLinkToClipboard = copyAboutLink,
                        isSmallLandscape = isSmallLandscape,
                    )
                }
            }
        }
    }
}
