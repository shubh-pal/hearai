package com.hearai.app.ui.screens.home

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hearai.app.audio.ListeningController
import com.hearai.app.audio.ListeningService
import com.hearai.app.audio.ListeningUiState
import com.hearai.app.data.prefs.SecureKeyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    listeningController: ListeningController,
    private val secureKeyStore: SecureKeyStore,
) : AndroidViewModel(application) {

    val listeningState: StateFlow<ListeningUiState> = listeningController.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListeningUiState())

    val hasApiKey: Boolean get() = secureKeyStore.hasKey()

    /** Starting/stopping is delegated to the foreground service (§4 Mic capture) rather than
     * called directly, so the pipeline survives the Activity being backgrounded. */
    fun toggleListening() {
        val context = getApplication<Application>()
        val intent = Intent(context, ListeningService::class.java)
        if (listeningState.value.isListening) {
            intent.action = ListeningService.ACTION_STOP
            context.startService(intent)
        } else {
            if (!hasApiKey) return
            context.startForegroundService(intent)
        }
    }
}
