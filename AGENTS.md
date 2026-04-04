# Knowledge Base References

This project references API documentation and analysis notes stored in an Obsidian vault.

## Obsidian Vault Location

```
/mnt/dav/obsidian/mine/opencode/
```

## Directory Structure

### API Documentation (`api/`)

Official OpenCode API reference documents (v1.3.10, OpenAPI 3.1.1). Each file covers one API module.

```
api/
├── README.md              # API overview, auth, common headers
├── session.md             # Session CRUD + messaging (27 endpoints)
├── global.md              # Health check, SSE events, config (7 endpoints)
├── event.md               # Instance-level SSE events
├── provider.md            # LLM provider management + OAuth
├── file.md                # File browsing + content reading
├── find.md                # Text search, file search, symbol search
├── permission.md          # Runtime permission requests/replies
├── question.md            # AI-to-user structured questions
├── auth.md                # Provider credential management
├── config.md              # Configuration management
├── agent.md               # Agent definitions
├── command.md             # Slash command definitions
├── pty.md                 # Terminal sessions (REST + WebSocket)
├── mcp.md                 # MCP server management
├── vcs.md                 # Version control operations
├── lsp.md                 # Language server protocol
├── skill.md               # Skill definitions
├── path.md                # Path operations
├── formatter.md           # Code formatting
├── instance.md            # Instance management
├── log.md                 # Logging
├── experimental.md        # Experimental features (workspaces, worktree)
└── tui.md                 # CLI/TUI-specific (not used for Android)
```

### OC Remote Analysis (`oc-remote/`)

Reverse-engineered analysis of OC Remote (`dev.minios.ocremote`), the reference Android implementation for OpenCode.

```
oc-remote/
├── 01-architecture.md         # MVI + EventReducer pattern analysis
├── 02-api-list.md             # API endpoint usage inventory
├── 03-chat-screen.md          # ChatScreen implementation details
├── 04-home-screen.md          # HomeScreen + server management
├── 05-session-list.md         # Session list + operations
├── 06-server-settings.md      # Provider/model settings pages
└── 07-navigation-service.md   # Navigation, ForegroundService, SSE events
```

### Project Analysis

```
OpenCode Android客户端 - 项目分析.md    # Early project planning analysis
OpenCode Web前端API全解析.md            # Web frontend API usage analysis
```

## Test Server

```
http://192.168.31.203:4096
```

Available for functional validation against a real OpenCode server instance.

## Usage Notes

- These files are **read-only references** for understanding the OpenCode API and validated architecture patterns
- The Android client targets **API v1.3.10+** with 106 endpoints across 24 modules
- Architecture decisions (MVI + EventReducer) were validated against OC Remote's implementation
- The `tui.md` module is CLI-specific and should be **skipped** for Android development
