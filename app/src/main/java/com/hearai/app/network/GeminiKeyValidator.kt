package com.hearai.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

sealed interface KeyValidationResult {
    data object Valid : KeyValidationResult
    data class Invalid(val reason: String) : KeyValidationResult
    data class NetworkError(val reason: String) : KeyValidationResult
}

/**
 * §5 API Key Management: "Validate the key on entry with a trivial low-cost API call before
 * accepting it; show a clear success/failure state." Uses the cheapest possible call —
 * ListModels — purely to confirm the key authenticates, not to spend generation quota.
 */
@Singleton
class GeminiKeyValidator @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun validate(apiKey: String): KeyValidationResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext KeyValidationResult.Invalid("Key is empty")

        val request = Request.Builder()
            .url("$MODELS_ENDPOINT?key=$apiKey")
            .get()
            .build()

        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> KeyValidationResult.Valid
                    response.code == 400 || response.code == 401 || response.code == 403 ->
                        KeyValidationResult.Invalid("Google rejected this key (HTTP ${response.code})")
                    else -> KeyValidationResult.NetworkError("Unexpected response (HTTP ${response.code})")
                }
            }
        }.getOrElse { throwable ->
            KeyValidationResult.NetworkError(throwable.message ?: "Network error")
        }
    }

    private companion object {
        const val MODELS_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
    }
}
