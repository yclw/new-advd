package com.aeibi.avd.feature.projects

import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.domain.project.ProjectIconChange
import com.aeibi.avd.domain.project.ProjectIconUpload

sealed interface ProjectsAction {
    data class CreateConfirmed(
        val name: String,
        val description: String,
        val icon: ProjectIconUpload?
    ) : ProjectsAction
    data class UpdateConfirmed(
        val projectId: ProjectId,
        val name: String,
        val description: String,
        val iconChange: ProjectIconChange
    ) : ProjectsAction
    data class DeleteConfirmed(val projectId: ProjectId) : ProjectsAction
    data object RetryListLoad : ProjectsAction
    data class SelectProject(val projectId: ProjectId) : ProjectsAction
    data object AcknowledgeOperation : ProjectsAction
}
