package me.xiaok.opencode.e2e.steps

import android.util.Log
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import me.xiaok.opencode.e2e.utils.TestConfig
import me.xiaok.opencode.e2e.utils.captureStep
import me.xiaok.opencode.e2e.utils.waitForCondition

/**
 * Steps for image attachment and verification in E2E tests.
 *
 * Photo Picker interaction on Android 12 (SDK 31):
 * - Photo grid items are FrameLayout with content-desc="Photo taken on <date>"
 * - Tabs: "Photos" (selected) and "Albums" (clickable)
 * - Cancel button with content-desc="Cancel"
 */
class ImageSteps(
    private val device: UiDevice,
    private val config: TestConfig,
) {
    private val timeout: Long get() = config.timeout(10_000)

    /**
     * Click the "Attach image" button in the chat input bar.
     * This opens the system Photo Picker.
     */
    fun clickAttachImage() {
        // Strategy: Use resource-id to find the clickable Compose node,
        // capture its center coordinates, then click by coordinates.
        // We avoid keeping UiObject2 references across clicks to prevent StaleObjectException.
        val center = device.findObject(By.res("btn_attach_image"))?.visibleBounds?.let {
            Pair(it.centerX(), it.centerY())
        }

        if (center != null) {
            // Coordinate-based click avoids StaleObjectException
            device.click(center.first, center.second)
        } else {
            // Fallback: content-desc + clickable ancestor walk
            val attachBtn = device.findObject(By.desc("Attach image"))
            Log.d("ImageSteps", "attachBtn found: ${attachBtn != null}, bounds=${attachBtn?.visibleBounds}")
            if (attachBtn == null) {
                throw AssertionError("Attach image button not found in chat input bar")
            }

            val target = findClickableAncestor(attachBtn)
            Log.d("ImageSteps", "clickableAncestor: ${target != null}, bounds=${target?.visibleBounds}")
            if (target != null) {
                val b = target.visibleBounds
                device.click(b.centerX(), b.centerY())
            } else {
                attachBtn.click()
            }
        }

        device.waitForCondition("Photo Picker to appear", config.timeout(30_000)) {
            isPhotoPickerOpen()
        }
    }

    /**
     * Select the first (most recent) photo from the picker.
     * Handles both the Photo Picker (PickVisualMedia) and the system file picker (GetContent).
     */
    fun selectFirstPhoto() {
        assertPhotoPickerOpen()

        // Try Photo Picker first (content-desc "Photo taken on ...")
        val photoPickerPhotos = device.findObjects(By.clazz("android.widget.FrameLayout"))
            .filter {
                try {
                    val desc = it.contentDescription ?: ""
                    desc.startsWith("Photo taken on")
                } catch (_: Exception) { false }
            }
            .sortedBy { it.visibleBounds.top }

        if (photoPickerPhotos.isNotEmpty()) {
            val firstPhoto = photoPickerPhotos.first()
            val bounds = firstPhoto.visibleBounds
            device.click(bounds.centerX(), bounds.centerY())
        } else {
            // Fallback for GetContent picker: look for grid items (ImageViews) in the picker
            // and click the first one that looks like an image thumbnail
            selectFirstImageInSystemPicker()
        }

        // Wait for picker to close and return to app
        device.waitForCondition("Image picker to close after selection", timeout) {
            !isPhotoPickerOpen()
        }
        Thread.sleep(500)
    }

    /**
     * Cancel the Photo Picker without selecting anything.
     */
    fun cancelPhotoPicker() {
        val cancelBtn = device.findObject(By.desc("Cancel"))
            ?: throw AssertionError("Cancel button not found in Photo Picker")
        cancelBtn.click()

        device.waitForCondition("Photo Picker to close after cancel", timeout) {
            !isPhotoPickerOpen()
        }
    }

    /**
     * Verify that an attached image preview is visible in the input bar.
     * The preview is an ImageView with contentDescription="Attached image".
     */
    fun assertImagePreviewVisible() {
        device.waitForCondition("Attached image preview to appear", timeout) {
            findAttachedImagePreview() != null
        }
    }

    /**
     * Verify that NO attached image preview is visible in the input bar.
     */
    fun assertImagePreviewNotVisible() {
        device.waitForCondition("Attached image preview to disappear", timeout) {
            findAttachedImagePreview() == null
        }
    }

    /**
     * Remove the attached image preview from the input bar.
     * Clicks the "Remove" button (X overlay on the thumbnail).
     */
    fun removeAttachedImage() {
        val removeBtn = device.findObject(By.desc("Remove"))
            ?: throw AssertionError("Remove button not found on image preview")
        removeBtn.click()
        Thread.sleep(300)
    }

    /**
     * Verify that a sent image is rendered in the chat message area.
     *
     * After sending, the user's message bubble should contain an ImageView
     * (rendered by Coil's AsyncImage). We check for ImageView nodes in the
     * scrollable chat area (above the input bar).
     */
    fun assertImageInChatHistory() {
        val inputBarBottom = findInputBarTop()
        device.waitForCondition("Image to appear in chat history", config.timeout(15_000)) {
            val imageViews = device.findObjects(By.clazz("android.widget.ImageView"))
            imageViews.any { iv ->
                try {
                    val bounds = iv.visibleBounds
                    // Image must be above the input bar and have reasonable size
                    bounds.bottom < inputBarBottom &&
                        bounds.width() > 50 && bounds.height() > 50 &&
                        iv.contentDescription != "Attached image" &&
                        iv.contentDescription != "Attach image"
                } catch (_: Exception) { false }
            }
        }
    }

    // --- Internal helpers ---

    /**
     * Check if an image picker is currently in the foreground.
     * Handles both the Photo Picker (PickVisualMedia) and the DocumentsUI picker (GetContent).
     */
    private fun isPhotoPickerOpen(): Boolean {
        // Check both mCurrentFocus and mFocusedApp since PhotoPicker
        // may not steal focus from the test app in some Android versions.
        val output = device.executeShellCommand("dumpsys window windows")
        return output.contains("PhotoPicker") ||
            output.contains("ExternalStorageProvider") ||
            output.contains("DocumentsActivity") ||
            output.contains("ChooserActivity")
    }

    private fun assertPhotoPickerOpen() {
        if (!isPhotoPickerOpen()) {
            throw AssertionError("Photo Picker is not open")
        }
    }

    /**
     * Find the attached image preview node in the input bar.
     */
    private fun findAttachedImagePreview() = device.findObject(By.desc("Attached image"))

    /**
     * Find the Y coordinate of the top edge of the input bar area.
     * This separates the chat scroll area from the input controls.
     */
    private fun findInputBarTop(): Int {
        val editText = device.findObject(By.clazz("android.widget.EditText"))
        if (editText != null) {
            return editText.visibleBounds.top - 100
        }
        return (device.displayHeight * 0.75).toInt()
    }

    private fun findClickableAncestor(node: UiObject2): UiObject2? {
        var current = node.parent
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable && current.visibleBounds.width() > 0) return current
            current = current.parent
            depth++
        }
        return null
    }

    // Fallback for GetContent picker: find and click the first image thumbnail.
    private fun selectFirstImageInSystemPicker() {
        // Wait a moment for the picker UI to settle
        Thread.sleep(1000)

        // Strategy: look for clickable ImageView elements that are likely image thumbnails
        // The system picker shows images in a grid with ImageView widgets
        val imageViews = device.findObjects(By.clazz("android.widget.ImageView"))
            .filter { iv ->
                try {
                    val bounds = iv.visibleBounds
                    val desc = iv.contentDescription ?: ""
                    // Must be visible, have reasonable size for a thumbnail, and not be a UI icon
                    bounds.width() > 50 && bounds.height() > 50 &&
                        !desc.contains("Navigate") && !desc.contains("Back") &&
                        !desc.contains("Menu") && !desc.contains("Search") &&
                        !desc.contains("More") && !desc.contains("Cancel")
                } catch (_: Exception) { false }
            }

        if (imageViews.isEmpty()) {
            throw AssertionError("No images found in system picker")
        }

        // Click the first one (sorted by position - top-left first)
        val target = imageViews.sortedWith(compareBy({ it.visibleBounds.top }, { it.visibleBounds.left })).first()
        val bounds = target.visibleBounds
        device.click(bounds.centerX(), bounds.centerY())
    }
}
