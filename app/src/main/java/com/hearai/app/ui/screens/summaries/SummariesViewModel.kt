package com.hearai.app.ui.screens.summaries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearai.app.audio.ListeningController
import com.hearai.app.data.model.Summary
import com.hearai.app.data.prefs.SecureKeyStore
import com.hearai.app.data.repo.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SummariesViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val listeningController: ListeningController,
    private val secureKeyStore: SecureKeyStore,
) : ViewModel() {

    val summaries: StateFlow<List<Summary>> = sessionRepository.observeAllSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** §6.6: manual "summarize now" action in addition to the automatic interval. */
    fun summarizeNow() {
        val sessionId = listeningController.state.value.sessionId ?: return
        val apiKey = secureKeyStore.getKey() ?: return
        viewModelScope.launch {
            // Summarize everything since the oldest unsent segment isn't tracked separately here;
            // a simple recent window keeps this action cheap and predictable.
            listeningController.summarizeNow(sessionId, apiKey, System.currentTimeMillis() - 5 * 60_000L)
        }
    }
}
