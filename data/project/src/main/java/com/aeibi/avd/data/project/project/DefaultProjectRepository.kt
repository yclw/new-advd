package com.aeibi.avd.data.project.project

import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.filesystem.ControlledFileSystem
import com.aeibi.avd.core.filesystem.FileSystemResult
import com.aeibi.avd.core.filesystem.RelativePath
import com.aeibi.avd.core.model.Project
import com.aeibi.avd.data.project.ProjectMutationLease
import com.aeibi.avd.data.project.ProjectStorageLayout
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Singleton
internal class DefaultProjectRepository @Inject constructor(
    private val fileSystem: ControlledFileSystem,
    private val mutationLease: ProjectMutationLease
) : ProjectRepository {
    private val mutex = Mutex()
    private val projects =
        MutableStateFlow<OperationResult<List<Project>>>(OperationResult.Success(emptyList()))
    private var loaded = false
    private var lastIconRevisionEpochMillis = 0L

    override fun observeProjects(): Flow<OperationResult<List<Project>>> = projects.onStart {
        ensureLoaded()
    }

    override suspend fun refresh(): OperationResult<Unit> = mutex.withLock {
        loaded = false
        ensureLoadedLocked()
    }

    override suspend fun getProject(projectId: ProjectId): Project? = mutex.withLock {
        if (ensureLoadedLocked() is OperationResult.Failure) return@withLock null
        currentProjects().firstOrNull { it.id == projectId }
    }

    override suspend fun createProject(
        name: String,
        description: String,
        icon: ProjectIconData?
    ): OperationResult<Project> = mutex.withLock {
        ensureLoadedLocked().failureOrNull()?.let { return@withLock it }
        if (currentProjects().any { it.name.key() == name.key() }) {
            return@withLock failure(ProjectDataError.NameAlreadyExists)
        }
        val iconBytes = icon?.copyPngBytes()
        iconBytes?.let(::iconError)?.let { return@withLock failure(it) }

        val id = ProjectId(UUID.randomUUID().toString())
        val now = System.currentTimeMillis()
        val revision = iconBytes?.let { nextIconRevision() }
        val project = Project(
            id = id,
            name = name,
            description = description,
            hasCustomIcon = revision != null,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now
        )
        val stagingDirectory = ProjectStorageLayout.stagingProjectDirectory(id)
        if (fileSystem.createDirectories(ProjectStorageLayout.projectsDirectory).isFailure() ||
            fileSystem.createDirectories(
                ProjectStorageLayout.stagingWorkspaceDirectory(id)
            ).isFailure() ||
            fileSystem.createDirectories(
                ProjectStorageLayout.stagingAssetsDirectory(id)
            ).isFailure()
        ) {
            return@withLock cleanUpAndFail(stagingDirectory)
        }
        if (iconBytes != null &&
            fileSystem.writeBytesAtomically(
                ProjectStorageLayout.stagingIconPath(id, checkNotNull(revision)),
                iconBytes
            ).isFailure()
        ) {
            return@withLock cleanUpAndFail(stagingDirectory)
        }
        if (fileSystem.writeTextAtomically(
                ProjectStorageLayout.stagingMetadataPath(id),
                ProjectMetadata.from(project, revision).toJson()
            ).isFailure() ||
            fileSystem.moveDirectoryAtomically(
                stagingDirectory,
                ProjectStorageLayout.projectDirectory(id)
            ).isFailure()
        ) {
            return@withLock cleanUpAndFail(stagingDirectory)
        }
        publish(currentProjects() + project)
        OperationResult.Success(project)
    }

    override suspend fun updateProfile(
        projectId: ProjectId,
        name: String,
        description: String,
        iconChange: ProjectIconDataChange
    ): OperationResult<Project> = mutex.withLock {
        ensureLoadedLocked().failureOrNull()?.let { return@withLock it }
        val current = currentProjects().firstOrNull { it.id == projectId }
            ?: return@withLock failure(ProjectDataError.ProjectNotFound)
        if (currentProjects().any { it.id != projectId && it.name.key() == name.key() }) {
            return@withLock failure(ProjectDataError.NameAlreadyExists)
        }
        val replacement = (iconChange as? ProjectIconDataChange.Replace)?.icon
        replacement?.copyPngBytes()?.let(::iconError)?.let { return@withLock failure(it) }

        val oldRevision = readMetadata(projectId)?.iconRevision
        if (current.hasCustomIcon && oldRevision == null) return@withLock storageFailure()
        val newRevision = replacement?.let { nextIconRevision() }
        val updated = current.copy(
            name = name,
            description = description,
            hasCustomIcon = when (iconChange) {
                ProjectIconDataChange.Keep -> current.hasCustomIcon
                ProjectIconDataChange.Remove -> false
                is ProjectIconDataChange.Replace -> true
            },
            updatedAtEpochMillis = System.currentTimeMillis()
        )
        if (replacement != null &&
            fileSystem.writeBytesAtomically(
                ProjectStorageLayout.iconPath(projectId, checkNotNull(newRevision)),
                replacement.copyPngBytes()
            ).isFailure()
        ) {
            return@withLock storageFailure()
        }
        if (fileSystem.writeTextAtomically(
                ProjectStorageLayout.metadataPath(projectId),
                ProjectMetadata.from(
                    updated,
                    when (iconChange) {
                        ProjectIconDataChange.Keep -> oldRevision
                        ProjectIconDataChange.Remove -> null
                        is ProjectIconDataChange.Replace -> newRevision
                    }
                ).toJson()
            ).isFailure()
        ) {
            newRevision?.let { fileSystem.deleteFile(ProjectStorageLayout.iconPath(projectId, it)) }
            return@withLock storageFailure()
        }
        publish(currentProjects().map { if (it.id == projectId) updated else it })
        OperationResult.Success(updated)
    }

    override suspend fun loadIcon(projectId: ProjectId): OperationResult<ProjectIconData?> =
        mutex.withLock {
            ensureLoadedLocked().failureOrNull()?.let { return@withLock it }
            val project = currentProjects().firstOrNull { it.id == projectId }
                ?: return@withLock failure(ProjectDataError.ProjectNotFound)
            if (!project.hasCustomIcon) return@withLock OperationResult.Success(null)
            val revision = readMetadata(projectId)?.iconRevision
                ?: return@withLock failure(ProjectDataError.InvalidIcon)
            when (
                val result = fileSystem.readBytes(
                    ProjectStorageLayout.iconPath(projectId, revision)
                )
            ) {
                is FileSystemResult.Failure -> storageFailure()
                is FileSystemResult.Success -> result.value?.let { bytes ->
                    iconError(bytes)?.let(::failure)
                        ?: OperationResult.Success(ProjectIconData.fromPng(bytes))
                } ?: failure(ProjectDataError.InvalidIcon)
            }
        }

    override suspend fun delete(projectId: ProjectId): OperationResult<Unit> =
        mutationLease.withLease(projectId) {
            mutex.withLock {
                ensureLoadedLocked().failureOrNull()?.let { return@withLock it }
                if (fileSystem.deleteDirectory(
                        ProjectStorageLayout.projectDirectory(projectId)
                    ).isFailure()
                ) {
                    return@withLock storageFailure()
                }
                publish(currentProjects().filterNot { it.id == projectId })
                OperationResult.Success(Unit)
            }
        }

    private suspend fun ensureLoaded() {
        mutex.withLock { ensureLoadedLocked() }
    }

    private suspend fun ensureLoadedLocked(): OperationResult<Unit> {
        if (loaded) return OperationResult.Success(Unit)
        val directories = when (
            val result = fileSystem.listDirectories(
                ProjectStorageLayout.projectsDirectory
            )
        ) {
            is FileSystemResult.Failure -> return storageFailure()
            is FileSystemResult.Success -> result.value
        }
        val restored = buildList {
            directories.filterNot { it.startsWith('.') }.forEach { directoryName ->
                readProject(directoryName)?.let(::add)
            }
        }.sortedWith(
            compareByDescending<Project> { it.updatedAtEpochMillis }.thenBy { it.id.value }
        )
        loaded = true
        publish(restored)
        return OperationResult.Success(Unit)
    }

    private suspend fun readProject(directoryName: String): Project? {
        val id = ProjectId(directoryName)
        val metadata = when (
            val result = fileSystem.readText(
                ProjectStorageLayout.metadataPath(id)
            )
        ) {
            is FileSystemResult.Success -> result.value
            is FileSystemResult.Failure -> null
        }
        return metadata?.let(ProjectMetadata::fromJson)?.takeIf { it.id == id.value }?.toProject()
    }

    private suspend fun readMetadata(projectId: ProjectId): ProjectMetadata? = when (
        val result = fileSystem.readText(ProjectStorageLayout.metadataPath(projectId))
    ) {
        is FileSystemResult.Failure -> null
        is FileSystemResult.Success -> result.value?.let(ProjectMetadata::fromJson)
    }

    private suspend fun cleanUpAndFail(directory: RelativePath): OperationResult.Failure {
        fileSystem.deleteDirectory(directory)
        return storageFailure()
    }

    private suspend fun writeProject(project: Project, iconRevision: String?): Boolean =
        fileSystem.writeTextAtomically(
            ProjectStorageLayout.metadataPath(project.id),
            ProjectMetadata.from(project, iconRevision).toJson()
        ) is FileSystemResult.Success

    private fun currentProjects(): List<Project> =
        (projects.value as? OperationResult.Success)?.value.orEmpty()

    private fun publish(values: List<Project>) {
        projects.value = OperationResult.Success(
            values.sortedWith(
                compareByDescending<Project> {
                    it.updatedAtEpochMillis
                }.thenBy { it.id.value }
            )
        )
    }

    private fun String.key(): String =
        Normalizer.normalize(this, Normalizer.Form.NFC).lowercase(Locale.ROOT)

    private fun iconError(bytes: ByteArray): ProjectDataError? = when {
        bytes.size > MAX_ICON_BYTES -> ProjectDataError.IconTooLarge
        !bytes.isPng512() -> ProjectDataError.InvalidIcon
        else -> null
    }

    private fun nextIconRevision(): String {
        lastIconRevisionEpochMillis = maxOf(
            System.currentTimeMillis(),
            lastIconRevisionEpochMillis + 1
        )
        return lastIconRevisionEpochMillis.toString()
    }

    private fun <T> failure(error: ProjectDataError): OperationResult<T> =
        OperationResult.Failure(error)

    private fun storageFailure(): OperationResult.Failure =
        OperationResult.Failure(ProjectDataError.StorageUnavailable)

    private fun OperationResult<Unit>.failureOrNull(): OperationResult.Failure? =
        this as? OperationResult.Failure
}

@Serializable
private data class ProjectMetadata(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val description: String,
    val iconRevision: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
) {
    fun toProject(): Project? {
        return Project(
            id = ProjectId(id),
            name = name,
            description = description,
            hasCustomIcon = iconRevision != null,
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = updatedAtEpochMillis
        )
    }

    fun toJson(): String = projectMetadataJson.encodeToString(ProjectMetadata.serializer(), this)

    companion object {
        fun from(project: Project, iconRevision: String?) = ProjectMetadata(
            schemaVersion = SCHEMA_VERSION,
            id = project.id.value,
            name = project.name,
            description = project.description,
            iconRevision = iconRevision,
            createdAtEpochMillis = project.createdAtEpochMillis,
            updatedAtEpochMillis = project.updatedAtEpochMillis
        )

        fun fromJson(json: String): ProjectMetadata? = runCatching {
            projectMetadataJson.decodeFromString(ProjectMetadata.serializer(), json)
        }.getOrNull()?.takeIf { it.schemaVersion == SCHEMA_VERSION }
    }
}

private const val SCHEMA_VERSION = 2
private const val MAX_ICON_BYTES = 2 * 1024 * 1024

private fun ByteArray.isPng512(): Boolean {
    if (size < 24) return false
    val signature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    if (!signature.indices.all { this[it] == signature[it] }) return false
    if (this[12] != 'I'.code.toByte() ||
        this[13] != 'H'.code.toByte() ||
        this[14] != 'D'.code.toByte() ||
        this[15] != 'R'.code.toByte()
    ) {
        return false
    }
    return readPngInt(16) == 512 && readPngInt(20) == 512
}

private fun ByteArray.readPngInt(offset: Int): Int = ((this[offset].toInt() and 0xff) shl 24) or
    ((this[offset + 1].toInt() and 0xff) shl 16) or
    ((this[offset + 2].toInt() and 0xff) shl 8) or
    (this[offset + 3].toInt() and 0xff)

private fun FileSystemResult<*>.isFailure() = this is FileSystemResult.Failure

private val projectMetadataJson = Json
