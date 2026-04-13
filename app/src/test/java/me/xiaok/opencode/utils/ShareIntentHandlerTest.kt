package me.xiaok.opencode.utils

import android.content.Intent
import android.net.Uri
import me.xiaok.opencode.utils.ShareIntentHandler.SharedContent
import me.xiaok.opencode.utils.TimeoutRule
import org.junit.Rule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareIntentHandlerTest {

    @get:Rule
    val timeoutRule = TimeoutRule()

    private lateinit var handler: ShareIntentHandler

    @Before
    fun setup() {
        handler = ShareIntentHandler()
    }

    @Test
    fun `parse ACTION_SEND with text`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, "Hello, world!")
            type = "text/plain"
        }

        val result = handler.parse(intent)

        assertNotNull(result)
        assertEquals("Hello, world!", result!!.text)
        assertTrue(result.imageUris.isEmpty())
    }

    @Test
    fun `parse ACTION_SEND with image uri`() {
        val uri = Uri.parse("content://media/images/42")
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "image/png"
        }

        val result = handler.parse(intent)

        assertNotNull(result)
        assertNull(result!!.text)
        assertEquals(listOf(uri), result.imageUris)
    }

    @Test
    fun `parse ACTION_SEND_MULTIPLE with images`() {
        val uri1 = Uri.parse("content://media/images/1")
        val uri2 = Uri.parse("content://media/images/2")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                arrayListOf(uri1, uri2),
            )
            type = "image/*"
        }

        val result = handler.parse(intent)

        assertNotNull(result)
        assertEquals(2, result!!.imageUris.size)
        assertEquals(uri1, result.imageUris[0])
        assertEquals(uri2, result.imageUris[1])
    }

    @Test
    fun `parse returns null for unknown action`() {
        val intent = Intent(Intent.ACTION_VIEW)

        val result = handler.parse(intent)

        assertNull(result)
    }

    @Test
    fun `isShareIntent returns true for ACTION_SEND`() {
        val intent = Intent(Intent.ACTION_SEND)

        assertTrue(handler.isShareIntent(intent))
    }

    @Test
    fun `isShareIntent returns true for ACTION_SEND_MULTIPLE`() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)

        assertTrue(handler.isShareIntent(intent))
    }

    @Test
    fun `isShareIntent returns false for other actions`() {
        val intent = Intent(Intent.ACTION_VIEW)

        assertFalse(handler.isShareIntent(intent))
    }
}
