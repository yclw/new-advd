package com.aeibi.avd.feature.projects

import androidx.annotation.StringRes
import com.aeibi.avd.core.ui.UiError

@StringRes
internal fun projectsErrorResource(error: UiError): Int = when (error.messageKey) {
    "project_invalid_name" -> R.string.projects_error_invalid_name
    "project_invalid_description" -> R.string.projects_error_invalid_description
    "project_name_exists" -> R.string.projects_error_name_exists
    "project_not_found" -> R.string.projects_error_not_found
    "project_invalid_state" -> R.string.projects_error_invalid_state
    "project_icon_invalid", "project_icon_too_large", "project_icon_preparation_failed" ->
        R.string.projects_icon_error
    "project_initialization_failed" -> R.string.projects_error_initialization
    "project_runtime_close_failed" -> R.string.projects_error_runtime_close
    else -> R.string.projects_error_storage
}
