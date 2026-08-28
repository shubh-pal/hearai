package com.hearai.app.audio

import com.hearai.app.data.model.VadSensitivity
import kotlin.math.sqrt

/**
 * §4 VAD gate: "an on-device, lightweight voice-activity detector decides when to
 * open/keep open the streaming connection. Silence should not consume API tokens or battery.
 * This is a hard requirement, not an optimization."
 *
 * Implementation is a simple RMS-energy gate with hysteresis (separate open/close thresholds
 * and a hangover window) so a connection doesn't chatter open/closed mid-word. This is
 * deliberately not a neural VAD — §8 calls for "local, on-device, gates all streaming" with no
 * requirement for ML-grade accuracy, and a lightweight detector keeps CPU/battery cost low.
 */
class VoiceActivityDetector(sensitivity: VadSensitivity) {

    // Lower sensitivity setting = higher threshold = fewer false opens, at the cost of missing
    // quiet speech (§6.8: "trades off missed quiet speech vs. token/battery usage").
    private val openThreshold: Double = when (sensitivity) {
        VadSensitivity.LOW -> 900.0
        VadSensitivity.MEDIUM -> 500.0
        VadSensitivity.HIGH -> 250.0
    }
    private val closeThreshold: Double = openThreshold * 0.6

    /** How many consecutive silent frames to tolerate before actually closing the gate, so a
     * short pause mid-sentence doesn't truncate the stream. At 20ms/frame this is ~800ms. */
    private val hangoverFrames = 40

    private var isSpeechActive = false
    private var silentFrameStreak = 0

    /** Feed one frame of 16-bit PCM mono audio; returns true while the gate should be (or stay) open. */
    fun processFrame(pcm16: ShortArray): Boolean {
        val rms = rms(pcm16)
        val aboveOpen = rms >= openThreshold
        val aboveClose = rms >= closeThreshold

        if (!isSpeechActive) {
            if (aboveOpen) {
                isSpeechActive = true
                silentFrameStreak = 0
            }
        } else {
            if (aboveClose) {
                silentFrameStreak = 0
            } else {
                silentFrameStreak++
                if (silentFrameStreak >= hangoverFrames) {
                    isSpeechActive = false
                    silentFrameStreak = 0
                }
            }
        }
        return isSpeechActive
    }

    fun reset() {
        isSpeechActive = false
        silentFrameStreak = 0
    }

    private fun rms(pcm16: ShortArray): Double {
        if (pcm16.isEmpty()) return 0.0
        var sumSquares = 0.0
        for (sample in pcm16) sumSquares += sample.toDouble() * sample.toDouble()
        return sqrt(sumSquares / pcm16.size)
    }
}
