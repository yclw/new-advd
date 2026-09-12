package com.aeibi.avd.data.project

import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.filesystem.RelativePath

internal object ProjectStorageLayout {
    val projectsDirectory = path("projects")
    val stagingDirectory = path("projects/.staging")

    fun projectDirectory(projectId: ProjectId): RelativePath = path("projects/${projectId.value}")

    fun metadataPath(projectId: ProjectId): RelativePath =
        path("projects/${projectId.value}/project.json")

    fun stagingMetadataPath(projectId: ProjectId): RelativePath =
        path("projects/.staging/${projectId.value}/project.json")

    fun stagingProjectDirectory(projectId: ProjectId): RelativePath =
        path("projects/.staging/${projectId.value}")

    fun initializationJournalPath(projectId: ProjectId): RelativePath =
        path("projects/${projectId.value}/initialization.json")

    fun workspaceDirectory(projectId: ProjectId): RelativePath =
        path("projects/${projectId.value}/workspace")

    fun stagingWorkspaceDirectory(projectId: ProjectId): RelativePath =
        path("projects/.staging/${projectId.value}/workspace")

    fun stagingPayloadDirectory(projectId: ProjectId, operationId: String): RelativePath =
        path("projects/.staging/${projectId.value}/$operationId")

    fun stagingPayloadWorkspaceDirectory(projectId: ProjectId, operationId: String): RelativePath =
        path("projects/.staging/${projectId.value}/$operationId/workspace")

    fun stagingPayloadGitDirectory(projectId: ProjectId, operationId: String): RelativePath =
        path("projects/.staging/${projectId.value}/$operationId/git")

    fun gitDirectory(projectId: ProjectId): RelativePath = path("projects/${projectId.value}/git")

    fun stagingAssetsDirectory(projectId: ProjectId): RelativePath =
        path("projects/.staging/${projectId.value}/assets")

    fun iconPath(projectId: ProjectId, revision: String): RelativePath =
        path("projects/${projectId.value}/assets/icon-$revision.png")

    fun stagingIconPath(projectId: ProjectId, revision: String): RelativePath =
        path("projects/.staging/${projectId.value}/assets/icon-$revision.png")

    private fun path(value: String): RelativePath =
        checkNotNull(RelativePath.of(value)) { "Project path must be valid." }
}
