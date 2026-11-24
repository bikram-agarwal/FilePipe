# Keep rules for running the screenshot suite against the release-type devRelease build.
# Applied ONLY when building with -PscreenshotsOnDevRelease=true (../capture_screenshots.ps1),
# so normal devRelease/release outputs are unchanged. The androidTest code seeds demo data
# through these app classes, which R8 would otherwise strip or rename.
# Regenerate after changing the screenshot tests: list the app classes they reference with
#   jdeps -verbose:class <androidTest classes>/dev/bikram/filepipe/screenshots

-keep class dev.bikram.filepipe.MainActivity { *; }
-keep class dev.bikram.filepipe.data.preferences.AppColorSource { *; }
-keep class dev.bikram.filepipe.data.preferences.AppPreferences { *; }
-keep class dev.bikram.filepipe.data.preferences.AppPreferences$Companion { *; }
-keep class dev.bikram.filepipe.data.preferences.AppThemeMode { *; }
-keep class dev.bikram.filepipe.data.preferences.FolderAccessMode { *; }
-keep class dev.bikram.filepipe.data.preferences.SwipeAction { *; }
-keep class dev.bikram.filepipe.data.preferences.ThemePaletteStyle { *; }
-keep class dev.bikram.filepipe.data.preferences.UpdateCheckSchedule { *; }
-keep class dev.bikram.filepipe.data.preferences.UserPreferencesRepository { *; }
-keep class dev.bikram.filepipe.devtools.DevMockFileMove { *; }
-keep class dev.bikram.filepipe.di.SettingsDependenciesEntryPoint { *; }
-keep class dev.bikram.filepipe.domain.export.RulesBackupJsonKt { *; }
-keep class dev.bikram.filepipe.domain.model.ConflictPolicy { *; }
-keep class dev.bikram.filepipe.domain.model.FileMoved { *; }
-keep class dev.bikram.filepipe.domain.model.FileOrientation { *; }
-keep class dev.bikram.filepipe.domain.model.FileUndoStatus { *; }
-keep class dev.bikram.filepipe.domain.model.HistorySortDirection { *; }
-keep class dev.bikram.filepipe.domain.model.HistorySortKey { *; }
-keep class dev.bikram.filepipe.domain.model.OperationMode { *; }
-keep class dev.bikram.filepipe.domain.model.Rule { *; }
-keep class dev.bikram.filepipe.domain.model.RuleIcon { *; }
-keep class dev.bikram.filepipe.domain.model.RuleSchedule { *; }
-keep class dev.bikram.filepipe.domain.model.RunHistory { *; }
-keep class dev.bikram.filepipe.domain.model.RunStatus { *; }
-keep class dev.bikram.filepipe.domain.model.ScheduleType { *; }
-keep class dev.bikram.filepipe.domain.model.TriggerType { *; }
-keep class dev.bikram.filepipe.domain.usecase.ImportRulesUseCase { *; }

# The Hilt component implementing the entry point the tests use.
-keep class * implements dev.bikram.filepipe.di.SettingsDependenciesEntryPoint { *; }

# Libraries the androidTest APK shares with the app. At runtime the test uses the app APK's copy,
# so anything R8 strips from the app (e.g. androidx.tracing, which only the test runner needs)
# fails with NoClassDefFoundError / NoSuchMethodError. Derived from the test APK's references
# (Compose UI test, coroutines test, AndroidX Test, Room migration tests); kept by package so
# edits to the tests don't need new rules.
-keep class androidx.activity.** { *; }
-keep class androidx.arch.core.** { *; }
-keep class androidx.collection.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.concurrent.futures.** { *; }
-keep class androidx.core.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.room.** { *; }
-keep class androidx.sqlite.** { *; }
-keep class androidx.tracing.** { *; }
-keep class com.google.common.util.concurrent.ListenableFuture { *; }
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }
