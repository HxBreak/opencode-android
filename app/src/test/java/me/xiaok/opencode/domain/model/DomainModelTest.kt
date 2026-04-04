package me.xiaok.opencode.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for domain models beyond JSON deserialization.
 * Tests sealed class exhaustiveness, computed properties, edge cases,
 * and serialization round-trips.
 *
 * Complement to ModelDeserializationTest - no duplication.
 */
class DomainModelTest {

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

    // === Part Exhaustive Subtype Tests ===

    @Test
    fun `Part-Text serializes and round-trips correctly`() {
        val original = Part.Text(text = "Hello", id = "p1", sessionId = "s1", messageId = "m1")
        val encoded = json.encodeToString(Part.serializer(), original)
        val decoded = json.decodeFromString<Part>(encoded)
        assertTrue(decoded is Part.Text)
        assertEquals(original.text, (decoded as Part.Text).text)
    }

    @Test
    fun `Part-Reasoning serializes and round-trips correctly`() {
        val original = Part.Reasoning(text = "Thinking", id = "p1", sessionId = "s1", messageId = "m1")
        val encoded = json.encodeToString(Part.serializer(), original)
        val decoded = json.decodeFromString<Part>(encoded)
        assertTrue(decoded is Part.Reasoning)
        assertEquals(original.text, (decoded as Part.Reasoning).text)
    }

    @Test
    fun `Part-Tool serializes and round-trips correctly`() {
        val original = Part.Tool(tool = "bash", state = ToolState(status = "running"), id = "p1", sessionId = "s1", messageId = "m1", callId = "call_1")
        val encoded = json.encodeToString(Part.serializer(), original)
        val decoded = json.decodeFromString<Part>(encoded)
        assertTrue(decoded is Part.Tool)
        assertEquals(original.tool, (decoded as Part.Tool).tool)
        assertEquals(original.callId, decoded.callId)
    }

    @Test
    fun `Part-File serializes and round-trips correctly`() {
        val original = Part.File(name = "test.txt", url = "http://example.com/file.txt", id = "p1", sessionId = "s1", messageId = "m1")
        val encoded = json.encodeToString(Part.serializer(), original)
        val decoded = json.decodeFromString<Part>(encoded)
        assertTrue(decoded is Part.File)
        assertEquals(original.name, (decoded as Part.File).name)
        assertEquals(original.url, decoded.url)
    }

    @Test
    fun `Part-Subtask serializes and round-trips correctly`() {
        val original = Part.Subtask(agent = "explore", prompt = "Test", output = "Result", id = "p1", sessionId = "s1", messageId = "m1")
        val encoded = json.encodeToString(Part.serializer(), original)
        val decoded = json.decodeFromString<Part>(encoded)
        assertTrue(decoded is Part.Subtask)
        assertEquals(original.agent, (decoded as Part.Subtask).agent)
        assertEquals(original.prompt, decoded.prompt)
    }

    @Test
    fun `Part-StepStart serializes and round-trips correctly`() {
        val original = Part.StepStart(name = "Plan", id = "p1", sessionId = "s1", messageId = "m1")
        val encoded = json.encodeToString(Part.serializer(), original)
        val decoded = json.decodeFromString<Part>(encoded)
        assertTrue(decoded is Part.StepStart)
        assertEquals(original.name, (decoded as Part.StepStart).name)
    }

    @Test
    fun `Part-StepFinish serializes and round-trips correctly`() {
        val original = Part.StepFinish(reason = "done", cost = 0.5, tokens = TokenUsage(total = 100), id = "p1", sessionId = "s1", messageId = "m1")
        val encoded = json.encodeToString(Part.serializer(), original)
        val decoded = json.decodeFromString<Part>(encoded)
        assertTrue(decoded is Part.StepFinish)
        assertEquals(original.reason, (decoded as Part.StepFinish).reason)
        assertEquals(original.cost, decoded.cost, 0.001)
    }

    @Test
    fun `Part-Snapshot serializes and round-trips correctly`() {
        val original = Part.Snapshot(snapshotId = "snap_1", label = "Checkpoint", id = "p1", sessionId = "s1", messageId = "m1")
        val encoded = json.encodeToString(Part.serializer(), original)
        val decoded = json.decodeFromString<Part>(encoded)
        assertTrue(decoded is Part.Snapshot)
        assertEquals(original.snapshotId, (decoded as Part.Snapshot).snapshotId)
        assertEquals(original.label, decoded.label)
    }

    @Test
    fun `Part-Patch serializes and round-trips correctly`() {
        val diffs = listOf(FileDiff(path = "test.txt", additions = 5, deletions = 2))
        val original = Part.Patch(diffs = diffs, id = "p1", sessionId = "s1", messageId = "m1")
        val encoded = json.encodeToString(Part.serializer(), original)
        val decoded = json.decodeFromString<Part>(encoded)
        assertTrue(decoded is Part.Patch)
        assertEquals(original.diffs.size, (decoded as Part.Patch).diffs.size)
        assertEquals(original.diffs[0].path, decoded.diffs[0].path)
    }

    @Test
    fun `Part-Agent serializes and round-trips correctly`() {
        val original = Part.Agent(agent = "Build", model = ModelRef(providerID = "zhipu", modelID = "glm-5"), id = "p1", sessionId = "s1", messageId = "m1")
        val encoded = json.encodeToString(Part.serializer(), original)
        val decoded = json.decodeFromString<Part>(encoded)
        assertTrue(decoded is Part.Agent)
        assertEquals(original.agent, (decoded as Part.Agent).agent)
        assertEquals(original.model.providerID, decoded.model.providerID)
    }

    @Test
    fun `Part-Retry serializes and round-trips correctly`() {
        val original = Part.Retry(error = "Failed", id = "p1", sessionId = "s1", messageId = "m1")
        val encoded = json.encodeToString(Part.serializer(), original)
        val decoded = json.decodeFromString<Part>(encoded)
        assertTrue(decoded is Part.Retry)
        assertEquals(original.error, (decoded as Part.Retry).error)
    }

    @Test
    fun `Part-Compaction serializes and round-trips correctly`() {
        val original = Part.Compaction(summary = "Compressed", id = "p1", sessionId = "s1", messageId = "m1")
        val encoded = json.encodeToString(Part.serializer(), original)
        val decoded = json.decodeFromString<Part>(encoded)
        assertTrue(decoded is Part.Compaction)
        assertEquals(original.summary, (decoded as Part.Compaction).summary)
    }

    @Test
    fun `Part all 12 subtypes serialize and deserialize correctly`() {
        val parts = listOf(
            Part.Text(text = "t", id = "p1", sessionId = "s1", messageId = "m1"),
            Part.Reasoning(text = "r", id = "p2", sessionId = "s1", messageId = "m1"),
            Part.Tool(tool = "bash", id = "p3", sessionId = "s1", messageId = "m1"),
            Part.File(id = "p4", sessionId = "s1", messageId = "m1"),
            Part.Subtask(id = "p5", sessionId = "s1", messageId = "m1"),
            Part.StepStart(id = "p6", sessionId = "s1", messageId = "m1"),
            Part.StepFinish(tokens = TokenUsage(), id = "p7", sessionId = "s1", messageId = "m1"),
            Part.Snapshot(id = "p8", sessionId = "s1", messageId = "m1"),
            Part.Patch(id = "p9", sessionId = "s1", messageId = "m1"),
            Part.Agent(id = "p10", sessionId = "s1", messageId = "m1"),
            Part.Retry(id = "p11", sessionId = "s1", messageId = "m1"),
            Part.Compaction(id = "p12", sessionId = "s1", messageId = "m1")
        )

        // All 12 subtypes should round-trip
        val decodedParts = parts.map { part ->
            val encoded = json.encodeToString(Part.serializer(), part)
            json.decodeFromString<Part>(encoded)
        }

        assertEquals(12, decodedParts.size)

        // Verify each subtype is preserved
        assertTrue(decodedParts[0] is Part.Text)
        assertTrue(decodedParts[1] is Part.Reasoning)
        assertTrue(decodedParts[2] is Part.Tool)
        assertTrue(decodedParts[3] is Part.File)
        assertTrue(decodedParts[4] is Part.Subtask)
        assertTrue(decodedParts[5] is Part.StepStart)
        assertTrue(decodedParts[6] is Part.StepFinish)
        assertTrue(decodedParts[7] is Part.Snapshot)
        assertTrue(decodedParts[8] is Part.Patch)
        assertTrue(decodedParts[9] is Part.Agent)
        assertTrue(decodedParts[10] is Part.Retry)
        assertTrue(decodedParts[11] is Part.Compaction)
    }

    // === SseEvent Exhaustive Type Tests ===

    @Test
    fun `SseEvent all 24 event types serialize and deserialize`() {
        val events = listOf(
            SseEvent.ServerConnected,
            SseEvent.ServerHeartbeat,
            SseEvent.ServerInstanceDisposed,
            SseEvent.SessionCreated(Session(id = "s1")),
            SseEvent.SessionUpdated(Session(id = "s1")),
            SseEvent.SessionDeleted(Session(id = "s1")),
            SseEvent.SessionStatusChanged("s1", SessionStatus.BUSY),
            SseEvent.SessionIdle("s1"),
            SseEvent.SessionDiff("s1", emptyList()),
            SseEvent.SessionError("s1", null),
            SseEvent.MessageUpdated(Message(info = MessageInfo(id = "m1"))),
            SseEvent.MessageRemoved("s1", "m1"),
            SseEvent.MessagePartUpdated(Part.Text(id = "p1")),
            SseEvent.MessagePartDelta("s1", "m1", "p1", "text", "delta"),
            SseEvent.MessagePartRemoved("s1", "m1", "p1"),
            SseEvent.PermissionAsked(PermissionRequest(id = "prq1")),
            SseEvent.PermissionReplied("s1", "prq1"),
            SseEvent.QuestionAsked(QuestionRequest(id = "q1")),
            SseEvent.QuestionReplied("s1", "q1"),
            SseEvent.QuestionRejected("s1", "q1"),
            SseEvent.TodoUpdated("s1", emptyList()),
            SseEvent.VcsBranchUpdated("main"),
            SseEvent.LspUpdated,
            SseEvent.ProjectUpdated(Project(id = "p1"))
        )

        assertEquals(24, events.size)

        // All events should serialize and deserialize without errors
        events.forEach { event ->
            val encoded = json.encodeToString(SseEvent.serializer(), event)
            val decoded = json.decodeFromString<SseEvent>(encoded)
            assertNotNull(decoded)
        }
    }

    @Test
    fun `SseEvent-SessionCreated round-trips with session`() {
        val session = Session(id = "ses_1", title = "Test")
        val event = SseEvent.SessionCreated(session)
        val encoded = json.encodeToString(SseEvent.serializer(), event)
        val decoded = json.decodeFromString<SseEvent>(encoded)
        assertTrue(decoded is SseEvent.SessionCreated)
        assertEquals("ses_1", (decoded as SseEvent.SessionCreated).session.id)
    }

    @Test
    fun `SseEvent-SessionStatusChanged round-trips with status`() {
        val event = SseEvent.SessionStatusChanged(sessionId = "ses_1", status = SessionStatus.BUSY)
        val encoded = json.encodeToString(SseEvent.serializer(), event)
        val decoded = json.decodeFromString<SseEvent>(encoded)
        assertTrue(decoded is SseEvent.SessionStatusChanged)
        assertEquals("ses_1", (decoded as SseEvent.SessionStatusChanged).sessionId)
        assertEquals(SessionStatus.BUSY, decoded.status)
    }

    @Test
    fun `SseEvent-SessionDiff round-trips with diffs`() {
        val diffs = listOf(FileDiff(path = "test.txt", additions = 1, deletions = 0))
        val event = SseEvent.SessionDiff(sessionId = "ses_1", diffs = diffs)
        val encoded = json.encodeToString(SseEvent.serializer(), event)
        val decoded = json.decodeFromString<SseEvent>(encoded)
        assertTrue(decoded is SseEvent.SessionDiff)
        assertEquals("ses_1", (decoded as SseEvent.SessionDiff).sessionId)
        assertEquals(1, decoded.diffs.size)
    }

    @Test
    fun `SseEvent-MessageRemoved round-trips with session and message IDs`() {
        val event = SseEvent.MessageRemoved(sessionId = "ses_1", messageId = "msg_1")
        val encoded = json.encodeToString(SseEvent.serializer(), event)
        val decoded = json.decodeFromString<SseEvent>(encoded)
        assertTrue(decoded is SseEvent.MessageRemoved)
        assertEquals("ses_1", (decoded as SseEvent.MessageRemoved).sessionId)
        assertEquals("msg_1", decoded.messageId)
    }

    @Test
    fun `SseEvent-MessagePartUpdated round-trips with part`() {
        val part = Part.Text(text = "Hello", id = "p1", sessionId = "s1", messageId = "m1")
        val event = SseEvent.MessagePartUpdated(part)
        val encoded = json.encodeToString(SseEvent.serializer(), event)
        val decoded = json.decodeFromString<SseEvent>(encoded)
        assertTrue(decoded is SseEvent.MessagePartUpdated)
        assertTrue((decoded as SseEvent.MessagePartUpdated).part is Part.Text)
        assertEquals("Hello", ((decoded as SseEvent.MessagePartUpdated).part as Part.Text).text)
    }

    @Test
    fun `SseEvent-MessagePartDelta round-trips with delta info`() {
        val event = SseEvent.MessagePartDelta(
            sessionId = "ses_1",
            messageId = "msg_1",
            partId = "p1",
            field = "text",
            delta = "appended"
        )
        val encoded = json.encodeToString(SseEvent.serializer(), event)
        val decoded = json.decodeFromString<SseEvent>(encoded)
        assertTrue(decoded is SseEvent.MessagePartDelta)
        assertEquals("appended", (decoded as SseEvent.MessagePartDelta).delta)
    }

    @Test
    fun `SseEvent-MessagePartRemoved round-trips with IDs`() {
        val event = SseEvent.MessagePartRemoved(sessionId = "ses_1", messageId = "msg_1", partId = "p1")
        val encoded = json.encodeToString(SseEvent.serializer(), event)
        val decoded = json.decodeFromString<SseEvent>(encoded)
        assertTrue(decoded is SseEvent.MessagePartRemoved)
        assertEquals("p1", (decoded as SseEvent.MessagePartRemoved).partId)
    }

    @Test
    fun `SseEvent-PermissionAsked round-trips with permission`() {
        val permission = PermissionRequest(
            id = "prq_1",
            permission = "file",
            patterns = listOf("*.txt")
        )
        val event = SseEvent.PermissionAsked(permission)
        val encoded = json.encodeToString(SseEvent.serializer(), event)
        val decoded = json.decodeFromString<SseEvent>(encoded)
        assertTrue(decoded is SseEvent.PermissionAsked)
        assertEquals("file", (decoded as SseEvent.PermissionAsked).permission.permission)
    }

    @Test
    fun `SseEvent-PermissionReplied round-trips with IDs`() {
        val event = SseEvent.PermissionReplied(sessionId = "ses_1", requestId = "prq_1")
        val encoded = json.encodeToString(SseEvent.serializer(), event)
        val decoded = json.decodeFromString<SseEvent>(encoded)
        assertTrue(decoded is SseEvent.PermissionReplied)
        assertEquals("prq_1", (decoded as SseEvent.PermissionReplied).requestId)
    }

    @Test
    fun `SseEvent-QuestionAsked round-trips with question`() {
        val question = QuestionRequest(
            id = "qst_1",
            questions = listOf(
                QuestionInfo(
                    question = "Continue?",
                    options = listOf(
                        QuestionOption(label = "yes"),
                        QuestionOption(label = "no")
                    )
                )
            )
        )
        val event = SseEvent.QuestionAsked(question)
        val encoded = json.encodeToString(SseEvent.serializer(), event)
        val decoded = json.decodeFromString<SseEvent>(encoded)
        assertTrue(decoded is SseEvent.QuestionAsked)
        assertEquals("Continue?", (decoded as SseEvent.QuestionAsked).question.questions[0].question)
    }

    @Test
    fun `SseEvent-QuestionReplied round-trips with IDs`() {
        val event = SseEvent.QuestionReplied(sessionId = "ses_1", requestId = "qst_1")
        val encoded = json.encodeToString(SseEvent.serializer(), event)
        val decoded = json.decodeFromString<SseEvent>(encoded)
        assertTrue(decoded is SseEvent.QuestionReplied)
        assertEquals("qst_1", (decoded as SseEvent.QuestionReplied).requestId)
    }

    @Test
    fun `SseEvent-QuestionRejected round-trips with IDs`() {
        val event = SseEvent.QuestionRejected(sessionId = "ses_1", requestId = "qst_1")
        val encoded = json.encodeToString(SseEvent.serializer(), event)
        val decoded = json.decodeFromString<SseEvent>(encoded)
        assertTrue(decoded is SseEvent.QuestionRejected)
        assertEquals("qst_1", (decoded as SseEvent.QuestionRejected).requestId)
    }

    @Test
    fun `SseEvent-TodoUpdated round-trips with todos`() {
        val todos = listOf(Todo(id = "td_1", content = "Task 1"))
        val event = SseEvent.TodoUpdated(sessionId = "ses_1", todos = todos)
        val encoded = json.encodeToString(SseEvent.serializer(), event)
        val decoded = json.decodeFromString<SseEvent>(encoded)
        assertTrue(decoded is SseEvent.TodoUpdated)
        assertEquals("ses_1", (decoded as SseEvent.TodoUpdated).sessionId)
        assertEquals("Task 1", decoded.todos[0].content)
    }

    @Test
    fun `SseEvent-VcsBranchUpdated round-trips with branch name`() {
        val event = SseEvent.VcsBranchUpdated(branch = "main")
        val encoded = json.encodeToString(SseEvent.serializer(), event)
        val decoded = json.decodeFromString<SseEvent>(encoded)
        assertTrue(decoded is SseEvent.VcsBranchUpdated)
        assertEquals("main", (decoded as SseEvent.VcsBranchUpdated).branch)
    }

    @Test
    fun `SseEvent-ProjectUpdated round-trips with project`() {
        val project = Project(id = "prj_1", name = "MyProject")
        val event = SseEvent.ProjectUpdated(project)
        val encoded = json.encodeToString(SseEvent.serializer(), event)
        val decoded = json.decodeFromString<SseEvent>(encoded)
        assertTrue(decoded is SseEvent.ProjectUpdated)
        assertEquals("MyProject", (decoded as SseEvent.ProjectUpdated).project.name)
    }

    // === ServerConnection authHeader Tests ===

    @Test
    fun `ServerConnection authHeader returns null when username and password are empty`() {
        val conn = ServerConnection(
            id = "conn1",
            name = "Test",
            baseUrl = "http://example.com",
            username = "",
            password = ""
        )
        assertNull(conn.authHeader)
    }

    @Test
    fun `ServerConnection authHeader returns Basic auth when username is set`() {
        val conn = ServerConnection(
            id = "conn1",
            name = "Test",
            baseUrl = "http://example.com",
            username = "admin",
            password = ""
        )
        assertNotNull(conn.authHeader)
        assertTrue(conn.authHeader!!.startsWith("Basic "))
    }

    @Test
    fun `ServerConnection authHeader returns Basic auth when password is set`() {
        val conn = ServerConnection(
            id = "conn1",
            name = "Test",
            baseUrl = "http://example.com",
            username = "",
            password = "secret"
        )
        assertNotNull(conn.authHeader)
        assertTrue(conn.authHeader!!.startsWith("Basic "))
    }

    @Test
    fun `ServerConnection authHeader correctly encodes username and password`() {
        val conn = ServerConnection(
            id = "conn1",
            name = "Test",
            baseUrl = "http://example.com",
            username = "admin",
            password = "secret123"
        )
        assertNotNull(conn.authHeader)
        val header = conn.authHeader!!
        // Decode and verify
        val encoded = header.removePrefix("Basic ")
        val decoded = String(java.util.Base64.getDecoder().decode(encoded))
        assertEquals("admin:secret123", decoded)
    }

    @Test
    fun `ServerConnection authHeader handles special characters in credentials`() {
        val conn = ServerConnection(
            id = "conn1",
            name = "Test",
            baseUrl = "http://example.com",
            username = "user@example.com",
            password = "p@ss:w0rd!"
        )
        assertNotNull(conn.authHeader)
        val header = conn.authHeader!!
        val encoded = header.removePrefix("Basic ")
        val decoded = String(java.util.Base64.getDecoder().decode(encoded))
        assertEquals("user@example.com:p@ss:w0rd!", decoded)
    }

    // === Session Optional Field Default Tests ===

    @Test
    fun `Session optional fields default to null or empty`() {
        val session = Session(id = "ses_1")

        assertEquals("ses_1", session.id)
        assertEquals("", session.slug)
        assertEquals("", session.projectID)
        assertNull(session.workspaceID)
        assertEquals("", session.directory)
        assertNull(session.parentID)
        assertEquals("", session.title)
        assertEquals("", session.version)
        assertNull(session.summary)
        assertNull(session.share)
        assertTrue(session.permission.isEmpty())
        assertNull(session.revert)
        assertNotNull(session.time)
        assertEquals(0L, session.time.created)
        assertEquals(0L, session.time.updated)
        assertNull(session.time.compacting)
        assertNull(session.time.archived)
    }

    @Test
    fun `Session with all optional fields set`() {
        val session = Session(
            id = "ses_1",
            slug = "my-slug",
            projectID = "prj_1",
            workspaceID = "ws_1",
            directory = "/path/to/project",
            parentID = "ses_parent",
            title = "My Session",
            version = "v1.0",
            summary = SessionSummary(additions = 10, deletions = 5),
            share = SessionShare(url = "http://example.com/share"),
            permission = listOf(PermissionRule(permission = "file", pattern = "*.kt", action = "allow")),
            revert = RevertInfo(messageID = "msg_1", partID = "p1"),
            time = SessionTime(created = 1000L, updated = 2000L)
        )

        assertEquals("my-slug", session.slug)
        assertEquals("prj_1", session.projectID)
        assertEquals("ws_1", session.workspaceID)
        assertEquals("/path/to/project", session.directory)
        assertEquals("ses_parent", session.parentID)
        assertEquals("My Session", session.title)
        assertEquals("v1.0", session.version)
        assertNotNull(session.summary)
        assertEquals(10, session.summary!!.additions)
        assertNotNull(session.share)
        assertEquals("http://example.com/share", session.share!!.url)
        assertEquals(1, session.permission.size)
        assertNotNull(session.revert)
        assertEquals("msg_1", session.revert!!.messageID)
        assertEquals(1000L, session.time.created)
        assertEquals(2000L, session.time.updated)
    }

    // === Serialization Round-Trip Tests ===

    @Test
    fun `Session serialization round-trip`() {
        val original = Session(
            id = "ses_1",
            title = "Test Session",
            directory = "/path",
            summary = SessionSummary(additions = 5, deletions = 3),
            permission = listOf(
                PermissionRule(permission = "file", pattern = "*.kt", action = "allow")
            )
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<Session>(encoded)

        assertEquals(original.id, decoded.id)
        assertEquals(original.title, decoded.title)
        assertEquals(original.directory, decoded.directory)
        assertEquals(original.summary?.additions, decoded.summary?.additions)
        assertEquals(original.summary?.deletions, decoded.summary?.deletions)
        assertEquals(original.permission.size, decoded.permission.size)
        assertEquals(original.permission[0].permission, decoded.permission[0].permission)
    }

    @Test
    fun `Message serialization round-trip`() {
        val original = Message(
            info = MessageInfo(
                role = "assistant",
                id = "msg_1",
                sessionID = "ses_1",
                time = MessageTime(created = 1000L, completed = 2000L),
                modelID = "glm-5",
                providerID = "zhipu",
                agent = "Build",
                cost = 0.5
            ),
            parts = listOf(
                Part.Text(text = "Hello", id = "p1", sessionId = "ses_1", messageId = "msg_1"),
                Part.Tool(tool = "bash", state = ToolState(status = "completed"), id = "p2", sessionId = "ses_1", messageId = "msg_1")
            )
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<Message>(encoded)

        assertEquals(original.id, decoded.id)
        assertEquals(original.role, decoded.role)
        assertEquals(original.info.modelID, decoded.info.modelID)
        assertEquals(original.info.providerID, decoded.info.providerID)
        assertEquals(original.info.agent, decoded.info.agent)
        assertEquals(original.info.cost!!, decoded.info.cost!!, 0.001)
        assertEquals(original.parts.size, decoded.parts.size)
        assertEquals((original.parts[0] as Part.Text).text, (decoded.parts[0] as Part.Text).text)
        assertEquals((original.parts[1] as Part.Tool).tool, (decoded.parts[1] as Part.Tool).tool)
    }

    @Test
    fun `ToolState serialization round-trip`() {
        val original = ToolState(
            status = "completed",
            title = "Build complete",
            output = "Success",
            error = ""
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ToolState>(encoded)

        assertEquals(original.status, decoded.status)
        assertEquals(original.title, decoded.title)
        assertEquals(original.output, decoded.output)
        assertEquals(original.error, decoded.error)
        assertTrue(decoded.isCompleted)
        assertFalse(decoded.isError)
    }

    @Test
    fun `Part-File serialization round-trip`() {
        val original = Part.File(
            id = "p1",
            sessionId = "ses_1",
            messageId = "msg_1",
            name = "test.txt",
            url = "http://example.com/test.txt",
            mimeType = "text/plain"
        )

        val encoded = json.encodeToString(Part.serializer(), original)
        val decoded = json.decodeFromString<Part>(encoded)

        assertTrue(decoded is Part.File)
        assertEquals(original.name, (decoded as Part.File).name)
        assertEquals(original.url, decoded.url)
        assertEquals(original.mimeType, decoded.mimeType)
    }

    @Test
    fun `Part-Subtask serialization round-trip`() {
        val original = Part.Subtask(
            id = "p1",
            sessionId = "ses_1",
            messageId = "msg_1",
            agent = "explore",
            prompt = "Find files",
            output = "Found 10 files"
        )

        val encoded = json.encodeToString(Part.serializer(), original)
        val decoded = json.decodeFromString<Part>(encoded)

        assertTrue(decoded is Part.Subtask)
        assertEquals(original.agent, (decoded as Part.Subtask).agent)
        assertEquals(original.prompt, decoded.prompt)
        assertEquals(original.output, decoded.output)
    }

    // === Edge Cases ===

    @Test
    fun `Message with empty parts list serializes correctly`() {
        val message = Message(info = MessageInfo(role = "user", id = "msg_1", sessionID = "ses_1"))
        val encoded = json.encodeToString(message)
        val decoded = json.decodeFromString<Message>(encoded)

        assertTrue(decoded.parts.isEmpty())
    }

    @Test
    fun `Part-Text with empty text serializes correctly`() {
        val part = Part.Text(text = "", id = "p1", sessionId = "s1", messageId = "m1")
        val encoded = json.encodeToString(Part.serializer(), part)
        val decoded = json.decodeFromString<Part>(encoded)

        assertTrue(decoded is Part.Text)
        assertEquals("", (decoded as Part.Text).text)
    }

    @Test
    fun `ToolState minimal defaults serialize correctly`() {
        val state = ToolState()
        val encoded = json.encodeToString(state)
        val decoded = json.decodeFromString<ToolState>(encoded)

        assertEquals("pending", decoded.status)
        assertTrue(decoded.isPending)
        assertFalse(decoded.isRunning)
        assertFalse(decoded.isCompleted)
        assertFalse(decoded.isError)
        assertEquals("", decoded.output)
        assertEquals("", decoded.title)
        assertEquals("", decoded.error)
        assertNull(decoded.input)
        assertNull(decoded.metadata)
    }

    @Test
    fun `ErrorInfo message convenience accessor returns data message when present`() {
        val error = ErrorInfo(
            name = "APIError",
            data = ErrorData(message = "Something went wrong", statusCode = 500)
        )

        assertEquals("Something went wrong", error.message)
    }

    @Test
    fun `ErrorInfo message convenience accessor returns empty string when data is null`() {
        val error = ErrorInfo(name = "UnknownError", data = null)

        assertEquals("", error.message)
    }

    @Test
    fun `ErrorInfo message convenience accessor returns empty string when data message is empty`() {
        val error = ErrorInfo(
            name = "APIError",
            data = ErrorData(message = "")
        )

        assertEquals("", error.message)
    }

    @Test
    fun `SessionSummary with all zero values serializes correctly`() {
        val summary = SessionSummary()
        val encoded = json.encodeToString(summary)
        val decoded = json.decodeFromString<SessionSummary>(encoded)

        assertEquals(0, decoded.additions)
        assertEquals(0, decoded.deletions)
        assertEquals(0, decoded.files)
        assertEquals(0, decoded.diffs)
    }

    @Test
    fun `TokenUsage with negative values serializes correctly`() {
        val tokens = TokenUsage(
            total = 100L,
            input = -1000L,  // Can be negative in API responses
            output = 50L,
            reasoning = 10L,
            cache = CacheInfo(read = 2000L, write = 100L)
        )

        val encoded = json.encodeToString(tokens)
        val decoded = json.decodeFromString<TokenUsage>(encoded)

        assertEquals(100L, decoded.total)
        assertEquals(-1000L, decoded.input)
        assertEquals(50L, decoded.output)
        assertEquals(10L, decoded.reasoning)
        assertEquals(2000L, decoded.cache.read)
        assertEquals(100L, decoded.cache.write)
    }

    @Test
    fun `ServerConnection with autoConnect flag serializes correctly`() {
        val conn = ServerConnection(
            id = "conn1",
            name = "Test",
            baseUrl = "http://example.com",
            autoConnect = false
        )

        val encoded = json.encodeToString(conn)
        val decoded = json.decodeFromString<ServerConnection>(encoded)

        assertFalse(decoded.autoConnect)
    }

    @Test
    fun `PartTime with null start and end serializes correctly`() {
        val partTime = PartTime()
        val encoded = json.encodeToString(partTime)
        val decoded = json.decodeFromString<PartTime>(encoded)

        assertNull(decoded.start)
        assertNull(decoded.end)
    }

    @Test
    fun `PartTime with only start time serializes correctly`() {
        val partTime = PartTime(start = 1000L)
        val encoded = json.encodeToString(partTime)
        val decoded = json.decodeFromString<PartTime>(encoded)

        assertEquals(1000L, decoded.start)
        assertNull(decoded.end)
    }

    @Test
    fun `MessageTime with completed timestamp serializes correctly`() {
        val time = MessageTime(
            created = 1000L,
            updated = 2000L,
            completed = 3000L
        )

        val encoded = json.encodeToString(time)
        val decoded = json.decodeFromString<MessageTime>(encoded)

        assertEquals(1000L, decoded.created)
        assertEquals(2000L, decoded.updated)
        assertEquals(3000L, decoded.completed)
    }

    @Test
    fun `MessageTime without completed timestamp serializes correctly`() {
        val time = MessageTime(
            created = 1000L,
            updated = 2000L
        )

        val encoded = json.encodeToString(time)
        val decoded = json.decodeFromString<MessageTime>(encoded)

        assertEquals(1000L, decoded.created)
        assertEquals(2000L, decoded.updated)
        assertNull(decoded.completed)
    }

    @Test
    fun `StepFinish with null reason serializes correctly`() {
        val finish = Part.StepFinish(
            reason = null,
            cost = 0.0,
            tokens = TokenUsage(),
            id = "p1",
            sessionId = "s1",
            messageId = "m1"
        )

        val encoded = json.encodeToString(Part.serializer(), finish)
        val decoded = json.decodeFromString<Part>(encoded)

        assertTrue(decoded is Part.StepFinish)
        assertNull((decoded as Part.StepFinish).reason)
    }

    @Test
    fun `Part-File with nullable mimeType serializes correctly`() {
        val part = Part.File(
            id = "p1",
            sessionId = "s1",
            messageId = "m1",
            name = "test.txt",
            url = "http://example.com/test.txt",
            mimeType = null
        )

        val encoded = json.encodeToString(Part.serializer(), part)
        val decoded = json.decodeFromString<Part>(encoded)

        assertTrue(decoded is Part.File)
        assertNull((decoded as Part.File).mimeType)
    }
}
