package dev.bikram.filepipe.ui.screens.historydetail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.filepipe.data.repository.RunHistoryRepository
import dev.bikram.filepipe.domain.model.FileMoved
import dev.bikram.filepipe.domain.model.RunHistory
import dev.bikram.filepipe.domain.usecase.UndoRunUseCase
import dev.bikram.filepipe.ui.feedback.toUserMessage
import dev.bikram.filepipe.ui.navigation.Screen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val runHistoryRepository: RunHistoryRepository,
        private val undoRunUseCase: UndoRunUseCase,
        @param:ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        private val historyId: Long = savedStateHandle[Screen.HistoryDetail.ARG_HISTORY_ID] ?: 0L

        private val _history = MutableStateFlow<RunHistory?>(null)
        val history: StateFlow<RunHistory?> = _history.asStateFlow()

        val files: StateFlow<List<FileMoved>> =
            runHistoryRepository
                .getFilesForRun(historyId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        // One-shot snackbar messages: a Channel so each is delivered exactly once.
        private val _userMessages = Channel<String>(Channel.BUFFERED)
        val userMessages: Flow<String> = _userMessages.receiveAsFlow()

        init {
            viewModelScope.launch {
                _history.value = runHistoryRepository.getHistoryById(historyId)
            }
        }

        fun undoRun() =
            viewModelScope.launch {
                val result = undoRunUseCase(historyId)
                _userMessages.trySend(result.toUserMessage(appContext))
                _history.value = runHistoryRepository.getHistoryById(historyId)
            }
    }
