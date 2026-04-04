package me.xiaok.opencode.ui.screens.diff

import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import me.xiaok.opencode.utils.CoroutineTestRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiffViewerViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()
    private val testScope get() = coroutineRule.testScope

    private lateinit var vm: DiffViewerViewModel

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun teardown() { unmockkStatic(android.util.Log::class) }

    private fun createVm(savedStateHandle: SavedStateHandle = SavedStateHandle()): DiffViewerViewModel {
        return DiffViewerViewModel(savedStateHandle)
    }

    private fun collectAndAdvance(viewModel: DiffViewerViewModel) {
        testScope.backgroundScope.launch { viewModel.uiState.collect {} }
        testScope.advanceUntilIdle()
    }

    @Test
    fun `uiState reads diffText from SavedStateHandle`() {
        val diff = "--- a/file.kt\n+++ b/file.kt\n@@ -1,3 +1,5 @@"
        vm = createVm(SavedStateHandle(mapOf("diffText" to diff)))
        collectAndAdvance(vm)
        assertEquals(diff, vm.uiState.value.diffText)
    }

    @Test
    fun `uiState reads title from SavedStateHandle`() {
        vm = createVm(SavedStateHandle(mapOf("title" to "src/Main.kt")))
        collectAndAdvance(vm)
        assertEquals("src/Main.kt", vm.uiState.value.title)
    }

    @Test
    fun `uiState defaults when no SavedStateHandle values`() {
        vm = createVm(SavedStateHandle())
        collectAndAdvance(vm)
        assertEquals("", vm.uiState.value.diffText)
        assertNull(vm.uiState.value.title)
    }

    @Test
    fun `uiState has empty defaults`() {
        val state = DiffViewerUiState()
        assertEquals("", state.diffText)
        assertNull(state.title)
    }
}
