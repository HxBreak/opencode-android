# Architecture

> Based on OpenCode API v1.3.10 (106 endpoints, 24 modules)
> Reference implementation: OC Remote (`dev.minios.ocremote`) — validated MVI + EventReducer pattern

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│ OpenCode Android — Architecture Overview                        │
│                                                                 │
│ ┌─── UI Layer (Compose + MVI) ────────────────────────────────┐ │
│ │  Home → ProjectList → SessionList → Chat ─┬→ FileBrowser   │ │
│ │     ├→ ServerSettings                     └→ Terminal      │ │
│ │     ├→ Settings                                            │ │
│ │     └→ About                                               │ │
│ └─────────────────────────────────────────────────────────────┘ │
│         │ UiState (StateFlow)                                    │
│ ┌─── ViewModel Layer ──────────────────────────────────────────┐ │
│ │  combine(EventReducer flows + local state) → UiState        │ │
│ └─────────────────────────────────────────────────────────────┘ │
│         │ Intent / Action                                        │
│ ┌─── Data Layer ───────────────────────────────────────────────┐ │
│ │  ┌─────────────┐  ┌──────────────┐  ┌────────────────────┐ │ │
│ │  │ EventReducer │  │ OpenCodeApi  │  │ SseClient          │ │ │
│ │  │ (Redux-like) │  │ (Ktor REST)  │  │ (OkHttp SSE)       │ │ │
│ │  │ 24 events →  │  │ ~80 endpoints│  │ /global/event      │ │ │
│ │  │ 11 StateFlows│  │ stateless    │  │ heartbeat+backoff  │ │ │
│ │  └──────┬───────┘  └──────┬───────┘  └─────────┬──────────┘ │ │
│ │         │                 │                     │            │ │
│ │  ┌──────▼─────────────────▼─────────────────────▼──────────┐ │ │
│ │  │              OpenCode Server (HTTP + SSE)                │ │ │
│ │  └─────────────────────────────────────────────────────────┘ │ │
│ │  ┌──────────┐  ┌────────────┐  ┌──────────────────────────┐ │ │
 │ │  │ Room DB      │  │ DataStore        │  │ EncryptedSharedPrefs    │ │ │
 │ │  │ message      │  │ settings/drafts  │  │ server credentials     │ │ │
 │ │  │ cache        │  │                  │  │ (wraps Android Keystore)│ │ │
 │ │  └──────────┘  └────────────┘  └──────────────────────────┘ │ │
│ └──────────────────────────────────────────────────────────────┘ │
│         ↑                                                        │
│ ┌─── Service Layer ────────────────────────────────────────────┐ │
│ │  OpenCodeConnectionService (ForegroundService)               │ │
│ │  - SSE long connection  - Notifications                      │ │
│ │  - Exponential backoff reconnection                          │ │
│ └──────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Architecture | MVI + EventReducer | SSE event-driven model fits Redux-style state reduction |
| DI | Hilt | Validated in OC Remote; mature and scalable |
| Network | Ktor + OkHttp | Native Kotlin, excellent SSE/WebSocket support |
| Storage | Room + DataStore | Room for message caching (offline), DataStore for settings |
| Auth storage | EncryptedSharedPreferences | Wraps Android Keystore automatically, simple SharedPreferences API |
| No WebView | Full native | Better UX, less maintenance |
| No Termux | Deferred to P3+ | Focus on remote client first |
| minSdk | 28 (Android 9) | Modern baseline, >95% device coverage |
| Language | English primary | International audience |

### Differences from OC Remote

| Aspect | OC Remote | This Project |
|--------|-----------|-------------|
| Package | `dev.minios.ocremote` | `me.xiaok.opencode` |
| minSdk | 26 | 28 |
| Local Storage | DataStore only | Room + DataStore (message caching) |
| WebView | Yes (fallback) | No (full native) |
| Termux Runtime | Yes (core feature) | Deferred (P3+) |
| Language | Multi-language (15) | English primary |

---

## Tech Stack

| Category | Technology | Version | Purpose |
|----------|-----------|---------|---------|
| Language | Kotlin | 2.0.21 | Primary language |
| UI | Jetpack Compose | BOM 2024.09.00 | Declarative UI |
| Design | Material 3 | — | Design system |
| Navigation | Compose Navigation | — | Type-safe routing (sealed class) |
| DI | Hilt | — | Dependency injection |
| Network (REST) | Ktor Client | — | HTTP API calls |
| Network (Engine) | OkHttp | — | SSE + WebSocket support |
| Serialization | kotlinx.serialization | — | Polymorphic sealed classes |
| Async | Coroutines + Flow | — | Reactive streams |
| Database | Room | — | Message/session caching |
| Preferences | DataStore | — | Settings persistence |
| Security | EncryptedSharedPreferences | — | Credential encryption (wraps Keystore via security-crypto) |
| Images | Coil 3 | — | Compose-native image loading |
| Markdown | multiplatform-markdown-renderer | — | Chat message rendering |
| Terminal | Custom VT100 | — | PTY terminal emulation |

---

## Package Structure

```
me.xiaok.opencode/
├── data/
│   ├── api/                           # Network layer
│   │   ├── OpenCodeApi.kt            # REST API client (stateless design)
│   │   ├── SseClient.kt              # SSE long-polling client
│   │   └── WsClient.kt               # WebSocket client (PTY)
│   ├── repository/
│   │   ├── EventReducer.kt           # ⭐ Central state management (Redux pattern)
│   │   ├── ServerRepository.kt       # Server connection persistence
│   │   ├── SettingsRepository.kt     # App settings
│   │   ├── DraftRepository.kt        # Chat drafts
│   │   └── CacheRepository.kt        # Room cache (sessions/messages)
│   └── local/
│       ├── db/                        # Room database
│       │   ├── AppDatabase.kt
│       │   ├── dao/SessionDao.kt
│       │   ├── dao/MessageDao.kt
│       │   └── entity/
 │       └── datastore/                 # DataStore preferences
 │       └── security/                  # EncryptedSharedPreferences wrapper
├── domain/model/                      # Domain models (sealed class hierarchies)
│   ├── ServerConnection.kt           # Connection info (baseUrl + authHeader)
│   ├── Session.kt                    # Session entity
│   ├── Message.kt                    # User / Assistant sealed class
│   ├── Part.kt                       # ⭐ 12 Part types (sealed class)
│   ├── SseEvent.kt                   # ⭐ 24 SSE event types
│   ├── ToolState.kt                  # pending/running/completed/error
│   ├── SessionStatus.kt             # idle/busy/retry
│   ├── Provider.kt                   # Provider + Model info
│   ├── Permission.kt                # Permission request
│   ├── Question.kt                  # Question request
│   └── FileNode.kt                  # File browser node
├── ui/
│   ├── navigation/
│   │   ├── NavGraph.kt
│   │   └── Screen.kt                 # sealed class routes
│   ├── screens/
│   │   ├── home/                     # Home + server management
│   │   ├── projects/                 # Project list (per server)
│   │   ├── sessions/                 # Session list (per project)
│   │   ├── chat/                     # ⭐ Core: chat + terminal
│   │   ├── server/                   # Provider/Model management
│   │   ├── files/                    # File browser
│   │   ├── settings/                 # App settings
│   │   └── about/                    # About page
│   ├── components/                   # Reusable components
│   │   ├── chat/                     # Message bubbles / tool cards
│   │   ├── terminal/                 # VT100 terminal component
│   │   └── common/                   # Shared components
│   └── theme/
├── service/
│   └── OpenCodeConnectionService.kt  # Foreground SSE service
├── di/
│   ├── NetworkModule.kt             # Ktor/OkHttp DI
│   ├── DatabaseModule.kt            # Room DI
│   └── RepositoryModule.kt          # Repository DI
├── OpenCodeApp.kt                   # Application (Hilt entry point)
└── MainActivity.kt                  # Single Activity host
```

---

## Core Architecture Components

### 1. EventReducer (Central State Management)

The heart of the app. All SSE events flow through `processEvent()`, updating reactive state.

Pattern: Redux / Event Sourcing

```
SSE Event Stream → SseClient → EventReducer.processEvent()
      │
      ├── serverSessions:    StateFlow<Map<serverId, Set<sessionId>>>
      ├── sessions:          StateFlow<List<Session>>
      ├── sessionStatuses:   StateFlow<Map<sessionId, SessionStatus>>
      ├── messages:          StateFlow<Map<sessionId, List<Message>>>
      ├── parts:             StateFlow<Map<messageId, List<Part>>>
      ├── sessionDiffs:      StateFlow<Map<sessionId, List<FileDiff>>>
      ├── permissions:       StateFlow<Map<sessionId, List<PermissionRequest>>>
      ├── questions:         StateFlow<Map<sessionId, List<QuestionRequest>>>
      ├── todos:             StateFlow<Map<sessionId, List<Todo>>>
      ├── vcsBranch:         StateFlow<String?>
      └── projectInfo:       StateFlow<Project?>
                                         │
                                         ▼
                      ViewModels combine() → UiState → Compose UI
```

**Key operations:**

| Method | Purpose |
|--------|---------|
| `processEvent(event)` | Main dispatch — routes 24 event types to state updates |
| `setSessions(serverId, list)` | Bulk init from REST API |
| `setMessages(sessionId, list)` | Bulk init from REST API |
| `clearForServer(serverId)` | Cleanup on disconnect |
| `clearAll()` | Full state reset |

Optimistic updates: `updateSessionStatus()`, `removeQuestion()` — immediate UI feedback.

### 2. OpenCodeApi (REST Client)

Stateless design: all methods accept `ServerConnection(baseUrl, authHeader)`.

Common request headers:

| Header | Value | Purpose |
|--------|-------|---------|
| `Authorization` | `Basic {base64}` | Authentication (optional) |
| `x-opencode-directory` | encoded path | Project directory scoping |
| `x-opencode-workspace` | workspace ID | Workspace scoping |

### 3. SseClient (Server-Sent Events)

Connects to `GET /global/event` for real-time updates:

- Heartbeat: 10s interval, 15s timeout → auto-reconnect
- Exponential backoff (configurable: aggressive 5s / normal 30s / conservative 60s)
- Runs in `OpenCodeConnectionService` (ForegroundService)
- Multi-server: one SSE connection per server

### 4. OpenCodeConnectionService (Foreground Service)

- Persistent notification (per-server grouping, InboxStyle)
- Event notifications: session idle, permission asked, question asked, session error
- Deep link: notification tap → navigate to specific session
- Watchdog: re-post notification if system kills it
- Notification channels: connection (LOW), tasks (HIGH), tasks_silent (LOW), permissions (HIGH)

---

## Error Handling Strategy

### Network Error Classification

| Error Type | Examples | UI Response | Retry |
|------------|----------|-------------|-------|
| **Transient** | Timeout, 503, DNS failure | Snackbar with retry button | Auto (exponential backoff) |
| **Auth** | 401, 403 | Redirect to server edit, highlight credential fields | Manual only |
| **Client** | 400, 404, 409 | Inline error in relevant screen | No auto-retry |
| **Server** | 500, 502 | Full-screen error banner with "Retry" | Manual only |
| **SSE Disconnect** | Connection lost | Top bar: "Reconnecting…" pulse + retry countdown | Auto (backoff per settings) |

### SSE Error Recovery

```
Connection Lost
  → Immediate: Show "Reconnecting…" in top bar (pulse animation)
  → 0-5s: Aggressive reconnect attempts (every 2s)
  → 5-30s: Normal backoff (5s, 10s, 20s, 30s cap)
  → 30s+: Conservative (30s interval) + notification "Connection lost"
  → On reconnect: 
      1. Fetch current state via REST (sessions, statuses)
      2. Resume SSE stream
      3. Dismiss "Reconnecting…" indicator
      4. SnackBar: "Reconnected" (2s, auto-dismiss)
```

### EventReducer Error Resilience

- **Out-of-order events**: Each event carries full entity data (not diffs). Process by upsert — later event always wins.
- **Duplicate events**: Idempotent by design — `upsert` operations naturally deduplicate.
- **Unknown event type**: Log + skip. Never crash on forward-compatible additions.
- **Malformed event JSON**: Catch per-event, log with `event.type`, continue processing stream.

### API Call Error Pattern

```kotlin
// Every API call follows this pattern in ViewModels
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val kind: ErrorKind, val message: String, val retryable: Boolean) : ApiResult<T>()
    object Loading : ApiResult<Nothing>()
}

enum class ErrorKind { NETWORK, AUTH, CLIENT, SERVER, TIMEOUT }
```

- `Loading` → show spinner/skeleton
- `Error(retryable=true)` → show error + retry button
- `Error(retryable=false)` → show error message only
- `Success` → update UI state

---

## Multi-Server Concurrency Model

### Design: Single EventReducer + Server-Prefixed StateFlows

EventReducer is a **global `@Singleton`**. All StateFlows key by `serverId` to support multiple simultaneous server connections.

```
┌─────────────────────────────────────────────┐
│ EventReducer (@Singleton)                    │
│                                              │
│  activeServers: StateFlow<Set<serverId>>     │
│  serverSessions: Map<serverId, Set<sId>>     │
│  sessions: Map<sessionId, Session>           │
│  sessionStatuses: Map<sessionId, Status>     │
│  messages: Map<sessionId, List<Message>>     │
│  parts: Map<messageId, List<Part>>           │
│  permissions: Map<sessionId, List<Perm>>     │
│  questions: Map<sessionId, List<Quest>>      │
│                                              │
│  processEvent(serverId, event)               │
│  clearForServer(serverId)                    │
└─────────────────────────────────────────────┘
         ▲           ▲           ▲
    SseClient(A) SseClient(B) SseClient(C)
         │           │           │
     Server A    Server B    Server C
```

### Key Rules

1. **`sessionId` is globally unique** (UUID from server) → no collision across servers
2. **Session-to-server mapping** via `serverSessions` → reverse lookup for any session
3. **SSE events tagged with `serverId`** by SseClient before dispatching to EventReducer
4. **Server disconnect** → `clearForServer(serverId)` removes all associated data
5. **UI always knows current server** → passed as navigation argument, ViewModel filters StateFlows

### ViewModel Filtering Pattern

```kotlin
// Each screen ViewModel receives a serverId and filters global state
class ChatViewModel(serverId: String, sessionId: String) : ViewModel() {
    val uiState = combine(
        eventReducer.sessions.map { it[sessionId] },
        eventReducer.messages.map { it[sessionId] ?: emptyList() },
        eventReducer.parts,
        eventReducer.permissions.map { it[sessionId] ?: emptyList() },
        eventReducer.questions.map { it[sessionId] ?: emptyList() },
    ) { session, messages, parts, perms, questions ->
        ChatUiState(session, messages, parts, perms, questions)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatUiState())
}
```

---

## Hilt DI Scope Planning

### Module Structure

```
di/
├── NetworkModule.kt          # @Singleton — Ktor client, OkHttp, SSE factory
├── DatabaseModule.kt         # @Singleton — Room database, DAOs
├── RepositoryModule.kt       # @Singleton — EventReducer, repositories
├── SecurityModule.kt         # @Singleton — EncryptedSharedPreferences
└── ServiceModule.kt          # @Singleton — ForegroundService helpers
```

### Scope Assignments

| Component | Scope | Lifetime | Rationale |
|-----------|-------|----------|-----------|
| `EventReducer` | `@Singleton` | App process | Single source of truth for all state |
| `OpenCodeApi` | `@Singleton` | App process | Stateless, shares Ktor client |
| `AppDatabase` | `@Singleton` | App process | Single DB connection pool |
| `ServerRepository` | `@Singleton` | App process | Manages all server connections |
| `SettingsRepository` | `@Singleton` | App process | Single DataStore instance |
| `DraftRepository` | `@Singleton` | App process | Single DataStore instance |
| `CacheRepository` | `@Singleton` | App process | Wraps Room DAOs |
| `KtorClient` | `@Singleton` | App process | Connection pooling, shared engine |
| `OkHttpClient` | `@Singleton` | App process | SSE + WebSocket connection sharing |
| `EncryptedSharedPreferences` | `@Singleton` | App process | Single encrypted file handle |
| `SseClient` | **Unscoped** (factory) | Per-server | Created/destroyed on connect/disconnect |
| `ViewModel` | `@HiltViewModel` | Navigation destination | Scoped to NavGraph entry |

### SseClient Lifecycle

SseClient is NOT a singleton — it's created per-server connection and held by `ServerRepository`:

```kotlin
class ServerRepository @Inject constructor(/* ... */) {
    private val sseClients = mutableMapOf<String, SseClient>()  // serverId → client
    
    fun connect(server: ServerConnection) {
        val client = sseClientFactory.create(server)
        sseClients[server.id] = client
        client.connect()
    }
    
    fun disconnect(serverId: String) {
        sseClients.remove(serverId)?.disconnect()
        eventReducer.clearForServer(serverId)
    }
}
```

---

## Navigation Parameters

### Route Definitions

```kotlin
@Serializable
sealed class Screen {
    @Serializable data object Home : Screen()
    @Serializable data class ProjectList(val serverId: String) : Screen()
    @Serializable data class SessionList(val serverId: String, val projectId: String, val directory: String) : Screen()
    @Serializable data class Chat(val serverId: String, val sessionId: String) : Screen()
    @Serializable data class FileBrowser(val serverId: String, val sessionId: String? = null) : Screen()
    @Serializable data class ServerSettings(val serverId: String) : Screen()
    @Serializable data class ServerProviders(val serverId: String) : Screen()
    @Serializable data class ServerModelFilter(val serverId: String) : Screen()
    @Serializable data object Settings : Screen()
    @Serializable data object About : Screen()
}
```

### Deep Link URI Patterns

| Screen | URI Pattern | Example |
|--------|------------|---------|
| Chat | `opencode://session/{sessionId}` | Tap notification → specific session |
| ProjectList | `opencode://projects/{serverId}` | Quick access to server projects |
| Settings | `opencode://settings` | — |

### Share Intent Handling

- `ACTION_SEND` with `image/*` → Pick session → Navigate to `Chat` with image attachment
- `ACTION_SEND` with `text/plain` → Pick session → Navigate to `Chat` with pre-filled message

---

## Message Pagination & Cache Sync

### Strategy: API-First, Room as Cache

```
┌──────────────────────────────────────────────┐
│ ChatScreen opens (sessionId)                  │
│   │                                           │
│   ├─ 1. Read Room cache → instant display     │
│   ├─ 2. GET /session/{id}/message → refresh   │
│   ├─ 3. Diff: upsert new, flag removed        │
│   ├─ 4. Write updated list to Room            │
│   └─ 5. SSE takes over for real-time updates  │
│                                               │
│ Load more (scroll up):                        │
│   ├─ 1. Read next page from Room (instant)    │
│   └─ 2. GET with ?after=messageId → sync      │
│                                               │
│ SSE pushes new message/part:                  │
│   ├─ 1. EventReducer updates StateFlow        │
│   └─ 2. Background: write to Room cache       │
│                                               │
│ Dedup: messageId as primary key → upsert      │
└──────────────────────────────────────────────┘
```

### Pagination Parameters

| Param | Default | Description |
|-------|---------|-------------|
| `limit` | 50 (configurable 25-200) | Messages per page |
| `after` | null | Cursor-based: message ID to load older |
| `before` | null | Cursor-based: message ID to load newer |

- No Paging 3 library — custom cursor-based pagination is simpler for our append-only chat model
- OOM protection: catch `OutOfMemoryError` → halve `limit` → retry

### Room Cache Invalidation

| Trigger | Action |
|---------|--------|
| Server disconnect | `CLEAR FROM messages WHERE serverId = ?` |
| Session deleted (SSE) | `DELETE FROM messages WHERE sessionId = ?` |
| App backgrounded > 1h | Soft expiry: re-fetch on next open |
| Manual refresh (pull-down) | Re-fetch current page + newer |

---

## ProGuard / R8 Configuration

`proguard-rules.pro` must include:

```proguard
# kotlinx.serialization — keep serializer classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class me.xiaok.opencode.domain.model.**$$serializer { *; }
-keepclassmembers class me.xiaok.opencode.domain.model.** {
    *** Companion;
}
-keepclasseswithmembers class me.xiaok.opencode.domain.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor Client — keep serializable request/response classes
-keep class me.xiaok.opencode.data.api.dto.** { *; }

# OkHttp SSE
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Sealed class polymorphism
-keep class * extends me.xiaok.opencode.domain.model.Part { *; }
-keep class * extends me.xiaok.opencode.domain.model.Message { *; }
-keep class * extends me.xiaok.opencode.domain.model.ToolState { *; }
-keep class * extends me.xiaok.opencode.domain.model.SseEvent { *; }
```

> **Note:** Validate with `./gradlew assembleRelease` + run on device after any serialization changes.

---

## Offline Experience Strategy

### Tiered Offline Support

| Tier | Feature | Status |
|------|---------|--------|
| **Read cache** | View previously loaded sessions & messages offline | P2 |
| **Write queue** | Queue messages when offline, auto-send on reconnect | P3 |
| **Full offline** | Complete session interaction without network | Not planned |

### P2: Read Cache Behavior

1. **Network available** → API-first, cache as fallback (see Message Pagination section)
2. **Network lost** → Show cached data with "Offline" banner in top bar
3. **Cached content**: Session list + messages for all connected servers
4. **Actions disabled**: Send message, create session, file browse — grayed out with "No connection" tooltip

### P3: Write Queue (Optional)

```
User sends message while offline
  → Save to Room: { sessionId, text, images, agent, model, timestamp, status=QUEUED }
  → Show in chat with "Queued" badge + "Sending when connected…" subtitle
  → On reconnect: flush queue in order → call prompt_async for each
  → On failure: mark as FAILED → user can tap to retry or delete
```

---

## Testing Strategy

### Layer-by-Layer Approach

| Layer | Test Type | Framework | Scope |
|-------|-----------|-----------|-------|
| **EventReducer** | Unit test | JUnit + Turbine | Every event type → state transition |
| **Domain Models** | Unit test | JUnit | Serialization round-trip, sealed class exhaustiveness |
| **API Client** | Unit test | JUnit + Ktor MockEngine | Request format, response parsing, error handling |
| **Repositories** | Unit test | JUnit + MockK | Data flow, cache coordination |
| **ViewModels** | Unit test | JUnit + Turbine | UiState emission, intent handling |
| **Compose UI** | Screenshot test | Roborazzi | Key screens (light/dark) |
| **Compose UI** | Instrumented | Compose Testing | Critical flows (send message, reply permission) |

### Priority

1. **P0**: EventReducer unit tests (core state machine, must be bulletproof)
2. **P1**: API client tests (serialization + parsing), ViewModel tests
3. **P2**: Repository tests, screenshot tests
4. **P3**: Instrumented UI tests

### Test Infrastructure

```kotlin
// No test framework choices needed beyond standard:
testImplementation("junit:junit:4.13.2")
testImplementation("io.mockk:mockk:1.13.12")
testImplementation("app.cash.turbine:turbine:1.1.0")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
androidTestImplementation("androidx.compose.ui:ui-test-junit4")
debugImplementation("androidx.compose.ui:ui-test-manifest")
```

---

## Accessibility

### Minimum Viable Accessibility (P1+)

| Feature | Approach | Effort |
|---------|----------|--------|
| TalkBack | Content descriptions on all interactive elements, meaningful text for tool cards | Low (Compose does most automatically) |
| Font scaling | Respect system `fontScale`, chat font size setting overrides independently | Low |
| Color contrast | Material 3 defaults meet WCAG AA; test custom colors (tool state indicators) | Low |
| Touch targets | Material 3 minimum 48dp — ensure tool card buttons, FAB meet this | None (Compose default) |
| Focus order | Automatic in Compose `LazyColumn`; verify chat input bar order | Low |

### Accessibility Testing

- Android Accessibility Scanner (free app) — run on each screen
- TalkBack walkthrough — manually verify critical flows (chat, permission dialog)
- Not a blocking P0 item, but should be validated before P2

---

## Page Planning

### Navigation Flow

```
Home ──→ ProjectList ──→ SessionList ──→ Chat ←── Core page
  │                            │               │
  │                            │               ├→ FileBrowser (side drawer)
  │                            │               └→ Terminal (embedded toggle)
  │                            │
  ├────→ ServerSettings ──→ ServerProviders
  │                    └──→ ServerModelFilter
  ├────→ Settings
  └────→ About

Deep link: notification → Chat (specific session)
Share intent: images → session picker → Chat
```

### Routes

```kotlin
sealed class Screen(val route: String) {
    Home("home")
    ProjectList("projects/{serverId}")
    SessionList("sessions/{serverId}/{projectId}/{directory}")
    Chat("chat/{serverId}/{sessionId}")
    FileBrowser("files")
    ServerSettings("server_settings")
    ServerProviders("server_providers")
    ServerModelFilter("server_model_filter")
    Settings("settings")
    About("about")
}
```

---

### Page 1: HomeScreen — Server Management (Entry Page)

**APIs:** `GET /global/health`, `GET /global/config`

```
┌──────────────────────────────────┐
│ TopAppBar                        │
│  OpenCode  [+Add] [Settings]     │
├──────────────────────────────────┤
│ Server Card 1                    │
│  Server Name · url:port          │
│  ● Connected                     │
│  [Projects] [Settings] [Disconnect]│
├──────────────────────────────────┤
│ Server Card 2 (disconnected)     │
│  [Connect]                       │
├──────────────────────────────────┤
│ Empty state (no servers)         │
└──────────────────────────────────┘
```

**Features:**
- Saved server list (DataStore): name, URL, username, password, auto-connect
- Add/Edit server dialog: URL validation (auto-add `http://`), optional Basic Auth
- Connection flow: health check → start foreground service → SSE takes over
- Server status: connected (green) / connecting (pulse) / disconnected
- QR code quick connect (optional, P3)

---

### Page 2: ProjectListScreen — Project List (New)

**APIs:** `GET /project`

```
┌──────────────────────────────────┐
│ TopAppBar                        │
│  ← Server Name  [+New Session]  │
├──────────────────────────────────┤
│ Project list (LazyColumn)        │
│  ┌────────────────────────────┐  │
│  │ 📁 myapp                   │  │
│  │ ~/projects/myapp · git     │  │
│  │ 12 sessions · Mar 31       │  │
│  └────────────────────────────┘  │
│  ┌────────────────────────────┐  │
│  │ 📁 opencode-android        │  │
│  │ ~/projects/opencode · git  │  │
│  │ 5 sessions · Mar 30        │  │
│  └────────────────────────────┘  │
│                                  │
│       FAB (+ New Session)       │
└──────────────────────────────────┘
```

**Features:**
- Project list loaded from `GET /project` API
- Per-row: project name + directory path (tilde format) + VCS badge + session count + last updated
- Tap project → navigate to SessionList filtered by that project's directory
- FAB: quick new session (creates in selected project or prompts for directory)
- Empty state: "No projects yet" with "New Session" action → direct to Chat
- Real-time via SSE: `project.updated` event

---

### Page 3: SessionListScreen — Session List (Per Project)

**APIs:** `GET /session?directory=xxx`, `POST /session`, `DELETE /session/{id}`, `PATCH /session/{id}`

```
┌──────────────────────────────────┐
│ TopAppBar (normal/select mode)   │
│  ← Project Name                 │
├──────────────────────────────────┤
│ Session list (LazyColumn)        │
│  Add authentication              │
│  Mar 31, 14:30 · +12 -3         │
│  ← swipe delete / rename →      │
│                                  │
│       FAB (+ New Session)       │
└──────────────────────────────────┘
```

**Features:**
- Filtered by project directory (`GET /session?directory=xxx`)
- Per-row: title + timestamp + status (pulse/retry dot) + change stats (+N/-M)
- Swipe: left → delete, right → rename
- Long press → selection mode (batch delete)
- FAB: create new session in current project directory
- Real-time via SSE: `session.created`, `session.updated`, `session.deleted`

---

### Page 4: ChatScreen — Chat & Terminal ⭐ Core

**APIs:** Session message CRUD, `prompt_async`, `abort`, `fork`, `share`, `revert`, `unrevert`, `summarize`, `command`, `shell`, `diff`, `todo`, `permission`, `question`, `agent`, `command`

```
┌──────────────────────────────────┐
│ TopAppBar                        │
│  ← Title  [Token/Cost] [Stop] ⋮ │
├──────────────────────────────────┤
│  Message list / Terminal (toggle)│
│  - User message bubbles          │
│  - Assistant replies (streaming) │
│  - Tool call cards               │
│  - Permission/Question requests  │
│  - Revert banner                 │
├──────────────────────────────────┤
│ ChatInputBar                     │
│  Status: ● Working  Context: 42% │
│  [Agent ▼] [Model ▼] [🧠] [📎] │
│  ┌─────────────────────┐ [➤]    │
│  │ Type a message...   │        │
│  └─────────────────────┘        │
└──────────────────────────────────┘
```

**TopAppBar Menu:** Open in Web / New session / Fork / Compact / Review changes / Share / Rename / Export

**12 Part Types Rendering:**

| Part Type | UI Rendering |
|-----------|-------------|
| `text` | Markdown rendering (syntax highlighting) |
| `reasoning` | Collapsed "Thinking", italic + left accent border |
| `tool` | Dispatched to specific tool card |
| `file` | Image thumbnail or file card |
| `subtask` | Sub-agent card (agent + prompt + output) |
| `step-start` | Step group header (collapsible) |
| `step-finish` | Step group footer with cost/tokens |
| `snapshot` | Snapshot reference badge |
| `patch` | File change list, expandable diff view |
| `agent` | Agent switch marker |
| `retry` | Retry record (error display) |
| `compaction` | Context compression marker |

**Tool Card Types (by `ToolPart.tool`):**

| Tool | Card Content |
|------|-------------|
| `bash` | Command + output (ANSI stripped) + copy |
| `read` | File path + offset/limit |
| `write` | File path + content preview (max 5000 chars) |
| `edit` | Diff view (red/green) + stats (+N/-M) |
| `glob`/`grep` | Search pattern + result count |
| `task` | Sub-agent description + output |
| `todowrite` | Todo list (checkbox style) |
| `webfetch`/`websearch` | URL/query + content preview |
| Default | Generic: title + status + output |

Tool states: Pending (yellow) → Running (blue, spinner) → Completed (green) → Error (red)

**ChatInputBar Features:**
- Status row: working state pulse + context usage % (<70% gray / 70-89% orange / >=90% red)
- Selector row: Agent / Model / Variant (thinking depth)
- `@` file mention popup, `/` command popup, `!` shell mode
- Image attachment → WebP compression
- Draft auto-save (text, images, agent, variant)

**Terminal (Embedded Toggle):**
- VT100 via WebSocket `/pty/{id}/connect`
- ANSI colors, box-drawing, scrollback, cursor blink
- Pinch-to-zoom (6-20sp), virtual keyboard overlay
- Multi-tab (cross-session, per-server)

**Message Pagination:** Initial 50 (configurable), double on load-more, OOM catch + halve retry

---

### Page 5: FileBrowserScreen — File Browser

**APIs:** `GET /file`, `GET /file/content`, `GET /file/status`, `GET /find`, `GET /find/file`, `GET /find/symbol`, `GET /vcs`

**Features:**
- Directory tree browser (LazyColumn, expandable)
- File viewer: syntax highlighting + Git diff
- Dual search: text content (regex, ripgrep) / file name (fuzzy)
- Git status badges: added/modified/deleted
- Navigate from ChatScreen tool cards

---

### Page 6-7: ServerProvidersScreen — Provider Management

**APIs:** `GET /provider`, `GET /provider/auth`, `POST /provider/{id}/oauth/authorize`, `POST /provider/{id}/oauth/callback`, `PUT /auth/{id}`, `DELETE /auth/{id}`, `POST /global/dispose`

**Features:**
- Connected / Available groups, sorted by popularity
- API Key connect (input dialog)
- OAuth connect: device code / browser / authorization code
- Headless OAuth failure → auto-fallback to browser
- Source tags: env (non-removable) / config / api / custom

---

### Page 8: ServerModelFilterScreen — Model Filtering

**APIs:** `GET /provider`, `GET /config`

- Grouped by Provider, visibility Switch per model
- Search filter (name + ID), hidden models excluded from ChatScreen picker

---

### Page 9: SettingsScreen — App Settings

DataStore-backed local settings:

| Category | Settings |
|----------|----------|
| General | Reconnect mode (aggressive/normal/conservative) |
| Appearance | Theme (system/light/dark), Dynamic color, AMOLED dark |
| Chat Display | Font size (small/medium/large), Compact messages, Code word wrap, Collapse tools |
| Chat Behavior | Initial messages (25-200), Confirm send, Haptic feedback, Keep screen on |
| Images | Compress (on/off), Max side (720-4096px), WebP quality (40-80) |
| Terminal | Font size (6-20sp slider) |
| Notifications | Enabled, Silent mode |

---

### Page 10: AboutScreen

Static: app name, version, description, GitHub link, OpenCode link, license.

---

## API Mapping

### Full Endpoint Reference (106 endpoints, 24 modules)

| Priority | Module | Endpoints | Key Endpoints |
|----------|--------|-----------|---------------|
| 1 | `global` | 7 | `GET /global/health`, `GET /global/event` (SSE), `GET/PATCH /global/config`, `POST /global/dispose`, `GET /global/sync-event` |
| 2 | `session` | 27 | CRUD + `POST /{id}/prompt_async`, `POST /{id}/abort`, `POST /{id}/fork`, `POST /{id}/share`, `POST /{id}/revert`, `POST /{id}/command`, `POST /{id}/shell`, `GET /{id}/message`, `GET /{id}/diff`, `GET /{id}/todo`, `GET /{id}/children`, `POST /{id}/init`, `POST /{id}/summarize`, `DELETE /{id}/message/{msgId}`, `PATCH/DELETE /{id}/message/{msgId}/part/{partId}`, `POST /{id}/permissions/{permId}` |
| 3 | `event` | 1 | `GET /event` (instance-level SSE) |
| 4 | `permission` | 2 | `GET /permission`, `POST /permission/{id}/reply` |
| 5 | `question` | 3 | `GET /question`, `POST /question/{id}/reply`, `POST /question/{id}/reject` |
| 6 | `project` | 4 | `GET /project`, `GET /project/current` |
| 7 | `provider` | 4 | `GET /provider`, `GET /provider/auth`, `POST /{id}/oauth/authorize`, `POST /{id}/oauth/callback` |
| 8 | `config` | 3 | `GET /config`, `PATCH /config`, `GET /config/providers` |
| 9 | `auth` | 2 | `PUT /auth/{id}`, `DELETE /auth/{id}` |
| 10 | `file` | 3 | `GET /file`, `GET /file/content`, `GET /file/status` |
| 11 | `find` | 3 | `GET /find` (text), `GET /find/file`, `GET /find/symbol` |
| 12 | `mcp` | 8 | MCP server management |
| 13 | `pty` | 6 | `POST /pty`, `DELETE /{id}`, `PUT /{id}`, `WS /{id}/connect` |
| 14 | `experimental` | 11 | Workspaces, worktree |
| 15-24 | Others | 8 | `agent`, `command`, `skill`, `path`, `vcs`, `lsp`, `formatter`, `instance`, `log` |
| — | `tui` | 13 | CLI-specific (**skip for Android**) |

### Authentication

- **HTTP Basic Auth**: `Authorization: Basic base64(username:password)`
- **Directory Header**: `x-opencode-directory: <encoded-path>`
- **Workspace Header**: `x-opencode-workspace: <workspace-id>`
- **Query params alternative**: `?directory=xxx&workspace=xxx`

### SSE Endpoints

| Endpoint | Scope | Usage |
|----------|-------|-------|
| `GET /global/event` | All projects/sessions | **Primary** — use this |
| `GET /event` | Current workspace instance | Secondary — requires directory param |
| `GET /global/sync-event` | Cross-instance sync | Advanced — versioned events |

---

## SSE Event System

24 event types processed by EventReducer:

### Server Events

| Event | Data | Action |
|-------|------|--------|
| `server.connected` | — | Mark connection active |
| `server.heartbeat` | — | Reset timeout (no state change) |
| `server.instance_disposed` | — | Cleanup instance state |

### Session Events

| Event | Data | Action |
|-------|------|--------|
| `session.created` | `Session` | Add to list + link to server |
| `session.updated` | `Session` | Upsert session |
| `session.deleted` | `Session` | Remove + all associated data |
| `session.status` | `sessionId, SessionStatus` | Update idle/busy/retry |
| `session.idle` | `sessionId` | Mark idle |
| `session.diff` | `sessionId, List<FileDiff>` | Update file changes |
| `session.error` | `sessionId?, error` | Display error |

### Message Events

| Event | Data | Action |
|-------|------|--------|
| `message.updated` | `Message` | Add/update message metadata |
| `message.removed` | `sessionId, messageId` | Remove message + parts |
| `message.part.updated` | `Part` | Add/update part |
| `message.part.delta` | `sessionId, messageId, partId, field, delta` | **Append streaming text** |
| `message.part.removed` | `sessionId, messageId, partId` | Remove part |

### Interaction Events

| Event | Data | Action |
|-------|------|--------|
| `permission.asked` | `PermissionRequest` | Show permission dialog |
| `permission.replied` | `sessionId, requestId` | Dismiss UI |
| `question.asked` | `QuestionRequest` | Show question dialog |
| `question.replied` | `sessionId, requestId` | Dismiss UI |
| `question.rejected` | `sessionId, requestId` | Dismiss UI |

### Other Events

| Event | Data | Action |
|-------|------|--------|
| `todo.updated` | `sessionId, todos[]` | Update todo list |
| `vcs.branch.updated` | `branch` | Update Git branch |
| `lsp.updated` | — | Ignore (client-side) |
| `project.updated` | `Project` | Update project info |

---

## Data Models

### Session

```kotlin
data class Session(
    val id: String,
    val slug: String,              // URL-friendly short ID
    val projectID: String,
    val workspaceID: String?,
    val directory: String,
    val parentID: String?,         // Parent session (sub-tasks)
    val title: String,
    val version: String,
    val summary: SessionSummary?,  // additions, deletions, files, diffs
    val share: SessionShare?,      // Share URL
    val permission: PermissionRuleset?,
    val revert: RevertInfo?,
    val time: SessionTime,         // created, updated, compacting, archived
)
```

### Message (Sealed Class)

```kotlin
sealed class Message {
    abstract val id: String
    abstract val sessionId: String
    abstract val role: String
    abstract val time: MessageTime

    data class User(
        // ... base fields
        val agent: String,
        val model: ModelRef,       // providerID + modelID
        val variant: String?,
        val summary: UserSummary?,
    ) : Message()

    data class Assistant(
        // ... base fields
        val parentId: String,      // Linked user message
        val modelId: String,
        val providerId: String,
        val cost: Double,
        val tokens: TokenUsage,    // input, output, reasoning, cache
        val error: ErrorInfo?,     // 7 error types
        val finish: String?,
    ) : Message()
}
```

### Part (12-Type Sealed Class)

```kotlin
sealed class Part {
    abstract val id: String
    abstract val sessionId: String
    abstract val messageId: String

    data class Text(...) : Part()          // Markdown text
    data class Reasoning(...) : Part()     // AI thinking process
    data class Tool(...) : Part()          // Tool call + ToolState state machine
    data class File(...) : Part()          // File/image attachment
    data class Subtask(...) : Part()       // Sub-agent task
    data class StepStart(...) : Part()     // Step group start
    data class StepFinish(...) : Part()    // Step group end (cost/tokens)
    data class Snapshot(...) : Part()      // Snapshot reference
    data class Patch(...) : Part()         // Patch/diff set
    data class Agent(...) : Part()         // Agent switch marker
    data class Retry(...) : Part()         // Retry record
    data class Compaction(...) : Part()    // Context compression
}
```

### ToolState (State Machine)

```kotlin
sealed class ToolState {
    data class Pending(val input: JsonElement, val raw: JsonElement?) : ToolState()
    data class Running(val input: JsonElement, val title: String?, val metadata: JsonElement?) : ToolState()
    data class Completed(val input: JsonElement, val output: String, val title: String, val metadata: JsonElement?) : ToolState()
    data class Error(val input: JsonElement, val error: String, val metadata: JsonElement?) : ToolState()
}
```

### SessionStatus

```kotlin
enum class SessionStatus { IDLE, BUSY, RETRY }
```

---

## Development Roadmap

| Phase | Duration | Deliverables |
|-------|----------|-------------|
| **P0 — Skeleton** | 1-2 weeks | Architecture setup (Hilt + Ktor + Room + Navigation), HomeScreen (server connect), EventReducer, SSE Client, ForegroundService |
| **P1 — Core Chat** | 2-3 weeks | SessionList, ChatScreen (message send/receive + streaming + Markdown), 12 Part renderers, 8+ tool cards, Permission/Question dialogs, Provider management |
| **P2 — Files + Terminal** | 2 weeks | FileBrowser, PTY terminal (WebSocket), Session operations (fork/share/revert/command), Draft system |
| **P3 — Polish** | 1-2 weeks | Settings page, Model filtering, Notification system, Deep links, Share intent handling, Image compression |
| **P4 — Advanced** | 1-2 weeks | Termux local runtime (optional), MCP management, Experimental features, Offline caching, Widgets |

### P0 Priority API Modules

| Module | Endpoints | Required For |
|--------|-----------|-------------|
| `global` | 3 (health, event SSE, config) | Connection + real-time |
| `project` | 1 (list) | Project list screen |
| `session` | 9 (list, create, get, delete, update, status, messages, prompt_async, abort) | Core chat flow |
| `event` | SSE processing | EventReducer |
| **Total P0** | **~14 endpoints** | **Functional MVP** |
