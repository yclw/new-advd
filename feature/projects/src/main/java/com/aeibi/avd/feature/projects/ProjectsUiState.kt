package com.aeibi.avd.feature.projects

import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.model.ProjectIcon
import com.aeibi.avd.core.ui.ContentState
import com.aeibi.avd.core.ui.OperationState

data class ProjectsUiState(
    val content: ContentState<List<ProjectItem>> = ContentState.Loading,
    val operation: OperationState = OperationState.Idle
)

data class ProjectItem(
    val id: ProjectId,
    val name: String,
    val description: String,
    val icon: ProjectIcon?
)
