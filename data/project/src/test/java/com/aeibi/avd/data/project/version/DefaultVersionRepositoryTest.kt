package com.aeibi.avd.data.project.version

import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.filesystem.ControlledFileSystem
import com.aeibi.avd.core.filesystem.FileSystemResult
import com.aeibi.avd.core.filesystem.RelativePath
import com.aeibi.avd.core.git.ControlledGit
import com.aeibi.avd.core.git.GitCommit
import com.aeibi.avd.core.git.GitCommitRequest
import com.aeibi.avd.core.git.GitError
import com.aeibi.avd.core.git.GitRepositoryLocation
import com.aeibi.avd.core.git.GitResult
import com.aeibi.avd.core.git.GitRevision
import com.aeibi.avd.core.git.GitStatus
import com.aeibi.avd.core.model.VersionCreator
import com.aeibi.avd.core.model.VersionSnapshotType
import com.aeibi.avd.data.project.ProjectMutationLease
import com.aeibi.avd.data.project.project.InitializationJournalStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class DefaultVersionRepositoryTest {
    private val projectId = ProjectId("project")

    @Test
    fun `initial revision is a system snapshot with stable trailers`() = runBlocking {
        val git = FakeGit()
        val repository = repository(git)

        val snapshot = repository.createInitialRevision(projectId).successValue()

        assertEquals(VersionCreator.SYSTEM, snapshot.creator)
        assertEquals(VersionSnapshotType.INITIALIZATION, snapshot.type)
        val request = git.requests.single()
        assertEquals(" ", request.subject)
        assertEquals(
            setOf(
                "AVD-Snapshot-Schema",
                "AVD-Snapshot-Type",
                "AVD-Snapshot-Creator",
                "AVD-Operation-Id"
            ),
            request.trailers.map { it.token }.toSet()
        )
        assertFalse(request.trailers.any { it.token.contains("Created-At") })
    }

    @Test
    fun `restore appends restoration commit and preserves linear history`() = runBlocking {
        val git = FakeGit()
        val repository = repository(git)
        val initial = repository.createInitialRevision(projectId).successValue()
        val current = repository.createSnapshot(
            projectId,
            VersionCreator.USER,
            VersionSnapshotType.MANUAL
        ).successValue()

        val restoration = repository.restore(
            projectId,
            initial.id,
            VersionCreator.USER
        ).successValue()
        val observed = repository.observeSnapshots(projectId).first().successValue()

        assertEquals(VersionSnapshotType.RESTORATION, restoration.type)
        assertEquals(initial.id, restoration.restoredFromSnapshotId)
        assertEquals(listOf(restoration.id, current.id, initial.id), observed.map { it.id })
        assertNotNull(git.restoredRevision)
    }

    @Test
    fun `restore saves dirty worktree before creating restoration`() = runBlocking {
        val git = FakeGit()
        val repository = repository(git)
        val initial = repository.createInitialRevision(projectId).successValue()
        git.markDirty()

        repository.restore(projectId, initial.id, VersionCreator.AGENT).successValue()

        assertEquals(
            listOf(
                VersionSnapshotType.INITIALIZATION,
                VersionSnapshotType.PRE_RESTORE,
                VersionSnapshotType.RESTORATION
            ),
            git.requests.map { request ->
                VersionSnapshotType.valueOf(
                    request.trailers.first {
                        it.token == "AVD-Snapshot-Type"
                    }.value
                )
            }
        )
    }

    private fun repository(git: FakeGit): DefaultVersionRepository = DefaultVersionRepository(
        git = git,
        repositoryLocator = ProjectGitRepositoryLocator {
            GitRepositoryLocation("/projects/${it.value}/workspace", "/projects/${it.value}/git")
        },
        initializationJournals = InitializationJournalStore(NoOpFileSystem),
        mutationLease = ProjectMutationLease()
    )

    private class FakeGit(hasChanges: Boolean = false) : ControlledGit {
        private val commits = mutableListOf<GitCommit>()
        val requests = mutableListOf<GitCommitRequest>()
        var restoredRevision: GitRevision? = null
        private var initialized = false
        private var dirty = hasChanges

        override suspend fun initialize(repository: GitRepositoryLocation): GitResult<Unit> {
            if (initialized) return GitResult.Failure(GitError.REPOSITORY_ALREADY_EXISTS)
            initialized = true
            return GitResult.Success(Unit)
        }

        override suspend fun commitAll(
            repository: GitRepositoryLocation,
            request: GitCommitRequest
        ): GitResult<GitRevision> {
            requests += request
            val revision = checkNotNull(GitRevision.of("revision-${commits.size + 1}"))
            commits += GitCommit(
                revision = revision,
                parentRevisions = commits.lastOrNull()?.revision?.let(::listOf).orEmpty(),
                committedAtEpochMillis = request.signature.timestampEpochSeconds * 1_000,
                trailers = request.trailers
            )
            dirty = false
            return GitResult.Success(revision)
        }

        override suspend fun status(repository: GitRepositoryLocation): GitResult<GitStatus> =
            GitResult.Success(GitStatus(commits.lastOrNull()?.revision, dirty))

        override suspend fun readHistory(
            repository: GitRepositoryLocation
        ): GitResult<List<GitCommit>> = GitResult.Success(commits.asReversed())

        override suspend fun restoreWorkTree(
            repository: GitRepositoryLocation,
            revision: GitRevision
        ): GitResult<Unit> {
            restoredRevision = revision
            dirty = true
            return GitResult.Success(Unit)
        }

        fun markDirty() {
            dirty = true
        }
    }
}

private object NoOpFileSystem : ControlledFileSystem {
    override suspend fun createDirectories(path: RelativePath): FileSystemResult<Unit> =
        success(Unit)
    override suspend fun listDirectories(path: RelativePath): FileSystemResult<List<String>> =
        success(emptyList())
    override suspend fun readText(path: RelativePath): FileSystemResult<String?> = success(null)
    override suspend fun writeTextAtomically(
        path: RelativePath,
        content: String
    ): FileSystemResult<Unit> = success(Unit)
    override suspend fun readBytes(path: RelativePath): FileSystemResult<ByteArray?> = success(null)
    override suspend fun writeBytesAtomically(
        path: RelativePath,
        bytes: ByteArray
    ): FileSystemResult<Unit> = success(Unit)
    override suspend fun moveDirectoryAtomically(
        source: RelativePath,
        destination: RelativePath
    ): FileSystemResult<Unit> = success(Unit)
    override suspend fun deleteFile(path: RelativePath): FileSystemResult<Unit> = success(Unit)
    override suspend fun deleteDirectory(path: RelativePath): FileSystemResult<Unit> = success(Unit)
    override suspend fun exists(path: RelativePath): FileSystemResult<Boolean> = success(false)

    private fun <T> success(value: T): FileSystemResult<T> = FileSystemResult.Success(value)
}

private fun <T> OperationResult<T>.successValue(): T = (this as OperationResult.Success).value
