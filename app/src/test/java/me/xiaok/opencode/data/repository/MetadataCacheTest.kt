package me.xiaok.opencode.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.fixtures.TestFixtures
import me.xiaok.opencode.utils.CoroutineTestRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MetadataCacheTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val api = mockk<OpenCodeApi>()
    private val server = TestFixtures.testServerConnection()
    private lateinit var cache: MetadataCache

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0

        cache = MetadataCache(api)
    }

    @After
    fun teardown() {
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `getProviders returns cached value on second call`() = runTest {
        val providers = TestFixtures.testProviderList()
        coEvery { api.getProviders(server) } returns providers

        val first = cache.getProviders(server.id, server)
        val second = cache.getProviders(server.id, server)

        assertEquals(providers, first)
        assertEquals(providers, second)
        coVerify(exactly = 1) { api.getProviders(server) }
    }

    @Test
    fun `refreshProviders bypasses cache and updates stored value`() = runTest {
        val firstProviders = TestFixtures.testProviderList()
        val refreshedProviders = TestFixtures.testProviderList(
            all = listOf(TestFixtures.testProvider(id = "openai", name = "OpenAI")),
            connected = listOf("openai"),
        )
        coEvery { api.getProviders(server) } returnsMany listOf(firstProviders, refreshedProviders)

        cache.getProviders(server.id, server)
        val refreshed = cache.refreshProviders(server.id, server)
        val cachedAgain = cache.getProviders(server.id, server)

        assertEquals(refreshedProviders, refreshed)
        assertEquals(refreshedProviders, cachedAgain)
        coVerify(exactly = 2) { api.getProviders(server) }
    }

    @Test
    fun `invalidateServer clears only one server cache`() = runTest {
        val otherServer = TestFixtures.testServerConnection(id = "server_2")
        val providers1 = TestFixtures.testProviderList()
        val providers2 = TestFixtures.testProviderList(
            all = listOf(TestFixtures.testProvider(id = "openai")),
            connected = listOf("openai"),
        )
        coEvery { api.getProviders(server) } returns providers1
        coEvery { api.getProviders(otherServer) } returns providers2

        cache.getProviders(server.id, server)
        cache.getProviders(otherServer.id, otherServer)

        cache.invalidateServer(server.id)

        cache.getProviders(server.id, server)
        cache.getProviders(otherServer.id, otherServer)

        coVerify(exactly = 2) { api.getProviders(server) }
        coVerify(exactly = 1) { api.getProviders(otherServer) }
    }

    @Test
    fun `invalidateAll clears providers agents and commands caches`() = runTest {
        val agents = listOf(TestFixtures.testAgentConfig())
        val commands = listOf(TestFixtures.testCommandInfo())
        coEvery { api.getProviders(server) } returns TestFixtures.testProviderList()
        coEvery { api.getAgents(server) } returns agents
        coEvery { api.getCommands(server) } returns commands

        cache.getProviders(server.id, server)
        cache.getAgents(server.id, server)
        cache.getCommands(server.id, server)

        cache.invalidateAll()

        cache.getProviders(server.id, server)
        cache.getAgents(server.id, server)
        cache.getCommands(server.id, server)

        coVerify(exactly = 2) { api.getProviders(server) }
        coVerify(exactly = 2) { api.getAgents(server) }
        coVerify(exactly = 2) { api.getCommands(server) }
    }
}
