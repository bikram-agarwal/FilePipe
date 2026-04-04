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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bikram.filepipe.data.preferences.AppPreferences
import dev.bikram.filepipe.data.preferences.AppThemeMode
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.domain.usecase.RulesAutoExportTrigger
import dev.bikram.filepipe.shortcuts.AppShortcutsManager
import dev.bikram.filepipe.shortcuts.PendingShortcutRepository
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import dev.bikram.filepipe.manualrun.ManualRunForegroundCoordinator
import dev.bikram.filepipe.manualrun.ManualRunForegroundService
import dev.bikram.filepipe.ui.navigation.AppNavigation
import dev.bikram.filepipe.ui.theme.FilePipeTheme
import dagger.hilt.android.AndroidEntryPoint
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
    lateinit var manualRunForegroundCoordinator: ManualRunForegroundCoordinator

    private val processManualRunObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            stopService(Intent(this@MainActivity, ManualRunForegroundService::class.java))
        }

        override fun onStop(owner: LifecycleOwner) {
            if (manualRunForegroundCoordinator.isManualRunActive()) {
                ContextCompat.startForegroundService(
                    this@MainActivity,
                    Intent(this@MainActivity, ManualRunForegroundService::class.java)
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShortcutIntent(intent)
        handleOpenHistoryDetailIntent(intent)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processManualRunObserver)

        setContent {
            val preferences by userPreferencesRepository.preferencesFlow
                .collectAsStateWithLifecycle(initialValue = AppPreferences.DEFAULT)

            var introSeenAtLaunch by remember { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(Unit) {
                introSeenAtLaunch = userPreferencesRepository.getPreferencesSnapshot().hasSeenIntro
            }

            SideEffect {
                val nightMode = when (preferences.themeMode) {
                    AppThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    AppThemeMode.DARK, AppThemeMode.BLACK -> AppCompatDelegate.MODE_NIGHT_YES
                    AppThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(nightMode)
            }

            FilePipeTheme(
                themeMode = preferences.themeMode,
                colorSource = preferences.colorSource,
                themePaletteStyle = preferences.themePaletteStyle,
                hapticFeedbackEnabled = preferences.hapticFeedbackEnabled
            ) {
                if (introSeenAtLaunch == null) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(Modifier.fillMaxSize())
                    }
                } else {
                    AppNavigation(
                        hasSeenIntro = preferences.hasSeenIntro,
                        introSeenAtLaunch = introSeenAtLaunch!!,
                        preferences = preferences,
                        pendingShortcutRepository = pendingShortcutRepository
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processManualRunObserver)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
        handleOpenHistoryDetailIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch {
            rulesAutoExportTrigger.flushIfPending()
        }
    }

    private fun handleShortcutIntent(intent: Intent?) {
        val ruleId = intent?.getLongExtra(AppShortcutsManager.EXTRA_SHORTCUT_RULE_ID, -1L) ?: -1L
        if (ruleId != -1L) {
            pendingShortcutRepository.requestRunRule(ruleId)
        }
    }

    private fun handleOpenHistoryDetailIntent(intent: Intent?) {
        val historyId = intent?.getLongExtra(
            PendingShortcutRepository.EXTRA_OPEN_HISTORY_DETAIL_ID,
            -1L
        ) ?: -1L
        if (historyId != -1L) {
            pendingShortcutRepository.requestOpenHistoryDetail(historyId)
            intent?.removeExtra(PendingShortcutRepository.EXTRA_OPEN_HISTORY_DETAIL_ID)
        }
    }
}

