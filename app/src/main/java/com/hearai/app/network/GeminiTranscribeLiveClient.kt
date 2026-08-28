package com.hearai.app.network

import android.util.Base64
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A finalized or in-progress piece of transcript coming back from the Live API.
 * [isFinal] mirrors the model's turn-completion signal; interim results let the UI show
 * fast-appearing text (§8 Latency: "target sub-second caption appearance") before it's final.
 */
data class TranscriptEvent(
    val text: String,
    val detectedLanguage: String,
    val isFinal: Boolean,
)

sealed interface TranscribeConnectionState {
    data object Connecting : TranscribeConnectionState
    data object Connected : TranscribeConnectionState
    data class Disconnected(val willRetry: Boolean) : TranscribeConnectionState
    data class Error(val message: String, val isRateLimited: Boolean) : TranscribeConnectionState
}

/**
 * §4 Transcription: streams mic audio to Gemini 3.5 Transcribe Live over WebSocket with
 * automatic language detection (no fixed source language) so mid-conversation code-switching
 * (§2 Goals) is picked up without reconnecting. This deliberately does not use the
 * conversational (audio-in/audio-out) Live API — transcription only, per §4.
 *
 * The exact wire schema for "Gemini 3.5 Transcribe Live" should be confirmed against the
 * current Google AI Studio / Gemini API docs before shipping; the message shapes below follow
 * the established Gemini Live (BidiGenerateContent) conventions — a JSON `setup` message
 * followed by base64-encoded 16-bit PCM audio chunks, and streamed `serverContent` transcript
 * updates back — and are isolated behind this class so the wire format can be adjusted in one
 * place (see §11 Open Questions).
 */
@Singleton
class GeminiTranscribeLiveClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private var webSocket: WebSocket? = null

    fun connect(apiKey: String): Flow<TranscribeSessionEvent> = callbackFlow {
        trySend(TranscribeSessionEvent.State(TranscribeConnectionState.Connecting))

        val request = Request.Builder()
            .url("$ENDPOINT?key=$apiKey")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                ws.send(json.encodeToString(SetupMessage.serializer(), SetupMessage()))
                trySend(TranscribeSessionEvent.State(TranscribeConnectionState.Connected))
            }

            override fun onMessage(ws: WebSocket, text: String) {
                val parsed = runCatching { json.decodeFromString(ServerMessage.serializer(), text) }.getOrNull()
                val content = parsed?.serverContent ?: return
                val transcript = content.inputTranscription ?: return
                trySend(
                    TranscribeSessionEvent.Transcript(
                        TranscriptEvent(
                            text = transcript.text,
                            detectedLanguage = transcript.languageCode ?: "und",
                            isFinal = content.turnComplete == true,
                        ),
                    ),
                )
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(code, reason)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                trySend(TranscribeSessionEvent.State(TranscribeConnectionState.Disconnected(willRetry = code != NORMAL_CLOSURE)))
                close()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                val isRateLimited = response?.code == 429
                trySend(
                    TranscribeSessionEvent.State(
                        TranscribeConnectionState.Error(t.message ?: "WebSocket failure", isRateLimited),
                    ),
                )
                close()
            }
        }

        webSocket = okHttpClient.newWebSocket(request, listener)

        awaitClose {
            webSocket?.close(NORMAL_CLOSURE, "client closed")
            webSocket = null
        }
    }

    /** Called only while the local VAD gate (§4) has decided speech is present — silence never
     * reaches this method, which is what keeps token/battery usage down (§8 Quota/battery). */
    fun sendAudioChunk(pcm16: ByteArray) {
        val encoded = Base64.encodeToString(pcm16, Base64.NO_WRAP)
        webSocket?.send(json.encodeToString(AudioChunkMessage.serializer(), AudioChunkMessage(audio = encoded)))
    }

    fun disconnect() {
        webSocket?.close(NORMAL_CLOSURE, "session ended")
        webSocket = null
    }

    private companion object {
        const val ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        const val NORMAL_CLOSURE = 1000
    }
}

sealed interface TranscribeSessionEvent {
    data class State(val state: TranscribeConnectionState) : TranscribeSessionEvent
    data class Transcript(val event: TranscriptEvent) : TranscribeSessionEvent
}

@Serializable
private data class SetupMessage(
    val setup: Setup = Setup(),
) {
    @Serializable
    data class Setup(
        val model: String = "models/gemini-3.5-transcribe-live",
        @SerialName("generation_config") val generationConfig: GenerationConfig = GenerationConfig(),
    )

    @Serializable
    data class GenerationConfig(
        @SerialName("response_modalities") val responseModalities: List<String> = listOf("TEXT"),
        // Deliberately no fixed source language — §4 requires automatic detection so
        // mid-conversation code-switching is followed rather than pinned to one language.
    )
}

@Serializable
private data class AudioChunkMessage(
    val audio: String,
    @SerialName("mime_type") val mimeType: String = "audio/pcm;rate=16000",
)

@Serializable
private data class ServerMessage(
    @SerialName("server_content") val serverContent: ServerContent? = null,
)

@Serializable
private data class ServerContent(
    @SerialName("input_transcription") val inputTranscription: InputTranscription? = null,
    @SerialName("turn_complete") val turnComplete: Boolean? = null,
)

@Serializable
private data class InputTranscription(
    val text: String,
    @SerialName("language_code") val languageCode: String? = null,
)
