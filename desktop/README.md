# HearAI Desktop (Windows)

A real-time caption overlay for meetings and screen shares. A small translucent
caption box floats **below your cursor and follows it around the screen** (or can be
pinned), so you never look away from shared content to read what's being said.

Same transcription pipeline as the HearAI Android app: audio → local voice-activity
gate → Gemini Live WebSocket → text. Bring your own Gemini API key.

## Run (dev)

```bash
cd desktop
npm install
npm start
```

A tray icon appears (and the Settings window opens on first run). Paste your Gemini
API key, pick an audio source, then **Start listening**.

## Build a Windows installer

```bash
npm run dist
```

Produces an NSIS installer in `dist/`. (Must be run on Windows, or with a Windows
cross-build toolchain.)

## Controls

| | |
|---|---|
| `Ctrl+Shift+H` | start / stop listening |
| `Ctrl+Shift+P` | toggle follow-cursor ↔ pinned |
| Tray menu | start/stop, position mode, settings, quit |

## Settings

- **Audio source** — *Meeting / system audio* (what the remote participants say,
  captured via Windows loopback) or *Microphone* (local room audio).
- **Position** — *Follow cursor* (box trails the mouse, flips at screen edges) or
  *Pinned* (fixed spot; set X/Y in Settings).
- **Appearance** — font size, box width, opacity, number of lines.
- **Hide caption box from screen shares / recordings** — on by default. The overlay
  stays visible on your screen but is excluded from capture (`setContentProtection`),
  so if *you* are the one sharing, your captions don't show to everyone.
- **VAD sensitivity** — how loud audio must be before it's streamed (saves quota on
  silence).

## How it works

- `src/main/main.js` — tray app, the three windows, cursor-follow loop, global
  shortcuts, Windows loopback-audio handler.
- `src/renderer/engine.*` — hidden window: captures audio, resamples to 16 kHz PCM in
  an AudioWorklet, runs the VAD gate, streams to Gemini.
- `src/renderer/overlay.*` — the transparent, click-through caption window.
- `src/renderer/settings.*` — configuration UI.
- `src/renderer/lib/` — `vad.js` and `gemini-live.js`, ported from the Android app.

## Status / caveats

This was scaffolded and boot-tested on macOS; the **Windows-specific paths need
testing on Windows**:

- **System (loopback) audio** uses the Chromium `chromeMediaSource: 'desktop'` trick,
  with `getDisplayMedia({audio:'loopback'})` as a fallback. Loopback audio is a
  Windows capability — on macOS the system-audio option won't capture anything.
- **`setContentProtection`** excludes the window from capture on Windows 10 2004+ and
  recent macOS; behaviour on older builds varies.
- **Cursor-follow smoothness** — the box is repositioned on a 40 ms timer. If it feels
  laggy or janky on your hardware, lower the interval in `positionOverlay`'s
  `setInterval`, or use pinned mode.
- The tray icon is a placeholder PNG.
- Gemini model id `models/gemini-3.5-transcribe-live` and the Live wire format match
  what the Android app now uses successfully; if Google changes the schema, update
  `src/renderer/lib/gemini-live.js`.
