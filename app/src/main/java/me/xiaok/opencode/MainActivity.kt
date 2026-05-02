package me.xiaok.opencode

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.domain.model.ServerConnection
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

        const val EXTRA_SERVER_ID = "serverId"
        const val EXTRA_SERVER_NAME = "serverName"
        const val EXTRA_SERVER_URL = "serverUrl"
        const val EXTRA_SERVER_USERNAME = "serverUsername"
        const val EXTRA_SERVER_PASSWORD = "serverPassword"

        @Volatile
        var pendingShareContent: ShareIntentHandler.SharedContent? = null
            private set
    }

    @Inject lateinit var shareIntentHandler: ShareIntentHandler
    @Inject lateinit var errorCollector: ErrorCollector
    @Inject lateinit var serverRepository: ServerRepository

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
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
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

        if (handleAddServerDeeplink(intent)) return
        ensureServerFromUri(intent)

        navController?.handleDeepLink(intent)
        handleShareIntent(intent)
    }

    private fun handleAddServerDeeplink(intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_VIEW) return false
        val data = intent.data ?: return false
        if (data.scheme != "opencode" || data.host != "addServer") return false

        val serverId = intent.getStringExtra(EXTRA_SERVER_ID) ?: return false
        val name = intent.getStringExtra(EXTRA_SERVER_NAME) ?: return false
        val url = intent.getStringExtra(EXTRA_SERVER_URL) ?: return false

        val server = ServerConnection(
            id = serverId,
            name = name,
            baseUrl = url,
            username = intent.getStringExtra(EXTRA_SERVER_USERNAME) ?: "",
            password = intent.getStringExtra(EXTRA_SERVER_PASSWORD) ?: "",
            autoConnect = false,
        )

        Log.d(TAG, "addServer deeplink: id=$serverId name=$name url=$url")
        lifecycleScope.launch {
            serverRepository.addServer(server)
            serverRepository.connect(serverId)
        }
        return true
    }

    /**
     * Extract server credentials from URI query params (e.g. `opencode://session/...?serverName=X&serverUrl=Y`).
     * If the server doesn't exist locally, add and connect it before navigation proceeds.
     */
    private fun ensureServerFromUri(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val data = intent.data ?: return
        if (data.scheme != "opencode") return

        val serverId = data.pathSegments.firstOrNull() ?: return
        val serverName = data.getQueryParameter("serverName") ?: return
        val serverUrl = data.getQueryParameter("serverUrl") ?: return

        if (serverRepository.getServer(serverId) != null) return

        val server = ServerConnection(
            id = serverId,
            name = serverName,
            baseUrl = serverUrl,
            username = data.getQueryParameter("username") ?: "",
            password = data.getQueryParameter("password") ?: "",
            autoConnect = false,
        )

        Log.d(TAG, "ensureServerFromUri: adding server id=$serverId name=$serverName")
        lifecycleScope.launch {
            serverRepository.addServer(server)
            serverRepository.connect(serverId)
        }
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
