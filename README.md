# OpenCode Android

> ⚠️ **Disclaimer**: This is an **unofficial** community project and is NOT affiliated with or endorsed by the official OpenCode team.

An Android client for [OpenCode](https://opencode.ai/) — an open-source AI coding assistant platform (134K+ GitHub Stars). The app connects to a running OpenCode server via its HTTP REST API and SSE event stream, providing a native mobile experience for managing coding sessions, chatting with AI agents, browsing files, and monitoring tool executions.

This project is forked from [OC Remote](https://play.google.com/store/apps/details?id=dev.minios.ocremote) (the official OpenCode Remote Android app), rebuilding it with an MVI + EventReducer architecture for improved state management and extensibility.

## Screenshots

<p align="center">
  <img src="screenshots/01_home.png" width="22%" />
  <img src="screenshots/03_project_list.png" width="22%" />
  <img src="screenshots/04_session_list.png" width="22%" />
  <img src="screenshots/05_chat.png" width="22%" />
</p>

## Project Status

🚧 **Early Development** — Architecture planning complete, implementation starting.

> Note: This project was previously developed primarily with the GLM-5.1 model. Development has slowed recently due to current token costs.

## Build

```bash
./gradlew assembleDebug
```

Requires Android SDK with compileSdk 36. Keystore already configured via `keystore.properties`.

## License

MIT
