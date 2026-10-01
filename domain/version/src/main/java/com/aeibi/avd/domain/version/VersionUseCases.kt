package com.aeibi.avd.domain.version

import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.common.SnapshotId
import com.aeibi.avd.core.model.VersionCreator
import com.aeibi.avd.core.model.VersionSnapshot
import com.aeibi.avd.core.model.VersionSnapshotType
import com.aeibi.avd.data.project.version.VersionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetInitialVersionStateUseCase @Inject constructor(
    private val versionRepository: VersionRepository
) {
    suspend operator fun invoke(projectId: ProjectId): OperationResult<InitialVersionAvailability> =
        when (val result = versionRepository.initialVersionState(projectId)) {
            is OperationResult.Failure -> result
            is OperationResult.Success -> OperationResult.Success(
                InitialVersionAvailability(
                    result.value.recorded,
                    result.value.hasUnrecordedChanges
                )
            )
        }
}

data class InitialVersionAvailability(val recorded: Boolean, val hasUnrecordedChanges: Boolean)

class RecordInitialVersionUseCase @Inject constructor(
    private val versionRepository: VersionRepository
) {
    suspend operator fun invoke(projectId: ProjectId): OperationResult<VersionSnapshot> =
        versionRepository.createInitialRevision(projectId)
}

class ObserveSnapshotsUseCase @Inject constructor(
    private val versionRepository: VersionRepository
) {
    operator fun invoke(projectId: ProjectId): Flow<OperationResult<List<VersionSnapshot>>> =
        versionRepository.observeSnapshots(projectId)
}

class CreateSnapshotUseCase @Inject constructor(private val versionRepository: VersionRepository) {
    suspend operator fun invoke(request: CreateSnapshotRequest): OperationResult<VersionSnapshot> =
        versionRepository.createSnapshot(request.projectId, request.creator, request.type)
}

data class CreateSnapshotRequest(
    val projectId: ProjectId,
    val creator: VersionCreator,
    val type: VersionSnapshotType
)

class RestoreSnapshotUseCase @Inject constructor(private val versionRepository: VersionRepository) {
    suspend operator fun invoke(request: RestoreSnapshotRequest): OperationResult<VersionSnapshot> =
        versionRepository.restore(request.projectId, request.snapshotId, request.creator)
}

data class RestoreSnapshotRequest(
    val projectId: ProjectId,
    val snapshotId: SnapshotId,
    val creator: VersionCreator
)
