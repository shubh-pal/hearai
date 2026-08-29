'use strict';

/**
 * Runs in the AudioWorklet thread. Receives mono Float32 audio (already resampled
 * to the AudioContext rate — we create the context at 16 kHz), accumulates it into
 * fixed 20 ms frames (320 samples) and posts them to the main thread as Int16.
 */
const FRAME = 320; // 20ms @ 16kHz

class PcmFramer extends AudioWorkletProcessor {
  constructor() {
    super();
    this._buf = new Float32Array(FRAME);
    this._n = 0;
  }

  process(inputs) {
    const ch = inputs[0] && inputs[0][0];
    if (!ch) return true;
    for (let i = 0; i < ch.length; i++) {
      this._buf[this._n++] = ch[i];
      if (this._n === FRAME) {
        const out = new Int16Array(FRAME);
        for (let k = 0; k < FRAME; k++) {
          const s = Math.max(-1, Math.min(1, this._buf[k]));
          out[k] = s < 0 ? s * 0x8000 : s * 0x7fff;
        }
        this.port.postMessage(out, [out.buffer]);
        this._n = 0;
      }
    }
    return true;
  }
}

registerProcessor('pcm-framer', PcmFramer);
