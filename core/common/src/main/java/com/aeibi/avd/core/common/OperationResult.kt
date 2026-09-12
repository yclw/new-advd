package com.aeibi.avd.core.common

sealed interface OperationResult<out T> {
    data class Success<T>(val value: T) : OperationResult<T>
    data class Failure(val error: AppError) : OperationResult<Nothing>
}

interface AppError {
    val code: ErrorCode
    val retryable: Boolean
}
