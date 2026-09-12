package com.aeibi.avd.data.project.version

import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.common.SnapshotId
import com.aeibi.avd.core.model.VersionCreator
import com.aeibi.avd.core.model.VersionSnapshot
import com.aeibi.avd.core.model.VersionSnapshotType
import kotlinx.coroutines.flow.Flow

interface VersionRepository {
    fun observeSnapshots(projectId: ProjectId): Flow<OperationResult<List<VersionSnapshot>>>

    suspend fun createInitialRevision(projectId: ProjectId): OperationResult<VersionSnapshot>

    suspend fun createSnapshot(
        projectId: ProjectId,
        creator: VersionCreator,
        type: VersionSnapshotType
    ): OperationResult<VersionSnapshot>

    suspend fun restore(
        projectId: ProjectId,
        snapshotId: SnapshotId,
        creator: VersionCreator
    ): OperationResult<VersionSnapshot>
}
