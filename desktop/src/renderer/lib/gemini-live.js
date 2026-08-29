'use strict';

/**
 * Streams 16 kHz PCM16 audio to Gemini Live over a WebSocket and emits transcript
 * events. Wire format mirrors the (corrected) Android GeminiTranscribeLiveClient:
 *
 *   → setup:        {"setup":{"model":"models/gemini-3.5-transcribe-live", ...}}
 *   → audio:        {"realtimeInput":{"mediaChunks":[{"mimeType":"audio/pcm;rate=16000","data":"<b64>"}]}}
 *   ← transcript:   {"serverContent":{"interimInputTranscription":{"text":"…"}}}
 *                   {"serverContent":{"inputTranscription":{"text":"…"}}}   (final)
 */

import { romanize } from './romanize.js';

const ENDPOINT =
  'wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent';

const ROMANIZE_INSTRUCTION =
  'Transcribe speech in whatever language is spoken, but ALWAYS write the output in ' +
  'Latin/Roman script (romanized), never in Devanagari, Arabic, CJK or any other script. ' +
  'Example: Hindi speech "तू कैसा है" must be written as "tu kaisa hai". Keep it natural ' +
  'romanization the way people text, not a strict academic transliteration.';

export class GeminiLiveClient extends EventTarget {
  constructor({ romanizeOutput = true } = {}) {
    super();
    this.ws = null;
    this._closedByUs = false;
    this._romanize = romanizeOutput;
  }

  connect(apiKey) {
    this._closedByUs = false;
    this._emitState('connecting');
    const ws = new WebSocket(`${ENDPOINT}?key=${encodeURIComponent(apiKey)}`);
    this.ws = ws;

    ws.addEventListener('open', () => {
      const setup = {
        model: 'models/gemini-3.5-transcribe-live',
        generationConfig: { responseModalities: ['TEXT'] },
        inputAudioTranscription: {},
      };
      if (this._romanize) {
        setup.systemInstruction = { parts: [{ text: ROMANIZE_INSTRUCTION }] };
      }
      ws.send(JSON.stringify({ setup }));
      this._emitState('connected');
    });

    ws.addEventListener('message', async (ev) => {
      let text = ev.data;
      if (text instanceof Blob) text = await text.text();
      let msg;
      try { msg = JSON.parse(text); } catch { return; }
      const c = msg.serverContent;
      if (!c) return;
      const t = c.inputTranscription || c.interimInputTranscription;
      const modelText = (c.modelTurn?.parts || []).map((p) => p.text).filter(Boolean).join('');
      let out = (t && t.text) || modelText;
      if (!out || !out.trim()) return;
      if (this._romanize) {
        const before = out;
        out = romanize(out);
        console.log('[hearai] transcript', JSON.stringify(before), '->', JSON.stringify(out));
      }
      this.dispatchEvent(new CustomEvent('transcript', {
        detail: {
          text: out,
          language: (t && t.languageCode) || 'und',
          isFinal: !!(c.inputTranscription?.text?.trim()) || c.turnComplete === true,
        },
      }));
    });

    ws.addEventListener('close', (ev) => {
      this._emitState(this._closedByUs ? 'idle' : 'disconnected',
        this._closedByUs ? null : `closed ${ev.code} ${ev.reason || ''}`.trim());
    });
    ws.addEventListener('error', () => this._emitState('error', 'WebSocket error'));
  }

  /** @param {Int16Array} pcm16 */
  sendAudio(pcm16) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;
    const bytes = new Uint8Array(pcm16.buffer, pcm16.byteOffset, pcm16.byteLength);
    let bin = '';
    for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
    const b64 = btoa(bin);
    this.ws.send(JSON.stringify({
      realtimeInput: { mediaChunks: [{ mimeType: 'audio/pcm;rate=16000', data: b64 }] },
    }));
  }

  disconnect() {
    this._closedByUs = true;
    try { this.ws && this.ws.close(1000, 'client closed'); } catch {}
    this.ws = null;
  }

  _emitState(connection, message) {
    this.dispatchEvent(new CustomEvent('state', { detail: { connection, message: message || null } }));
  }
}
