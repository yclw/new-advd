package com.aeibi.avd.data.project.project

import com.aeibi.avd.core.common.AppError
import com.aeibi.avd.core.common.ErrorCode

sealed class ProjectDataError(override val code: ErrorCode, override val retryable: Boolean) :
    AppError {
    data object NameAlreadyExists : ProjectDataError(ErrorCode("project_name_exists"), false)
    data object ProjectNotFound : ProjectDataError(ErrorCode("project_not_found"), false)
    data object StorageUnavailable : ProjectDataError(
        ErrorCode("project_storage_unavailable"),
        true
    )
    data object InvalidState : ProjectDataError(ErrorCode("project_invalid_state"), false)
    data object InvalidIcon : ProjectDataError(ErrorCode("project_icon_invalid"), false)
    data object IconTooLarge : ProjectDataError(ErrorCode("project_icon_too_large"), false)
    data object InitializationInvalid : ProjectDataError(
        ErrorCode("project_initialization_invalid"),
        false
    )
    data object InitializationRecoveryRequired : ProjectDataError(
        ErrorCode("project_initialization_recovery_required"),
        true
    )
}
