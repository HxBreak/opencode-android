# domain/model — API Contract Layer

API mirror: 31 files mapping server JSON to Kotlin types via kotlinx.serialization. No business logic.

## Where to Look

| Concept | File | Notes |
|---------|------|-------|
| Message parts (text/tool/file/patch…) | `Part.kt` | 11-subclass sealed class, `@SerialName` discriminator |
| SSE events (24 types) | `SseEvent.kt` | 5 categories: server/session/message/interaction/other |
| Chat messages | `Message.kt` | `MessageInfo` + `List<Part>`; `UserSummarySerializer` handles `true`→object quirk |
| Session metadata | `Session.kt` | Session, SessionSummary, PermissionRule |
| Session lifecycle | `SessionStatus.kt` | `Idle` / `Busy` / `Retry` sealed class |
| AI providers & models | `Provider.kt` | Provider→Model→Capabilities hierarchy |
| Tool execution state | `ToolState.kt` | String-discriminator status (pending/running/completed/error) |
| Permissions flow | `Permission.kt` | Request → Reply, linked via SseEvent |
| Questions flow | `Question.kt` | Request → Reply/Reject, linked via SseEvent |
| File diffs | `Part.kt` (`FileDiff`) | Shared by Part.Patch, SessionDiff, UserSummary |
| Client-side commands | `BuiltInCommand.kt` | Not from server — 24 local slash commands |
| Chat input state | `ChatDraft.kt` | Draft persistence with MentionItem references |
| SSE transport | `SseEnvelope.kt` | `SseEnvelope` (global) vs `InstanceSseEnvelope` (per-session) |

## Conventions

- **Serialization**: All types use `@Serializable`. Fields use `@SerialName("snake_case")` to map server JSON. Every field has a default value (`""`, `emptyList()`, `null`) for forward compatibility.
- **Sealed class discriminators**: `Part` and `SseEvent` use `@SerialName` on each subclass for polymorphic deserialization. Add new variants at the end, never reorder.
- **ID format**: Server-issued IDs prefixed by type (`ses_`, `msg_`, `part_`). Don't parse or generate these client-side.
- **Timestamps**: `Long` (Unix milliseconds). Wrap in `Date(time)` for display.
- **JsonObject fields**: `ToolState.raw`, `LogEntry.extra`, `PermissionRequest.metadata` hold untyped server data — access via key paths, don't cast.
- **Adding a new SSE event**: Add subclass in `SseEvent.kt`, add `when` branch in `EventReducer`, add unit test for state transition. Follow existing naming: `{Noun}{Verb}`ed (e.g., `SessionCreated`, not `CreateSession`).
- **Exempt from 800-line rule**: Sealed class hierarchies (Part, SseEvent) are bound by API contract and may exceed the limit.

## Anti-Patterns

- **Don't add business logic here.** Models are plain data. Put computed display logic in UI layer, state logic in EventReducer/ViewModel.
- **Don't rename `@SerialName` values** — they must match server API exactly. Kotlin property names can differ.
- **Don't make fields non-nullable without checking server responses.** Missing fields deserialize as defaults; making them non-nullable without defaults will crash.
- **Don't use Gson annotations** (`@SerializedName`) — project uses kotlinx.serialization exclusively.
- **Don't create circular references** between model files. Keep the dependency graph acyclic: `SseEvent` references `Part`/`Message`/`Session`, never the reverse.
