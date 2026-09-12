package com.aeibi.avd.data.project.project

import com.aeibi.avd.core.common.AppError
import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.common.SnapshotId
import com.aeibi.avd.core.model.Project
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun observeProjects(): Flow<OperationResult<List<Project>>>
    suspend fun refresh(): OperationResult<Unit>
    suspend fun getProject(projectId: ProjectId): Project?
    suspend fun createDraft(
        name: String,
        description: String,
        icon: ProjectIconData?
    ): OperationResult<Project>
    suspend fun updateProfile(
        projectId: ProjectId,
        name: String,
        description: String,
        iconChange: ProjectIconDataChange
    ): OperationResult<Project>
    suspend fun loadIcon(projectId: ProjectId): OperationResult<ProjectIconData?>
    suspend fun delete(projectId: ProjectId): OperationResult<Unit>
    suspend fun prepareInitialization(
        projectId: ProjectId,
        content: InitialWorkspaceContent
    ): OperationResult<Unit>
    suspend fun publishInitialization(
        projectId: ProjectId,
        initialRevisionId: SnapshotId
    ): OperationResult<Project>
    suspend fun resolveInitializationFailure(
        projectId: ProjectId,
        error: AppError
    ): OperationResult<Unit>
    suspend fun recoverInitialization(projectId: ProjectId): OperationResult<Project?>
}

class ProjectIconData private constructor(private val bytes: ByteArray) {
    fun copyPngBytes(): ByteArray = bytes.copyOf()

    companion object {
        fun fromPng(bytes: ByteArray): ProjectIconData = ProjectIconData(bytes.copyOf())
    }
}

sealed interface ProjectIconDataChange {
    data object Keep : ProjectIconDataChange
    data object Remove : ProjectIconDataChange
    data class Replace(val icon: ProjectIconData) : ProjectIconDataChange
}
