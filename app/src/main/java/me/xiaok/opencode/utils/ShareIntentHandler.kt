package me.xiaok.opencode.utils

import android.content.Intent
import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShareIntentHandler @Inject constructor() {

    data class SharedContent(
        val text: String? = null,
        val imageUris: List<Uri> = emptyList(),
    )

    fun parse(intent: Intent): SharedContent? {
        return when (intent.action) {
            Intent.ACTION_SEND -> parseSend(intent)
            Intent.ACTION_SEND_MULTIPLE -> parseSendMultiple(intent)
            else -> null
        }
    }

    private fun parseSend(intent: Intent): SharedContent {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

        return SharedContent(
            text = text,
            imageUris = if (uri != null) listOf(uri) else emptyList(),
        )
    }

    private fun parseSendMultiple(intent: Intent): SharedContent {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            ?: emptyList()

        return SharedContent(
            text = text,
            imageUris = uris,
        )
    }

    fun isShareIntent(intent: Intent): Boolean {
        return intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE
    }
}
