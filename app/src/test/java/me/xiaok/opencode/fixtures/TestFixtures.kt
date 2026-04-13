package me.xiaok.opencode.fixtures

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.xiaok.opencode.domain.model.*

/**
 * Test fixtures providing factory methods for all domain models.
 * Each factory uses sensible defaults so tests only need to override relevant fields.
 */
object TestFixtures {

    // === Session ===

    fun testSession(
        id: String = "ses_test123",
        slug: String = "test-slug",
        projectID: String = "prj_test",
        workspaceID: String? = null,
        directory: String = "/home/user/project",
        parentID: String? = null,
        title: String = "Test Session",
        version: String = "v1",
        summary: SessionSummary? = null,
        share: SessionShare? = null,
        permission: List<PermissionRule> = emptyList(),
        revert: RevertInfo? = null,
        time: SessionTime = testSessionTime(),
    ) = Session(
        id = id,
        slug = slug,
        projectID = projectID,
        workspaceID = workspaceID,
        directory = directory,
        parentID = parentID,
        title = title,
        version = version,
        summary = summary,
        share = share,
        permission = permission,
        revert = revert,
        time = time,
    )

    fun testSessionSummary(
        additions: Int = 10,
        deletions: Int = 5,
        files: Int = 3,
        diffs: Int = 2,
    ) = SessionSummary(
        additions = additions,
        deletions = deletions,
        files = files,
        diffs = diffs,
    )

    fun testSessionShare(
        url: String = "https://share.opencode.dev/abc123",
    ) = SessionShare(url = url)

    fun testPermissionRule(
        permission: String = "bash",
        pattern: String = "**/*.sh",
        action: String = "allow",
    ) = PermissionRule(
        permission = permission,
        pattern = pattern,
        action = action,
    )

    fun testRevertInfo(
        messageID: String = "msg_revert123",
        partID: String = "prt_revert123",
    ) = RevertInfo(
        messageID = messageID,
        partID = partID,
    )

    fun testSessionTime(
        created: Long = 1712000000000L,
        updated: Long = 1712000100000L,
        compacting: Long? = null,
        archived: Long? = null,
    ) = SessionTime(
        created = created,
        updated = updated,
        compacting = compacting,
        archived = archived,
    )

    // === Message ===

    fun testMessage(
        info: MessageInfo = testMessageInfo(),
        parts: List<Part> = emptyList(),
    ) = Message(
        info = info,
        parts = parts,
    )

    fun testMessageInfo(
        role: String = "assistant",
        id: String = "msg_test123",
        sessionID: String = "ses_test123",
        time: MessageTime = testMessageTime(),
        summary: UserSummary? = null,
        parentID: String? = null,
        modelID: String? = "claude-3-sonnet",
        providerID: String? = "anthropic",
        mode: String? = "primary",
        agent: String? = "code",
        path: MessagePath? = null,
        cost: Double? = 0.005,
        tokens: TokenUsage? = testTokenUsage(),
        error: ErrorInfo? = null,
        finish: String? = "stop",
    ) = MessageInfo(
        role = role,
        id = id,
        sessionID = sessionID,
        time = time,
        summary = summary,
        parentID = parentID,
        modelID = modelID,
        providerID = providerID,
        mode = mode,
        agent = agent,
        path = path,
        cost = cost,
        tokens = tokens,
        error = error,
        finish = finish,
    )

    fun testUserMessageInfo(
        id: String = "msg_user123",
        sessionID: String = "ses_test123",
        text: String = "Hello, can you help me?",
    ) = MessageInfo(
        role = "user",
        id = id,
        sessionID = sessionID,
    )

    fun testTokenUsage(
        total: Long = 1000L,
        input: Long = 600L,
        output: Long = 400L,
        reasoning: Long = 100L,
        cache: CacheInfo = testCacheInfo(),
    ) = TokenUsage(
        total = total,
        input = input,
        output = output,
        reasoning = reasoning,
        cache = cache,
    )

    fun testCacheInfo(
        read: Long = 500L,
        write: Long = 200L,
    ) = CacheInfo(
        read = read,
        write = write,
    )

    fun testErrorInfo(
        name: String = "APIError",
        data: ErrorData? = testErrorData(),
    ) = ErrorInfo(
        name = name,
        data = data,
    )

    fun testErrorData(
        message: String = "This model is not available in your region.",
        statusCode: Int? = 403,
        isRetryable: Boolean? = false,
    ) = ErrorData(
        message = message,
        statusCode = statusCode,
        isRetryable = isRetryable,
    )

    fun testUserSummary(
        diffs: List<FileDiff> = listOf(testFileDiff()),
    ) = UserSummary(diffs = diffs)

    fun testMessageTime(
        created: Long = 1712000000000L,
        updated: Long = 1712000050000L,
        completed: Long? = 1712000100000L,
    ) = MessageTime(
        created = created,
        updated = updated,
        completed = completed,
    )

    fun testMessagePath(
        cwd: String = "/home/user/project",
        root: String = "/home/user/project",
    ) = MessagePath(
        cwd = cwd,
        root = root,
    )

    fun testModelRef(
        providerID: String = "anthropic",
        modelID: String = "claude-3-sonnet",
    ) = ModelRef(
        providerID = providerID,
        modelID = modelID,
    )

    // === Parts ===

    fun testTextPart(
        id: String = "prt_text123",
        sessionId: String = "ses_test123",
        messageId: String = "msg_test123",
        text: String = "Hello from assistant",
        time: PartTime? = testPartTime(),
    ) = Part.Text(
        id = id,
        sessionId = sessionId,
        messageId = messageId,
        text = text,
        time = time,
    )

    fun testReasoningPart(
        id: String = "prt_reason123",
        sessionId: String = "ses_test123",
        messageId: String = "msg_test123",
        text: String = "Let me think about this step by step...",
    ) = Part.Reasoning(
        id = id,
        sessionId = sessionId,
        messageId = messageId,
        text = text,
    )

    fun testToolPart(
        id: String = "prt_tool123",
        sessionId: String = "ses_test123",
        messageId: String = "msg_test123",
        tool: String = "bash",
        state: ToolState = testToolState(),
        callId: String = "call_abc123",
    ) = Part.Tool(
        id = id,
        sessionId = sessionId,
        messageId = messageId,
        tool = tool,
        state = state,
        callId = callId,
    )

    fun testFilePart(
        id: String = "prt_file123",
        sessionId: String = "ses_test123",
        messageId: String = "msg_test123",
        name: String = "screenshot.png",
        url: String = "https://files.opencode.dev/screenshot.png",
        mimeType: String? = "image/png",
    ) = Part.File(
        id = id,
        sessionId = sessionId,
        messageId = messageId,
        name = name,
        url = url,
        mimeType = mimeType,
    )

    fun testSubtaskPart(
        id: String = "prt_subtask123",
        sessionId: String = "ses_test123",
        messageId: String = "msg_test123",
        agent: String = "explore",
        prompt: String = "Find all Kotlin files in src/",
        output: String = "Found 42 Kotlin files",
    ) = Part.Subtask(
        id = id,
        sessionId = sessionId,
        messageId = messageId,
        agent = agent,
        prompt = prompt,
        output = output,
    )

    fun testStepStartPart(
        id: String = "prt_stepstart123",
        sessionId: String = "ses_test123",
        messageId: String = "msg_test123",
        name: String = "code",
    ) = Part.StepStart(
        id = id,
        sessionId = sessionId,
        messageId = messageId,
        name = name,
    )

    fun testStepFinishPart(
        id: String = "prt_stepfin123",
        sessionId: String = "ses_test123",
        messageId: String = "msg_test123",
        reason: String? = "stop",
        cost: Double = 0.003,
        tokens: TokenUsage = testTokenUsage(),
    ) = Part.StepFinish(
        id = id,
        sessionId = sessionId,
        messageId = messageId,
        reason = reason,
        cost = cost,
        tokens = tokens,
    )

    fun testSnapshotPart(
        id: String = "prt_snap123",
        sessionId: String = "ses_test123",
        messageId: String = "msg_test123",
        snapshotId: String = "snap_abc123",
        label: String = "before-refactor",
    ) = Part.Snapshot(
        id = id,
        sessionId = sessionId,
        messageId = messageId,
        snapshotId = snapshotId,
        label = label,
    )

    fun testPatchPart(
        id: String = "prt_patch123",
        sessionId: String = "ses_test123",
        messageId: String = "msg_test123",
        diffs: List<FileDiff> = listOf(testFileDiff()),
    ) = Part.Patch(
        id = id,
        sessionId = sessionId,
        messageId = messageId,
        diffs = diffs,
    )

    fun testAgentPart(
        id: String = "prt_agent123",
        sessionId: String = "ses_test123",
        messageId: String = "msg_test123",
        agent: String = "code",
        model: ModelRef = testModelRef(),
    ) = Part.Agent(
        id = id,
        sessionId = sessionId,
        messageId = messageId,
        agent = agent,
        model = model,
    )

    fun testRetryPart(
        id: String = "prt_retry123",
        sessionId: String = "ses_test123",
        messageId: String = "msg_test123",
        error: String = "Rate limit exceeded, retrying...",
    ) = Part.Retry(
        id = id,
        sessionId = sessionId,
        messageId = messageId,
        error = error,
    )

    fun testCompactionPart(
        id: String = "prt_compact123",
        sessionId: String = "ses_test123",
        messageId: String = "msg_test123",
        summary: String = "Previous context compressed for token efficiency",
    ) = Part.Compaction(
        id = id,
        sessionId = sessionId,
        messageId = messageId,
        summary = summary,
    )

    fun testPartTime(
        start: Long? = 1712000000000L,
        end: Long? = 1712000010000L,
    ) = PartTime(
        start = start,
        end = end,
    )

    fun testFileDiff(
        path: String = "src/main/kotlin/App.kt",
        additions: Int = 15,
        deletions: Int = 3,
        before: String = "old code",
        after: String = "new code",
    ) = FileDiff(
        path = path,
        additions = additions,
        deletions = deletions,
        before = before,
        after = after,
    )

    // === ToolState ===

    fun testToolState(
        status: String = "completed",
        input: JsonElement? = JsonObject(mapOf("command" to JsonPrimitive("ls -la"))),
        output: String = "total 32\ndrwxr-xr-x  5 user user 4096 Apr  1 10:00 .",
        title: String = "List files",
        error: String = "",
        metadata: JsonElement? = null,
        raw: JsonElement? = null,
    ) = ToolState(
        status = status,
        input = input,
        output = output,
        title = title,
        error = error,
        metadata = metadata,
        raw = raw,
    )

    // === ServerConnection ===

    fun testServerConnection(
        id: String = "server_local",
        name: String = "Local Dev Server",
        baseUrl: String = "http://192.168.1.100:4096",
        username: String = "",
        password: String = "",
        autoConnect: Boolean = true,
    ) = ServerConnection(
        id = id,
        name = name,
        baseUrl = baseUrl,
        username = username,
        password = password,
        autoConnect = autoConnect,
    )

    // === SSE Events ===

    fun testSseSessionCreated(
        session: Session = testSession(),
    ) = SseEvent.SessionCreated(session = session)

    fun testSseSessionUpdated(
        session: Session = testSession(),
    ) = SseEvent.SessionUpdated(session = session)

    fun testSseSessionDeleted(
        session: Session = testSession(),
    ) = SseEvent.SessionDeleted(session = session)

    fun testSseSessionStatusChanged(
        sessionId: String = "ses_test123",
        status: SessionStatus = SessionStatus.Busy,
    ) = SseEvent.SessionStatusChanged(
        sessionId = sessionId,
        status = status,
    )

    fun testSseSessionIdle(
        sessionId: String = "ses_test123",
    ) = SseEvent.SessionIdle(sessionId = sessionId)

    fun testSseSessionDiff(
        sessionId: String = "ses_test123",
        diffs: List<FileDiff> = listOf(testFileDiff()),
    ) = SseEvent.SessionDiff(
        sessionId = sessionId,
        diffs = diffs,
    )

    fun testSseSessionError(
        sessionId: String? = "ses_test123",
        error: ErrorInfo? = testErrorInfo(),
    ) = SseEvent.SessionError(
        sessionId = sessionId,
        error = error,
    )

    fun testSseMessageUpdated(
        message: Message = testMessage(),
    ) = SseEvent.MessageUpdated(message = message)

    fun testSseMessageRemoved(
        sessionId: String = "ses_test123",
        messageId: String = "msg_test123",
    ) = SseEvent.MessageRemoved(
        sessionId = sessionId,
        messageId = messageId,
    )

    fun testSseMessagePartUpdated(
        part: Part = testTextPart(),
    ) = SseEvent.MessagePartUpdated(part = part)

    fun testSseMessagePartDelta(
        sessionId: String = "ses_test123",
        messageId: String = "msg_test123",
        partId: String = "prt_text123",
        field: String = "text",
        delta: String = " more text",
    ) = SseEvent.MessagePartDelta(
        sessionId = sessionId,
        messageId = messageId,
        partId = partId,
        field = field,
        delta = delta,
    )

    fun testSseMessagePartRemoved(
        sessionId: String = "ses_test123",
        messageId: String = "msg_test123",
        partId: String = "prt_text123",
    ) = SseEvent.MessagePartRemoved(
        sessionId = sessionId,
        messageId = messageId,
        partId = partId,
    )

    fun testSsePermissionAsked(
        permission: PermissionRequest = testPermissionRequest(),
    ) = SseEvent.PermissionAsked(permission = permission)

    fun testSsePermissionReplied(
        sessionId: String = "ses_test123",
        requestId: String = "req_perm123",
    ) = SseEvent.PermissionReplied(
        sessionId = sessionId,
        requestId = requestId,
    )

    fun testSseQuestionAsked(
        question: QuestionRequest = testQuestionRequest(),
    ) = SseEvent.QuestionAsked(question = question)

    fun testSseQuestionReplied(
        sessionId: String = "ses_test123",
        requestId: String = "req_quest123",
    ) = SseEvent.QuestionReplied(
        sessionId = sessionId,
        requestId = requestId,
    )

    fun testSseQuestionRejected(
        sessionId: String = "ses_test123",
        requestId: String = "req_quest123",
    ) = SseEvent.QuestionRejected(
        sessionId = sessionId,
        requestId = requestId,
    )

    fun testSseTodoUpdated(
        sessionId: String = "ses_test123",
        todos: List<Todo> = listOf(testTodo()),
    ) = SseEvent.TodoUpdated(
        sessionId = sessionId,
        todos = todos,
    )

    fun testSseVcsBranchUpdated(
        branch: String = "feature/test-branch",
    ) = SseEvent.VcsBranchUpdated(branch = branch)

    fun testSseProjectUpdated(
        project: Project = testProject(),
    ) = SseEvent.ProjectUpdated(project = project)

    // === Permission ===

    fun testPermissionRequest(
        id: String = "req_perm123",
        sessionID: String = "ses_test123",
        permission: String = "bash",
        patterns: List<String> = listOf("rm -rf *"),
        metadata: Map<String, JsonElement> = emptyMap(),
        always: List<String> = emptyList(),
        tool: PermissionToolInfo = testPermissionToolInfo(),
    ) = PermissionRequest(
        id = id,
        sessionID = sessionID,
        permission = permission,
        patterns = patterns,
        metadata = metadata,
        always = always,
        tool = tool,
    )

    fun testPermissionToolInfo(
        messageID: String = "msg_test123",
        callID: String = "call_abc123",
    ) = PermissionToolInfo(
        messageID = messageID,
        callID = callID,
    )

    fun testPermissionReply(
        reply: String = "once",
        message: String? = null,
    ) = PermissionReply(
        reply = reply,
        message = message,
    )

    // === Question ===

    fun testQuestionRequest(
        id: String = "req_quest123",
        sessionID: String = "ses_test123",
        questions: List<QuestionInfo> = listOf(testQuestionInfo()),
        tool: QuestionToolRef = testQuestionToolRef(),
    ) = QuestionRequest(
        id = id,
        sessionID = sessionID,
        questions = questions,
        tool = tool,
    )

    fun testQuestionInfo(
        question: String = "Which framework would you like to use?",
        header: String = "Framework Selection",
        options: List<QuestionOption> = listOf(testQuestionOption()),
        multiple: Boolean = false,
        custom: Boolean = true,
    ) = QuestionInfo(
        question = question,
        header = header,
        options = options,
        multiple = multiple,
        custom = custom,
    )

    fun testQuestionOption(
        label: String = "React",
        description: String = "A JavaScript library for building UIs",
    ) = QuestionOption(
        label = label,
        description = description,
    )

    fun testQuestionToolRef(
        messageID: String = "msg_test123",
        callID: String = "call_abc123",
    ) = QuestionToolRef(
        messageID = messageID,
        callID = callID,
    )

    // === FileNode ===

    fun testFileNode(
        name: String = "src",
        path: String = "src",
        absolute: String = "/home/user/project/src",
        type: String = "directory",
        ignored: Boolean = false,
    ) = FileNode(
        name = name,
        path = path,
        absolute = absolute,
        type = type,
        ignored = ignored,
    )

    fun testFileNodeFile(
        name: String = "App.kt",
        path: String = "src/main/kotlin/App.kt",
        absolute: String = "/home/user/project/src/main/kotlin/App.kt",
        ignored: Boolean = false,
    ) = FileNode(
        name = name,
        path = path,
        absolute = absolute,
        type = "file",
        ignored = ignored,
    )

    // === Project ===

    fun testProject(
        id: String = "prj_test",
        worktree: String = "/home/user/project",
        vcs: String? = "git",
        name: String? = "opencode-android",
        icon: ProjectIcon? = null,
        commands: ProjectCommands? = null,
        time: ProjectTime = testProjectTime(),
        sandboxes: List<String> = emptyList(),
    ) = Project(
        id = id,
        worktree = worktree,
        vcs = vcs,
        name = name,
        icon = icon,
        commands = commands,
        time = time,
        sandboxes = sandboxes,
    )

    fun testProjectIcon(
        url: String? = "https://icon.example.com/project.png",
        override: String? = null,
        color: String? = "#FF5722",
    ) = ProjectIcon(
        url = url,
        override = override,
        color = color,
    )

    fun testProjectCommands(
        start: String? = "./gradlew run",
    ) = ProjectCommands(start = start)

    fun testProjectTime(
        created: Long = 1711900000000L,
        updated: Long = 1712000000000L,
        initialized: Long? = 1711900100000L,
    ) = ProjectTime(
        created = created,
        updated = updated,
        initialized = initialized,
    )

    // === ChatDraft ===

    fun testChatDraft(
        text: String = "Help me fix this bug",
        selectedAgent: String? = "code",
        selectedModel: ModelRef? = testModelRef(),
        selectedVariant: String? = "default",
        imageUris: List<String> = emptyList(),
        timestamp: Long = 1712000000000L,
    ) = ChatDraft(
        text = text,
        selectedAgent = selectedAgent,
        selectedModel = selectedModel,
        selectedVariant = selectedVariant,
        imageUris = imageUris,
        timestamp = timestamp,
    )

    // === Todo ===

    fun testTodo(
        id: String = "todo_1",
        content: String = "Implement feature X",
        status: String = "pending",
        priority: String = "medium",
    ) = Todo(
        id = id,
        content = content,
        status = status,
        priority = priority,
    )

    // === VcsInfo ===

    fun testVcsInfo(
        branch: String? = "main",
    ) = VcsInfo(branch = branch)

    // === AgentConfig ===

    fun testAgentConfig(
        name: String = "code",
        description: String = "Main coding agent",
        mode: String = "primary",
        native: Boolean = false,
        hidden: Boolean = false,
        model: ModelRef? = null,
        steps: Int? = null,
        color: String? = null,
    ) = AgentConfig(
        name = name,
        description = description,
        mode = mode,
        native = native,
        hidden = hidden,
        model = model,
        steps = steps,
        color = color,
    )

    // === Provider ===

    fun testProvider(
        id: String = "anthropic",
        name: String = "Anthropic",
        source: String = "builtin",
        env: List<String> = listOf("ANTHROPIC_API_KEY"),
        models: Map<String, Model> = mapOf("claude-3-sonnet" to testModel()),
    ) = Provider(
        id = id,
        name = name,
        source = source,
        env = env,
        models = models,
    )

    fun testProviderList(
        all: List<Provider> = listOf(testProvider()),
        default: Map<String, String> = mapOf("code" to "anthropic"),
        connected: List<String> = listOf("anthropic"),
    ) = ProviderList(
        all = all,
        default = default,
        connected = connected,
    )

    fun testModel(
        id: String = "claude-3-sonnet",
        name: String = "Claude 3 Sonnet",
        capabilities: ModelCapabilities = testModelCapabilities(),
        cost: ModelCost = testModelCost(),
        limit: ModelLimits = testModelLimits(),
    ) = Model(
        id = id,
        name = name,
        capabilities = capabilities,
        cost = cost,
        limit = limit,
    )

    fun testModelCapabilities(
        reasoning: Boolean = true,
        toolcall: Boolean = true,
        attachment: Boolean = true,
    ) = ModelCapabilities(
        reasoning = reasoning,
        toolcall = toolcall,
        attachment = attachment,
    )

    fun testModelCost(
        input: Double = 0.003,
        output: Double = 0.015,
    ) = ModelCost(input = input, output = output)

    fun testModelLimits(
        context: Long = 200000L,
        output: Long = 4096L,
    ) = ModelLimits(context = context, output = output)

    // === PtyInfo ===

    fun testPtyInfo(
        id: String = "pty_test123",
        title: String = "Build Terminal",
        command: String = "bash",
        args: List<String> = listOf("-c", "./gradlew build"),
        cwd: String = "/home/user/project",
        status: String = "running",
        pid: Long = 12345L,
    ) = PtyInfo(
        id = id,
        title = title,
        command = command,
        args = args,
        cwd = cwd,
        status = status,
        pid = pid,
    )

    fun testPtyCreateRequest(
        command: String? = "bash",
        args: List<String>? = null,
        cwd: String? = "/home/user/project",
        title: String? = "New Terminal",
        env: Map<String, String>? = null,
    ) = PtyCreateRequest(
        command = command,
        args = args,
        cwd = cwd,
        title = title,
        env = env,
    )

    fun testPtyUpdateRequest(
        title: String? = null,
        size: PtySize? = testPtySize(),
    ) = PtyUpdateRequest(
        title = title,
        size = size,
    )

    fun testPtySize(
        rows: Int = 24,
        cols: Int = 80,
    ) = PtySize(rows = rows, cols = cols)

    // === McpInfo ===

    fun testMcpStatus(
        status: String = "connected",
        error: String? = null,
    ) = McpStatus(status = status, error = error)

    fun testMcpServerCreateRequest(
        name: String = "filesystem",
        config: McpServerConfig = testMcpServerConfig(),
    ) = McpServerCreateRequest(
        name = name,
        config = config,
    )

    fun testMcpServerConfig(
        type: String = "local",
        command: List<String> = listOf("npx", "@modelcontextprotocol/server-filesystem"),
        environment: Map<String, String> = emptyMap(),
        enabled: Boolean = true,
        timeout: Long = 5000L,
        url: String = "",
        headers: Map<String, String> = emptyMap(),
        oauth: McpOAuthConfig? = null,
    ) = McpServerConfig(
        type = type,
        command = command,
        environment = environment,
        enabled = enabled,
        timeout = timeout,
        url = url,
        headers = headers,
        oauth = oauth,
    )

    fun testMcpOAuthConfig(
        clientId: String = "client_abc123",
        scope: String = "read write",
    ) = McpOAuthConfig(
        clientId = clientId,
        scope = scope,
    )

    fun testMcpAuthUrl(
        authorizationUrl: String = "https://auth.example.com/authorize?client_id=client_abc123",
    ) = McpAuthUrl(authorizationUrl = authorizationUrl)

    // === Experimental ===

    fun testWorkspaceCreateRequest(
        id: String? = null,
        type: String = "worktree",
        branch: String? = "feature/new-workspace",
        extra: JsonElement? = null,
    ) = WorkspaceCreateRequest(
        id = id,
        type = type,
        branch = branch,
        extra = extra,
    )

    fun testWorktreeCreateRequest(
        name: String? = "feature-branch",
        startCommand: String? = "./gradlew run",
    ) = WorktreeCreateRequest(
        name = name,
        startCommand = startCommand,
    )

    fun testWorktreeDeleteRequest(
        directory: String = "/home/user/project/.worktrees/feature-branch",
    ) = WorktreeDeleteRequest(directory = directory)

    fun testWorktreeResetRequest(
        directory: String = "/home/user/project/.worktrees/feature-branch",
    ) = WorktreeResetRequest(directory = directory)

    // === FileStatus ===

    fun testFileStatus(
        path: String = "src/main/kotlin/App.kt",
        added: Int = 10,
        removed: Int = 2,
        status: String = "modified",
    ) = FileStatus(
        path = path,
        added = added,
        removed = removed,
        status = status,
    )

    // === LspInfo ===

    fun testLspInfo(
        id: String = "kotlin",
        name: String = "Kotlin Language Server",
        root: String = "/home/user/project",
        status: String = "connected",
    ) = LspInfo(
        id = id,
        name = name,
        root = root,
        status = status,
    )

    // === LogEntry ===

    fun testLogEntry(
        service: String = "opencode",
        level: String = "info",
        message: String = "Session created successfully",
        extra: Map<String, JsonElement> = emptyMap(),
    ) = LogEntry(
        service = service,
        level = level,
        message = message,
        extra = extra,
    )

    // === PathInfo ===

    fun testPathInfo(
        home: String = "/home/user",
        state: String = "/home/user/.local/share/opencode",
        config: String = "/home/user/.config/opencode",
        worktree: String = "/home/user/project",
        directory: String = "/home/user/project",
    ) = PathInfo(
        home = home,
        state = state,
        config = config,
        worktree = worktree,
        directory = directory,
    )

    // === FormatterInfo ===

    fun testFormatterInfo(
        name: String = "ktfmt",
        extensions: List<String> = listOf(".kt", ".kts"),
        enabled: Boolean = true,
    ) = FormatterInfo(
        name = name,
        extensions = extensions,
        enabled = enabled,
    )

    // === CommandInfo ===

    fun testCommandInfo(
        name: String = "commit",
        description: String = "Create a git commit with AI-generated message",
        agent: String? = "code",
        source: String = "command",
        template: kotlinx.serialization.json.JsonElement = kotlinx.serialization.json.JsonPrimitive("Create a commit for: {{args}}"),
        hints: List<String> = listOf("args"),
    ) = CommandInfo(
        name = name,
        description = description,
        agent = agent,
        source = source,
        template = template,
        hints = hints,
    )

    // === SkillInfo ===

    fun testSkillInfo(
        name: String = "code-review",
        description: String = "Perform code review on changed files",
        location: String = ".opencode/skills/code-review/SKILL.md",
        content: String = "# Code Review Skill\n...",
    ) = SkillInfo(
        name = name,
        description = description,
        location = location,
        content = content,
    )

    // === SseEnvelope ===

    fun testSseEnvelope(
        directory: String = "/home/user/project",
        payload: SsePayload = testSsePayload(),
    ) = SseEnvelope(
        directory = directory,
        payload = payload,
    )

    fun testSsePayload(
        type: String = "session.created",
        properties: JsonObject? = JsonObject(emptyMap()),
    ) = SsePayload(
        type = type,
        properties = properties,
    )

    fun testInstanceSseEnvelope(
        type: String = "session.created",
        properties: JsonObject? = JsonObject(emptyMap()),
    ) = InstanceSseEnvelope(
        type = type,
        properties = properties,
    )

    // === SessionStatus (sealed class) ===
    // SessionStatus.Idle, SessionStatus.Busy, SessionStatus.Retry(attempt, message, next)

    // === ConfigProviders ===

    fun testConfigProviders(
        providers: List<Provider> = listOf(testProvider()),
        default: Map<String, String> = mapOf("code" to "anthropic"),
    ) = ConfigProviders(
        providers = providers,
        default = default,
    )
}
