package me.xiaok.opencode.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.data.repository.SettingsRepository
import me.xiaok.opencode.domain.model.ServerConnection
import me.xiaok.opencode.domain.model.SessionStatus
import javax.inject.Inject

/**
 * Foreground service that manages SSE connections to OpenCode servers.
 *
 * Responsibilities:
 * - Keeps SSE connections alive in the background (ForegroundService + WakeLock)
 * - Shows persistent notification with connection status (InboxStyle)
 * - Posts event notifications: session idle, permission asked, question asked, session error
 * - Watchdog: re-posts notification every 60s to survive system kills
 * - Stops self when no active connections remain
 *
 * Lifecycle:
 * 1. HomeViewModel.connect() → startForegroundService(ACTION_CONNECT)
 * 2. Service.onCreate() → post foreground notification, start observers
 * 3. Service.onStartCommand() → serverRepository.connect/disconnect
 * 4. StateFlow observers → update notifications on state changes
 * 5. Service.onDestroy() → disconnect all, release WakeLock
 */
@AndroidEntryPoint
class OpenCodeConnectionService : Service() {

    @Inject lateinit var serverRepository: ServerRepository
    @Inject lateinit var eventReducer: EventReducer
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var notificationManager: NotificationManagerCompat
    @Inject lateinit var powerManager: PowerManager
    @Inject lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    private var wakeLock: PowerManager.WakeLock? = null
    private var connectionObserverJob: Job? = null
    private var eventObserverJob: Job? = null
    private var watchdogRunnable: Runnable? = null

    // Track which events we've already notified about (avoid duplicates)
    private val notifiedIdleSessions = mutableSetOf<String>()
    private val notifiedPermissions = mutableSetOf<String>()
    private val notifiedQuestions = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        // Post foreground notification immediately (Android requirement: within 5s)
        val initialNotification = notificationHelper.buildForegroundNotification(
            activeServers = emptySet(),
            serverNames = emptyMap()
        )
        startForeground(
            NotificationHelper.FOREGROUND_NOTIFICATION_ID,
            initialNotification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        startStateObservers()
        startWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        intent?.action?.let { action ->
            val serverId = intent.getStringExtra(NotificationHelper.EXTRA_SERVER_ID)

            when (action) {
                NotificationHelper.ACTION_CONNECT -> {
                    serverId?.let { id ->
                        serviceScope.launch { serverRepository.connect(id) }
                    }
                }

                NotificationHelper.ACTION_DISCONNECT -> {
                    serverId?.let { id ->
                        serverRepository.disconnect(id)
                        checkAndStopIfEmpty()
                    }
                }

                NotificationHelper.ACTION_AUTO_CONNECT -> {
                    serviceScope.launch { serverRepository.autoConnect() }
                }

                NotificationHelper.ACTION_STOP -> {
                    stopSelf()
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")

        stopWatchdog()
        connectionObserverJob?.cancel()
        eventObserverJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()

        serverRepository.disconnectAll()

        notifiedIdleSessions.clear()
        notifiedPermissions.clear()
        notifiedQuestions.clear()
    }

    // === State Observers ===

    private fun startStateObservers() {
        // Observe connection states → update foreground notification + manage WakeLock
        connectionObserverJob = serviceScope.launch {
            combine(
                serverRepository.connectionStates,
                serverRepository.servers,
            ) { connectionStates, servers ->
                ConnectionStateSnapshot(connectionStates, servers)
            }.collect { snapshot ->
                val connectedIds = snapshot.connectionStates
                    .filter { it.value == ServerRepository.ConnectionState.CONNECTED }
                    .keys

                val serverNames = snapshot.servers.associate { it.id to it.name }

                val notification = notificationHelper.buildForegroundNotification(
                    activeServers = connectedIds,
                    serverNames = serverNames
                )
                notificationManager.notify(
                    NotificationHelper.FOREGROUND_NOTIFICATION_ID,
                    notification
                )

                if (connectedIds.isNotEmpty()) {
                    acquireWakeLock()
                } else {
                    releaseWakeLock()
                    checkAndStopIfEmpty()
                }
            }
        }

        // Observe event notifications (session idle, permission, question)
        eventObserverJob = serviceScope.launch {
            settingsRepository.notificationsEnabled.collectLatest { enabled ->
                if (!enabled) return@collectLatest

                launch { observeSessionStatuses() }
                launch { observePermissions() }
                launch { observeQuestions() }
                launch { observeSessionErrors() }
            }
        }
    }

    /**
     * Observe session status changes → post notification when session goes IDLE.
     */
    private suspend fun observeSessionStatuses() {
        eventReducer.sessionStatuses.collect { statuses ->
            statuses.forEach { (sessionId, status) ->
                if (status == SessionStatus.IDLE && sessionId !in notifiedIdleSessions) {
                    notifiedIdleSessions.add(sessionId)

                    val session = eventReducer.sessions.value[sessionId] ?: return@forEach
                    val serverId = findServerForSession(sessionId) ?: return@forEach

                    val notification = notificationHelper.buildSessionIdleNotification(
                        serverId = serverId,
                        sessionId = sessionId,
                        sessionTitle = session.title.ifEmpty { "Session" }
                    )
                    val id = notificationHelper.getNotificationId(serverId, "idle_$sessionId")
                    notificationManager.notify(id, notification)
                }
            }

            // Clean up tracking for sessions that are no longer idle
            val idleSessions = statuses.filterValues { it == SessionStatus.IDLE }.keys
            notifiedIdleSessions.retainAll(idleSessions)
        }
    }

    /**
     * Observe permission requests → post notification when a new permission is asked.
     */
    private suspend fun observePermissions() {
        eventReducer.permissions.collect { permissionsMap ->
            permissionsMap.forEach { (sessionId, permissions) ->
                permissions.forEach { permission ->
                    if (permission.id !in notifiedPermissions) {
                        notifiedPermissions.add(permission.id)

                        val serverId = findServerForSession(sessionId) ?: return@forEach

                        val notification = notificationHelper.buildPermissionNotification(
                            serverId = serverId,
                            sessionId = sessionId,
                            permissionName = permission.permission.ifEmpty { "Permission required" }
                        )
                        val id = notificationHelper.getNotificationId(
                            serverId, "permission_${permission.id}"
                        )
                        notificationManager.notify(id, notification)
                    }
                }
            }

            val currentIds = permissionsMap.values.flatten().map { it.id }.toSet()
            notifiedPermissions.retainAll(currentIds)
        }
    }

    /**
     * Observe question requests → post notification when a new question is asked.
     */
    private suspend fun observeQuestions() {
        eventReducer.questions.collect { questionsMap ->
            questionsMap.forEach { (sessionId, questions) ->
                questions.forEach { question ->
                    if (question.id !in notifiedQuestions) {
                        notifiedQuestions.add(question.id)

                        val serverId = findServerForSession(sessionId) ?: return@forEach

                        val notification = notificationHelper.buildQuestionNotification(
                            serverId = serverId,
                            sessionId = sessionId,
                            questionText = question.questions.firstOrNull()?.question ?: "Question asked"
                        )
                        val id = notificationHelper.getNotificationId(
                            serverId, "question_${question.id}"
                        )
                        notificationManager.notify(id, notification)
                    }
                }
            }

            val currentIds = questionsMap.values.flatten().map { it.id }.toSet()
            notifiedQuestions.retainAll(currentIds)
        }
    }

    // === WakeLock Management ===

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$TAG:SSEConnection"
            ).apply { acquire() }
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }

    // === Watchdog ===

    private fun startWatchdog() {
        watchdogRunnable = object : Runnable {
            override fun run() {
                rePostForegroundNotification()
                handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
            }
        }
        handler.postDelayed(watchdogRunnable!!, WATCHDOG_INTERVAL_MS)
    }

    private fun stopWatchdog() {
        watchdogRunnable?.let { handler.removeCallbacks(it) }
        watchdogRunnable = null
    }

    /**
     * Observe session errors → post error notifications.
     */
    private suspend fun observeSessionErrors() {
        eventReducer.sessionErrors.collect { errors ->
            errors.forEach { (sessionId, error) ->
                val serverId = findServerForSession(sessionId) ?: return@forEach
                val notification = notificationHelper.buildErrorNotification(
                    serverId = serverId,
                    sessionId = sessionId,
                    errorMessage = error
                )
                val id = notificationHelper.getNotificationId(serverId, "error_$sessionId")
                notificationManager.notify(id, notification)
            }
        }
    }

    private fun rePostForegroundNotification() {
        serviceScope.launch {
            val connectedIds = serverRepository.connectionStates.value
                .filter { it.value == ServerRepository.ConnectionState.CONNECTED }
                .keys
            val serverNames = serverRepository.servers.value.associate { it.id to it.name }

            val notification = notificationHelper.buildForegroundNotification(
                activeServers = connectedIds,
                serverNames = serverNames
            )
            notificationManager.notify(
                NotificationHelper.FOREGROUND_NOTIFICATION_ID,
                notification
            )
        }
    }

    // === Helpers ===

    private fun findServerForSession(sessionId: String): String? {
        return eventReducer.serverSessions.value.entries
            .firstOrNull { sessionId in it.value }
            ?.key
    }

    private fun checkAndStopIfEmpty() {
        serviceScope.launch {
            delay(2000)
            val activeServers = eventReducer.activeServers.value
            val connecting = serverRepository.connectionStates.value.values
                .any { it == ServerRepository.ConnectionState.CONNECTING }

            if (activeServers.isEmpty() && !connecting) {
                Log.d(TAG, "No active connections, stopping service")
                stopSelf()
            }
        }
    }

    private data class ConnectionStateSnapshot(
        val connectionStates: Map<String, ServerRepository.ConnectionState>,
        val servers: List<ServerConnection>,
    )

    companion object {
        private const val TAG = "OpenCodeConnection"
        private const val WATCHDOG_INTERVAL_MS = 60_000L
    }
}
