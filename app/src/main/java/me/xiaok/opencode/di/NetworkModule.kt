package me.xiaok.opencode.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import me.xiaok.opencode.data.api.WsClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        classDiscriminator = "type"
    }

    /**
     * Main OkHttpClient shared by Ktor (REST API) and WebSocket.
     * Has normal timeouts suitable for request/response calls.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d("OkHttp", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * Dedicated OkHttpClient for SSE connections.
     * Isolated from the main client's connection pool so the persistent SSE
     * connection doesn't compete with REST API calls (POST /session/.../message).
     *
     * SSE keeps a long-lived HTTP connection open indefinitely, which can
     * exhaust OkHttp's connection pool per-host limit and cause POST requests
     * to hang waiting for an available connection.
     */
    @Provides
    @Singleton
    @Named("sse")
    fun provideSseOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MINUTES) // SSE: no read timeout
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideWsClient(okHttpClient: OkHttpClient): WsClient {
        return WsClient(okHttpClient)
    }

    @Provides
    @Singleton
    fun provideKtorClient(json: Json, okHttpClient: OkHttpClient): HttpClient {
        return HttpClient(OkHttp) {
            engine {
                preconfigured = okHttpClient
            }
            expectSuccess = true
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 30_000
            }
        }
    }
}
