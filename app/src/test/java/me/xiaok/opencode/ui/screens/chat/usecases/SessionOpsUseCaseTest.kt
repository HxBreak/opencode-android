package me.xiaok.opencode.ui.screens.chat.usecases

import io.mockk.*
import kotlinx.coroutines.test.runTest
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.CacheRepository
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.domain.model.TestFixtures
import me.xiaok.opencode.utils.ErrorCollector
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SessionOpsUseCaseTest {

    private val testServer = TestFixtures.testServerConnection()
    private lateinit var api: OpenCodeApi
    private lateinit var eventReducer: EventReducer
    private lateinit var serverRepository: ServerRepository
    private lateinit var errorCollector: ErrorCollector
    private lateinit var useCase: SessionOpsUseCase

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0

        val cacheRepository = mockk<CacheRepository>(relaxed = true)
        eventReducer = EventReducer(cacheRepository, kotlinx.coroutines.test.TestScope())
        api = mockk(relaxed = true)
        serverRepository = mockk(relaxed = true)
        errorCollector = mockk(relaxed = true)
        useCase = SessionOpsUseCase(api, eventReducer, serverRepository, errorCollector)
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    // ====================================================================
    // summarizeSession
    // ====================================================================

    @Test(expected = IllegalStateException::class)
    fun `summarizeSession throws when server not found`() = runTest {
        every { serverRepository.getServer("missing") } returns null

        useCase.summarizeSession(
            serverId = "missing",
            sessionId = "session1",
            selectedModel = null,
            sessionDirectory = null,
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `summarizeSession throws when API returns false`() = runTest {
        every { serverRepository.getServer(any()) } returns testServer
        coEvery { api.summarizeSession(any(), any()) } returns false

        useCase.summarizeSession(
            serverId = testServer.id,
            sessionId = "session1",
            selectedModel = null,
            sessionDirectory = null,
        )
    }

    @Test
    fun `summarizeSession succeeds when API returns true`() = runTest {
        every { serverRepository.getServer(any()) } returns testServer
        coEvery { api.summarizeSession(any(), any()) } returns true

        useCase.summarizeSession(
            serverId = testServer.id,
            sessionId = "session1",
            selectedModel = null,
            sessionDirectory = null,
        )

        coVerify { api.summarizeSession(any(), "session1") }
    }
}
