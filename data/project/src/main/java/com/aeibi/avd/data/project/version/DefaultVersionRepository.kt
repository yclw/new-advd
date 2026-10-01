package com.aeibi.avd.data.project.version

import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.common.SnapshotId
import com.aeibi.avd.core.filesystem.ControlledFileSystem
import com.aeibi.avd.core.filesystem.FileSystemResult
import com.aeibi.avd.core.git.ControlledGit
import com.aeibi.avd.core.git.GitCommit
import com.aeibi.avd.core.git.GitCommitRequest
import com.aeibi.avd.core.git.GitError
import com.aeibi.avd.core.git.GitRepositoryLocation
import com.aeibi.avd.core.git.GitResult
import com.aeibi.avd.core.git.GitRevision
import com.aeibi.avd.core.git.GitSignature
import com.aeibi.avd.core.git.GitTrailer
import com.aeibi.avd.core.model.VersionCreator
import com.aeibi.avd.core.model.VersionSnapshot
import com.aeibi.avd.core.model.VersionSnapshotType
import com.aeibi.avd.data.project.ProjectMutationLease
import com.aeibi.avd.data.project.ProjectStorageLayout
import java.nio.file.Paths
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

@Singleton
internal class DefaultVersionRepository @Inject constructor(
    private val fileSystem: ControlledFileSystem,
    private val git: ControlledGit,
    private val repositoryLocator: ProjectGitRepositoryLocator,
    private val mutationLease: ProjectMutationLease
) : VersionRepository {
    private val changes = MutableSharedFlow<ProjectId>(extraBufferCapacity = 1)

    override fun observeSnapshots(
        projectId: ProjectId
    ): Flow<OperationResult<List<VersionSnapshot>>> = changes
        .filter { it == projectId }
        .onStart { emit(projectId) }
        .map { loadSnapshots(projectId) }

    override suspend fun initialVersionState(projectId: ProjectId): OperationResult<InitialVersionState> =
        mutationLease.withLease(projectId) {
            workspaceUnavailable(projectId)?.let { return@withLease it }
            val recorded = when (val exists = fileSystem.exists(ProjectStorageLayout.gitDirectory(projectId))) {
                is FileSystemResult.Failure -> return@withLease failure(VersionDataError.OperationFailed)
                is FileSystemResult.Success -> exists.value
            }
            if (recorded) {
                when (val history = loadSnapshotsLocked(projectId)) {
                    is OperationResult.Failure -> return@withLease history
                    is OperationResult.Success -> Unit
                }
                return@withLease when (val status = git.status(repositoryLocator.locate(projectId))) {
                    is GitResult.Failure -> failure(status.error.toVersionError())
                    is GitResult.Success -> success(
                        InitialVersionState(true, status.value.hasChanges)
                    )
                }
            }
            success(InitialVersionState(false))
        }

    override suspend fun createInitialRevision(
        projectId: ProjectId
    ): OperationResult<VersionSnapshot> = mutate(projectId) {
        workspaceUnavailable(projectId)?.let { return@mutate it }
        val repository = repositoryLocator.locate(projectId)
        when (val exists = fileSystem.exists(ProjectStorageLayout.gitDirectory(projectId))) {
            is FileSystemResult.Failure -> return@mutate failure(VersionDataError.OperationFailed)
            is FileSystemResult.Success -> if (exists.value) {
                return@mutate existingInitialRevision(projectId, repository)
            }
        }
        if (fileSystem.deleteDirectory(
                ProjectStorageLayout.stagingProjectDirectory(projectId)
            ) is FileSystemResult.Failure
        ) {
            return@mutate failure(VersionDataError.OperationFailed)
        }
        val operationId = UUID.randomUUID().toString()
        val stagingGit = ProjectStorageLayout.stagingPayloadGitDirectory(projectId, operationId)
        val stagingProject = ProjectStorageLayout.stagingProjectDirectory(projectId)
        val gitPath = Paths.get(repository.gitDirectoryPath)
        val stagedRepository = repository.copy(
            gitDirectoryPath = gitPath.parent.parent.resolve(".staging")
                .resolve(projectId.value).resolve(operationId).resolve("git").toString()
        )
        val created = when (val initialized = git.initialize(stagedRepository)) {
            is GitResult.Failure -> failure(initialized.error.toVersionError())
            is GitResult.Success -> createCommit(
                projectId, stagedRepository, VersionCreator.SYSTEM,
                VersionSnapshotType.INITIALIZATION, null
            )
        }
        if (created is OperationResult.Failure) {
            fileSystem.deleteDirectory(stagingProject)
            return@mutate created
        }
        if (fileSystem.moveDirectoryAtomically(
                stagingGit, ProjectStorageLayout.gitDirectory(projectId)
            ) is FileSystemResult.Failure
        ) {
            fileSystem.deleteDirectory(stagingProject)
            return@mutate failure(VersionDataError.OperationFailed)
        }
        fileSystem.deleteDirectory(stagingProject)
        created
    }

    override suspend fun createSnapshot(
        projectId: ProjectId,
        creator: VersionCreator,
        type: VersionSnapshotType
    ): OperationResult<VersionSnapshot> = mutate(projectId) {
        if (type != VersionSnapshotType.MANUAL && type != VersionSnapshotType.REPAIR) {
            return@mutate failure(VersionDataError.InvalidSnapshotType)
        }
        createCommit(
            projectId = projectId,
            repository = repositoryLocator.locate(projectId),
            creator = creator,
            type = type,
            restoredFromSnapshotId = null
        )
    }

    override suspend fun restore(
        projectId: ProjectId,
        snapshotId: SnapshotId,
        creator: VersionCreator
    ): OperationResult<VersionSnapshot> = mutate(projectId) {
        val repository = repositoryLocator.locate(projectId)
        val snapshots = when (val result = loadSnapshotsLocked(projectId)) {
            is OperationResult.Failure -> return@mutate result
            is OperationResult.Success -> result.value
        }
        val target = snapshots.firstOrNull { it.id == snapshotId }
            ?: return@mutate failure(VersionDataError.SnapshotNotFound)
        when (val status = git.status(repository)) {
            is GitResult.Failure -> return@mutate failure(status.error.toVersionError())
            is GitResult.Success -> if (status.value.hasChanges) {
                when (
                    val preRestore = createCommit(
                        projectId = projectId,
                        repository = repository,
                        creator = creator,
                        type = VersionSnapshotType.PRE_RESTORE,
                        restoredFromSnapshotId = null
                    )
                ) {
                    is OperationResult.Failure -> return@mutate preRestore
                    is OperationResult.Success -> Unit
                }
            }
        }
        when (val restore = git.restoreWorkTree(repository, revision(snapshotId))) {
            is GitResult.Failure -> failure(restore.error.toVersionError())
            is GitResult.Success -> createCommit(
                projectId = projectId,
                repository = repository,
                creator = creator,
                type = VersionSnapshotType.RESTORATION,
                restoredFromSnapshotId = target.id
            )
        }
    }

    private suspend fun workspaceUnavailable(projectId: ProjectId): OperationResult.Failure? =
        when (val exists = fileSystem.exists(ProjectStorageLayout.workspaceDirectory(projectId))) {
            is FileSystemResult.Failure -> OperationResult.Failure(VersionDataError.OperationFailed)
            is FileSystemResult.Success -> if (exists.value) null else
                OperationResult.Failure(VersionDataError.RepositoryUnavailable)
        }

    private suspend fun existingInitialRevision(
        projectId: ProjectId,
        repository: GitRepositoryLocation
    ): OperationResult<VersionSnapshot> = when (val history = git.readHistory(repository)) {
        is GitResult.Failure -> failure(history.error.toVersionError())
        is GitResult.Success -> if (history.value.isEmpty()) {
            failure(VersionDataError.HistoryCorrupted)
        } else {
            when (val snapshots = snapshots(projectId, history.value)) {
                is OperationResult.Failure -> snapshots
                is OperationResult.Success -> snapshots.value.singleOrNull {
                    it.type == VersionSnapshotType.INITIALIZATION &&
                        it.creator == VersionCreator.SYSTEM
                }?.let(::success)
                    ?: failure(VersionDataError.HistoryCorrupted)
            }
        }
    }

    private suspend fun createCommit(
        projectId: ProjectId,
        repository: GitRepositoryLocation,
        creator: VersionCreator,
        type: VersionSnapshotType,
        restoredFromSnapshotId: SnapshotId?
    ): OperationResult<VersionSnapshot> {
        val now = System.currentTimeMillis()
        val trailers = buildList {
            add(GitTrailer(TRAILER_SCHEMA, SCHEMA_VERSION.toString()))
            add(GitTrailer(TRAILER_TYPE, type.name))
            add(GitTrailer(TRAILER_CREATOR, creator.name))
            add(GitTrailer(TRAILER_OPERATION_ID, UUID.randomUUID().toString()))
            restoredFromSnapshotId?.let { add(GitTrailer(TRAILER_RESTORED_FROM, it.value)) }
        }
        val request = GitCommitRequest(
            subject = " ",
            trailers = trailers,
            signature = GitSignature(
                name = "AVD",
                email = "versions@local",
                timestampEpochSeconds = now / MILLIS_PER_SECOND
            )
        )
        return when (val committed = git.commitAll(repository, request)) {
            is GitResult.Failure -> failure(committed.error.toVersionError())
            is GitResult.Success -> snapshot(projectId, repository, committed.value)
        }
    }

    private suspend fun snapshot(
        projectId: ProjectId,
        repository: GitRepositoryLocation,
        revision: GitRevision
    ): OperationResult<VersionSnapshot> = when (val history = git.readHistory(repository)) {
        is GitResult.Failure -> failure(history.error.toVersionError())
        is GitResult.Success -> snapshots(projectId, history.value).flatMap { values ->
            values.firstOrNull { it.id.value == revision.value }?.let(::success)
                ?: failure(VersionDataError.HistoryCorrupted)
        }
    }

    private suspend fun loadSnapshots(
        projectId: ProjectId
    ): OperationResult<List<VersionSnapshot>> =
        mutationLease.withLease(projectId) { loadSnapshotsLocked(projectId) }

    private suspend fun loadSnapshotsLocked(
        projectId: ProjectId
    ): OperationResult<List<VersionSnapshot>> {
        val repository = repositoryLocator.locate(projectId)
        return when (val history = git.readHistory(repository)) {
            is GitResult.Failure -> failure(history.error.toVersionError())
            is GitResult.Success -> snapshots(projectId, history.value)
        }
    }

    private fun snapshots(
        projectId: ProjectId,
        history: List<GitCommit>
    ): OperationResult<List<VersionSnapshot>> {
        if (history.any { commit ->
                commit.trailers.any { trailer ->
                    trailer.token == TRAILER_SCHEMA && trailer.value != SCHEMA_VERSION.toString()
                }
            }
        ) {
            return failure(VersionDataError.HistoryMetadataUnsupported)
        }
        if (history.anyIndexed { index, commit ->
                val expectedParentCount = if (index == history.lastIndex) 0 else 1
                commit.parentRevisions.size != expectedParentCount ||
                    (
                        index < history.lastIndex &&
                            commit.parentRevisions.singleOrNull() != history[index + 1].revision
                        )
            }
        ) {
            return failure(VersionDataError.HistoryCorrupted)
        }
        val values = history.map { commit -> snapshot(projectId, commit) }
        if (values.any { it == null }) return failure(VersionDataError.HistoryCorrupted)
        val snapshots = values.filterNotNull()
        if (snapshots.count { it.type == VersionSnapshotType.INITIALIZATION } != 1 ||
            snapshots.lastOrNull()?.type != VersionSnapshotType.INITIALIZATION
        ) {
            return failure(VersionDataError.HistoryCorrupted)
        }
        if (snapshots.any { snapshot ->
                snapshot.restoredFromSnapshotId != null &&
                    snapshots.none { it.id == snapshot.restoredFromSnapshotId }
            }
        ) {
            return failure(VersionDataError.HistoryCorrupted)
        }
        return success(snapshots)
    }

    private fun snapshot(projectId: ProjectId, commit: GitCommit): VersionSnapshot? {
        val trailers = commit.trailers.filter { it.token.startsWith(TRAILER_PREFIX) }
        if (trailers.groupBy { it.token }.any { it.value.size != 1 }) return null
        val metadata = trailers.associate { it.token to it.value }
        if (metadata[TRAILER_SCHEMA] != SCHEMA_VERSION.toString()) return null
        val type = metadata[TRAILER_TYPE]?.let(VersionSnapshotType.entries::byName) ?: return null
        val creator = metadata[TRAILER_CREATOR]?.let(VersionCreator.entries::byName) ?: return null
        if (metadata[TRAILER_OPERATION_ID].isNullOrBlank()) return null
        val restoredFrom = metadata[TRAILER_RESTORED_FROM]?.let(::SnapshotId)
        if ((type == VersionSnapshotType.RESTORATION) != (restoredFrom != null)) return null
        return VersionSnapshot(
            id = SnapshotId(commit.revision.value),
            projectId = projectId,
            creator = creator,
            type = type,
            createdAtEpochMillis = commit.committedAtEpochMillis,
            restoredFromSnapshotId = restoredFrom
        )
    }

    private suspend fun <T> mutate(
        projectId: ProjectId,
        action: suspend () -> OperationResult<T>
    ): OperationResult<T> = mutationLease.withLease(projectId) {
        action().also { result ->
            if (result is OperationResult.Success) changes.tryEmit(projectId)
        }
    }

    private fun revision(snapshotId: SnapshotId): GitRevision =
        checkNotNull(GitRevision.of(snapshotId.value))

    private fun GitError.toVersionError(): VersionDataError = when (this) {
        GitError.REPOSITORY_NOT_FOUND,
        GitError.NATIVE_BACKEND_UNAVAILABLE
        -> VersionDataError.RepositoryUnavailable
        GitError.REPOSITORY_ALREADY_EXISTS -> VersionDataError.RepositoryAlreadyExists
        GitError.INVALID_REVISION -> VersionDataError.InvalidSnapshot
        GitError.NO_CHANGES -> VersionDataError.NoChanges
        else -> VersionDataError.OperationFailed
    }

    private fun <T> success(value: T): OperationResult<T> = OperationResult.Success(value)

    private fun <T> failure(error: VersionDataError): OperationResult<T> =
        OperationResult.Failure(error)
}

private fun <T, R> OperationResult<T>.flatMap(
    transform: (T) -> OperationResult<R>
): OperationResult<R> = when (this) {
    is OperationResult.Failure -> this
    is OperationResult.Success -> transform(value)
}

private fun <T> List<T>.anyIndexed(predicate: (Int, T) -> Boolean): Boolean =
    indices.any { predicate(it, this[it]) }

private fun <T : Enum<T>> Iterable<T>.byName(name: String): T? = firstOrNull { it.name == name }

private const val TRAILER_PREFIX = "AVD-"
private const val TRAILER_SCHEMA = "AVD-Snapshot-Schema"
private const val TRAILER_TYPE = "AVD-Snapshot-Type"
private const val TRAILER_CREATOR = "AVD-Snapshot-Creator"
private const val TRAILER_OPERATION_ID = "AVD-Operation-Id"
private const val TRAILER_RESTORED_FROM = "AVD-Restored-From"
private const val SCHEMA_VERSION = 1
private const val MILLIS_PER_SECOND = 1_000L
