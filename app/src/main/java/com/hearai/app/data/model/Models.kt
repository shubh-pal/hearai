package com.hearai.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * §9 Rough Data Model — one listening session.
 */
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey val id: String,
    val startTime: Long,
    val endTime: Long?,
    /** Distinct detected languages spoken during the session, for the history list snippet. */
    val detectedLanguages: List<String> = emptyList(),
)

/**
 * A single timestamped, language-tagged piece of transcript text (§4 transcript buffer, §9).
 */
@Entity(tableName = "transcript_segments")
data class TranscriptSegment(
    @PrimaryKey val id: String,
    val sessionId: String,
    val timestamp: Long,
    val detectedLanguage: String,
    val text: String,
    /** Best-effort speaker index if the underlying model ever exposes it. Diarization is v2 (§10). */
    val speaker: Int? = null,
)

/**
 * An AI-generated digest of the transcript since the last summary (§4 Summarizer, §9).
 */
@Entity(tableName = "summaries")
data class Summary(
    @PrimaryKey val id: String,
    val sessionId: String,
    val timeRangeStart: Long,
    val timeRangeEnd: Long,
    val text: String,
)

/** Validation state of a user-supplied Gemini API key (§5 BYOK). */
enum class KeyValidationStatus { UNKNOWN, VALIDATING, VALID, INVALID }

/**
 * Metadata about the stored API key. The key material itself never lives here — see
 * [com.hearai.app.data.prefs.SecureKeyStore], which is backed by EncryptedSharedPreferences.
 */
data class ApiKeyConfig(
    val hasKey: Boolean,
    val validatedAt: Long?,
    val lastValidationStatus: KeyValidationStatus,
)

enum class VadSensitivity { LOW, MEDIUM, HIGH }

enum class AppTheme { LIGHT, DARK, SYSTEM }

/** §6.8 Settings + §9 Settings data model. */
data class AppSettings(
    /** Minutes between automatic summaries; 0 means "off" (§6.8: off / 2 / 5 / 10 / 15). */
    val summarizationIntervalMinutes: Int = 5,
    val vadSensitivity: VadSensitivity = VadSensitivity.MEDIUM,
    val notificationSurfaceEnabled: Boolean = true, // always on while listening, Android requires it
    val overlaySurfaceEnabled: Boolean = false, // opt-in, requires Accessibility permission
    val textSize: TextSizeOption = TextSizeOption.MEDIUM,
    val theme: AppTheme = AppTheme.SYSTEM,
    /** null = keep session history indefinitely until manually deleted (§6.8, §11 open question). */
    val retentionDays: Int? = null,
)

enum class TextSizeOption { SMALL, MEDIUM, LARGE, EXTRA_LARGE }
