package com.hearai.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hearai.app.data.model.AppSettings
import com.hearai.app.data.model.AppTheme
import com.hearai.app.data.model.TextSizeOption
import com.hearai.app.data.model.VadSensitivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "hearai_settings")

/** §6.8 Settings + §9 Settings data model — everything here is non-secret (the API key lives in
 * [SecureKeyStore] instead). */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val SUMMARIZATION_INTERVAL = intPreferencesKey("summarization_interval_minutes")
        val VAD_SENSITIVITY = stringPreferencesKey("vad_sensitivity")
        val NOTIFICATION_SURFACE = booleanPreferencesKey("notification_surface_enabled")
        val OVERLAY_SURFACE = booleanPreferencesKey("overlay_surface_enabled")
        val TEXT_SIZE = stringPreferencesKey("text_size")
        val THEME = stringPreferencesKey("theme")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            summarizationIntervalMinutes = prefs[Keys.SUMMARIZATION_INTERVAL] ?: 5,
            vadSensitivity = prefs[Keys.VAD_SENSITIVITY]?.let {
                runCatching { VadSensitivity.valueOf(it) }.getOrNull()
            } ?: VadSensitivity.MEDIUM,
            notificationSurfaceEnabled = prefs[Keys.NOTIFICATION_SURFACE] ?: true,
            overlaySurfaceEnabled = prefs[Keys.OVERLAY_SURFACE] ?: false,
            textSize = prefs[Keys.TEXT_SIZE]?.let {
                runCatching { TextSizeOption.valueOf(it) }.getOrNull()
            } ?: TextSizeOption.MEDIUM,
            theme = prefs[Keys.THEME]?.let {
                runCatching { AppTheme.valueOf(it) }.getOrNull()
            } ?: AppTheme.SYSTEM,
            retentionDays = prefs[Keys.RETENTION_DAYS]?.takeIf { it > 0 },
        )
    }

    suspend fun setSummarizationInterval(minutes: Int) {
        context.settingsDataStore.edit { it[Keys.SUMMARIZATION_INTERVAL] = minutes }
    }

    suspend fun setVadSensitivity(sensitivity: VadSensitivity) {
        context.settingsDataStore.edit { it[Keys.VAD_SENSITIVITY] = sensitivity.name }
    }

    /** Notification surface can't be fully disabled while listening — Android requires a
     * foreground service notification (§6.8) — but the toggle is stored for UI purposes. */
    suspend fun setNotificationSurfaceEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.NOTIFICATION_SURFACE] = enabled }
    }

    suspend fun setOverlaySurfaceEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.OVERLAY_SURFACE] = enabled }
    }

    suspend fun setTextSize(size: TextSizeOption) {
        context.settingsDataStore.edit { it[Keys.TEXT_SIZE] = size.name }
    }

    suspend fun setTheme(theme: AppTheme) {
        context.settingsDataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setRetentionDays(days: Int?) {
        context.settingsDataStore.edit {
            if (days == null) it.remove(Keys.RETENTION_DAYS) else it[Keys.RETENTION_DAYS] = days
        }
    }
}
