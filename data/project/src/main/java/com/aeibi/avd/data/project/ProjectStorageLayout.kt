package com.aeibi.avd.data.project

import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.filesystem.RelativePath

internal object ProjectStorageLayout {
    fun projectDirectory(projectId: ProjectId): RelativePath = path("projects/${projectId.value}")

    fun stagingProjectDirectory(projectId: ProjectId): RelativePath =
        path("projects/.staging/${projectId.value}")

    fun workspaceDirectory(projectId: ProjectId): RelativePath =
        path("projects/${projectId.value}/workspace")

    fun stagingPayloadGitDirectory(projectId: ProjectId, operationId: String): RelativePath =
        path("projects/.staging/${projectId.value}/$operationId/git")

    fun gitDirectory(projectId: ProjectId): RelativePath = path("projects/${projectId.value}/git")

    private fun path(value: String): RelativePath =
        checkNotNull(RelativePath.of(value)) { "Project path must be valid." }
}
