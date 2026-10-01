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
    data object InvalidIcon : ProjectDataError(ErrorCode("project_icon_invalid"), false)
}
