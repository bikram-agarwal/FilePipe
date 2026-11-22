package dev.bikram.filepipe.ui.screens.historydetail

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.filepipe.data.repository.RunHistoryRepository
import dev.bikram.filepipe.domain.model.FileMoved
import dev.bikram.filepipe.domain.model.RunHistory
import dev.bikram.filepipe.domain.model.hasRecoverableDestination
import dev.bikram.filepipe.domain.usecase.UndoRunUseCase
import dev.bikram.filepipe.domain.usecase.shouldAttemptUndo
import dev.bikram.filepipe.shortcuts.PendingHistoryUndoRequest
import dev.bikram.filepipe.shortcuts.PendingShortcutRepository
import dev.bikram.filepipe.ui.feedback.toUserMessage
import dev.bikram.filepipe.ui.navigation.Screen
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HistoryDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val runHistoryRepository: RunHistoryRepository,
        private val undoRunUseCase: UndoRunUseCase,
        private val pendingShortcutRepository: PendingShortcutRepository,
        @param:ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        private val historyId: Long = savedStateHandle[Screen.HistoryDetail.ARG_HISTORY_ID] ?: 0L

        val history: StateFlow<RunHistory?> =
            runHistoryRepository
                .observeHistoryById(historyId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        val isUndoing: StateFlow<Boolean> =
            undoRunUseCase.activeUndoProgress
                .map { it.containsKey(historyId) }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    undoRunUseCase.isUndoInProgress(historyId),
                )

        val undoProgress: StateFlow<Float?> =
            undoRunUseCase.activeUndoProgress
                .map { it[historyId] }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    undoRunUseCase.getUndoProgress(historyId),
                )

        val pendingHistoryUndoRequest: StateFlow<PendingHistoryUndoRequest?> =
            pendingShortcutRepository.pendingHistoryUndoRequest

        val filesPagingFlow: Flow<PagingData<FileMoved>> =
            runHistoryRepository
                .getFilesForRunPaged(historyId)
                .cachedIn(viewModelScope)

        val fileCount: StateFlow<Int> =
            runHistoryRepository
                .observeFileCountForRun(historyId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

        val undoableFileIds: StateFlow<Set<Long>> =
            runHistoryRepository
                .getFilesForRun(historyId)
                .map { files ->
                    files
                        .asSequence()
                        .filter { it.hasRecoverableDestination && shouldAttemptUndo(it.undoStatus) }
                        .map { it.id }
                        .toSet()
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

        private val _isUndoSelectionMode = MutableStateFlow(false)
        val isUndoSelectionMode: StateFlow<Boolean> = _isUndoSelectionMode.asStateFlow()

        private val _selectedFileIds = MutableStateFlow<Set<Long>>(emptySet())
        val selectedFileIds: StateFlow<Set<Long>> = _selectedFileIds.asStateFlow()

        // One-shot snackbar messages: a Channel so each is delivered exactly once.
        private val _userMessages = Channel<String>(Channel.BUFFERED)
        val userMessages: Flow<String> = _userMessages.receiveAsFlow()

        fun enterUndoSelectionMode() {
            if (undoRunUseCase.isUndoInProgress(historyId)) return
            val available = undoableFileIds.value
            if (available.isEmpty()) return
            _selectedFileIds.value = available
            _isUndoSelectionMode.value = true
        }

        fun cancelUndoSelectionMode() {
            _isUndoSelectionMode.value = false
            _selectedFileIds.value = emptySet()
        }

        fun toggleFileSelection(fileId: Long) {
            if (!_isUndoSelectionMode.value || undoRunUseCase.isUndoInProgress(historyId)) return
            if (fileId !in undoableFileIds.value) return
            _selectedFileIds.update { current ->
                if (fileId in current) current - fileId else current + fileId
            }
        }

        fun toggleSelectAllUndoFiles() {
            if (!_isUndoSelectionMode.value || undoRunUseCase.isUndoInProgress(historyId)) return
            val available = undoableFileIds.value
            _selectedFileIds.update { current ->
                if (available.isNotEmpty() && current.containsAll(available)) emptySet() else available
            }
        }

        fun confirmSelectedUndo() {
            if (undoRunUseCase.isUndoInProgress(historyId)) return
            val selected = _selectedFileIds.value
            if (selected.isEmpty()) return
            _isUndoSelectionMode.value = false
            _selectedFileIds.value = emptySet()
            launchUndo(selectedFileIds = selected)
        }

        fun undoRunFromNotification(request: PendingHistoryUndoRequest) {
            if (request.historyId != historyId) return
            if (!pendingShortcutRepository.consumePendingHistoryUndo(request)) {
                return
            }
            if (undoRunUseCase.isUndoInProgress(historyId)) {
                NotificationManagerCompat.from(appContext).cancel(request.notificationId)
                return
            }
            _isUndoSelectionMode.value = false
            _selectedFileIds.value = emptySet()
            launchUndo(notificationId = request.notificationId)
        }

        private fun launchUndo(
            selectedFileIds: Set<Long>? = null,
            notificationId: Int? = null,
        ) {
            viewModelScope.launch {
                if (undoRunUseCase.isUndoInProgress(historyId)) return@launch
                try {
                    val result =
                        withContext(NonCancellable) {
                            undoRunUseCase(historyId, selectedFileIds = selectedFileIds)
                        }
                    result.toUserMessage(appContext)?.let { message ->
                        _userMessages.trySend(message)
                    }
                } finally {
                    notificationId?.let { completedNotificationId ->
                        NotificationManagerCompat.from(appContext).cancel(completedNotificationId)
                    }
                }
            }
        }
    }
