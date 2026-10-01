package com.aeibi.avd.data.project.project

import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.database.project.ProjectDao
import com.aeibi.avd.core.database.project.ProjectIconRow
import com.aeibi.avd.core.database.project.ProjectRow
import com.aeibi.avd.core.database.project.ProjectWithIconRow
import com.aeibi.avd.core.filesystem.ControlledFileSystem
import com.aeibi.avd.core.filesystem.FileSystemResult
import com.aeibi.avd.core.model.Project
import com.aeibi.avd.core.model.ProjectIcon
import com.aeibi.avd.data.project.ProjectMutationLease
import com.aeibi.avd.data.project.ProjectStorageLayout
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
internal class DefaultProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val fileSystem: ControlledFileSystem,
    private val mutationLease: ProjectMutationLease
) : ProjectRepository {
    private val mutex = Mutex()

    override fun observeProjects(): Flow<OperationResult<List<Project>>> =
        projectDao.observeProjects()
            .map<List<ProjectWithIconRow>, OperationResult<List<Project>>> { rows ->
                OperationResult.Success(rows.map(ProjectWithIconRow::toProject))
            }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(storageFailure())
            }

    override suspend fun refresh(): OperationResult<Unit> = attempt {
        projectDao.getProjects()
        OperationResult.Success(Unit)
    }

    override suspend fun getProject(projectId: ProjectId): Project? = try {
        projectDao.getProjectWithIcon(projectId.value)?.toProject()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    override suspend fun createProject(
        name: String,
        description: String,
        icon: ProjectIcon?
    ): OperationResult<Project> = attempt {
        mutex.withLock {
            val nameKey = name.key()
            if (projectDao.nameExists(nameKey)) {
                return@withLock failure(ProjectDataError.NameAlreadyExists)
            }
            val iconBytes = icon?.copyPngBytes()
            val id = ProjectId(UUID.randomUUID().toString())
            val now = System.currentTimeMillis()
            val iconRow = iconBytes?.let {
                ProjectIconRow(id.value, UUID.randomUUID().toString(), it)
            }
            val row = ProjectRow(
                id = id.value,
                name = name,
                nameKey = nameKey,
                description = description,
                iconRevision = iconRow?.revision,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
            val directory = ProjectStorageLayout.projectDirectory(id)
            if (fileSystem.createDirectories(
                    ProjectStorageLayout.workspaceDirectory(id)
                ) is FileSystemResult.Failure
            ) {
                fileSystem.deleteDirectory(directory)
                return@withLock storageFailure()
            }
            try {
                projectDao.insert(row, iconRow)
            } catch (error: Exception) {
                fileSystem.deleteDirectory(directory)
                throw error
            }
            OperationResult.Success(
                checkNotNull(projectDao.getProjectWithIcon(id.value)).toProject()
            )
        }
    }

    override suspend fun updateProfile(
        projectId: ProjectId,
        name: String,
        description: String,
        iconChange: ProjectIconDataChange
    ): OperationResult<Project> = attempt {
        mutex.withLock {
            val current = projectDao.getProject(projectId.value)
                ?: return@withLock failure(ProjectDataError.ProjectNotFound)
            val nameKey = name.key()
            if (projectDao.nameExists(nameKey, projectId.value)) {
                return@withLock failure(ProjectDataError.NameAlreadyExists)
            }
            val replacement = (iconChange as? ProjectIconDataChange.Replace)
                ?.icon?.copyPngBytes()
            val iconRow = replacement?.let {
                ProjectIconRow(projectId.value, UUID.randomUUID().toString(), it)
            }
            val updated = current.copy(
                name = name,
                nameKey = nameKey,
                description = description,
                iconRevision = when (iconChange) {
                    ProjectIconDataChange.Keep -> current.iconRevision
                    ProjectIconDataChange.Remove -> null
                    is ProjectIconDataChange.Replace -> checkNotNull(iconRow).revision
                },
                updatedAtEpochMillis = System.currentTimeMillis()
            )
            projectDao.update(updated, iconRow)
            OperationResult.Success(
                checkNotNull(projectDao.getProjectWithIcon(projectId.value)).toProject()
            )
        }
    }

    override suspend fun delete(projectId: ProjectId): OperationResult<Unit> = attempt {
        mutationLease.withLease(projectId) {
            projectDao.deleteProject(projectId.value)
            if (fileSystem.deleteDirectory(
                    ProjectStorageLayout.projectDirectory(projectId)
                ) is FileSystemResult.Failure
            ) {
                storageFailure()
            } else {
                OperationResult.Success(Unit)
            }
        }
    }

    private suspend fun <T> attempt(block: suspend () -> OperationResult<T>): OperationResult<T> =
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            storageFailure()
        }

    private fun <T> failure(error: ProjectDataError): OperationResult<T> =
        OperationResult.Failure(error)

    private fun storageFailure(): OperationResult.Failure =
        OperationResult.Failure(ProjectDataError.StorageUnavailable)

    private fun String.key(): String =
        Normalizer.normalize(this, Normalizer.Form.NFC).lowercase(Locale.ROOT)
}

private fun ProjectWithIconRow.toProject(): Project = Project(
    id = ProjectId(project.id),
    name = project.name,
    description = project.description,
    icon = iconPng?.let(ProjectIcon::fromPng),
    createdAtEpochMillis = project.createdAtEpochMillis,
    updatedAtEpochMillis = project.updatedAtEpochMillis
)
