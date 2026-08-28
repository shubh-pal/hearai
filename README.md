# HearAI — AI Hearing Assist

An Android app that acts as an AI-enhanced hearing aid: it listens to ambient audio in real
time, streams it to Gemini for transcription, and surfaces the text through multiple
always-visible channels (persistent notification, in-app view, optional floating overlay). It
targets 85+ languages with automatic detection and mid-conversation language switching, and can
periodically summarize what was said.

Full product spec: [`docs/requirements.md`](docs/requirements.md).

## Why it's different

Cloud-AI transcription quality + automatic language switching + AI summarization, at zero
backend cost to the developer — each user supplies their own Gemini API key (BYOK). See
[§5](docs/requirements.md#5-api-key-management-byok) for the key-handling and disclosure
requirements.

## Status

v1 scaffold — Android only (see [§3](docs/requirements.md#3-platform--constraints) for why iOS
is deferred). Core architecture, data layer, network clients, audio pipeline, and all v1 screens
are in place; see [Open Questions](docs/requirements.md#11-open-questions--assumptions-to-confirm-before-build)
for decisions still needed before a store release.

## Architecture

```
Mic capture → local VAD gate → WebSocket stream to Gemini 3.5 Transcribe Live
  → rolling transcript buffer → fan-out to display surfaces
  → periodic batch call to a text model for summarization
```

| Layer | Where |
|---|---|
| Data models, Room DB, DataStore settings, encrypted key store | `app/src/main/java/com/hearai/app/data/` |
| Gemini key validation, transcription WebSocket, summarizer | `app/src/main/java/com/hearai/app/network/` |
| Foreground listening service, VAD, mic capture, overlay bubble | `app/src/main/java/com/hearai/app/audio/` |
| Compose screens + navigation | `app/src/main/java/com/hearai/app/ui/` |
| Home screen widget (Glance) | `app/src/main/java/com/hearai/app/widget/` |
| Dependency injection (Hilt) | `app/src/main/java/com/hearai/app/di/` |

Stack: Kotlin, Jetpack Compose (Material 3), Hilt, Room, DataStore, EncryptedSharedPreferences,
OkHttp (WebSocket + REST), kotlinx.serialization, Glance widgets.

## Screens

Welcome → API Key Setup → Permissions → Home/Listening → Live Transcript, Summaries, Session
History (+ detail), Settings. See [§6](docs/requirements.md#6-screens) for the full spec of each.

## Building

Requires JDK 17 and the Android SDK (compileSdk 34, minSdk 31).

```
./gradlew assembleDebug
```

No API keys or secrets are needed to build — the app is BYOK; each user pastes their own Gemini
API key at first launch (never embedded, never logged — see
[§5](docs/requirements.md#5-api-key-management-byok)).

## Out of scope for v1

iOS, speaker diarization, multi-user/shared sessions, cloud backup/sync, and any
developer-hosted API key or billing. See [§10](docs/requirements.md#10-out-of-scope-v1).
