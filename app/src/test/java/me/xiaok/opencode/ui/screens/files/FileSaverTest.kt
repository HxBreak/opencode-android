package me.xiaok.opencode.ui.screens.files

import android.content.ContentResolver
import android.net.Uri
import io.mockk.*
import me.xiaok.opencode.domain.model.FileContent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileSaverTest {

    private val contentResolver = mockk<ContentResolver>(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun teardown() {
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `decodeBytes from text content returns UTF-8 bytes`() {
        val fc = FileContent(type = "text", content = "Hello World")
        val bytes = FileSaver.decodeBytes(fc)
        assertArrayEquals("Hello World".toByteArray(Charsets.UTF_8), bytes)
    }

    @Test
    fun `decodeBytes from base64 content decodes correctly`() {
        val fc = FileContent(
            type = "binary",
            content = "SGVsbG8gV29ybGQ=", // "Hello World" base64
            encoding = "base64",
        )
        val bytes = FileSaver.decodeBytes(fc)
        assertArrayEquals("Hello World".toByteArray(Charsets.UTF_8), bytes)
    }

    @Test
    fun `writeToUri writes bytes to content resolver`() {
        val uri = mockk<Uri>()
        val outputStream = ByteArrayOutputStream()
        every { contentResolver.openOutputStream(uri, "wt") } returns outputStream

        val bytes = "test data".toByteArray()
        FileSaver.writeToUri(contentResolver, uri, bytes)

        assertArrayEquals(bytes, outputStream.toByteArray())
        verify { contentResolver.openOutputStream(uri, "wt") }
    }

    @Test
    fun `extractFileName from path returns last segment`() {
        assertEquals("App.kt", FileSaver.extractFileName("src/main/App.kt"))
        assertEquals("readme.md", FileSaver.extractFileName("readme.md"))
    }

    @Test
    fun `guessExtension from mimeType returns correct extension`() {
        assertEquals("png", FileSaver.guessExtension("image/png"))
        assertEquals("jpeg", FileSaver.guessExtension("image/jpeg"))
        assertEquals("pdf", FileSaver.guessExtension("application/pdf"))
        assertNull(FileSaver.guessExtension(null))
    }
}
