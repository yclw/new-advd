package com.aeibi.avd.feature.projects.mapper

import com.aeibi.avd.core.common.AppError
import com.aeibi.avd.core.model.Project
import com.aeibi.avd.core.ui.UiError
import com.aeibi.avd.domain.project.ProjectIconContent
import com.aeibi.avd.feature.projects.ProjectIconPreview
import com.aeibi.avd.feature.projects.ProjectItem

internal fun Project.toProjectItem(icon: ProjectIconContent?): ProjectItem = ProjectItem(
    id = id,
    name = name,
    description = description,
    status = status,
    icon = icon?.let { ProjectIconPreview.fromPng(it.copyPngBytes()) }
)

internal fun AppError.toProjectsUiError(): UiError = UiError(
    messageKey = code.value,
    retryable = retryable
)
