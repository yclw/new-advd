package com.aeibi.avd.feature.projects

import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.ui.UiMessage

sealed interface ProjectsEffect {
    data class NavigateToProject(val projectId: ProjectId) : ProjectsEffect
    data class NavigateToProjectSetup(val projectId: ProjectId, val retry: Boolean) : ProjectsEffect
    data class NavigateToInitializationProgress(val projectId: ProjectId) : ProjectsEffect
    data class ShowMessage(val message: UiMessage) : ProjectsEffect
}
