package me.xiaok.opencode.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import me.xiaok.opencode.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val channels = listOf(
            NotificationChannel(
                CHANNEL_CONNECTION,
                "Connection Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Connection state changes"
                enableLights(false)
                enableVibration(false)
            },
            NotificationChannel(
                CHANNEL_TASKS,
                "Task Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Session idle, task complete"
                enableLights(true)
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_TASKS_SILENT,
                "Silent Tasks",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Non-urgent task updates"
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            },
            NotificationChannel(
                CHANNEL_PERMISSIONS,
                "Permission Requests",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Permission and question requests"
                enableLights(true)
                enableVibration(true)
            }
        )
        channels.forEach { notificationManager.createNotificationChannel(it) }
    }

    // === Foreground Notification ===

    fun buildForegroundNotification(
        activeServers: Set<String>,
        serverNames: Map<String, String>
    ): Notification {
        val title = if (activeServers.isNotEmpty()) {
            "OpenCode - ${activeServers.size} server${if (activeServers.size != 1) "s" else ""} connected"
        } else {
            "OpenCode"
        }

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)

        if (activeServers.isNotEmpty()) {
            activeServers.forEach { serverId ->
                val name = serverNames[serverId] ?: serverId
                inboxStyle.addLine(name)
            }
        } else {
            inboxStyle.addLine("Monitoring connections...")
        }

        val stopIntent = Intent(context, OpenCodeConnectionService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_CONNECTION)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(
                if (activeServers.isNotEmpty()) {
                    activeServers.map { serverNames[it] ?: it }.joinToString(", ")
                } else {
                    "Monitoring connections..."
                }
            )
            .setStyle(inboxStyle)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    // === Event Notification Builders ===

    fun buildSessionIdleNotification(
        serverId: String,
        sessionId: String,
        sessionTitle: String
    ): Notification {
        val deepLink = "opencode://session/$serverId/$sessionId"
        val notificationId = getNotificationId(serverId, "idle_$sessionId")
        return NotificationCompat.Builder(context, CHANNEL_TASKS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Session Idle")
            .setContentText(sessionTitle)
            .setAutoCancel(true)
            .setContentIntent(createDeepLinkPendingIntent(deepLink, notificationId))
            .build()
    }

    fun buildPermissionNotification(
        serverId: String,
        sessionId: String,
        permissionName: String
    ): Notification {
        val deepLink = "opencode://session/$serverId/$sessionId"
        val notificationId = getNotificationId(serverId, "permission_$sessionId")
        return NotificationCompat.Builder(context, CHANNEL_PERMISSIONS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Permission Request")
            .setContentText(permissionName)
            .setAutoCancel(true)
            .setContentIntent(createDeepLinkPendingIntent(deepLink, notificationId))
            .build()
    }

    fun buildQuestionNotification(
        serverId: String,
        sessionId: String,
        questionText: String
    ): Notification {
        val deepLink = "opencode://session/$serverId/$sessionId"
        val notificationId = getNotificationId(serverId, "question_$sessionId")
        val truncatedText = if (questionText.length > 50) {
            questionText.take(50) + "..."
        } else {
            questionText
        }
        return NotificationCompat.Builder(context, CHANNEL_PERMISSIONS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Question")
            .setContentText(truncatedText)
            .setAutoCancel(true)
            .setContentIntent(createDeepLinkPendingIntent(deepLink, notificationId))
            .build()
    }

    fun buildErrorNotification(
        serverId: String,
        sessionId: String?,
        errorMessage: String
    ): Notification {
        val deepLink = if (sessionId != null) {
            "opencode://session/$serverId/$sessionId"
        } else {
            "opencode://sessions/$serverId"
        }
        val notificationId = getNotificationId(serverId, "error${sessionId?.let { "_$it" } ?: ""}")
        return NotificationCompat.Builder(context, CHANNEL_TASKS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Session Error")
            .setContentText(errorMessage)
            .setAutoCancel(true)
            .setContentIntent(createDeepLinkPendingIntent(deepLink, notificationId))
            .build()
    }

    // === Deep Link PendingIntent ===

    fun createDeepLinkPendingIntent(uri: String, notificationId: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            setPackage(context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // === Notification ID Management ===

    fun getNotificationId(serverId: String, type: String): Int {
        return ("$serverId$type").hashCode().let { if (it < 0) -it else it }
    }

    fun getForegroundNotificationId(): Int = FOREGROUND_NOTIFICATION_ID

    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 1001
        const val CHANNEL_CONNECTION = "connection"
        const val CHANNEL_TASKS = "tasks"
        const val CHANNEL_TASKS_SILENT = "tasks_silent"
        const val CHANNEL_PERMISSIONS = "permissions"

        // Service action constants
        const val ACTION_CONNECT = "me.xiaok.opencode.action.CONNECT"
        const val ACTION_DISCONNECT = "me.xiaok.opencode.action.DISCONNECT"
        const val ACTION_AUTO_CONNECT = "me.xiaok.opencode.action.AUTO_CONNECT"
        const val ACTION_STOP = "me.xiaok.opencode.action.STOP"
        const val EXTRA_SERVER_ID = "server_id"
    }
}
