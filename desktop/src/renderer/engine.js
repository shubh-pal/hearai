'use strict';

import { VoiceActivityDetector } from './lib/vad.js';
import { GeminiLiveClient } from './lib/gemini-live.js';

let ctx = null;
let stream = null;
let node = null;
let client = null;
let vad = null;
let running = false;

const status = (connection, message) => window.hearai.engineStatus({ connection, message: message || null });

async function acquireStream(source) {
  if (source === 'mic') {
    return navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false },
    });
  }
  // System / loopback audio (Windows). Chromium requires a video track alongside
  // the desktop audio source; we grab a 1x1 frame and drop it immediately.
  try {
    const s = await navigator.mediaDevices.getUserMedia({
      audio: { mandatory: { chromeMediaSource: 'desktop' } },
      video: { mandatory: { chromeMediaSource: 'desktop', maxWidth: 1, maxHeight: 1, maxFrameRate: 1 } },
    });
    s.getVideoTracks().forEach((t) => { t.stop(); s.removeTrack(t); });
    return s;
  } catch (e) {
    // Fallback: modern getDisplayMedia + main-process loopback handler.
    const s = await navigator.mediaDevices.getDisplayMedia({ video: true, audio: true });
    s.getVideoTracks().forEach((t) => { t.stop(); s.removeTrack(t); });
    return s;
  }
}

async function start(cfg) {
  await stop();
  running = true;
  vad = new VoiceActivityDetector(cfg.vadSensitivity);

  try {
    stream = await acquireStream(cfg.audioSource);
  } catch (e) {
    status('error', `Could not open ${cfg.audioSource === 'mic' ? 'microphone' : 'system audio'}: ${e.message}`);
    running = false;
    return;
  }

  ctx = new AudioContext({ sampleRate: 16000 });
  await ctx.audioWorklet.addModule('./worklets/pcm-worklet.js');
  const src = ctx.createMediaStreamSource(stream);
  node = new AudioWorkletNode(ctx, 'pcm-framer');
  src.connect(node);
  // Worklet needs a graph path to a destination to be pulled; route through a muted gain.
  const sink = ctx.createGain();
  sink.gain.value = 0;
  node.connect(sink).connect(ctx.destination);

  client = new GeminiLiveClient({ romanizeOutput: cfg.romanizeOutput !== false });
  client.addEventListener('state', (e) => status(e.detail.connection, e.detail.message));
  client.addEventListener('transcript', (e) => window.hearai.engineTranscript(e.detail));
  client.connect(cfg.apiKey);

  node.port.onmessage = (ev) => {
    if (!running) return;
    const frame = ev.data; // Int16Array, 320 samples
    if (vad.process(frame)) client.sendAudio(frame);
  };
}

async function stop() {
  running = false;
  try { client && client.disconnect(); } catch {}
  client = null;
  try { node && node.disconnect(); } catch {}
  node = null;
  try { stream && stream.getTracks().forEach((t) => t.stop()); } catch {}
  stream = null;
  try { ctx && (await ctx.close()); } catch {}
  ctx = null;
}

window.hearai.onEngineStart((cfg) => { start(cfg); });
window.hearai.onEngineStop(() => { stop(); status('idle', null); });
window.hearai.onEngineConfig((cfg) => { if (running) start(cfg); });
