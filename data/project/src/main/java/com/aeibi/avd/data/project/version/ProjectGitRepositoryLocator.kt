package com.aeibi.avd.data.project.version

import android.content.Context
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.git.GitRepositoryLocation
import com.aeibi.avd.data.project.project.InitializationJournalStore
import com.aeibi.avd.data.project.project.InitializationPhase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

internal fun interface ProjectGitRepositoryLocator {
    suspend fun locate(projectId: ProjectId): GitRepositoryLocation
}

@Singleton
internal class AndroidProjectGitRepositoryLocator @Inject constructor(
    @ApplicationContext context: Context,
    private val initializationJournals: InitializationJournalStore
) : ProjectGitRepositoryLocator {
    private val root = context.filesDir.toPath().resolve("avd").resolve("projects")

    override suspend fun locate(projectId: ProjectId): GitRepositoryLocation {
        val projectDirectory = root.resolve(projectId.value)
        val journal = initializationJournals.read(projectId)
        val stagingDirectory = journal?.operationId?.let { operationId ->
            root.resolve(".staging").resolve(projectId.value).resolve(operationId)
        }
        val workspace = when (journal?.durablePhase()) {
            InitializationPhase.PREPARING,
            InitializationPhase.STAGED,
            InitializationPhase.INITIAL_REVISION_CREATED -> stagingDirectory?.resolve("workspace")
            InitializationPhase.WORKSPACE_PUBLISHED,
            InitializationPhase.PAYLOAD_PUBLISHED,
            InitializationPhase.READY_PUBLISHED -> projectDirectory.resolve("workspace")
            null -> projectDirectory.resolve("workspace")
        } ?: projectDirectory.resolve("workspace")
        val git = when (journal?.durablePhase()) {
            InitializationPhase.PREPARING,
            InitializationPhase.STAGED,
            InitializationPhase.INITIAL_REVISION_CREATED,
            InitializationPhase.WORKSPACE_PUBLISHED -> stagingDirectory?.resolve("git")
            InitializationPhase.PAYLOAD_PUBLISHED,
            InitializationPhase.READY_PUBLISHED,
            null -> projectDirectory.resolve("git")
        } ?: projectDirectory.resolve("git")
        return GitRepositoryLocation(
            workTreePath = workspace.toString(),
            gitDirectoryPath = git.toString()
        )
    }
}
