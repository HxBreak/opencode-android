# Features

## Deeplinks

The app supports the following deeplink URIs via `opencode://` scheme.

### `opencode://addServer`

Add and connect to a server programmatically. Credentials are passed as intent extras (not URI query params) to avoid shell encoding issues with special characters.

**Intent extras:**

| Extra | Required | Description |
|-------|----------|-------------|
| `serverId` | Yes | Unique identifier (UUID) for the server |
| `serverName` | Yes | Display name |
| `serverUrl` | Yes | Server base URL (e.g. `http://192.168.31.52:4000`) |
| `serverUsername` | No | Auth username |
| `serverPassword` | No | Auth password |

**Usage via adb:**

```bash
adb shell am start -a android.intent.action.VIEW \
  -d opencode://addServer \
  -f 0x24000000 \
  --es serverId "my-server-id" \
  --es serverName "My Server" \
  --es serverUrl "http://192.168.31.52:4000" \
  --es serverUsername "user" \
  --es serverPassword "pass" \
  me.xiaok.opencode/.MainActivity
```

- `-f 0x24000000` = `FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_CLEAR_TOP` — delivers intent to existing activity via `onNewIntent()` instead of creating a new instance.
- The app must already be running. `onNewIntent()` handles the deeplink, not `onCreate()`.

### `opencode://sessions/{serverId}`

Navigate to the session list for a connected server.

### `opencode://session/{serverId}/{sessionId}`

Navigate directly to a specific chat session. Supports optional query params to add the server on-the-fly if it doesn't exist locally:

| Param | Required | Description |
|-------|----------|-------------|
| `serverName` | With others | Display name |
| `serverUrl` | With others | Server base URL |
| `username` | No | Auth username |
| `password` | No | Auth password |

When `serverName` and `serverUrl` are present and the server is not already added, the app will add and connect to it before navigating.

**Example (shareable session URL):**

```
opencode://session/abc-123/ses_xyz?serverName=My%20Server&serverUrl=http%3A%2F%2F192.168.31.52%3A4000&username=user&password=pass
```

### `opencode://settings`

Navigate to the settings screen.
