package com.aeibi.avd.data.project.version

import android.content.Context
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.git.GitRepositoryLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

internal fun interface ProjectGitRepositoryLocator {
    suspend fun locate(projectId: ProjectId): GitRepositoryLocation
}

@Singleton
internal class AndroidProjectGitRepositoryLocator @Inject constructor(
    @ApplicationContext context: Context
) : ProjectGitRepositoryLocator {
    private val root = context.filesDir.toPath().resolve("avd").resolve("projects")

    override suspend fun locate(projectId: ProjectId): GitRepositoryLocation {
        val projectDirectory = root.resolve(projectId.value)
        return GitRepositoryLocation(
            workTreePath = projectDirectory.resolve("workspace").toString(),
            gitDirectoryPath = projectDirectory.resolve("git").toString()
        )
    }
}
