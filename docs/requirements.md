# AI Hearing Assist App — Requirements Document

**Working title:** HearAI (rename freely) **Platform:** Android (v1) — iOS deferred, see §3
**Version:** 1.0 draft **Date:** 2026-08-28

## 1. Overview & Problem Statement

An Android app that acts as an AI-enhanced hearing aid. It listens to ambient audio in real
time, streams it to Gemini's Live API for transcription, and surfaces the text to the user
through multiple always-visible channels (notification, in-app view, floating overlay). It
supports 85+ languages with automatic detection and mid-conversation language switching, and can
periodically summarize what was said.

Unlike existing tools (Google Live Transcribe, Sound Amplifier), this app combines cloud-AI
transcription quality with automatic language switching and AI summarization — while keeping
infrastructure cost at zero for the developer by having each user supply their own Gemini API
key (BYOK).

## 2. Goals

- Real-time, low-latency captioning of environmental/conversational audio
- Automatic language detection, including mid-conversation code-switching
- Multiple simultaneous display surfaces: persistent notification, in-app scroll view, floating
  overlay bubble
- Periodic AI-generated summaries of what was said while the user wasn't actively reading
- Zero backend cost to the developer — user's own Gemini API key powers all inference
- Battery- and quota-conscious: only stream audio to the API when speech is actually present

## 3. Platform & Constraints

**v1 target: Android only.** iOS restricts continuous background microphone access outside of
the MFi hearing-aid accessory framework, which is a hardware-certification path, not a software
one. Do not attempt background listening on iOS in v1 — if iOS is revisited later, scope it as
foreground-only or investigate MFi.

Minimum Android version: target Android 12+ (matches Google's own Live Transcribe & Notification
requirement, and gives access to modern foreground-service and notification APIs).

Requires: `RECORD_AUDIO`, foreground service permission, notification permission (Android 13+),
and — only if the overlay bubble ships — Accessibility Service permission (needed for a true
system-wide floating overlay).

## 4. Core Architecture

```
Mic capture → local VAD gate → WebSocket stream to Gemini 3.5 Transcribe Live
  → rolling transcript buffer → fan-out to display surfaces
  → periodic batch call to a text model for summarization
```

- **Mic capture:** runs inside a foreground service so it survives backgrounding/screen-off.
  Foreground service must show a persistent, dismiss-proof notification (Android requirement) —
  this doubles as Display Surface #1.
- **VAD gate:** an on-device, lightweight voice-activity detector decides when to open/keep open
  the streaming connection. Silence should not consume API tokens or battery. This is a hard
  requirement, not an optimization — see §8 for quota implications.
- **Transcription:** Gemini 3.5 Transcribe Live via WebSocket. Use automatic language detection
  (no fixed source language config) so it follows code-switching. Do not use the conversational
  Live API (audio-in/audio-out) — this app only needs transcription, not a talking-back agent.
- **Transcript buffer:** an in-memory rolling store of timestamped, language-tagged text
  segments for the current session. Persisted to local storage per session on stop.
- **Summarizer:** a separate, periodic (user-configurable interval, default 5 min) call to a
  lightweight text model (Flash-class) that takes the buffer since the last summary and returns
  a short digest. This is a distinct API call from the transcription stream.

## 5. API Key Management (BYOK)

- The app ships with **no embedded API key**. On first launch, the user must generate their own
  key at Google AI Studio and paste it into the app.
- Store the key using Android's encrypted storage (EncryptedSharedPreferences or Keystore-backed
  solution) — never in plain text, never logged.
- Validate the key on entry with a trivial low-cost API call before accepting it; show a clear
  success/failure state.
- Settings must let the user view (masked), replace, or remove their key at any time. Removing
  the key should immediately stop any active listening session.
- Onboarding copy must disclose: *"Your Gemini API key's free-tier terms apply. Google may use
  free-tier audio/text to improve their models unless your key is on a billing-linked tier.
  Everyone whose voice is picked up while you're listening is subject to this."* This is a real
  disclosure, not boilerplate — surface it before the user grants mic permission, not buried in a
  settings submenu.

## 6. Screens

### 6.1 Onboarding — Welcome
Explains what the app does in 2-3 lines and what it needs (mic, an API key, some permissions).
Single CTA: "Get started."

### 6.2 Onboarding — API Key Setup
Short instructions + link out to Google AI Studio to create a key. Input field for pasting the
key, paste-from-clipboard shortcut. "Validate" action → success/failure state. Privacy
disclosure text (see §5) shown here, must be acknowledged (checkbox or explicit "I understand"
tap) before proceeding.

### 6.3 Onboarding — Permissions
Sequential permission requests with plain-language explanation for each: microphone,
notifications, battery-optimization exemption (so Android doesn't kill the foreground service),
and optionally Accessibility Service (only if the user wants the floating overlay — make this
skippable).

### 6.4 Home / Listening Screen (main screen)
Large start/stop listening toggle. Live-updating preview of the last few transcript lines while
active. Currently detected language indicator (updates as it changes). Quick access to: full
transcript view, summaries, settings. Visual "actively listening" indicator (important for the
person speaking to see, not just the user — consider making this legible to bystanders too).

### 6.5 Live Transcript (full-screen)
Full scrolling transcript for the current session, auto-scrolling to newest text. Each segment
shows timestamp and detected language tag. Adjustable text size (accessibility-critical — mirror
what Live Transcribe offers). Option to flip text 180° (for handing the phone to the other
person to read). Pause/resume without ending the session.

### 6.6 Summaries
List of AI-generated summaries for the current/past sessions, newest first. Tap a summary to
jump to the corresponding transcript segment. Manual "summarize now" action in addition to the
automatic interval.

### 6.7 Session History
List of past listening sessions (date, duration, detected languages, snippet). Tap into a
session to view its full transcript and summaries. Export (share as text) and delete actions per
session.

### 6.8 Settings
API key management (view/replace/remove — see §5). Summarization interval (off / 2 / 5 / 10 /
15 min). Display surfaces toggle: persistent notification (on by default, cannot fully disable
while listening — Android requires it), floating overlay bubble (opt-in, requires Accessibility
permission), in-app only. VAD sensitivity (low/medium/high) — trades off missed quiet speech vs.
token/battery usage. Text size default, theme. Data & privacy: link back to the disclosure text,
option to auto-delete session history after N days.

### 6.9 Home Screen Widget
Do **not** attempt live word-by-word updates in the widget — Android widgets are
periodic-refresh, not real-time. Use the widget to show the **last summary snippet** and a
"listening: on/off" state, refreshed on a normal widget update cycle. Tapping it opens the Home
screen.

### 6.10 Floating Overlay Bubble (optional surface)
A small movable bubble (via Accessibility Service) that expands into a caption strip showing the
latest transcript line, visible over other apps. This is the "notification bar" experience
described in the original ask, but implemented as an overlay rather than relying solely on the
system notification tray, since it can render over any app the user is currently in.

## 7. Functional Requirements Summary

| Area | Requirement |
|---|---|
| Audio capture | Foreground service, survives screen-off and backgrounding |
| VAD | Local, on-device, gates all streaming — required, not optional |
| Transcription | Gemini 3.5 Transcribe Live, auto language detection, mid-stream language switching |
| Display | Notification (always), in-app view (always), overlay (opt-in), widget (summary snapshot only) |
| Summarization | Periodic batch call, configurable interval, manual trigger available |
| API key | User-supplied only, encrypted storage, validated on entry, removable |
| Sessions | Persisted locally, listable, exportable, deletable |
| Permissions | Requested with plain-language rationale before each grant |

## 8. Non-Functional Requirements

- **Quota/cost awareness:** Live API models show unlimited daily requests on the rate-limit
  console but are capped by tokens-per-minute (varies by model, e.g. ~20K TPM on Transcribe
  Live). VAD gating keeps normal single-user speech well within this — do not remove it as an
  optimization shortcut.
- **Battery:** continuous mic + WebSocket + network radio is expensive; VAD gating is the
  primary mitigation. Consider a low-power "standby" indicator when no speech has been detected
  for a while.
- **Latency:** target sub-second caption appearance from speech to on-screen text.
- **Privacy:** visible/audible indicator that listening is active (protects bystanders'
  awareness, not just the user's). Session data stays local unless the user explicitly exports
  it.
- **Resilience:** handle WebSocket drops/reconnects gracefully (session resumption where the API
  supports it); handle rate-limit (429) responses without crashing — back off and show a clear
  in-app state, don't fail silently.

## 9. Rough Data Model

- **Session:** id, start_time, end_time, list of TranscriptSegments, list of Summaries
- **TranscriptSegment:** id, session_id, timestamp, detected_language, text
- **Summary:** id, session_id, time_range_start, time_range_end, text
- **ApiKeyConfig:** encrypted_key, validated_at, last_validation_status
- **Settings:** summarization_interval, vad_sensitivity, display_surface_prefs, text_size, theme,
  retention_days

## 10. Out of Scope (v1)

- iOS
- Speaker diarization / labeling who said what (Gemini 3.5 Transcribe supports it — worth a v2
  evaluation, not v1)
- Multi-user/shared sessions
- Cloud backup/sync of session history
- Any developer-hosted API key or usage-based billing to the user

## 11. Open Questions / Assumptions to confirm before build

- Confirm whether a fresh, no-billing-linked AI Studio key actually shows "Unlimited" RPD on
  Live API models, or whether that requires billing to be linked (Tier 1) — affects onboarding
  copy and whether "free" needs a caveat.
- Confirm final app name/branding.
- Decide default display surface on first run (notification-only vs. also prompting for overlay
  permission immediately).
- Decide session retention default (auto-delete after X days vs. keep indefinitely until
  manually deleted).
