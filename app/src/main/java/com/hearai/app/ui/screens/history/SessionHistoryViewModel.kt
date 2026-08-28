package com.hearai.app.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearai.app.data.model.Session
import com.hearai.app.data.repo.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionHistoryViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val sessions: StateFlow<List<Session>> = sessionRepository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** §6.7: delete action per session. */
    fun deleteSession(session: Session) {
        viewModelScope.launch { sessionRepository.deleteSession(session) }
    }
}
