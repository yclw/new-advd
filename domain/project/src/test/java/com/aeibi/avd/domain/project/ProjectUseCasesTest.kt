package com.aeibi.avd.domain.project

import com.aeibi.avd.contract.projectruntime.AgentProjectRuntimeControl
import com.aeibi.avd.contract.projectruntime.PreviewProjectRuntimeControl
import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.AppError
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.common.SnapshotId
import com.aeibi.avd.core.model.Project
import com.aeibi.avd.core.model.ProjectStatus
import com.aeibi.avd.core.model.VersionCreator
import com.aeibi.avd.core.model.VersionSnapshot
import com.aeibi.avd.core.model.VersionSnapshotType
import com.aeibi.avd.data.project.project.ProjectIconData
import com.aeibi.avd.data.project.project.ProjectIconDataChange
import com.aeibi.avd.data.project.project.InitialWorkspaceContent
import com.aeibi.avd.data.project.project.ProjectRepository
import com.aeibi.avd.data.project.version.VersionRepository
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectUseCasesTest {
    @Test
    fun `create normalizes profile before repository call`() = runBlocking {
        val repository = FakeProjectRepository()
        val result = CreateDraftProjectUseCase(repository)(
            CreateProjectRequest("  Demo  ", "  Description  ", null)
        )

        assertEquals("Demo", repository.createdName)
        assertEquals("Description", repository.createdDescription)
        assertTrue(result is OperationResult.Success)
    }

    @Test
    fun `create rejects invalid name and description`() = runBlocking {
        val repository = FakeProjectRepository()
        val create = CreateDraftProjectUseCase(repository)

        val blank = create(CreateProjectRequest("   ", "", null))
        val longDescription = create(CreateProjectRequest("Demo", "a".repeat(281), null))

        assertEquals(ProjectDomainError.InvalidName, (blank as OperationResult.Failure).error)
        assertEquals(
            ProjectDomainError.InvalidDescription,
            (longDescription as OperationResult.Failure).error
        )
    }

    @Test
    fun `delete maps repository failure`() = runBlocking {
        val repository = FakeProjectRepository(deleteResult = OperationResult.Failure(StorageError))

        val result = DeleteProjectUseCase(
            repository,
            ConfirmProjectRuntimeCloseUseCase(NoOpAgentRuntime, NoOpPreviewRuntime)
        )(ProjectId("missing"))

        assertEquals(
            ProjectDomainError.StorageUnavailable,
            (result as OperationResult.Failure).error
        )
    }

    @Test
    fun `delete keeps project files when runtime close fails`() = runBlocking {
        val repository = FakeProjectRepository()

        val result = DeleteProjectUseCase(
            repository,
            ConfirmProjectRuntimeCloseUseCase(FailingAgentRuntime, NoOpPreviewRuntime)
        )(ProjectId("project"))

        assertEquals(
            ProjectDomainError.RuntimeCloseFailed,
            (result as OperationResult.Failure).error
        )
        assertFalse(repository.deleted)
    }

    @Test
    fun `blank initialization prepares then creates revision then publishes`() = runBlocking {
        val events = mutableListOf<String>()
        val projects = FakeProjectRepository(events = events)
        val versions = FakeVersionRepository(events)

        val result = InitializeBlankProjectUseCase(projects, versions)(ProjectId("project"))

        assertTrue(result is OperationResult.Success)
        assertEquals(listOf("prepare", "initial", "publish"), events)
        assertEquals(0, projects.initialContent!!.files.size)
    }

    private class FakeProjectRepository(
        private val deleteResult: OperationResult<Unit> = OperationResult.Success(Unit),
        private val events: MutableList<String> = mutableListOf()
    ) : ProjectRepository {
        var createdName: String? = null
        var createdDescription: String? = null
        var deleted = false
        var initialContent: InitialWorkspaceContent? = null

        override fun observeProjects(): Flow<OperationResult<List<Project>>> = emptyFlow()

        override suspend fun refresh(): OperationResult<Unit> = OperationResult.Success(Unit)

        override suspend fun getProject(projectId: ProjectId): Project? = null

        override suspend fun createDraft(
            name: String,
            description: String,
            icon: ProjectIconData?
        ): OperationResult<Project> {
            createdName = name
            createdDescription = description
            return OperationResult.Success(project(ProjectId("created"), name, description))
        }

        override suspend fun updateProfile(
            projectId: ProjectId,
            name: String,
            description: String,
            iconChange: ProjectIconDataChange
        ): OperationResult<Project> = OperationResult.Success(project(projectId, name, description))

        override suspend fun loadIcon(projectId: ProjectId): OperationResult<ProjectIconData?> =
            OperationResult.Success(null)

        override suspend fun delete(projectId: ProjectId): OperationResult<Unit> {
            deleted = true
            return deleteResult
        }

        override suspend fun prepareInitialization(
            projectId: ProjectId,
            content: InitialWorkspaceContent
        ): OperationResult<Unit> {
            events += "prepare"
            initialContent = content
            return OperationResult.Success(Unit)
        }

        override suspend fun publishInitialization(
            projectId: ProjectId,
            initialRevisionId: SnapshotId
        ): OperationResult<Project> {
            events += "publish"
            return OperationResult.Success(project(projectId, "Demo", ""))
        }

        override suspend fun resolveInitializationFailure(
            projectId: ProjectId,
            error: AppError
        ): OperationResult<Unit> = OperationResult.Success(Unit)

        override suspend fun recoverInitialization(projectId: ProjectId): OperationResult<Project?> =
            OperationResult.Success(null)
    }

    private class FakeVersionRepository(private val events: MutableList<String>) : VersionRepository {
        override fun observeSnapshots(projectId: ProjectId): Flow<OperationResult<List<VersionSnapshot>>> =
            emptyFlow()

        override suspend fun createInitialRevision(projectId: ProjectId): OperationResult<VersionSnapshot> {
            events += "initial"
            return OperationResult.Success(
                VersionSnapshot(
                    id = SnapshotId("initial"),
                    projectId = projectId,
                    creator = VersionCreator.SYSTEM,
                    type = VersionSnapshotType.INITIALIZATION,
                    createdAtEpochMillis = 0
                )
            )
        }

        override suspend fun createSnapshot(
            projectId: ProjectId,
            creator: VersionCreator,
            type: VersionSnapshotType
        ): OperationResult<VersionSnapshot> = error("Not used")

        override suspend fun restore(
            projectId: ProjectId,
            snapshotId: SnapshotId,
            creator: VersionCreator
        ): OperationResult<VersionSnapshot> = error("Not used")
    }
}

private object NoOpAgentRuntime : AgentProjectRuntimeControl {
    override suspend fun hasRunningTurn(projectId: ProjectId): Boolean = false

    override suspend fun requestStop(projectId: ProjectId): OperationResult<Unit> =
        OperationResult.Success(Unit)

    override suspend fun awaitStopped(
        projectId: ProjectId,
        timeout: Duration
    ): OperationResult<Unit> = OperationResult.Success(Unit)

    override suspend fun completeClose(projectId: ProjectId): OperationResult<Unit> =
        OperationResult.Success(Unit)

    override suspend fun markInterruptedAfterProcessDeath(
        projectId: ProjectId
    ): OperationResult<Unit> = OperationResult.Success(Unit)
}

private object NoOpPreviewRuntime : PreviewProjectRuntimeControl {
    override suspend fun stop(projectId: ProjectId): OperationResult<Unit> =
        OperationResult.Success(Unit)
}

private object FailingAgentRuntime : AgentProjectRuntimeControl by NoOpAgentRuntime {
    override suspend fun requestStop(projectId: ProjectId): OperationResult<Unit> =
        OperationResult.Failure(StorageError)
}

private fun project(id: ProjectId, name: String, description: String) = Project(
    id = id,
    name = name,
    description = description,
    hasCustomIcon = false,
    createdAtEpochMillis = 0,
    updatedAtEpochMillis = 0,
    status = ProjectStatus.DRAFT
)

private object StorageError : com.aeibi.avd.core.common.AppError {
    override val code = com.aeibi.avd.core.common.ErrorCode("project_storage_unavailable")
    override val retryable = true
}
