package me.xiaok.opencode.di

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.xiaok.opencode.service.NotificationHelper
import me.xiaok.opencode.service.OpenCodeConnectionService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideNotificationManagerCompat(
        @ApplicationContext context: Context
    ): NotificationManagerCompat {
        return NotificationManagerCompat.from(context)
    }

    @Provides
    @Singleton
    fun providePowerManager(
        @ApplicationContext context: Context
    ): PowerManager {
        return context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    /**
     * Create an intent to start the foreground service with ACTION_CONNECT.
     */
    fun connectIntent(context: Context, serverId: String): Intent {
        return Intent(context, OpenCodeConnectionService::class.java).apply {
            action = NotificationHelper.ACTION_CONNECT
            putExtra(NotificationHelper.EXTRA_SERVER_ID, serverId)
        }
    }

    /**
     * Create an intent to send ACTION_DISCONNECT to the foreground service.
     */
    fun disconnectIntent(context: Context, serverId: String): Intent {
        return Intent(context, OpenCodeConnectionService::class.java).apply {
            action = NotificationHelper.ACTION_DISCONNECT
            putExtra(NotificationHelper.EXTRA_SERVER_ID, serverId)
        }
    }

    /**
     * Create an intent to trigger auto-connect for all saved servers.
     */
    fun autoConnectIntent(context: Context): Intent {
        return Intent(context, OpenCodeConnectionService::class.java).apply {
            action = NotificationHelper.ACTION_AUTO_CONNECT
        }
    }

    /**
     * Create an intent to stop the foreground service.
     */
    fun stopIntent(context: Context): Intent {
        return Intent(context, OpenCodeConnectionService::class.java).apply {
            action = NotificationHelper.ACTION_STOP
        }
    }
}
