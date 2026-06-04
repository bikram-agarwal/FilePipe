package dev.bikram.filepipe

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.bikram.filepipe.data.preferences.AppPreferences
import dev.bikram.filepipe.data.preferences.AppThemeMode
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.domain.usecase.RulesAutoExportTrigger
import dev.bikram.filepipe.manualrun.ManualRunProcessLifecycleBinder
import dev.bikram.filepipe.shortcuts.AppShortcutsManager
import dev.bikram.filepipe.shortcuts.PendingShortcutRepository
import dev.bikram.filepipe.ui.InAppRatingAutoPromptHost
import dev.bikram.filepipe.ui.navigation.AppNavigation
import dev.bikram.filepipe.ui.theme.FilePipeTheme
import dev.bikram.filepipe.update.AppReviewLauncher
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var pendingShortcutRepository: PendingShortcutRepository

    @Inject
    lateinit var rulesAutoExportTrigger: RulesAutoExportTrigger

    @Inject
    lateinit var appReviewLauncher: AppReviewLauncher

    @Inject
    lateinit var manualRunProcessLifecycleBinder: ManualRunProcessLifecycleBinder

    private var isReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        manualRunProcessLifecycleBinder.ensureRegistered()
        splashScreen.setKeepOnScreenCondition { !isReady }
        enableEdgeToEdge()
        handleShortcutIntent(intent)
        handleOpenHistoryIntent(intent)
        handleOpenHistoryDetailIntent(intent)
        handleOpenSettingsUpdatesIntent(intent)

        setContent {
            val preferencesState by userPreferencesRepository.preferencesFlow
                .collectAsStateWithLifecycle(initialValue = null)

            val introSeenAtLaunch =
                remember(preferencesState != null) {
                    preferencesState?.hasSeenIntro
                }

            if (preferencesState != null) {
                isReady = true
            }

            SideEffect {
                preferencesState?.let { prefs ->
                    val nightMode =
                        when (prefs.themeMode) {
                            AppThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                            AppThemeMode.DARK, AppThemeMode.BLACK -> AppCompatDelegate.MODE_NIGHT_YES
                            AppThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        }
                    AppCompatDelegate.setDefaultNightMode(nightMode)
                }
            }

            val preferences = preferencesState ?: AppPreferences.DEFAULT
            FilePipeTheme(
                themeMode = preferences.themeMode,
                colorSource = preferences.colorSource,
                savedCustomSeedHexes = preferences.savedCustomSeedHexes,
                themePaletteStyle = preferences.themePaletteStyle,
                hapticFeedbackEnabled = preferences.hapticFeedbackEnabled,
                shadingIntensity = preferences.shadingIntensity,
                activeCustomSeedHex = preferences.activeCustomSeedHex,
                useGradientBackground = preferences.useGradientBackground,
                progressiveBlurEnabled = preferences.progressiveBlurEnabled,
            ) {
                if (introSeenAtLaunch == null) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Box(Modifier.fillMaxSize())
                    }
                } else {
                    val currentPrefs = preferencesState!!
                    InAppRatingAutoPromptHost(
                        preferences = currentPrefs,
                        activity = this@MainActivity,
                        userPreferencesRepository = userPreferencesRepository,
                        appReviewLauncher = appReviewLauncher,
                    )
                    AppNavigation(
                        hasSeenIntro = currentPrefs.hasSeenIntro,
                        introSeenAtLaunch = introSeenAtLaunch,
                        preferences = currentPrefs,
                        pendingShortcutRepository = pendingShortcutRepository,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
        handleOpenHistoryIntent(intent)
        handleOpenHistoryDetailIntent(intent)
        handleOpenSettingsUpdatesIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch {
            rulesAutoExportTrigger.flushIfPending()
        }
    }

    private fun handleShortcutIntent(intent: Intent?) {
        val sourceIntent = intent ?: return
        val ruleId = sourceIntent.getLongExtra(AppShortcutsManager.EXTRA_SHORTCUT_RULE_ID, -1L)
        if (ruleId != -1L) {
            ShortcutManagerCompat.reportShortcutUsed(this, "rule_$ruleId")
            pendingShortcutRepository.requestRunRule(ruleId)
            sourceIntent.removeExtra(AppShortcutsManager.EXTRA_SHORTCUT_RULE_ID)
        }
    }

    private fun handleOpenHistoryDetailIntent(intent: Intent?) {
        val sourceIntent = intent ?: return
        val historyId =
            sourceIntent.getLongExtra(
                PendingShortcutRepository.EXTRA_OPEN_HISTORY_DETAIL_ID,
                -1L,
            )
        if (historyId != -1L) {
            pendingShortcutRepository.requestOpenHistoryDetail(historyId)
            sourceIntent.removeExtra(PendingShortcutRepository.EXTRA_OPEN_HISTORY_DETAIL_ID)
        }
    }

    private fun handleOpenHistoryIntent(intent: Intent?) {
        val sourceIntent = intent ?: return
        if (sourceIntent.getBooleanExtra(PendingShortcutRepository.EXTRA_OPEN_HISTORY, false)) {
            pendingShortcutRepository.requestOpenHistory()
            sourceIntent.removeExtra(PendingShortcutRepository.EXTRA_OPEN_HISTORY)
        }
    }

    private fun handleOpenSettingsUpdatesIntent(intent: Intent?) {
        val sourceIntent = intent ?: return
        if (sourceIntent.getBooleanExtra(PendingShortcutRepository.EXTRA_OPEN_SETTINGS_UPDATES, false)) {
            pendingShortcutRepository.requestOpenSettingsForUpdates()
            sourceIntent.removeExtra(PendingShortcutRepository.EXTRA_OPEN_SETTINGS_UPDATES)
        }
    }
}
