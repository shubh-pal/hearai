# HearAI 1.0

First public build of HearAI — an AI hearing assist that captions the room, your calls and
your meetings in real time. Bring your own Gemini API key; nothing is stored off your device.

## Downloads

| Platform | File | Notes |
|---|---|---|
| Android 12+ | `HearAI-1.0.apk` | Sideload — allow "install from unknown source" |
| Windows 10+ (x64) | `HearAI-Desktop-Setup-0.1.0.exe` | NSIS installer; SmartScreen will warn (unsigned) — "More info" → "Run anyway" |
| macOS 12+ (Apple Silicon) | `HearAI-Desktop-0.1.0-arm64.dmg` | Unsigned — right-click the app → **Open** the first time |
| macOS 12+ (Intel) | `HearAI-Desktop-0.1.0-x64.dmg` | Same |

`SHA256SUMS.txt` lists the checksum for each file.

## What's inside

- **Android** — real-time ambient transcription, live phone-call captions, on-device name
  callout alerts, floating caption overlay, periodic AI summaries, local session history,
  85+ languages with automatic detection and mid-sentence switching.
- **Desktop** — tray app with a translucent caption box that follows your cursor (or pinned),
  meeting/system-audio capture on Windows, microphone capture on macOS, excluded from screen
  capture by default.

## Known limitations

- Builds are **not** code-signed. This is expected for a direct download.
- Call transcription only hears both sides on speakerphone (Android platform limit).
- Desktop system-audio (loopback) capture is Windows-only; macOS uses the microphone.
- Name-callout auto-restart after a device reboot is not implemented — reopen the app once.
