package com.aeibi.avd.data.project.workspace

import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.ProjectId

interface WorkspaceRepository {
    suspend fun read(projectId: ProjectId, relativePath: String): OperationResult<WorkspaceFile?>
    suspend fun list(
        projectId: ProjectId,
        directory: String = ""
    ): OperationResult<List<WorkspaceFile>>

    suspend fun search(
        projectId: ProjectId,
        query: String
    ): OperationResult<List<WorkspaceSearchMatch>>

    suspend fun edit(projectId: ProjectId, edit: WorkspaceEdit): OperationResult<Unit>
}

data class WorkspaceFile(val relativePath: String, val content: String)

data class WorkspaceSearchMatch(val relativePath: String, val line: Int, val preview: String)

data class WorkspaceEdit(val relativePath: String, val content: String)
