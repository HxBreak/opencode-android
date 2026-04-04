package me.xiaok.opencode.ui.screens.server

import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.fixtures.TestFixtures
import me.xiaok.opencode.utils.CoroutineTestRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerSettingsViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()
    private val testScope get() = coroutineRule.testScope

    private lateinit var serverRepository: ServerRepository

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0

        serverRepository = mockk(relaxed = true)
        every { serverRepository.servers } returns MutableStateFlow(emptyList())
    }

    @After
    fun teardown() { unmockkStatic(android.util.Log::class) }

    @Test
    fun `serverName resolves from servers flow`() {
        val server = TestFixtures.testServerConnection(id = "s1", name = "My Server")
        every { serverRepository.servers } returns MutableStateFlow(listOf(server))

        val vm = ServerSettingsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("serverId" to "s1")),
            serverRepository = serverRepository,
        )
        testScope.backgroundScope.launch { vm.serverName.collect {} }
        testScope.advanceUntilIdle()
        assertEquals("My Server", vm.serverName.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `constructor throws when serverId not in SavedStateHandle`() {
        ServerSettingsViewModel(
            savedStateHandle = SavedStateHandle(),
            serverRepository = serverRepository,
        )
    }
}
