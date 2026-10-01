package com.aeibi.avd.data.project.project

import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.model.Project
import com.aeibi.avd.core.model.ProjectIcon
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun observeProjects(): Flow<OperationResult<List<Project>>>
    suspend fun refresh(): OperationResult<Unit>
    suspend fun getProject(projectId: ProjectId): Project?
    suspend fun createProject(
        name: String,
        description: String,
        icon: ProjectIcon?
    ): OperationResult<Project>
    suspend fun updateProfile(
        projectId: ProjectId,
        name: String,
        description: String,
        iconChange: ProjectIconDataChange
    ): OperationResult<Project>
    suspend fun delete(projectId: ProjectId): OperationResult<Unit>
}

sealed interface ProjectIconDataChange {
    data object Keep : ProjectIconDataChange
    data object Remove : ProjectIconDataChange
    data class Replace(val icon: ProjectIcon) : ProjectIconDataChange
}
