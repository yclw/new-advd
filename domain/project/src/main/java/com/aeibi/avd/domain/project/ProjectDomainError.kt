package com.aeibi.avd.domain.project

import com.aeibi.avd.core.common.AppError
import com.aeibi.avd.core.common.ErrorCode

sealed class ProjectDomainError(override val code: ErrorCode, override val retryable: Boolean) :
    AppError {
    data object InvalidName : ProjectDomainError(ErrorCode("project_invalid_name"), false)
    data object NameAlreadyExists : ProjectDomainError(ErrorCode("project_name_exists"), false)
    data object ProjectNotFound : ProjectDomainError(ErrorCode("project_not_found"), false)
    data object StorageUnavailable : ProjectDomainError(
        ErrorCode("project_storage_unavailable"),
        true
    )
    data object InvalidState : ProjectDomainError(ErrorCode("project_invalid_state"), false)
    data object InvalidDescription : ProjectDomainError(
        ErrorCode("project_invalid_description"),
        false
    )
    data object InvalidIcon : ProjectDomainError(ErrorCode("project_icon_invalid"), false)
    data object IconTooLarge : ProjectDomainError(ErrorCode("project_icon_too_large"), false)
    data object RuntimeCloseFailed : ProjectDomainError(
        ErrorCode("project_runtime_close_failed"),
        true
    )
    data object InitializationFailed : ProjectDomainError(
        ErrorCode("project_initialization_failed"),
        true
    )
}
