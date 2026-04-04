package me.xiaok.opencode

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import me.xiaok.opencode.ui.navigation.OpenCodeNavGraph
import me.xiaok.opencode.ui.theme.OpencodeandroidTheme
import me.xiaok.opencode.utils.ErrorCollector
import me.xiaok.opencode.utils.LocalErrorCollector
import me.xiaok.opencode.utils.ShareIntentHandler
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"

        @Volatile
        var pendingShareContent: ShareIntentHandler.SharedContent? = null
            private set
    }

    @Inject lateinit var shareIntentHandler: ShareIntentHandler
    @Inject lateinit var errorCollector: ErrorCollector

    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) {
            handleShareIntent(intent)
        }

        setContent {
            OpencodeandroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    remember { this.navController = navController }
                    CompositionLocalProvider(
                        LocalErrorCollector provides errorCollector,
                    ) {
                        OpenCodeNavGraph(navController = navController)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navController?.handleDeepLink(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        if (!shareIntentHandler.isShareIntent(intent)) return

        val content = shareIntentHandler.parse(intent) ?: return
        Log.d(TAG, "Share intent: text=${content.text != null}, images=${content.imageUris.size}")

        pendingShareContent = content
    }

    fun consumeShareContent(): ShareIntentHandler.SharedContent? {
        val content = pendingShareContent
        pendingShareContent = null
        return content
    }
}
