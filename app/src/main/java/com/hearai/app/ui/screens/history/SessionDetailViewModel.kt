package com.hearai.app.ui.screens.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearai.app.data.model.Summary
import com.hearai.app.data.model.TranscriptSegment
import com.hearai.app.data.repo.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    sessionRepository: SessionRepository,
) : ViewModel() {

    val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    val segments: StateFlow<List<TranscriptSegment>> = sessionRepository.observeSegments(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summaries: StateFlow<List<Summary>> = sessionRepository.observeSummaries(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** §6.7: export (share as text). */
    fun exportAsText(): String =
        segments.value.joinToString("\n") { "[${it.detectedLanguage}] ${it.text}" }
}
