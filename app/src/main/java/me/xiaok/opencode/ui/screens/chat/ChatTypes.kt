package me.xiaok.opencode.ui.screens.chat

import android.net.Uri

/**
 * Represents an image attached to a draft message.
 * The [base64] data is compressed WebP ready for inline embedding.
 */
data class AttachedImage(
    val uri: Uri,
    val base64: String,
    val mimeType: String,
)
