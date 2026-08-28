package com.hearai.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

/** One 20ms frame of mono 16kHz 16-bit PCM audio — matches Gemini Live's expected input format. */
const val SAMPLE_RATE_HZ = 16_000
private const val FRAME_DURATION_MS = 20
private const val SAMPLES_PER_FRAME = SAMPLE_RATE_HZ * FRAME_DURATION_MS / 1000

/**
 * Thin wrapper around [AudioRecord] that emits fixed-size PCM frames as a cold [Flow]. Runs
 * inside the foreground service (§4 Mic capture: "runs inside a foreground service so it
 * survives backgrounding/screen-off").
 */
class AudioCapture {
    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked by the caller before starting.
    fun frames(): Flow<ShortArray> = callbackFlow {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = maxOf(minBufferSize, SAMPLES_PER_FRAME * 4)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            close(IllegalStateException("AudioRecord failed to initialize"))
            return@callbackFlow
        }

        audioRecord.startRecording()
        var running = true

        // AudioRecord.read() is blocking, so pump it from a dedicated thread rather than a
        // coroutine dispatcher thread pool.
        val thread = Thread {
            val frame = ShortArray(SAMPLES_PER_FRAME)
            while (running) {
                val read = audioRecord.read(frame, 0, frame.size)
                if (read > 0) {
                    val chunk = if (read == frame.size) frame.copyOf() else frame.copyOf(read)
                    trySend(chunk)
                }
            }
        }
        thread.start()

        awaitClose {
            running = false
            thread.join(500)
            audioRecord.stop()
            audioRecord.release()
        }
    }.flowOn(Dispatchers.Default)
}

/** Converts a PCM16 frame to little-endian bytes for the wire (§4 transcription stream). */
fun ShortArray.toPcm16Bytes(): ByteArray {
    val bytes = ByteArray(size * 2)
    for (i in indices) {
        val sample = this[i].toInt()
        bytes[i * 2] = (sample and 0xFF).toByte()
        bytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
    }
    return bytes
}
