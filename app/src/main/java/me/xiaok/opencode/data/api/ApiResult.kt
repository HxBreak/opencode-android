package me.xiaok.opencode.data.api

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(
        val kind: ErrorKind,
        val message: String,
        val retryable: Boolean,
    ) : ApiResult<Nothing>()
    data object Loading : ApiResult<Nothing>()
}

enum class ErrorKind {
    NETWORK, AUTH, CLIENT, SERVER, TIMEOUT
}
