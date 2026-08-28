package com.hearai.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearai.app.data.model.AppSettings
import com.hearai.app.data.model.AppTheme
import com.hearai.app.data.model.TextSizeOption
import com.hearai.app.data.model.VadSensitivity
import com.hearai.app.data.prefs.SecureKeyStore
import com.hearai.app.data.prefs.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val secureKeyStore: SecureKeyStore,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun maskedKey(): String? = secureKeyStore.maskedKey()

    /** §5: removing the key must immediately stop any active listening session. The caller
     * (Settings screen) is responsible for also stopping [com.hearai.app.audio.ListeningService]. */
    fun removeKey() = secureKeyStore.clearKey()

    fun setSummarizationInterval(minutes: Int) = viewModelScope.launch { settingsStore.setSummarizationInterval(minutes) }
    fun setVadSensitivity(sensitivity: VadSensitivity) = viewModelScope.launch { settingsStore.setVadSensitivity(sensitivity) }
    fun setOverlayEnabled(enabled: Boolean) = viewModelScope.launch { settingsStore.setOverlaySurfaceEnabled(enabled) }
    fun setTextSize(size: TextSizeOption) = viewModelScope.launch { settingsStore.setTextSize(size) }
    fun setTheme(theme: AppTheme) = viewModelScope.launch { settingsStore.setTheme(theme) }
    fun setRetentionDays(days: Int?) = viewModelScope.launch { settingsStore.setRetentionDays(days) }
}
