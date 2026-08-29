# HearAI — Features

HearAI is an Android app that works like an AI-enhanced hearing aid. It captures audio in
real time, transcribes it with Google's Gemini models, and surfaces the text through several
always-visible channels. It also watches for a phone call to caption, and can alert the user
when their own name is spoken nearby.

- **Platform:** Android only, `minSdk 31` (Android 12), `compileSdk`/`targetSdk 34`.
- **Cost model:** BYOK (bring your own key) — each user supplies their own Gemini API key.
  There is no developer backend and no billing.
- **Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Room, DataStore,
  EncryptedSharedPreferences, OkHttp (WebSocket + REST), kotlinx.serialization, Glance widget.

---

## 1. Real-time ambient transcription

The core feature. A foreground service captures the microphone and streams speech to Gemini
for live captions.

| Detail | Value |
|---|---|
| Capture | `AudioRecord`, mono 16 kHz PCM-16, 20 ms frames (`AudioSource.MIC`) |
| Local gate | On-device Voice Activity Detector — silence is never sent upstream, which controls token spend and battery |
| Transport | WebSocket to Gemini Live (`BidiGenerateContent`), model `gemini-3.5-transcribe-live` |
| Language | Automatic detection, no fixed source language; mid-conversation code-switching is followed without reconnecting (targets 85+ languages) |
| Script | Output is always romanized (Latin script) regardless of the spoken language, via a system instruction |
| Latency | Interim (fast, unstable) results shown immediately, replaced by final results |
| Resilience | Connection state is surfaced in the UI; rate-limit (429) and failure states back off instead of crashing |

**Key files:** `audio/AudioCapture.kt`, `audio/VoiceActivityDetector.kt`,
`audio/ListeningController.kt`, `audio/ListeningService.kt`,
`network/GeminiTranscribeLiveClient.kt`.

### Display surfaces (fan-out)

The same transcript state feeds every surface simultaneously:

1. **Persistent notification** — required by Android for the foreground service; doubles as
   the "listening is active" privacy indicator. Shows idle / transcribing status and a **Stop**
   action.
2. **In-app Live Transcript screen** — full scrollable transcript for the active session.
3. **Home screen preview** — the last few lines on the Home/Listening screen, plus an
   "actively listening" (speech detected) indicator and the current detected language.
4. **Floating overlay bubble** — a movable caption strip drawn over other apps (see §4).
5. **Home screen widget** (Glance) — last summary snippet + listening state, periodic refresh.

---

## 2. Live phone-call transcription

When enabled, HearAI auto-starts transcription for the duration of a phone call and shows the
captions through the overlay bubble and notification.

- **Trigger:** a `TelephonyCallback` (`CallStateMonitor`) detects the call connecting
  (`CALL_STATE_OFFHOOK`) and starts `ListeningService` in call mode; it stops when the call
  ends. Requires the `READ_PHONE_STATE` permission, requested when the toggle is switched on.
- **Capture:** uses `AudioSource.VOICE_COMMUNICATION` so the platform applies
  echo-cancellation and noise-suppression tuned for a call.
- **Platform limitation:** Android does **not** allow a normal (non-system) app to tap the
  far-end (remote party) call audio. On **speakerphone** the microphone picks up both sides
  acoustically; on the earpiece only the user's side is captured. The Settings copy and the
  overlay hint both tell the user to switch the call to speaker.
- **History:** call sessions are tagged `source = "call"` (vs `"ambient"`) in the session
  database.
- Toggle: **Settings → Phone calls → "Transcribe phone calls"** (off by default).

**Key files:** `audio/CallStateMonitor.kt`, `audio/ListeningController.kt` (`CaptureMode.CALL`),
`audio/ListeningService.kt` (`ACTION_START_CALL`), `HearAiApplication.kt` (wiring).

---

## 3. Name callout alerts

Lets the user be notified when their name is spoken near them, even with the app in the
background and the screen off.

- **Setup:** **Settings → Name callout** — enter your name, then flip the
  "Alert me when my name is called" switch. The switch stays disabled until a name is entered.
  Enabling it requests microphone (and, on Android 13+, notification) permission.
- **Detection:** fully on-device — no network, no Gemini tokens.
  - `NameSpotter` runs the platform `SpeechRecognizer` in **offline** mode
    (`EXTRA_PREFER_OFFLINE`) in a short restart loop.
  - `NameMatcher` normalizes each hypothesis, tokenizes it, and matches a token against the
    name by exact match **or** small edit distance (≤1, or ≤2 for names ≥6 characters) to
    tolerate speech-recognition errors (e.g. "Pranav" vs "Pranaw").
  - A **15-second debounce** prevents repeat alerts while the same conversation keeps saying
    the name.
- **Alert:** a distinctive vibration pattern plus a high-priority heads-up notification
  ("Someone nearby said …") on a dedicated `IMPORTANCE_HIGH` channel. Tapping it opens the app.
- **Mic sharing:** if the main transcription pipeline (ambient or call) is already running,
  the callout detector scans that live transcript instead of opening a second recognizer, so
  the two features never fight over the microphone.
- **Persistence:** runs as its own foreground service (`NameCalloutService`) with a quiet
  ongoing notification. It is **not** restarted automatically after a device reboot — the user
  re-enables it by opening the app once. Toggling the setting off stops the service.
- **Availability:** depends on an on-device recognition service / language pack being present;
  the spotter no-ops gracefully if none is available.

**Key files:** `audio/NameCalloutService.kt`, `audio/NameSpotter.kt`, `audio/NameMatcher.kt`
(unit-tested in `app/src/test/.../NameMatcherTest.kt`).

---

## 4. Floating overlay bubble

An opt-in caption strip that renders on top of whatever app is in the foreground.

- Implemented as a `TYPE_ACCESSIBILITY_OVERLAY` via a bound **Accessibility service**
  (`OverlayAccessibilityService`). It does **not** read screen content
  (`canRetrieveWindowContent="false"`) — the accessibility binding is used only to obtain the
  draw-over-other-apps capability.
- **Movable** — drag to reposition.
- Shows the latest transcript line while a session is active; hides itself when nothing is
  being transcribed. In call mode it shows a "put the call on speaker" hint until captions
  arrive.
- **Enabling it:** turn on **Settings → Floating overlay bubble**, then enable the HearAI
  service under **system Settings → Accessibility** (and grant "Display over other apps" on
  OEMs that require it). The bubble only appears while a listening or call session is running.
- There is no on-screen close button — the bubble is governed entirely by the accessibility
  service being enabled and a session being active.

**Key file:** `audio/OverlayAccessibilityService.kt`, config in
`res/xml/accessibility_service_config.xml`.

---

## 5. AI summarization

A separate, periodic batch call (distinct from the transcription stream) condenses what was
said.

- **Interval:** Settings — Off / 2 / 5 / 10 / 15 minutes (default 5).
- **Manual:** a "summarize now" action is available.
- Each summary covers the transcript **since the last summary** and is stored against the
  session with its time range.
- Uses a Gemini text model via REST (`GeminiSummarizer`); rate-limit / failure just skips that
  tick and retries on the next one with a larger buffer.

**Key files:** `network/GeminiSummarizer.kt`, `audio/ListeningController.kt`
(`startPeriodicSummarization`).

---

## 6. Sessions & history

- Every listening run is a **Session** persisted locally in Room (start/end time, distinct
  detected languages, `source` = ambient/call).
- Each transcript line is a timestamped, language-tagged **TranscriptSegment**.
- **Screens:** Session History (list) → Session Detail (full transcript + summaries for that
  session).
- **Summaries screen:** all summaries across sessions, newest first.
- **Retention:** Settings — auto-delete session history after Never / 7 / 30 / 90 days.
- Data stays on-device; there is no cloud sync or backup. Export is user-initiated only.

**Key files:** `data/model/Models.kt`, `data/db/*`, `data/repo/SessionRepository.kt`,
`ui/screens/history/*`, `ui/screens/summaries/*`.

---

## 7. API key management (BYOK)

- The user pastes their own **Gemini API key** at first launch (API Key Setup screen) or
  later from Settings.
- Stored in **EncryptedSharedPreferences** (`SecureKeyStore`) — never in plain DataStore,
  never logged, never embedded in the build.
- **Validation:** the key is checked against the Gemini API (`GeminiKeyValidator`); status is
  UNKNOWN / VALIDATING / VALID / INVALID.
- **Privacy disclosure:** before microphone permission is granted, the app shows that
  free-tier Gemini terms may let Google use audio/text to improve their models, and that this
  applies to everyone whose voice is captured.
- **Remove key:** immediately stops any active session and sends the user back through setup.

**Key files:** `data/prefs/SecureKeyStore.kt`, `network/GeminiKeyValidator.kt`,
`ui/screens/apikey/*`.

---

## 8. Settings

| Setting | Options | Notes |
|---|---|---|
| Gemini API key | set / edit / remove | Masked display; removal stops listening |
| Summarization interval | Off / 2 / 5 / 10 / 15 min | Default 5 |
| VAD sensitivity | Low / Medium / High | Tunes the local speech gate |
| Floating overlay bubble | on / off | Also needs the Accessibility grant |
| Transcribe phone calls | on / off | Requests `READ_PHONE_STATE`; speaker required for both sides |
| Name callout — name | free text | Required before the toggle can be enabled |
| Name callout — alert toggle | on / off | Requests mic + notification permission |
| Text size | Small / Medium / Large / Extra large | Affects transcript rendering |
| Theme | Light / Dark / System | |
| Auto-delete session history | Never / 7 / 30 / 90 days | |

**Key files:** `ui/screens/settings/SettingsScreen.kt`,
`ui/screens/settings/SettingsViewModel.kt`, `data/prefs/SettingsStore.kt`.

---

## 9. Onboarding & permissions

Flow: **Welcome → API Key Setup → Permissions → Home**. Once completed, later launches go
straight to Home.

Permissions requested, each with plain-language rationale:

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Capture audio for transcription and name detection |
| `POST_NOTIFICATIONS` (13+) | Status notification + name-callout alerts |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE` | Keep capturing while backgrounded / screen-off |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent the OS killing a long session |
| `SYSTEM_ALERT_WINDOW` / Accessibility | Floating overlay bubble (opt-in) |
| `READ_PHONE_STATE` | Detect a call starting/ending (opt-in) |
| `VIBRATE` | Name-callout haptic alert |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Reach the Gemini API |

**Key files:** `ui/screens/welcome/*`, `ui/screens/permissions/PermissionsScreen.kt`,
`AndroidManifest.xml`.

---

## 10. Home screen widget

A Glance widget showing the latest summary snippet and the current listening state, refreshed
periodically.

**Key files:** `widget/HearAiWidget.kt`, `res/xml/hearai_widget_info.xml`.

---

## Privacy summary

- Audio is streamed to Gemini Live only for transcription and is not stored by the app.
- Name-callout detection is 100% on-device.
- Session transcripts and summaries stay local; no cloud sync, no analytics upload.
- The persistent notification is a deliberate, visible indicator that capture is active.
- The API key is encrypted at rest and never logged.

## Out of scope (v1)

iOS, speaker diarization, multi-user / shared sessions, cloud backup or sync, any
developer-hosted API key or billing, and automatic name-callout restart after reboot.
