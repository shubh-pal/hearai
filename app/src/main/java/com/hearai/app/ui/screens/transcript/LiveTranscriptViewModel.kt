package com.hearai.app.ui.screens.transcript

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearai.app.audio.ListeningController
import com.hearai.app.data.model.TranscriptSegment
import com.hearai.app.data.repo.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LiveTranscriptViewModel @Inject constructor(
    listeningController: ListeningController,
    sessionRepository: SessionRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val segments: StateFlow<List<TranscriptSegment>> = listeningController.state
        .flatMapLatest { state ->
            val sessionId = state.sessionId
            if (sessionId == null) flowOf(emptyList()) else sessionRepository.observeSegments(sessionId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isListening: StateFlow<Boolean> = listeningController.state
        .map { it.isListening }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
