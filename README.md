# OpenCode Android

> ⚠️ **Disclaimer**: This is an **unofficial** community project and is NOT affiliated with or endorsed by the official OpenCode team.

An Android client for [OpenCode](https://opencode.ai/) — an open-source AI coding assistant platform (134K+ GitHub Stars). The app connects to a running OpenCode server via its HTTP REST API and SSE event stream, providing a native mobile experience for managing coding sessions, chatting with AI agents, browsing files, and monitoring tool executions.

This project is forked from [OC Remote](https://play.google.com/store/apps/details?id=dev.minios.ocremote) (the official OpenCode Remote Android app), rebuilding it with an MVI + EventReducer architecture for improved state management and extensibility.

## Architecture

**MVI + EventReducer (Redux-like)** — SSE event-driven architecture. All real-time events flow through a central `EventReducer`, exposing immutable `StateFlow`s that ViewModels subscribe to, driving Compose UI re-composition.

```
SSE Event → EventReducer → StateFlow → ViewModel → Compose UI
```

For detailed architecture documentation, see [ARCHITECTURE.md](./ARCHITECTURE.md).

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material 3 |
| Architecture | MVI + EventReducer (Redux-like) |
| Navigation | Compose Navigation (sealed class routes) |
| DI | Hilt |
| Network | Ktor Client (OkHttp engine) |
| Serialization | kotlinx.serialization (sealed class polymorphism) |
| Async | Coroutines + Flow |
| Local Storage | Room + DataStore Preferences |
| Image Loading | Coil 3 |
| Markdown | multiplatform-markdown-renderer |
| SSE | OkHttp EventSource |
| WebSocket | OkHttp WebSocket (PTY terminal) |
| Security | EncryptedSharedPreferences (credential storage) |

## Project Status

🚧 **Early Development** — Architecture planning complete, implementation starting.

## OpenCode API

The app targets OpenCode API v1.3.10+ with **106 endpoints across 24 modules**:

- **Session** (27 endpoints) — CRUD, messaging, streaming, sharing, forking, reverting
- **Global** (7 endpoints) — Health check, global SSE events, config
- **Provider** (4 endpoints) — LLM provider management + OAuth
- **Permission** (2 endpoints) — Runtime permission requests/replies
- **Question** (3 endpoints) — AI-to-user structured questions
- **File/Find** (6 endpoints) — File browsing, content reading, search
- **PTY** (6 endpoints) — Terminal sessions via REST + WebSocket
- **Auth** (2 endpoints) — Provider credential management
- + 16 more modules

Authentication: HTTP Basic Auth + `x-opencode-directory` / `x-opencode-workspace` headers.

## Build

```bash
./gradlew assembleDebug
```

Requires Android SDK with compileSdk 36. Keystore already configured via `keystore.properties`.

## License

MIT
