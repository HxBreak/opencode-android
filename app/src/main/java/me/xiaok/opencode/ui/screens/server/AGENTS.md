# Server Settings Screens

Per-server configuration: provider auth, model visibility, MCP servers, project config.

## Where to Look

| Task | File | Notes |
|------|------|-------|
| Settings hub (navigation menu) | `ServerSettingsScreen.kt` (192 lines) | Entry point, links to sub-screens |
| Provider list + API key auth | `ServerProvidersScreen.kt` (745 lines) | OAuth + key-based, `MetadataCache` for auth methods |
| Model show/hide toggles | `ServerModelFilterScreen.kt` (438 lines) | Per-provider expand/collapse, `SettingsRepository` |
| MCP server CRUD | `McpManagementScreen.kt` (572 lines) | Add/connect/disconnect, `McpServerCreateRequest` |
| Raw JSON config editor | `ProjectConfigScreen.kt` (183 lines) | GET/PATCH via `OpenCodeApi` |

## Conventions

- All ViewModels take `serverId` from `SavedStateHandle` (required, throws if missing).
- Route composable (e.g. `ServerSettingsRoute`) wraps the screen, collects state, delegates to stateless screen composable.
- Navigation wired in `NavGraph.kt` under `Screen.ServerSettings(serverId)` composable block.
- `ErrorCollector` used for logging in all ViewModels except `ServerSettingsViewModel` (read-only).
- `ProvidersUiState` built via chained `combine` flows; other ViewModels use `MutableStateFlow.update`.
- Tests mirror source structure: `app/src/test/.../server/XxxViewModelTest.kt` for each ViewModel.
