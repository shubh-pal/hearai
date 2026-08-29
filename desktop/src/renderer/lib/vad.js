'use strict';

/**
 * On-device voice-activity gate — a simple RMS-energy detector with hysteresis
 * (separate open/close thresholds + a hangover window) so the stream doesn't
 * chatter open/closed mid-word. Ported from the Android app's VoiceActivityDetector.
 *
 * Frames are Int16 PCM. At 16 kHz, 20 ms = 320 samples; hangover of 40 frames ≈ 800 ms.
 */
export class VoiceActivityDetector {
  constructor(sensitivity = 'medium') {
    const open = { low: 900, medium: 500, high: 250 }[sensitivity] ?? 500;
    this.openThreshold = open;
    this.closeThreshold = open * 0.6;
    this.hangoverFrames = 40;
    this.active = false;
    this.silentStreak = 0;
  }

  /** @param {Int16Array} frame @returns {boolean} true while the gate should stay open */
  process(frame) {
    const rms = this._rms(frame);
    if (!this.active) {
      if (rms >= this.openThreshold) { this.active = true; this.silentStreak = 0; }
    } else if (rms >= this.closeThreshold) {
      this.silentStreak = 0;
    } else if (++this.silentStreak >= this.hangoverFrames) {
      this.active = false;
      this.silentStreak = 0;
    }
    return this.active;
  }

  reset() { this.active = false; this.silentStreak = 0; }

  _rms(frame) {
    if (!frame.length) return 0;
    let sum = 0;
    for (let i = 0; i < frame.length; i++) sum += frame[i] * frame[i];
    return Math.sqrt(sum / frame.length);
  }
}
