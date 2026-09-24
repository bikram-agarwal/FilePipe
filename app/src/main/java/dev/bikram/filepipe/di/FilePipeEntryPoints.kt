package dev.bikram.filepipe.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.data.repository.RuleRepository
import dev.bikram.filepipe.domain.usecase.ExportRulesUseCase
import dev.bikram.filepipe.domain.usecase.ImportRulesUseCase
import dev.bikram.filepipe.domain.usecase.RulesAutoExportTrigger
import dev.bikram.filepipe.update.AppReviewLauncher
import dev.bikram.filepipe.update.PlayInAppUpdateProgressController
import dev.bikram.filepipe.update.PlayInAppUpdateStarter
import dev.bikram.filepipe.update.PlayUpdateSessionHandle
import dev.bikram.filepipe.update.UpdateAvailableNotifier
import dev.bikram.filepipe.update.UpdateCheckWorkScheduler

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsDependenciesEntryPoint {
    fun userPreferencesRepository(): UserPreferencesRepository

    fun exportRulesUseCase(): ExportRulesUseCase

    fun importRulesUseCase(): ImportRulesUseCase

    fun rulesAutoExportTrigger(): RulesAutoExportTrigger

    fun playInAppUpdateStarter(): PlayInAppUpdateStarter

    fun playInAppUpdateProgressController(): PlayInAppUpdateProgressController

    fun playUpdateSessionHandle(): PlayUpdateSessionHandle

    fun updateAvailableNotifier(): UpdateAvailableNotifier

    fun updateCheckWorkScheduler(): UpdateCheckWorkScheduler

    fun appReviewLauncher(): AppReviewLauncher

    fun ruleRepository(): RuleRepository
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DevOptionsDependenciesEntryPoint {
    fun userPreferencesRepository(): UserPreferencesRepository

    fun ruleRepository(): RuleRepository

    fun updateCheckWorkScheduler(): UpdateCheckWorkScheduler

    fun updateAvailableNotifier(): UpdateAvailableNotifier
}
