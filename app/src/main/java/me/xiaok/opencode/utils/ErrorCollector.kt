package me.xiaok.opencode.utils

import me.xiaok.opencode.data.repository.ErrorLogRepository
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility for logging errors from ViewModels and other components.
 * Stores errors in the local database for viewing in the Error Log screen.
 */
@Singleton
class ErrorCollector @Inject constructor(
    private val errorLogRepository: ErrorLogRepository,
) {
    fun logError(throwable: Throwable, screen: String = "") {
        errorLogRepository.logError(
            message = throwable.message ?: throwable.javaClass.simpleName,
            screen = screen,
            stackTrace = throwable.stackTraceToString(),
        )
    }

    fun logError(message: String, screen: String = "", stackTrace: String = "") {
        errorLogRepository.logError(
            message = message,
            screen = screen,
            stackTrace = stackTrace,
        )
    }

    private fun Throwable.stackTraceToString(): String {
        val sw = StringWriter()
        PrintWriter(sw).use { pw ->
            printStackTrace(pw)
            var cause = this.cause
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
