package dev.bikram.filepipe.diagnostics

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.SystemClock
import android.os.storage.StorageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.bikram.filepipe.BuildConfig
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.preferences.AppPreferences
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import kotlin.system.exitProcess

object DiagnosticLog {
    private const val DIAGNOSTICS_DIR = "diagnostics"
    private const val LOG_FILE_NAME = "filepipe-diagnostics.log"
    private const val SHARE_FILE_NAME = "filepipe-diagnostics.txt"
    private const val MAX_LOG_BYTES = 256 * 1024

    @Volatile
    private var crashHandlerInstalled = false

    fun installCrashHandler(context: Context) {
        if (crashHandlerInstalled) return
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            record(
                appContext,
                appContext.getString(R.string.diagnostics_uncaught_exception_format, thread.name),
                throwable,
            )
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                exitProcess(2)
            }
        }
        crashHandlerInstalled = true
    }

    fun record(
        context: Context,
        message: String,
        throwable: Throwable? = null,
    ) {
        runCatching {
            val logFile = logFile(context)
            logFile.parentFile?.mkdirs()
            trimIfNeeded(logFile)
            logFile.appendText(
                buildString {
                    append(Instant.now())
                    append(" | ")
                    append(message)
                    append('\n')
                    if (throwable != null) {
                        append(stackTraceText(throwable))
                        append('\n')
                    }
                },
            )
        }
    }

    fun createShareFile(
        context: Context,
        preferences: AppPreferences? = null,
    ): File {
        val shareFile = File(File(context.cacheDir, DIAGNOSTICS_DIR), SHARE_FILE_NAME)
        shareFile.parentFile?.mkdirs()
        val logText = runCatching { logFile(context).readText() }.getOrDefault("")
        shareFile.writeText(
            buildString {
                appendLine(context.getString(R.string.diagnostics_title))
                appendLine(context.getString(R.string.diagnostics_generated_format, Instant.now().toString()))
                appendLine(context.getString(R.string.diagnostics_package_format, context.packageName))
                appendLine(context.getString(R.string.diagnostics_version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                appendLine(context.getString(R.string.diagnostics_flavor_format, BuildConfig.FLAVOR))
                appendLine(context.getString(R.string.diagnostics_build_type_format, BuildConfig.BUILD_TYPE))
                appendLine(context.getString(R.string.diagnostics_device_format, Build.MANUFACTURER, Build.MODEL))
                appendLine(context.getString(R.string.diagnostics_android_format, Build.VERSION.RELEASE, Build.VERSION.SDK_INT))
                appendLine()
                appendSystemSnapshot(context)
                appendLine()
                appendPreferencesSnapshot(context, preferences)
                appendLine()
                appendDiagnosticSection(context.getString(R.string.diagnostics_section_app_log))
                append(logText.ifBlank { context.getString(R.string.diagnostics_no_app_log_entries) })
            },
        )
        return shareFile
    }

    fun clear(context: Context) {
        runCatching {
            val logFile = logFile(context)
            if (logFile.exists()) {
                logFile.writeText("")
            }
        }
    }

    private fun logFile(context: Context): File = File(File(context.filesDir, DIAGNOSTICS_DIR), LOG_FILE_NAME)

    private fun StringBuilder.appendSystemSnapshot(context: Context) {
        val packageManager = context.packageManager
        val packageInfo =
            runCatching {
                packageManager.getPackageInfo(context.packageName, 0)
            }.getOrNull()
        val appInfo = packageInfo?.applicationInfo
        val notificationManagerCompat = NotificationManagerCompat.from(context)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        appendDiagnosticSection(context.getString(R.string.diagnostics_section_environment))
        appendLine(context.getString(R.string.diagnostics_locale_format, Locale.getDefault().toString()))
        appendLine(context.getString(R.string.diagnostics_timezone_format, TimeZone.getDefault().id))
        appendLine(context.getString(R.string.diagnostics_uptime_format, SystemClock.uptimeMillis()))
        appendLine(context.getString(R.string.diagnostics_elapsed_realtime_format, SystemClock.elapsedRealtime()))
        appendLine(context.getString(R.string.diagnostics_target_sdk_format, appInfo?.targetSdkVersion?.toString() ?: unknownValue(context)))
        appendLine(
            context.getString(
                R.string.diagnostics_first_install_format,
                packageInfo?.firstInstallTime?.let(Instant::ofEpochMilli)?.toString() ?: unknownValue(context),
            ),
        )
        appendLine(
            context.getString(
                R.string.diagnostics_last_update_format,
                packageInfo?.lastUpdateTime?.let(Instant::ofEpochMilli)?.toString() ?: unknownValue(context),
            ),
        )
        appendLine(context.getString(R.string.diagnostics_installer_format, installerPackageName(context)))
        val filesDirAllocatableBytes = allocatableBytes(context, context.filesDir)
        appendLine(
            context.resources.getQuantityString(
                R.plurals.diagnostics_files_dir_space_format,
                filesDirAllocatableBytes.toInt(),
                filesDirAllocatableBytes,
            ),
        )
        val cacheDirAllocatableBytes = allocatableBytes(context, context.cacheDir)
        appendLine(
            context.resources.getQuantityString(
                R.plurals.diagnostics_cache_dir_space_format,
                cacheDirAllocatableBytes.toInt(),
                cacheDirAllocatableBytes,
            ),
        )
        appendLine(context.getString(R.string.diagnostics_external_storage_state_format, Environment.getExternalStorageState()))
        appendLine()
        appendDiagnosticSection(context.getString(R.string.diagnostics_section_permissions_app_access))
        appendLine(context.getString(R.string.diagnostics_notifications_enabled_format, notificationManagerCompat.areNotificationsEnabled().toString()))
        appendLine(context.getString(R.string.diagnostics_post_notifications_granted_format, postNotificationsGranted(context)))
        appendLine(context.getString(R.string.diagnostics_all_files_access_granted_format, allFilesAccessGranted()))
        appendLine(
            context.getString(
                R.string.diagnostics_ignoring_battery_optimizations_format,
                powerManager.isIgnoringBatteryOptimizations(context.packageName).toString(),
            ),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            appendLine(
                context.getString(
                    R.string.diagnostics_exact_alarms_allowed_format,
                    alarmManager.canScheduleExactAlarms().toString(),
                ),
            )
        } else {
            appendLine(
                context.getString(
                    R.string.diagnostics_exact_alarms_allowed_format,
                    context.getString(R.string.diagnostics_value_not_required),
                ),
            )
        }
        appendPersistedUriPermissions(context)
        appendLine()
        appendNotificationChannels(context)
    }

    private fun StringBuilder.appendPreferencesSnapshot(
        context: Context,
        preferences: AppPreferences?,
    ) {
        appendDiagnosticSection(context.getString(R.string.diagnostics_section_settings))
        if (preferences == null) {
            appendLine(context.getString(R.string.diagnostics_settings_snapshot_unavailable))
            return
        }
        appendLine(context.getString(R.string.diagnostics_theme_mode_format, preferences.themeMode.toString()))
        appendLine(context.getString(R.string.diagnostics_color_source_format, preferences.colorSource.toString()))
        appendLine(context.getString(R.string.diagnostics_palette_style_format, preferences.themePaletteStyle.toString()))
        appendLine(context.getString(R.string.diagnostics_saved_custom_seeds_format, preferences.savedCustomSeedHexes.size))
        appendLine(context.getString(R.string.diagnostics_active_custom_seed_format, preferences.activeCustomSeedHex.isNotBlank().toString()))
        appendLine(context.getString(R.string.diagnostics_gradient_background_format, preferences.useGradientBackground.toString()))
        appendLine(context.getString(R.string.diagnostics_surface_shading_format, preferences.shadingIntensity.toString()))
        appendLine(context.getString(R.string.diagnostics_folder_access_mode_format, preferences.folderAccessMode.toString()))
        appendLine(context.getString(R.string.diagnostics_log_retention_days_format, preferences.logRetentionDays))
        appendLine(context.getString(R.string.diagnostics_auto_export_rule_change_format, preferences.autoExportOnRuleChange.toString()))
        appendLine(context.getString(R.string.diagnostics_scheduled_export_enabled_format, preferences.scheduledExportEnabled.toString()))
        appendLine(context.getString(R.string.diagnostics_local_backup_folder_format, redactedLocation(context, preferences.exportFolderUri)))
        appendLine(context.getString(R.string.diagnostics_cloud_backup_folder_format, redactedLocation(context, preferences.cloudExportFolderUri)))
        appendLine(context.getString(R.string.diagnostics_update_check_schedule_format, preferences.updateCheckSchedule.toString()))
        appendLine(context.getString(R.string.diagnostics_notify_new_updates_format, preferences.notifyOnNewUpdates.toString()))
        appendLine(context.getString(R.string.diagnostics_save_apk_downloads_format, preferences.saveUpdateApkToDownloads.toString()))
        appendLine(context.getString(R.string.diagnostics_apk_downloads_copy_succeeded_format, preferences.updateApkDownloadsCopySucceeded.toString()))
        appendLine(context.getString(R.string.diagnostics_haptics_enabled_format, preferences.hapticFeedbackEnabled.toString()))
        appendLine(context.getString(R.string.diagnostics_progressive_blur_enabled_format, preferences.progressiveBlurEnabled.toString()))
        appendLine(context.getString(R.string.diagnostics_swipe_start_to_end_format, preferences.swipeStartToEnd.toString()))
        appendLine(context.getString(R.string.diagnostics_swipe_end_to_start_format, preferences.swipeEndToStart.toString()))
        appendLine(context.getString(R.string.diagnostics_bookmarked_folders_format, preferences.bookmarkedFolders.size))
        appendLine(context.getString(R.string.diagnostics_intro_seen_format, preferences.hasSeenIntro.toString()))
        appendLine(context.getString(R.string.diagnostics_in_app_review_never_ask_again_format, preferences.inAppReviewAutoNeverAskAgain.toString()))
    }

    private fun StringBuilder.appendPersistedUriPermissions(context: Context) {
        val permissions = context.contentResolver.persistedUriPermissions
        val readCount = permissions.count { it.isReadPermission }
        val writeCount = permissions.count { it.isWritePermission }
        val treeCount = permissions.count { it.uri.toString().contains("/tree/") }
        appendLine(context.getString(R.string.diagnostics_persisted_uri_permissions_format, permissions.size))
        appendLine(context.getString(R.string.diagnostics_persisted_uri_read_grants_format, readCount))
        appendLine(context.getString(R.string.diagnostics_persisted_uri_write_grants_format, writeCount))
        appendLine(context.getString(R.string.diagnostics_persisted_tree_grants_format, treeCount))
    }

    private fun StringBuilder.appendNotificationChannels(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels =
            runCatching {
                notificationManager.notificationChannels.sortedBy { channel -> channel.id }
            }.getOrDefault(emptyList())
        appendDiagnosticSection(context.getString(R.string.diagnostics_section_notification_channels))
        if (channels.isEmpty()) {
            appendLine(context.getString(R.string.diagnostics_no_channels_registered))
            return
        }
        channels.forEach { channel ->
            appendLine(
                context.getString(
                    R.string.diagnostics_notification_channel_format,
                    channel.id,
                    channel.importance,
                    (channel.sound != null).toString(),
                    channel.shouldVibrate().toString(),
                    channel.shouldShowLights().toString(),
                    channel.canBypassDnd().toString(),
                    channel.lockscreenVisibility,
                ),
            )
        }
    }

    private fun allocatableBytes(
        context: Context,
        directory: File,
    ): Long {
        val storageManager = context.getSystemService(StorageManager::class.java) ?: return directory.usableSpace
        return runCatching {
            storageManager.getAllocatableBytes(storageManager.getUuidForPath(directory))
        }.getOrDefault(directory.usableSpace)
    }

    private fun postNotificationsGranted(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            ).toString()
        } else {
            context.getString(R.string.diagnostics_value_not_required)
        }

    private fun allFilesAccessGranted(): String = Environment.isExternalStorageManager().toString()

    private fun installerPackageName(context: Context): String =
        runCatching {
            context.packageManager
                .getInstallSourceInfo(context.packageName)
                .installingPackageName
                .orEmpty()
                .ifBlank { unknownValue(context) }
        }.getOrDefault(unknownValue(context))

    private fun redactedLocation(
        context: Context,
        value: String,
    ): String =
        when {
            value.isBlank() -> {
                context.getString(R.string.diagnostics_value_not_configured)
            }

            value.startsWith("content://") -> {
                context.getString(R.string.diagnostics_value_configured_content_uri)
            }

            value.startsWith("/") -> {
                context.getString(R.string.diagnostics_value_configured_filesystem_path)
            }

            else -> {
                context.getString(
                    R.string.diagnostics_value_configured_reference_format,
                    value.substringBefore(':', missingDelimiterValue = unknownValue(context)),
                )
            }
        }

    private fun unknownValue(context: Context): String = context.getString(R.string.diagnostics_value_unknown)

    private fun StringBuilder.appendDiagnosticSection(title: String) {
        appendLine(title)
        appendLine("=".repeat(title.length))
    }

    private fun trimIfNeeded(logFile: File) {
        if (!logFile.exists() || logFile.length() <= MAX_LOG_BYTES) return
        val text = logFile.readText()
        val keepFrom = (text.length / 2).coerceAtLeast(0)
        logFile.writeText(text.substring(keepFrom))
    }

    private fun stackTraceText(throwable: Throwable): String {
        val stringWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stringWriter))
        return stringWriter.toString()
    }
}
