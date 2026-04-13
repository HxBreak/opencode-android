package me.xiaok.opencode.ui.screens.chat.usecases

import me.xiaok.opencode.domain.model.*
import me.xiaok.opencode.utils.TimeoutRule
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SessionStatsUseCaseTest {

    @get:Rule
    val timeoutRule = TimeoutRule()

    private lateinit var useCase: SessionStatsUseCase

    @Before
    fun setUp() {
        useCase = SessionStatsUseCase()
    }

    @Test
    fun `computeSessionStats with normal messages returns correct stats`() {
        val messages = listOf(
            Message(info = MessageInfo(role = "user", id = "msg1")),
            Message(
                info = MessageInfo(
                    role = "assistant",
                    id = "msg2",
                    cost = 0.05,
                    tokens = TokenUsage(
                        total = 1000,
                        input = 600,
                        output = 200,
                        reasoning = 100,
                        cache = CacheInfo(read = 50, write = 50),
                    ),
                    providerID = "provider1",
                    modelID = "model1",
                ),
            ),
            Message(info = MessageInfo(role = "user", id = "msg3")),
            Message(
                info = MessageInfo(
                    role = "assistant",
                    id = "msg4",
                    cost = 0.03,
                    tokens = TokenUsage(
                        total = 500,
                        input = 300,
                        output = 100,
                        reasoning = 50,
                        cache = CacheInfo(read = 25, write = 25),
                    ),
                    providerID = "provider1",
                    modelID = "model1",
                ),
            ),
        )
        val providers = listOf(
            Provider(
                id = "provider1",
                models = mapOf(
                    "model1" to Model(limit = ModelLimits(context = 2000)),
                ),
            ),
        )
        val selectedModel = ModelRef(providerID = "provider1", modelID = "model1")

        val stats = useCase.computeSessionStats(messages, providers, selectedModel)

        assertEquals(2, stats.conversationTurns)
        assertEquals(0.08, stats.totalCost, 0.001)
        // last assistant tokens: 300 + 100 + 50 + 25 + 25 = 500
        assertEquals(500L, stats.totalTokens)
        // contextUsagePercent = (500 / 2000) * 100 = 25
        assertEquals(25, stats.contextUsagePercent)
    }

    @Test
    fun `computeSessionStats with empty messages returns zeros`() {
        val messages = emptyList<Message>()
        val providers = listOf(
            Provider(
                id = "provider1",
                models = mapOf("model1" to Model(limit = ModelLimits(context = 2000))),
            ),
        )
        val selectedModel = ModelRef(providerID = "provider1", modelID = "model1")

        val stats = useCase.computeSessionStats(messages, providers, selectedModel)

        assertEquals(0, stats.conversationTurns)
        assertEquals(0L, stats.totalTokens)
        assertEquals(0.0, stats.totalCost, 0.001)
        assertEquals(0, stats.contextUsagePercent)
    }

    @Test
    fun `computeSessionStats computes context usage percent from selected model`() {
        val messages = listOf(
            Message(info = MessageInfo(role = "user", id = "msg1")),
            Message(
                info = MessageInfo(
                    role = "assistant",
                    id = "msg2",
                    tokens = TokenUsage(
                        total = 500,
                        input = 300,
                        output = 100,
                        reasoning = 50,
                        cache = CacheInfo(read = 25, write = 25),
                    ),
                ),
            ),
        )
        val providers = listOf(
            Provider(
                id = "provider1",
                models = mapOf("model1" to Model(limit = ModelLimits(context = 1000))),
            ),
        )
        val selectedModel = ModelRef(providerID = "provider1", modelID = "model1")

        val stats = useCase.computeSessionStats(messages, providers, selectedModel)

        // 500 / 1000 * 100 = 50
        assertEquals(50, stats.contextUsagePercent)
    }

    @Test
    fun `computeSessionStats falls back to last assistant message model for context limit`() {
        val messages = listOf(
            Message(info = MessageInfo(role = "user", id = "msg1")),
            Message(
                info = MessageInfo(
                    role = "assistant",
                    id = "msg2",
                    tokens = TokenUsage(
                        total = 500,
                        input = 300,
                        output = 100,
                        reasoning = 50,
                        cache = CacheInfo(read = 25, write = 25),
                    ),
                    providerID = "provider1",
                    modelID = "model1",
                ),
            ),
        )
        val providers = listOf(
            Provider(
                id = "provider1",
                models = mapOf("model1" to Model(limit = ModelLimits(context = 5000))),
            ),
        )
        val selectedModel: ModelRef? = null

        val stats = useCase.computeSessionStats(messages, providers, selectedModel)

        // 500 / 5000 * 100 = 10
        assertEquals(10, stats.contextUsagePercent)
    }

    @Test
    fun `computeSessionStats with cache tokens included in total`() {
        val messages = listOf(
            Message(info = MessageInfo(role = "user", id = "msg1")),
            Message(
                info = MessageInfo(
                    role = "assistant",
                    id = "msg2",
                    cost = 0.1,
                    tokens = TokenUsage(
                        total = 2000,
                        input = 800,
                        output = 400,
                        reasoning = 200,
                        cache = CacheInfo(read = 300, write = 300),
                    ),
                ),
            ),
        )
        val providers = listOf(
            Provider(
                id = "provider1",
                models = mapOf("model1" to Model(limit = ModelLimits(context = 10000))),
            ),
        )
        val selectedModel = ModelRef(providerID = "provider1", modelID = "model1")

        val stats = useCase.computeSessionStats(messages, providers, selectedModel)

        // total = 800 + 400 + 200 + 300 + 300 = 2000
        assertEquals(2000L, stats.totalTokens)
        // 2000 / 10000 * 100 = 20
        assertEquals(20, stats.contextUsagePercent)
    }

    @Test
    fun `computeSessionStats context usage capped at 100 percent`() {
        val messages = listOf(
            Message(info = MessageInfo(role = "user", id = "msg1")),
            Message(
                info = MessageInfo(
                    role = "assistant",
                    id = "msg2",
                    tokens = TokenUsage(
                        total = 2000,
                        input = 1000,
                        output = 500,
                        reasoning = 200,
                        cache = CacheInfo(read = 150, write = 150),
                    ),
                ),
            ),
        )
        val providers = listOf(
            Provider(
                id = "provider1",
                models = mapOf("model1" to Model(limit = ModelLimits(context = 500))),
            ),
        )
        val selectedModel = ModelRef(providerID = "provider1", modelID = "model1")

        val stats = useCase.computeSessionStats(messages, providers, selectedModel)

        // 2000 / 500 * 100 = 400, but capped at 100
        assertEquals(100, stats.contextUsagePercent)
    }

    @Test
    fun `computeSessionStats with no matching provider returns zero context usage`() {
        val messages = listOf(
            Message(info = MessageInfo(role = "user", id = "msg1")),
            Message(
                info = MessageInfo(
                    role = "assistant",
                    id = "msg2",
                    tokens = TokenUsage(
                        total = 500,
                        input = 300,
                        output = 100,
                        reasoning = 50,
                        cache = CacheInfo(read = 25, write = 25),
                    ),
                ),
            ),
        )
        val providers = emptyList<Provider>()
        val selectedModel = ModelRef(providerID = "unknown", modelID = "unknown")

        val stats = useCase.computeSessionStats(messages, providers, selectedModel)

        assertEquals(0, stats.contextUsagePercent)
        assertEquals(500L, stats.totalTokens)
    }

    @Test
    fun `computeSessionStats ignores messages without tokens`() {
        val messages = listOf(
            Message(info = MessageInfo(role = "user", id = "msg1")),
            Message(info = MessageInfo(role = "assistant", id = "msg2")),
            Message(info = MessageInfo(role = "user", id = "msg3")),
            Message(
                info = MessageInfo(
                    role = "assistant",
                    id = "msg4",
                    cost = 0.02,
                    tokens = TokenUsage(
                        total = 100,
                        input = 60,
                        output = 20,
                        reasoning = 10,
                        cache = CacheInfo(read = 5, write = 5),
                    ),
                ),
            ),
        )
        val providers = emptyList<Provider>()
        val selectedModel: ModelRef? = null

        val stats = useCase.computeSessionStats(messages, providers, selectedModel)

        assertEquals(2, stats.conversationTurns)
        assertEquals(100L, stats.totalTokens)
        assertEquals(0.02, stats.totalCost, 0.001)
    }
}
