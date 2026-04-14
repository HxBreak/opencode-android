# Chat Screen

Chat is the core conversation view. SSE events flow through EventReducer into ChatViewModel, which delegates business logic to UseCases and exposes a single `ChatUiState` flow.

## Where to Look

| Concern | File(s) | Notes |
|---------|---------|-------|
| State & orchestration | `ChatViewModel.kt` | 957 lines, owns UiState, routes to UseCases |
| UiState shape | `ChatViewModel.kt` lines 57-96 | `ChatUiState`, `ChatTurn`, `ChildSessionInfo` |
| Sending messages | `usecases/SendMessageUseCase.kt` | SSE stream setup, draft clearing, image handling |
| Loading messages | `usecases/MessageLoadingUseCase.kt` | Initial load + pagination with cursor |
| Session ops | `usecases/SessionOpsUseCase.kt` | Fork, share, unshare, revert, archive |
| Model/agent picking | `usecases/ModelSelectionUseCase.kt` | Provider list, model defaults, variant cycling |
| Slash commands | `usecases/ChatCommandUseCase.kt` | Dispatches undo/redo/compact/share/fork/etc |
| Draft persistence | `usecases/DraftManagementUseCase.kt` | Save/clear/restore drafts with images |
| @ mentions | `usecases/MentionManagementUseCase.kt` | Add/remove/reconcile file and agent mentions |
| Permissions & questions | `usecases/PermissionQuestionUseCase.kt` | Reply to permission prompts and questions |
| Token/cost stats | `usecases/SessionStatsUseCase.kt` | Context usage %, cost aggregation |
| Child session loading | `usecases/SessionNavigationUseCase.kt` | Load child sessions, pending questions |
| Main composable | `ChatScreen.kt` | 1322 lines, turn list, toolbar, snackbars |
| Input bar | `ChatInputBar.kt` | Text field, image attach, send button |
| Part rendering | `PartRenderers.kt`, `ToolPartRenderers.kt`, `MiscPartRenderers.kt`, `TextPartRenderer.kt` | Dispatches `Part` subtypes to composables |
| Context tool grouping | `ContextToolGroup.kt`, `PartRenderers.kt` | Collapses consecutive read/glob/grep into one card |
| Tool cards | `ToolCard.kt`, `ToolCardUtils.kt`, `ToolOutputContent.kt` | Expandable tool result cards |
| Model picker dialog | `ModelPickerDialog.kt` | Provider/model/agent selection UI |
| Full screen editor | `FullScreenEditor.kt` | Code block viewer |
| Mention popup | `MentionPopup.kt`, `MentionTransformation.kt` | Autocomplete popup for @ file/agent |
| Status bar | `ChatStatusBar.kt` | Token count, cost, context usage |
| Dialogs | `ChatDialogs.kt` | Share, delete, fork confirmation dialogs |
| Sentinels | `TodoSentinel.kt`, `HoverSentinel.kt` | Todo list overlay, hover state tracking |
| Selectors | `ChatSelectors.kt` | Derived state selectors (turn grouping, etc) |
| Type definitions | `ChatTypes.kt` | `AttachedImage` data class |
| Tests | `ChatViewModelTest.kt`, `TurnGroupingTest.kt`, `usecases/*Test.kt` | ViewModel + UseCase unit tests |

## Conventions

- **UseCase pattern**: Each UseCase is `@Inject constructor(...)` with a single `suspend fun execute(...)` (or small set of operations). ViewModel calls UseCases and folds results into `_uiState`.
- **UseCases return sealed results**: `SendResult`, `LoadResult`, `CommandResult`, `QuestionResult` pattern. ViewModel maps these to UiState updates.
- **EventReducer is the single source of truth** for messages/parts/permissions. UseCases mutate it via `eventReducer.processEvent()` or direct setters; ViewModel observes its `StateFlow`s.
- **Turn grouping**: Messages are grouped into `ChatTurn` (user + assistant messages) via `ChatSelectors.kt`. Parts within turns are grouped into `TurnPartGroup` for rendering.
- **Context tool collapsing**: Consecutive read/glob/grep/list/find parts merge into `GroupedPart.ContextGroup` to reduce visual noise.

## Anti-Patterns

- **Don't put business logic in ChatViewModel directly.** Extract to a UseCase if it needs API calls, EventReducer mutations, or complex state computation.
- **Don't observe EventReducer flows in Composables.** ChatViewModel is the only subscriber; Composables observe `_uiState`.
- **Don't add new Part renderers outside the existing files.** Add to the appropriate `*PartRenderer.kt` file based on category (tool, text, misc).
- **Don't skip error handling in UseCases.** Every UseCase that calls the API must catch exceptions and return a typed error result or use `ErrorCollector`.
- **Don't grow ChatScreen.kt further.** New UI sections should be extracted into standalone composables in separate files.
