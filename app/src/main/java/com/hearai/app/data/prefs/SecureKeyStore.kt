package com.hearai.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hearai.app.data.model.ApiKeyConfig
import com.hearai.app.data.model.KeyValidationStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * §5 API Key Management (BYOK).
 *
 * The user's Gemini API key is stored via EncryptedSharedPreferences (Keystore-backed AES),
 * never in plain text and never logged. Nothing here ever exposes the raw key except
 * [getKey], which is read only at the moment a network call needs it.
 */
@Singleton
class SecureKeyStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getKey(): String? = prefs.getString(KEY_API_KEY, null)

    fun hasKey(): Boolean = !getKey().isNullOrBlank()

    fun setKey(rawKey: String) {
        prefs.edit()
            .putString(KEY_API_KEY, rawKey)
            .putLong(KEY_VALIDATED_AT, 0L)
            .putString(KEY_VALIDATION_STATUS, KeyValidationStatus.UNKNOWN.name)
            .apply()
    }

    /** Removing the key must immediately stop any active listening session (§5). Callers are
     * responsible for stopping [com.hearai.app.audio.ListeningService] when this returns. */
    fun clearKey() {
        prefs.edit().clear().apply()
    }

    fun recordValidationResult(status: KeyValidationStatus, atMillis: Long) {
        prefs.edit()
            .putString(KEY_VALIDATION_STATUS, status.name)
            .putLong(KEY_VALIDATED_AT, atMillis)
            .apply()
    }

    /** Masked key for display in Settings (§5: "view (masked)"), e.g. "AIza••••••••90ab". */
    fun maskedKey(): String? {
        val key = getKey() ?: return null
        if (key.length <= 8) return "•".repeat(key.length)
        return key.take(4) + "•".repeat(8) + key.takeLast(4)
    }

    fun config(): ApiKeyConfig {
        val status = prefs.getString(KEY_VALIDATION_STATUS, null)
            ?.let { runCatching { KeyValidationStatus.valueOf(it) }.getOrNull() }
            ?: KeyValidationStatus.UNKNOWN
        val validatedAt = prefs.getLong(KEY_VALIDATED_AT, 0L).takeIf { it > 0L }
        return ApiKeyConfig(
            hasKey = hasKey(),
            validatedAt = validatedAt,
            lastValidationStatus = status,
        )
    }

    private companion object {
        const val FILE_NAME = "hearai_secure_prefs"
        const val KEY_API_KEY = "gemini_api_key"
        const val KEY_VALIDATED_AT = "gemini_api_key_validated_at"
        const val KEY_VALIDATION_STATUS = "gemini_api_key_validation_status"
    }
}
