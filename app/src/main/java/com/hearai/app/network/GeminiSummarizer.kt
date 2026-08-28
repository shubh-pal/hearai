package com.hearai.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SummarizeResult {
    data class Success(val text: String) : SummarizeResult
    data class RateLimited(val retryAfterSeconds: Int?) : SummarizeResult
    data class Failure(val reason: String) : SummarizeResult
}

/**
 * §4 Summarizer: "a separate, periodic (user-configurable interval, default 5 min) call to a
 * lightweight text model (Flash-class) that takes the buffer since the last summary and returns
 * a short digest. This is a distinct API call from the transcription stream." Also used for the
 * manual "summarize now" action (§6.6).
 */
@Singleton
class GeminiSummarizer @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    suspend fun summarize(apiKey: String, transcriptText: String): SummarizeResult = withContext(Dispatchers.IO) {
        if (transcriptText.isBlank()) return@withContext SummarizeResult.Success("")

        val requestBody = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(
                            text = "Summarize the following transcript segment in 1-3 short sentences, " +
                                "preserving names, dates, and decisions. Do not add commentary.\n\n$transcriptText",
                        ),
                    ),
                ),
            ),
        )

        val request = Request.Builder()
            .url("$ENDPOINT?key=$apiKey")
            .post(json.encodeToString(GenerateContentRequest.serializer(), requestBody).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 429 -> {
                        val retryAfter = response.header("Retry-After")?.toIntOrNull()
                        SummarizeResult.RateLimited(retryAfter)
                    }
                    !response.isSuccessful -> SummarizeResult.Failure("HTTP ${response.code}")
                    else -> {
                        val body = response.body?.string().orEmpty()
                        val parsed = json.decodeFromString(GenerateContentResponse.serializer(), body)
                        val text = parsed.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        if (text.isNullOrBlank()) {
                            SummarizeResult.Failure("Empty response")
                        } else {
                            SummarizeResult.Success(text.trim())
                        }
                    }
                }
            }
        }.getOrElse { throwable ->
            SummarizeResult.Failure(throwable.message ?: "Network error")
        }
    }

    private companion object {
        // Flash-class model per §4 ("lightweight text model") — a distinct, cheap batch call
        // from the transcription WebSocket stream.
        const val ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
private data class GenerateContentRequest(val contents: List<Content>)

@Serializable
private data class Content(val parts: List<Part>)

@Serializable
private data class Part(val text: String)

@Serializable
private data class GenerateContentResponse(val candidates: List<Candidate> = emptyList())

@Serializable
private data class Candidate(val content: Content? = null)
