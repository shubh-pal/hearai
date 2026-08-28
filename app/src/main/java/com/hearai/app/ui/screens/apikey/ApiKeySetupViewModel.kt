package com.hearai.app.ui.screens.apikey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearai.app.data.model.KeyValidationStatus
import com.hearai.app.data.prefs.SecureKeyStore
import com.hearai.app.network.GeminiKeyValidator
import com.hearai.app.network.KeyValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ApiKeySetupUiState(
    val keyInput: String = "",
    val isValidating: Boolean = false,
    val validationStatus: KeyValidationStatus = KeyValidationStatus.UNKNOWN,
    val errorMessage: String? = null,
    val disclosureAcknowledged: Boolean = false,
) {
    /** §6.2: "Continue" is only actionable once the key validated and the disclosure (§5) is
     * acknowledged. */
    val canContinue: Boolean get() = validationStatus == KeyValidationStatus.VALID && disclosureAcknowledged
}

@HiltViewModel
class ApiKeySetupViewModel @Inject constructor(
    private val secureKeyStore: SecureKeyStore,
    private val keyValidator: GeminiKeyValidator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApiKeySetupUiState())
    val uiState: StateFlow<ApiKeySetupUiState> = _uiState.asStateFlow()

    fun onKeyInputChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            keyInput = value,
            validationStatus = KeyValidationStatus.UNKNOWN,
            errorMessage = null,
        )
    }

    fun onDisclosureAcknowledgedChanged(acknowledged: Boolean) {
        _uiState.value = _uiState.value.copy(disclosureAcknowledged = acknowledged)
    }

    /** §5: "Validate the key on entry with a trivial low-cost API call before accepting it;
     * show a clear success/failure state." */
    fun validate() {
        val key = _uiState.value.keyInput.trim()
        if (key.isBlank()) return

        _uiState.value = _uiState.value.copy(isValidating = true, validationStatus = KeyValidationStatus.VALIDATING)
        viewModelScope.launch {
            when (val result = keyValidator.validate(key)) {
                is KeyValidationResult.Valid -> {
                    secureKeyStore.setKey(key)
                    secureKeyStore.recordValidationResult(KeyValidationStatus.VALID, System.currentTimeMillis())
                    _uiState.value = _uiState.value.copy(
                        isValidating = false,
                        validationStatus = KeyValidationStatus.VALID,
                        errorMessage = null,
                    )
                }
                is KeyValidationResult.Invalid -> {
                    _uiState.value = _uiState.value.copy(
                        isValidating = false,
                        validationStatus = KeyValidationStatus.INVALID,
                        errorMessage = result.reason,
                    )
                }
                is KeyValidationResult.NetworkError -> {
                    _uiState.value = _uiState.value.copy(
                        isValidating = false,
                        validationStatus = KeyValidationStatus.UNKNOWN,
                        errorMessage = "Couldn't reach Google right now: ${result.reason}",
                    )
                }
            }
        }
    }
}
