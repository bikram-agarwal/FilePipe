package dev.bikram.filepipe.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bikram.filepipe.data.preferences.FolderAccessMode
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.data.preferences.treatAsSafUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingPermissionsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    /**
     * Holds the permissions screen radio choice while the onboarding NavBackStackEntry exists.
     * Survives navigating to the rule wizard (composable leaves composition; [remember] does not).
     * After intro is done, seed from stored [AppPreferences.folderAccessMode] so revisiting
     * onboarding from Settings matches the user's setting (first run still defaults to full access).
     */
    private val onboardingFolderAccessSelection = MutableStateFlow(FolderAccessMode.ALL_FILES_PREFERRED)

    init {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.preferencesFlow.first()
            if (!prefs.hasSeenIntro) return@launch
            onboardingFolderAccessSelection.value =
                if (prefs.folderAccessMode.treatAsSafUi()) {
                    FolderAccessMode.SAF_ONLY
                } else {
                    FolderAccessMode.ALL_FILES_PREFERRED
                }
        }
    }
    val onboardingFolderAccessSelectionState: StateFlow<FolderAccessMode> =
        onboardingFolderAccessSelection.asStateFlow()

    fun setOnboardingFolderAccessSelection(mode: FolderAccessMode) {
        onboardingFolderAccessSelection.value = mode
    }

    fun setFolderAccessMode(mode: FolderAccessMode) {
        viewModelScope.launch {
            userPreferencesRepository.setFolderAccessMode(mode)
        }
    }
}
