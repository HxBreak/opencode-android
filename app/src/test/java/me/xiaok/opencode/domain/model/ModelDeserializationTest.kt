package me.xiaok.opencode.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.xiaok.opencode.utils.TimeoutRule
import org.junit.Rule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying JSON deserialization of domain models
 * against real API response data from the OpenCode test server.
 */
class ModelDeserializationTest {

    @get:Rule
    val timeoutRule = TimeoutRule()

    private lateinit var json: Json

    @Before
    fun setup() {
        json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            classDiscriminator = "type"
        }
    }

    // === Session Tests ===

    @Test
    fun `deserialize Session from real API response`() {
        val sessionJson = """{
            "id": "ses_357ba8b6cffebij5gVazzyXtk4",
            "slug": "abc123",
            "projectID": "prj_123",
            "workspaceID": null,
            "directory": "/Users/user/project",
            "parentID": null,
            "title": "My Session",
            "version": "v1",
            "summary": {"additions": 10, "deletions": 5, "files": 3, "diffs": 2},
            "share": {"url": "https://example.com/share/abc"},
            "permission": [
                {"permission": "task", "pattern": "*", "action": "deny"},
                {"permission": "file", "pattern": "*.txt", "action": "allow"}
            ],
            "revert": null,
            "time": {"created": 1772725329530, "updated": 1772725400000}
        }"""

        val session = json.decodeFromString<Session>(sessionJson)

        assertEquals("ses_357ba8b6cffebij5gVazzyXtk4", session.id)
        assertEquals("My Session", session.title)
        assertEquals("/Users/user/project", session.directory)
        assertEquals(10, session.summary?.additions)
        assertEquals(5, session.summary?.deletions)
        assertEquals("https://example.com/share/abc", session.share?.url)
        assertEquals(2, session.permission.size)
        assertEquals("task", session.permission[0].permission)
        assertEquals("*", session.permission[0].pattern)
        assertEquals("deny", session.permission[0].action)
        assertEquals("file", session.permission[1].permission)
        assertEquals(1772725329530L, session.time.created)
    }

    @Test
    fun `deserialize Session without permission field`() {
        val sessionJson = """{
            "id": "ses_test",
            "title": "No Permissions"
        }"""

        val session = json.decodeFromString<Session>(sessionJson)
        assertEquals("ses_test", session.id)
        assertEquals("No Permissions", session.title)
        assertTrue(session.permission.isEmpty())
    }

    // === Message Tests ===

    @Test
    fun `deserialize user Message from real API response`() {
        val messageJson = """{
            "info": {
                "role": "user",
                "time": {"created": 1772725329530},
                "summary": {"diffs": []},
                "agent": "Prometheus (Plan Builder)",
                "model": {"providerID": "zhipu", "modelID": "glm-5"},
                "id": "msg_cbea9de490017NI3b0QKGsf3fp",
                "sessionID": "ses_357ba8b6cffebij5gVazzyXtk4"
            },
            "parts": [
                {"type": "text", "text": "Hello, how are you?", "id": "p1", "sessionID": "ses_357ba8b6cffebij5gVazzyXtk4", "messageID": "msg_cbea9de490017NI3b0QKGsf3fp"}
            ]
        }"""

        val message = json.decodeFromString<Message>(messageJson)

        assertEquals("msg_cbea9de490017NI3b0QKGsf3fp", message.id)
        assertEquals("ses_357ba8b6cffebij5gVazzyXtk4", message.sessionId)
        assertEquals("user", message.role)
        assertTrue(message.isUser)
        assertFalse(message.isAssistant)
        assertEquals(1772725329530L, message.time.created)
        assertEquals(1, message.parts.size)
        val textPart = message.parts[0] as Part.Text
        assertEquals("Hello, how are you?", textPart.text)
    }

    @Test
    fun `deserialize assistant Message from real API response`() {
        val messageJson = """{
            "info": {
                "role": "assistant",
                "time": {"created": 1772367412328, "completed": 1772367419121},
                "parentID": "msg_ca953dd03001OwUbRJUluU2yBm",
                "modelID": "glm-5",
                "providerID": "zhipu",
                "mode": "Prometheus (Plan Builder)",
                "agent": "Prometheus (Plan Builder)",
                "path": {"cwd": "/Users/liuji/wework/wework", "root": "/"},
                "cost": 0,
                "tokens": {"total": 1476, "input": -111076, "output": 40, "reasoning": 0, "cache": {"read": 112512, "write": 0}},
                "finish": "tool-calls",
                "id": "msg_ca9547c68001H0yiAffEVOHfGX",
                "sessionID": "ses_357ba8b6cffebij5gVazzyXtk4"
            },
            "parts": [
                {"type": "step-start", "id": "ps1", "sessionID": "ses_357ba8b6cffebij5gVazzyXtk4", "messageID": "msg_ca9547c68001H0yiAffEVOHfGX"},
                {"type": "text", "text": "I'll help you with that.", "time": {"start": 1772367419103, "end": 1772367419103}, "id": "pt1", "sessionID": "ses_357ba8b6cffebij5gVazzyXtk4", "messageID": "msg_ca9547c68001H0yiAffEVOHfGX"},
                {"type": "tool", "callID": "call_xxx", "tool": "bash", "state": {"status": "completed", "input": {"command": "ls -la"}, "output": "total 32\ndrwxr-xr-x", "title": "List files", "metadata": {}}, "id": "ptl1", "sessionID": "ses_357ba8b6cffebij5gVazzyXtk4", "messageID": "msg_ca9547c68001H0yiAffEVOHfGX"},
                {"type": "step-finish", "reason": "tool-calls", "cost": 0, "tokens": {"total": 1476, "input": -111076, "output": 40, "reasoning": 0, "cache": {"read": 112512, "write": 0}}, "id": "pf1", "sessionID": "ses_357ba8b6cffebij5gVazzyXtk4", "messageID": "msg_ca9547c68001H0yiAffEVOHfGX"}
            ]
        }"""

        val message = json.decodeFromString<Message>(messageJson)

        assertEquals("msg_ca9547c68001H0yiAffEVOHfGX", message.id)
        assertEquals("assistant", message.role)
        assertTrue(message.isAssistant)
        assertFalse(message.isUser)
        assertEquals(1772367419121L, message.time.completed)
        assertEquals("glm-5", message.info.modelID)
        assertEquals("zhipu", message.info.providerID)
        assertEquals("Prometheus (Plan Builder)", message.info.agent)
        assertEquals("tool-calls", message.info.finish)
        assertEquals("/Users/liuji/wework/wework", message.info.path?.cwd)

        // Check TokenUsage with nested cache
        val tokens = message.info.tokens!!
        assertEquals(1476L, tokens.total)
        assertEquals(-111076L, tokens.input)
        assertEquals(40L, tokens.output)
        assertEquals(0L, tokens.reasoning)
        assertEquals(112512L, tokens.cache.read)
        assertEquals(0L, tokens.cache.write)

        // Check parts - 4 parts total
        assertEquals(4, message.parts.size)

        // StepStart
        val stepStart = message.parts[0] as Part.StepStart
        assertEquals("ps1", stepStart.id)

        // Text with time
        val text = message.parts[1] as Part.Text
        assertEquals("I'll help you with that.", text.text)
        assertNotNull(text.time)
        assertEquals(1772367419103L, text.time?.start)
        assertEquals(1772367419103L, text.time?.end)

        // Tool with state
        val tool = message.parts[2] as Part.Tool
        assertEquals("bash", tool.tool)
        assertEquals("call_xxx", tool.callId)
        assertTrue(tool.state.isCompleted)
        assertEquals("List files", tool.state.title)
        assertEquals("total 32\ndrwxr-xr-x", tool.state.output)

        // StepFinish with reason
        val stepFinish = message.parts[3] as Part.StepFinish
        assertEquals("tool-calls", stepFinish.reason)
        assertEquals(0.0, stepFinish.cost, 0.001)
        assertEquals(1476L, stepFinish.tokens.total)
    }

    // === Part Type Discriminator Tests ===

    @Test
    fun `deserialize Part-Text via type discriminator`() {
        val partJson = """{"type": "text", "text": "Hello", "id": "p1", "sessionID": "s1", "messageID": "m1"}"""
        val part = json.decodeFromString<Part>(partJson)
        assertTrue(part is Part.Text)
        assertEquals("Hello", (part as Part.Text).text)
    }

    @Test
    fun `deserialize Part-Reasoning via type discriminator`() {
        val partJson = """{"type": "reasoning", "text": "Thinking...", "id": "p1", "sessionID": "s1", "messageID": "m1"}"""
        val part = json.decodeFromString<Part>(partJson)
        assertTrue(part is Part.Reasoning)
        assertEquals("Thinking...", (part as Part.Reasoning).text)
    }

    @Test
    fun `deserialize Part-Tool via type discriminator`() {
        val partJson = """{
            "type": "tool",
            "tool": "bash",
            "callID": "call_123",
            "state": {"status": "running", "title": "Running command"},
            "id": "p1",
            "sessionID": "s1",
            "messageID": "m1"
        }"""
        val part = json.decodeFromString<Part>(partJson)
        assertTrue(part is Part.Tool)
        val tool = part as Part.Tool
        assertEquals("bash", tool.tool)
        assertEquals("call_123", tool.callId)
        assertTrue(tool.state.isRunning)
        assertEquals("Running command", tool.state.title)
    }

    @Test
    fun `deserialize Part step-start via type discriminator`() {
        val partJson = """{"type": "step-start", "name": "Plan", "id": "p1", "sessionID": "s1", "messageID": "m1"}"""
        val part = json.decodeFromString<Part>(partJson)
        assertTrue(part is Part.StepStart)
        assertEquals("Plan", (part as Part.StepStart).name)
    }

    @Test
    fun `deserialize Part step-finish via type discriminator`() {
        val partJson = """{
            "type": "step-finish",
            "reason": "tool-calls",
            "cost": 0.5,
            "tokens": {"total": 100, "input": 50, "output": 50, "reasoning": 0, "cache": {"read": 0, "write": 0}},
            "id": "p1",
            "sessionID": "s1",
            "messageID": "m1"
        }"""
        val part = json.decodeFromString<Part>(partJson)
        assertTrue(part is Part.StepFinish)
        val finish = part as Part.StepFinish
        assertEquals("tool-calls", finish.reason)
        assertEquals(0.5, finish.cost, 0.001)
        assertEquals(100L, finish.tokens.total)
    }

    @Test
    fun `deserialize Part-Snapshot via type discriminator`() {
        val partJson = """{"type": "snapshot", "snapshotID": "snap_123", "label": "Checkpoint", "id": "p1", "sessionID": "s1", "messageID": "m1"}"""
        val part = json.decodeFromString<Part>(partJson)
        assertTrue(part is Part.Snapshot)
        assertEquals("snap_123", (part as Part.Snapshot).snapshotId)
        assertEquals("Checkpoint", part.label)
    }

    @Test
    fun `deserialize Part-Patch via type discriminator`() {
        val partJson = """{
            "type": "patch",
            "diffs": [
                {"file": "src/Main.kt", "additions": 5, "deletions": 2, "before": "", "after": ""}
            ],
            "id": "p1",
            "sessionID": "s1",
            "messageID": "m1"
        }"""
        val part = json.decodeFromString<Part>(partJson)
        assertTrue(part is Part.Patch)
        val patch = part as Part.Patch
        assertEquals(1, patch.diffs.size)
        assertEquals("src/Main.kt", patch.diffs[0].path)
        assertEquals(5, patch.diffs[0].additions)
    }

    @Test
    fun `deserialize Part-Agent via type discriminator`() {
        val partJson = """{
            "type": "agent",
            "agent": "Build",
            "model": {"providerID": "zhipu", "modelID": "glm-5"},
            "id": "p1",
            "sessionID": "s1",
            "messageID": "m1"
        }"""
        val part = json.decodeFromString<Part>(partJson)
        assertTrue(part is Part.Agent)
        val agent = part as Part.Agent
        assertEquals("Build", agent.agent)
        assertEquals("zhipu", agent.model.providerID)
        assertEquals("glm-5", agent.model.modelID)
    }

    @Test
    fun `deserialize Part-Retry via type discriminator`() {
        val partJson = """{"type": "retry", "error": "Rate limit exceeded", "id": "p1", "sessionID": "s1", "messageID": "m1"}"""
        val part = json.decodeFromString<Part>(partJson)
        assertTrue(part is Part.Retry)
        assertEquals("Rate limit exceeded", (part as Part.Retry).error)
    }

    @Test
    fun `deserialize Part-Compaction via type discriminator`() {
        val partJson = """{"type": "compaction", "summary": "Context compressed", "id": "p1", "sessionID": "s1", "messageID": "m1"}"""
        val part = json.decodeFromString<Part>(partJson)
        assertTrue(part is Part.Compaction)
        assertEquals("Context compressed", (part as Part.Compaction).summary)
    }

    // === ToolState Tests ===

    @Test
    fun `deserialize ToolState pending`() {
        val stateJson = """{"status": "pending", "input": {"command": "ls"}}"""
        val state = json.decodeFromString<ToolState>(stateJson)
        assertTrue(state.isPending)
        assertFalse(state.isRunning)
        assertFalse(state.isCompleted)
        assertFalse(state.isError)
    }

    @Test
    fun `deserialize ToolState running`() {
        val stateJson = """{"status": "running", "title": "Compiling..."}"""
        val state = json.decodeFromString<ToolState>(stateJson)
        assertTrue(state.isRunning)
        assertEquals("Compiling...", state.title)
    }

    @Test
    fun `deserialize ToolState completed`() {
        val stateJson = """{"status": "completed", "output": "Success", "title": "Build complete"}"""
        val state = json.decodeFromString<ToolState>(stateJson)
        assertTrue(state.isCompleted)
        assertEquals("Success", state.output)
        assertEquals("Build complete", state.title)
    }

    @Test
    fun `deserialize ToolState error`() {
        val stateJson = """{"status": "error", "error": "Command failed"}"""
        val state = json.decodeFromString<ToolState>(stateJson)
        assertTrue(state.isError)
        assertEquals("Command failed", state.error)
    }

    @Test
    fun `ToolState childSessionId extracts sessionId from metadata`() {
        val stateJson = """{
            "status": "completed",
            "title": "Sub-agent task",
            "metadata": {"sessionId": "ses_child_abc123", "model": {"providerID": "zhipu", "modelID": "glm-5"}}
        }"""
        val state = json.decodeFromString<ToolState>(stateJson)
        assertTrue(state.isCompleted)
        assertEquals("ses_child_abc123", state.childSessionId)
    }

    @Test
    fun `ToolState childSessionId returns null when metadata absent`() {
        val stateJson = """{"status": "completed", "title": "Bash command"}"""
        val state = json.decodeFromString<ToolState>(stateJson)
        assertNull(state.childSessionId)
    }

    @Test
    fun `ToolState childSessionId returns null when metadata has no sessionId`() {
        val stateJson = """{"status": "completed", "metadata": {"model": {"providerID": "zhipu"}}}"""
        val state = json.decodeFromString<ToolState>(stateJson)
        assertNull(state.childSessionId)
    }

    // === SessionStatus Tests ===

    private fun parseSessionStatus(jsonStr: String): SessionStatus {
        val jsonObj = json.parseToJsonElement(jsonStr).jsonObject
        val typeStr = jsonObj["type"]!!.jsonPrimitive.content
        return when (typeStr.lowercase()) {
            "idle" -> SessionStatus.Idle
            "busy" -> SessionStatus.Busy
            "retry" -> SessionStatus.Retry(
                attempt = jsonObj["attempt"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                message = jsonObj["message"]?.jsonPrimitive?.content ?: "",
                next = jsonObj["next"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            )
            else -> SessionStatus.Idle
        }
    }

    @Test
    fun `deserialize SessionStatus Idle`() {
        val status = parseSessionStatus("""{"type":"idle"}""")
        assertEquals(SessionStatus.Idle, status)
    }

    @Test
    fun `deserialize SessionStatus Busy`() {
        val status = parseSessionStatus("""{"type":"busy"}""")
        assertEquals(SessionStatus.Busy, status)
    }

    @Test
    fun `deserialize SessionStatus Retry with metadata`() {
        val status = parseSessionStatus("""{"type":"retry","attempt":2,"message":"Rate limited","next":1234567890}""")
        assertTrue(status is SessionStatus.Retry)
        assertEquals(2, (status as SessionStatus.Retry).attempt)
        assertEquals("Rate limited", status.message)
        assertEquals(1234567890L, status.next)
    }

    // === TokenUsage Tests ===

    @Test
    fun `deserialize TokenUsage with nested cache`() {
        val tokenJson = """{"total": 1476, "input": -111076, "output": 40, "reasoning": 0, "cache": {"read": 112512, "write": 0}}"""
        val tokens = json.decodeFromString<TokenUsage>(tokenJson)
        assertEquals(1476L, tokens.total)
        assertEquals(-111076L, tokens.input)
        assertEquals(40L, tokens.output)
        assertEquals(0L, tokens.reasoning)
        assertEquals(112512L, tokens.cache.read)
        assertEquals(0L, tokens.cache.write)
    }

    @Test
    fun `deserialize TokenUsage with defaults`() {
        val tokenJson = """{}"""
        val tokens = json.decodeFromString<TokenUsage>(tokenJson)
        assertEquals(0L, tokens.total)
        assertEquals(0L, tokens.input)
        assertEquals(0L, tokens.cache.read)
        assertEquals(0L, tokens.cache.write)
    }

    // === SSE Envelope Tests ===

    @Test
    fun `deserialize SseEnvelope from real data`() {
        val envelopeJson = """{"payload":{"type":"server.connected","properties":{}}}"""
        val envelope = json.decodeFromString<SseEnvelope>(envelopeJson)
        assertEquals("server.connected", envelope.payload.type)
    }

    // === SSE Event Tests ===

    @Test
    fun `deserialize SessionStatusChanged SSE event properties`() {
        val propsJson = """{
            "sessionID": "ses_abc123",
            "status": {"type": "busy"}
        }"""

        val jsonObj = json.parseToJsonElement(propsJson).jsonObject
        val sessionId = jsonObj["sessionID"]!!.jsonPrimitive.content
        val status = parseSessionStatus(jsonObj["status"].toString())

        assertEquals("ses_abc123", sessionId)
        assertEquals(SessionStatus.Busy, status)
    }

    // === Message with error ===

    @Test
    fun `deserialize assistant Message with error`() {
        val messageJson = """{
            "info": {
                "role": "assistant",
                "id": "msg_err",
                "sessionID": "ses_test",
                "time": {"created": 1000},
                "error": {"name": "APIError", "data": {"message": "This model is not available in your region.", "statusCode": 403, "isRetryable": false}}
            },
            "parts": []
        }"""

        val message = json.decodeFromString<Message>(messageJson)
        assertNotNull(message.info.error)
        assertEquals("APIError", message.info.error?.name)
        assertEquals("This model is not available in your region.", message.info.error?.message)
        assertEquals(403, message.info.error?.data?.statusCode)
        assertEquals(false, message.info.error?.data?.isRetryable)
    }

    // === Edge Cases ===

    @Test
    fun `Message computed properties work correctly`() {
        val msg = Message(
            info = MessageInfo(role = "user", id = "msg1", sessionID = "ses1"),
            parts = listOf(Part.Text(text = "Hello", id = "p1", sessionId = "ses1", messageId = "msg1"))
        )
        assertEquals("msg1", msg.id)
        assertEquals("ses1", msg.sessionId)
        assertEquals("user", msg.role)
        assertTrue(msg.isUser)
        assertFalse(msg.isAssistant)
    }

    @Test
    fun `Message empty parts list by default`() {
        val msg = Message(info = MessageInfo())
        assertTrue(msg.parts.isEmpty())
        assertEquals("", msg.id)
        assertEquals("", msg.role)
    }
}
