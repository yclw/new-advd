package com.aeibi.avd.core.ui

data class UiError(val messageKey: String, val retryable: Boolean, val operationId: String? = null)

sealed interface ContentState<out T> {
    data object Loading : ContentState<Nothing>
    data class Content<T>(val value: T) : ContentState<T>
    data object Empty : ContentState<Nothing>
    data class Error(val error: UiError) : ContentState<Nothing>
}

sealed interface OperationState {
    data object Idle : OperationState
    data object InProgress : OperationState
    data class Failed(val error: UiError) : OperationState
    data class Succeeded(val operationId: String) : OperationState
}
