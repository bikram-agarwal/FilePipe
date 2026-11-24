package dev.bikram.filepipe.screenshots

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import android.view.inspector.WindowInspector
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTestConfig
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dagger.hilt.android.EntryPointAccessors
import dev.bikram.filepipe.BuildConfig
import dev.bikram.filepipe.MainActivity
import dev.bikram.filepipe.data.preferences.AppColorSource
import dev.bikram.filepipe.data.preferences.AppThemeMode
import dev.bikram.filepipe.di.SettingsDependenciesEntryPoint
import dev.bikram.filepipe.domain.export.buildAppBackupJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * End-to-end Fastlane screenshot generator for FilePipe (8 phone + 4 tablet shots).
 *
 * Skipped during regular `connectedAndroidTest` runs unless `-e captureScreenshots true` is
 * passed via instrumentation arguments (see root `capture_screenshots.ps1`).
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FastlaneScreenshotsTest {
    @get:Rule(order = 0)
    val globalTimeout: Timeout = Timeout.seconds(45)

    @get:Rule(order = 1)
    val composeRule =
        createEmptyComposeRule(
            ComposeUiTestConfig(effectContext = UnconfinedTestDispatcher()),
        )

    private lateinit var targetContext: Context
    private lateinit var entryPoint: SettingsDependenciesEntryPoint

    @Before
    fun setUp() {
        val shouldCapture =
            InstrumentationRegistry.getArguments().getString(ARG_CAPTURE_SCREENSHOTS) == "true"
        Assume.assumeTrue(
            "Skipping screenshot suite (pass -e $ARG_CAPTURE_SCREENSHOTS true to run)",
            shouldCapture,
        )
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        entryPoint =
            EntryPointAccessors.fromApplication(
                targetContext.applicationContext,
                SettingsDependenciesEntryPoint::class.java,
            )
        ScreenshotDemoData.ensureDemoDirectoriesExist()
    }

    @Test
    fun seedDemoStateOnly() {
        val preferences =
            ScreenshotDemoData.buildBasePreferences(
                themeMode = AppThemeMode.DARK,
                colorSource = AppColorSource.CURATED_GROVE,
            )
        val backupJson = ScreenshotDemoData.buildPhoneDateGroupedHistoryBackupJson(preferences)
        seedBackupState(backupJson)
    }

    @Test
    fun phone1Rules() {
        val preferences =
            ScreenshotDemoData.buildBasePreferences(
                themeMode = AppThemeMode.DARK,
                colorSource = AppColorSource.CURATED_GROVE,
            )
        val backupJson =
            buildAppBackupJson(
                rules = ScreenshotDemoData.buildPhoneRules(),
                settings = preferences,
            )
        seedAndLaunch(backupJson) {
            waitForText("Videos")
            waitForText("Installables")
            captureDeviceScreenshot("1_rules.jpg")
        }
    }

    @Test
    fun phone2Multiselect() {
        val preferences =
            ScreenshotDemoData.buildBasePreferences(
                themeMode = AppThemeMode.LIGHT,
                colorSource = AppColorSource.CURATED_EMBER,
            )
        val backupJson =
            buildAppBackupJson(
                rules = ScreenshotDemoData.buildPhoneRules(),
                settings = preferences,
            )
        seedAndLaunch(backupJson) {
            waitForText("Videos")
            selectThreeRules("Videos", "Images", "Music")
            waitForContentDescription("Cancel selection")
            captureDeviceScreenshot("2_multiselect.jpg")
        }
    }

    @Test
    fun phone3Templates() {
        val preferences =
            ScreenshotDemoData.buildBasePreferences(
                themeMode = AppThemeMode.DARK,
                colorSource = AppColorSource.CUSTOM,
                activeCustomSeedHex = "#A16207",
                savedCustomSeedHexes = listOf("#A16207"),
            )
        val backupJson =
            buildAppBackupJson(
                rules = ScreenshotDemoData.buildPhoneRules(),
                settings = preferences,
            )
        seedAndLaunch(backupJson) {
            waitForText("Videos")
            composeRule.onNodeWithContentDescription("Add rule").performClick()
            waitForText("Use template")
            composeRule.onNodeWithText("Use template").performClick()
            waitForText("Screenshots")
            waitForText("All Files")
            captureDeviceScreenshot("3_templates.jpg")
        }
    }

    @Test
    fun phone4Edit() {
        val preferences =
            ScreenshotDemoData.buildBasePreferences(
                themeMode = AppThemeMode.DARK,
                colorSource = AppColorSource.CURATED_GROVE,
            )
        val backupJson =
            buildAppBackupJson(
                rules = ScreenshotDemoData.buildPhoneRules(),
                settings = preferences,
            )
        seedAndLaunch(backupJson) {
            waitForText("Videos")
            composeRule.onNodeWithContentDescription("Add rule").performClick()
            waitForText("Use template")
            composeRule.onNodeWithText("Use template").performClick()
            waitForText("jpg, jpeg, png, gif, heic, webp, bmp")
            composeRule.onNodeWithText("jpg, jpeg, png, gif, heic, webp, bmp").performClick()
            waitForText("Source folders")
            waitForText("Save")
            captureDeviceScreenshot("4_edit.jpg")
        }
    }

    @Test
    fun phone5Icons() {
        val preferences =
            ScreenshotDemoData.buildBasePreferences(
                themeMode = AppThemeMode.DARK,
                colorSource = AppColorSource.CURATED_GROVE,
            )
        val backupJson =
            buildAppBackupJson(
                rules = ScreenshotDemoData.buildPhoneRules(),
                settings = preferences,
            )
        seedAndLaunch(backupJson) {
            waitForText("Videos")
            composeRule.onNodeWithContentDescription("Add rule").performClick()
            waitForText("Use template")
            composeRule.onNodeWithText("Use template").performClick()
            waitForText("jpg, jpeg, png, gif, heic, webp, bmp")
            composeRule.onNodeWithText("jpg, jpeg, png, gif, heic, webp, bmp").performClick()
            waitForText("Source folders")
            composeRule.onNodeWithContentDescription("Choose rule icon").performClick()
            waitForText("Preset icons")
            composeRule
                .onNode(hasParent(hasContentDescription("Custom emoji")), useUnmergedTree = true)
                .performTextInput("💡")
            captureDeviceScreenshot("5_icons.jpg")
        }
    }

    @Test
    fun phone6History() {
        val preferences =
            ScreenshotDemoData.buildBasePreferences(
                themeMode = AppThemeMode.DARK,
                useBlackTheme = true,
                colorSource = AppColorSource.CURATED_OCEAN,
            )
        val backupJson = ScreenshotDemoData.buildPhoneDateGroupedHistoryBackupJson(preferences)
        seedAndLaunch(backupJson) {
            waitForText("Videos")
            composeRule.onNodeWithContentDescription("History").performClick()
            waitForText("Yesterday")
            captureDeviceScreenshot("6_history.jpg")
        }
    }

    @Test
    fun phone7History() {
        val preferences =
            ScreenshotDemoData.buildBasePreferences(
                themeMode = AppThemeMode.LIGHT,
                colorSource = AppColorSource.DEFAULT,
            )
        val backupJson =
            ScreenshotDemoData.buildMockMoveDetailBackupJson(
                settings = preferences,
                includeTabletStatusVariety = false,
            )
        seedAndLaunch(backupJson) {
            waitForText("Mock move")
            composeRule.onNodeWithContentDescription("History").performClick()
            waitForText("10 files moved", substring = true)
            composeRule.onAllNodesWithText("Mock move").onFirst().performClick()
            waitForText("Run summary")
            waitForText("conference-recording-01.mp4")
            // Selective undo: "Undo?" opens the selection with every file ticked; clear it and
            // pick three, so the shot shows undo isn't all-or-nothing.
            composeRule.onNodeWithText("Undo?").performClick()
            waitForText("Deselect all")
            composeRule.onNodeWithText("Deselect all").performClick()
            listOf(
                "conference-recording-01.mp4",
                "family-photo-archive.jpg",
                "podcast-mixdown.mp3",
            ).forEach { fileName ->
                composeRule.onNodeWithText(fileName).performClick()
            }
            waitForText("Run Undo (3)")
            captureDeviceScreenshot("7_history.jpg")
        }
    }

    @Test
    fun phone8Settings() {
        val preferences =
            ScreenshotDemoData.buildBasePreferences(
                themeMode = AppThemeMode.SYSTEM,
                colorSource = AppColorSource.CURATED_OCEAN,
            )
        val backupJson =
            buildAppBackupJson(
                rules = ScreenshotDemoData.buildPhoneRules(),
                settings = preferences,
            )
        seedAndLaunch(backupJson) {
            waitForText("Videos")
            composeRule.onNodeWithContentDescription("Settings").performClick()
            waitForText("Use pure black dark theme")
            expandCustomAccentPicker()
            waitForText("Select primary color")
            captureDeviceScreenshot("8_settings.jpg")
        }
    }

    @Test
    fun tablet1Rules() {
        val preferences =
            ScreenshotDemoData.buildBasePreferences(
                themeMode = AppThemeMode.LIGHT,
                colorSource = AppColorSource.CURATED_EMBER,
            )
        val backupJson =
            buildAppBackupJson(
                rules = ScreenshotDemoData.buildTabletRules(),
                settings = preferences,
            )
        seedAndLaunch(backupJson) {
            waitForText("Rule details")
            waitForText("Source folders")
            captureDeviceScreenshot("tablet_1_rules.jpg")
        }
    }

    @Test
    fun tablet2Multiselect() {
        val preferences =
            ScreenshotDemoData.buildBasePreferences(
                themeMode = AppThemeMode.LIGHT,
                colorSource = AppColorSource.CURATED_GROVE,
            )
        val backupJson =
            buildAppBackupJson(
                rules = ScreenshotDemoData.buildTabletRules(),
                settings = preferences,
            )
        seedAndLaunch(backupJson) {
            waitForText("Rule details")
            selectThreeRules("Images", "Videos", "Music")
            waitForText("3 selected rules")
            captureDeviceScreenshot("tablet_2_multiselect.jpg")
        }
    }

    @Test
    fun tablet3History() {
        val preferences =
            ScreenshotDemoData.buildBasePreferences(
                themeMode = AppThemeMode.DARK,
                useBlackTheme = true,
                colorSource = AppColorSource.CURATED_HONEY,
            )
        val backupJson =
            ScreenshotDemoData.buildMockMoveDetailBackupJson(
                settings = preferences,
                includeTabletStatusVariety = true,
            )
        seedAndLaunch(backupJson) {
            waitForText("Mock move")
            composeRule.onAllNodesWithText("History").onFirst().performClick()
            waitForContentDescription("Group runs")
            composeRule.onNodeWithContentDescription("Group runs").performClick()
            waitForText("Group by status")
            composeRule.onNodeWithText("Group by status").performClick()
            waitForText("Run summary")
            waitForText("conference-recording-01.mp4")
            captureDeviceScreenshot("tablet_3_history.jpg")
        }
    }

    @Test
    fun tablet4Settings() {
        val preferences =
            ScreenshotDemoData.buildBasePreferences(
                themeMode = AppThemeMode.SYSTEM,
                colorSource = AppColorSource.MATERIAL_YOU,
                savedCustomSeedHexes = listOf("#84CC16"),
            )
        val backupJson =
            buildAppBackupJson(
                rules = ScreenshotDemoData.buildTabletRules(),
                settings = preferences,
            )
        seedAndLaunch(backupJson) {
            waitForText("Rule details")
            composeRule.onAllNodesWithText("Settings").onFirst().performClick()
            waitForText("Use pure black dark theme")
            expandCustomAccentPicker()
            waitForText("Select primary color")
            captureDeviceScreenshot("tablet_4_settings.jpg")
        }
    }

    private fun expandCustomAccentPicker() {
        val horizontalScrollMatcher =
            SemanticsMatcher("HorizontalScrollAxisRange") { semanticsNode ->
                semanticsNode.config.contains(SemanticsProperties.HorizontalScrollAxisRange)
            }
        val accentRowMatcher =
            hasScrollToKeyAction() and
                horizontalScrollMatcher and
                !hasAnyDescendant(hasText("Tonal spot"))
        composeRule
            .onAllNodes(accentRowMatcher)
            .onFirst()
            .performScrollToKey("add_custom_seed")
        composeRule.waitForIdle()
        composeRule
            .onAllNodesWithContentDescription("Add custom accent color")
            .onFirst()
            .performClick()
    }

    private fun seedBackupState(backupJson: String) {
        runBlocking {
            entryPoint
                .importRulesUseCase()
                .restoreFromBackupJson(backupJson)
                .getOrThrow()
            entryPoint
                .userPreferencesRepository()
                .setLastSeenAppVersion(BuildConfig.VERSION_NAME)
        }
    }

    private fun seedAndLaunch(
        backupJson: String,
        block: () -> Unit,
    ) {
        finishAllActivities()
        seedBackupState(backupJson)
        val launchIntent =
            Intent(targetContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        targetContext.startActivity(launchIntent)
        try {
            block()
        } finally {
            finishAllActivities()
        }
    }

    private fun finishAllActivities() {
        val stages =
            listOf(
                Stage.PRE_ON_CREATE,
                Stage.CREATED,
                Stage.STARTED,
                Stage.RESUMED,
                Stage.PAUSED,
                Stage.STOPPED,
            )
        val activitiesToFinish = mutableListOf<Activity>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            stages.forEach { stage ->
                activitiesToFinish.addAll(monitor.getActivitiesInStage(stage))
            }
            activitiesToFinish.distinct().forEach { activity ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.finish()
                }
            }
        }
        SystemClock.sleep(250L)
    }

    private fun selectThreeRules(
        firstRuleName: String,
        secondRuleName: String,
        thirdRuleName: String,
    ) {
        val firstLongClickMatcher =
            SemanticsMatcher("onLongClick label == Select $firstRuleName") { semanticsNode ->
                semanticsNode.config
                    .getOrElseNullable(SemanticsActions.OnLongClick) { null }
                    ?.label == "Select $firstRuleName"
            }
        composeRule
            .onAllNodes(firstLongClickMatcher, useUnmergedTree = true)
            .onFirst()
            .performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.waitForIdle()

        listOf(secondRuleName, thirdRuleName).forEach { nextRuleName ->
            val selectClickMatcher =
                SemanticsMatcher("onClick label == Select $nextRuleName") { semanticsNode ->
                    semanticsNode.config
                        .getOrElseNullable(SemanticsActions.OnClick) { null }
                        ?.label == "Select $nextRuleName"
                }
            composeRule
                .onAllNodes(selectClickMatcher, useUnmergedTree = true)
                .onFirst()
                .performSemanticsAction(SemanticsActions.OnClick)
            composeRule.waitForIdle()
        }
    }

    private fun waitForText(
        text: String,
        substring: Boolean = false,
        timeoutMillis: Long = 15_000L,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule
                .onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun waitForContentDescription(
        description: String,
        timeoutMillis: Long = 15_000L,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule
                .onAllNodesWithContentDescription(description)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun hideSoftKeyboardIfVisible() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val resumedActivities =
                ActivityLifecycleMonitorRegistry
                    .getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
            val inputMethodManager =
                targetContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            resumedActivities.forEach { activity ->
                activity.currentFocus?.clearFocus()
            }
            // Bottom sheets and dialogs are separate windows (e.g. the rule-icon sheet's emoji
            // field in 5_icons), so hide the IME from every window, not just the activity's.
            val windowRootViews =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WindowInspector.getGlobalWindowViews()
                } else {
                    resumedActivities.map { activity -> activity.window.decorView }
                }
            windowRootViews.forEach { rootView ->
                inputMethodManager?.hideSoftInputFromWindow(rootView.windowToken, 0)
            }
        }
    }

    private fun captureDeviceScreenshot(fileName: String) {
        composeRule.waitForIdle()
        hideSoftKeyboardIfVisible()
        SystemClock.sleep(SETTLE_DELAY_MILLIS)
        composeRule.waitForIdle()

        val outputDirectory =
            targetContext.getExternalFilesDir(SCREENSHOT_SUBDIR)
                ?: File(targetContext.filesDir, SCREENSHOT_SUBDIR)
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }
        val outputFile = File(outputDirectory, fileName)
        val bitmap: Bitmap =
            checkNotNull(
                InstrumentationRegistry
                    .getInstrumentation()
                    .uiAutomation
                    .takeScreenshot(),
            ) {
                "UiAutomation.takeScreenshot() returned null for $fileName"
            }
        try {
            FileOutputStream(outputFile).use { outputStream ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)) {
                    "Failed to encode JPEG screenshot to ${outputFile.absolutePath}"
                }
                outputStream.flush()
            }
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        const val ARG_CAPTURE_SCREENSHOTS = "captureScreenshots"
        private const val SCREENSHOT_SUBDIR = "screenshots"
        private const val JPEG_QUALITY = 94
        private const val SETTLE_DELAY_MILLIS = 750L
    }
}
