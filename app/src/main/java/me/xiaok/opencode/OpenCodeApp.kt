package me.xiaok.opencode

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import me.xiaok.opencode.data.repository.ErrorLogRepository
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject

@HiltAndroidApp
class OpenCodeApp : Application() {

    @Inject lateinit var errorLogRepository: ErrorLogRepository

    private val defaultHandler by lazy { Thread.getDefaultUncaughtExceptionHandler() }

    override fun onCreate() {
        super.onCreate()
        installUncaughtExceptionHandler()
    }

    private fun installUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Log to our database
            val stackTrace = throwable.stackTraceToString()
            Log.e("OpenCodeApp", "Uncaught exception on ${thread.name}", throwable)

            // Use a synchronous write because the process is about to die
            try {
                errorLogRepository.logError(
                    message = throwable.message ?: throwable.javaClass.simpleName,
                    screen = "Uncaught (${thread.name})",
                    stackTrace = stackTrace,
                )
            } catch (_: Exception) {
                // Database write failed — nothing we can do
            }

            // Pass to the original handler (crash dialog, etc.)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun Throwable.stackTraceToString(): String {
        val sw = StringWriter()
        PrintWriter(sw).use { pw ->
            printStackTrace(pw)
            // Include cause chain
            var cause = cause
            while (cause != null) {
                pw.println()
                pw.print("Caused by: ")
                cause.printStackTrace(pw)
                cause = cause.cause
            }
        }
        return sw.toString()
    }
}
