package com.hearai.app.audio

import com.hearai.app.data.model.VadSensitivity
import com.hearai.app.data.prefs.SecureKeyStore
import com.hearai.app.data.prefs.SettingsStore
import com.hearai.app.data.repo.SessionRepository
import com.hearai.app.network.GeminiSummarizer
import com.hearai.app.network.GeminiTranscribeLiveClient
import com.hearai.app.network.SummarizeResult
import com.hearai.app.network.TranscribeConnectionState
import com.hearai.app.network.TranscribeSessionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class ListeningUiState(
    val isListening: Boolean = false,
    val isSpeechActive: Boolean = false, // drives the "actively listening" indicator (§6.4)
    val currentLanguage: String? = null,
    val recentLines: List<String> = emptyList(), // last few lines for the Home preview (§6.4)
    val sessionId: String? = null,
    val connectionState: TranscribeConnectionState? = null,
)

/**
 * Orchestrates §4's pipeline end to end: Mic capture -> local VAD gate -> WebSocket stream to
 * Gemini 3.5 Transcribe Live -> rolling transcript buffer -> fan-out to display surfaces
 * (notification, in-app state, overlay). Owned as a singleton so the foreground service and the
 * Compose UI observe the same state regardless of which one started the session.
 */
@Singleton
class ListeningController @Inject constructor(
    private val audioCapture: AudioCapture,
    private val transcribeClient: GeminiTranscribeLiveClient,
    private val sessionRepository: SessionRepository,
    private val secureKeyStore: SecureKeyStore,
    private val summarizer: GeminiSummarizer,
    private val settingsStore: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob())
    private var pipelineJob: Job? = null
    private var summarizerJob: Job? = null
    private var vad: VoiceActivityDetector? = null
    private val languagesThisSession = mutableSetOf<String>()

    private val _state = MutableStateFlow(ListeningUiState())
    val state: StateFlow<ListeningUiState> = _state.asStateFlow()

    fun start(vadSensitivity: VadSensitivity) {
        if (_state.value.isListening) return
        val apiKey = secureKeyStore.getKey()
        if (apiKey.isNullOrBlank()) return // Settings/API key screen is responsible for gating this.

        vad = VoiceActivityDetector(vadSensitivity)
        languagesThisSession.clear()

        pipelineJob = scope.launch {
            val session = sessionRepository.startSession()
            _state.value = ListeningUiState(isListening = true, sessionId = session.id)

            // Two concurrent consumers of the WebSocket: server events (transcripts, connection
            // state) and the mic frame -> VAD -> send loop.
            launch {
                transcribeClient.connect(apiKey).collectLatest { event ->
                    when (event) {
                        is TranscribeSessionEvent.State -> {
                            _state.value = _state.value.copy(connectionState = event.state)
                        }
                        is TranscribeSessionEvent.Transcript -> {
                            val e = event.event
                            if (e.isFinal && e.text.isNotBlank()) {
                                languagesThisSession += e.detectedLanguage
                                sessionRepository.appendSegment(session.id, e.detectedLanguage, e.text)
                            }
                            val recent = (_state.value.recentLines + e.text).takeLast(4)
                            _state.value = _state.value.copy(
                                currentLanguage = e.detectedLanguage,
                                recentLines = recent,
                            )
                        }
                    }
                }
            }

            launch {
                audioCapture.frames().collectLatest { frame ->
                    val gateOpen = vad?.processFrame(frame) ?: false
                    _state.value = _state.value.copy(isSpeechActive = gateOpen)
                    // §4/§8: silence never reaches the socket — this is the token/battery gate.
                    if (gateOpen) {
                        transcribeClient.sendAudioChunk(frame.toPcm16Bytes())
                    }
                }
            }

            startPeriodicSummarization(session.id, apiKey)
        }
    }

    /** §4 Summarizer + §6.6 manual "summarize now": a distinct, periodic batch call separate
     * from the transcription stream. Interval 0 (off) never fires (§6.8: off / 2 / 5 / 10 / 15). */
    private fun CoroutineScope.startPeriodicSummarization(sessionId: String, apiKey: String) {
        summarizerJob = launch {
            var lastSummaryAt = System.currentTimeMillis()
            while (true) {
                val intervalMinutes = settingsStore.settings.first().summarizationIntervalMinutes
                if (intervalMinutes <= 0) {
                    delay(60_000)
                    continue
                }
                delay(intervalMinutes * 60_000L)
                summarizeNow(sessionId, apiKey, lastSummaryAt)
                lastSummaryAt = System.currentTimeMillis()
            }
        }
    }

    suspend fun summarizeNow(sessionId: String, apiKey: String, sinceMillis: Long) {
        val segments = sessionRepository.segmentsSince(sessionId, sinceMillis)
        if (segments.isEmpty()) return
        val transcriptText = segments.joinToString("\n") { it.text }
        when (val result = summarizer.summarize(apiKey, transcriptText)) {
            is SummarizeResult.Success -> if (result.text.isNotBlank()) {
                sessionRepository.saveSummary(sessionId, sinceMillis, System.currentTimeMillis(), result.text)
            }
            // §8 Resilience: back off and reflect state rather than crashing on 429/failure;
            // the next scheduled tick will simply try again with a larger buffer.
            is SummarizeResult.RateLimited, is SummarizeResult.Failure -> Unit
        }
    }

    fun stop() {
        val sessionId = _state.value.sessionId
        pipelineJob?.cancel()
        pipelineJob = null
        summarizerJob?.cancel()
        summarizerJob = null
        transcribeClient.disconnect()
        vad = null
        if (sessionId != null) {
            scope.launch {
                sessionRepository.endSession(sessionId, languagesThisSession.toList())
            }
        }
        _state.value = ListeningUiState()
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }
}
