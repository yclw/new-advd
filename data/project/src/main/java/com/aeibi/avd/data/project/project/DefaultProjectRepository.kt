package com.aeibi.avd.data.project.project

import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.common.SnapshotId
import com.aeibi.avd.core.filesystem.ControlledFileSystem
import com.aeibi.avd.core.filesystem.FileSystemResult
import com.aeibi.avd.core.filesystem.RelativePath
import com.aeibi.avd.core.git.ControlledGit
import com.aeibi.avd.core.git.GitResult
import com.aeibi.avd.core.model.Project
import com.aeibi.avd.core.model.ProjectStatus
import com.aeibi.avd.data.project.ProjectMutationLease
import com.aeibi.avd.data.project.ProjectStorageLayout
import com.aeibi.avd.data.project.version.ProjectGitRepositoryLocator
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
    private val git: ControlledGit,
    private val repositoryLocator: ProjectGitRepositoryLocator,
    private val initializationJournals: InitializationJournalStore,
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

    override suspend fun createDraft(
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
            updatedAtEpochMillis = now,
            status = ProjectStatus.DRAFT
        )
        val stagingDirectory = ProjectStorageLayout.stagingProjectDirectory(id)
        if (fileSystem.createDirectories(ProjectStorageLayout.projectsDirectory).isFailure() ||
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
        if (current.status == ProjectStatus.INITIALIZING ||
            current.status == ProjectStatus.DELETING
        ) {
            return@withLock failure(ProjectDataError.InvalidState)
        }
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

    override suspend fun delete(projectId: ProjectId): OperationResult<Unit> = mutex.withLock {
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

    override suspend fun prepareInitialization(
        projectId: ProjectId,
        content: InitialWorkspaceContent
    ): OperationResult<Unit> = mutationLease.withLease(projectId) {
        mutex.withLock {
            ensureLoadedLocked().failureOrNull()?.let { return@withLock it }
            val current = currentProjects().firstOrNull { it.id == projectId }
                ?: return@withLock failure(ProjectDataError.ProjectNotFound)
            if (current.status != ProjectStatus.DRAFT && current.status != ProjectStatus.FAILED) {
                return@withLock failure(ProjectDataError.InvalidState)
            }
            val iconRevision = readMetadata(projectId)?.iconRevision
            val journal = newInitializationJournal()
            val payload = ProjectStorageLayout.stagingPayloadDirectory(projectId, journal.operationId)
            if (!initializationJournals.write(projectId, journal) ||
                !writeProject(current.copy(status = ProjectStatus.INITIALIZING), iconRevision)
            ) {
                return@withLock storageFailure()
            }
            if (fileSystem.deleteDirectory(ProjectStorageLayout.stagingProjectDirectory(projectId)).isFailure() ||
                fileSystem.createDirectories(
                    ProjectStorageLayout.stagingPayloadWorkspaceDirectory(projectId, journal.operationId)
                ).isFailure()
            ) {
                return@withLock failPreparation(projectId, current, iconRevision, payload)
            }
            val workspace = ProjectStorageLayout.stagingPayloadWorkspaceDirectory(
                projectId,
                journal.operationId
            )
            val writeFailed = content.files.any { file ->
                val path = checkNotNull(RelativePath.of("${workspace.value}/${file.relativePath}"))
                fileSystem.writeTextAtomically(path, file.content).isFailure()
            }
            if (writeFailed || !initializationJournals.write(
                    projectId,
                    journal.withPhase(InitializationPhase.STAGED)
                )
            ) {
                return@withLock failPreparation(projectId, current, iconRevision, payload)
            }
            publish(currentProjects().map { project ->
                if (project.id == projectId) project.copy(status = ProjectStatus.INITIALIZING) else project
            })
            OperationResult.Success(Unit)
        }
    }

    override suspend fun publishInitialization(
        projectId: ProjectId,
        initialRevisionId: SnapshotId
    ): OperationResult<Project> = mutationLease.withLease(projectId) {
        mutex.withLock {
            ensureLoadedLocked().failureOrNull()?.let { return@withLock it }
            val current = currentProjects().firstOrNull { it.id == projectId }
                ?: return@withLock failure(ProjectDataError.ProjectNotFound)
            if (current.status != ProjectStatus.INITIALIZING) {
                return@withLock failure(ProjectDataError.InvalidState)
            }
            val journal = initializationJournals.read(projectId)
                ?: return@withLock failure(ProjectDataError.InitializationRecoveryRequired)
            if (journal.revisionId() != initialRevisionId ||
                journal.durablePhase() !in setOf(
                    InitializationPhase.INITIAL_REVISION_CREATED,
                    InitializationPhase.WORKSPACE_PUBLISHED,
                    InitializationPhase.PAYLOAD_PUBLISHED
                )
            ) {
                return@withLock failure(ProjectDataError.InitializationInvalid)
            }
            val location = repositoryLocator.locate(projectId)
            when (val status = git.status(location)) {
                is GitResult.Failure -> return@withLock failure(
                    ProjectDataError.InitializationRecoveryRequired
                )
                is GitResult.Success -> if (
                    status.value.headRevision?.value != initialRevisionId.value || status.value.hasChanges
                ) {
                    return@withLock failure(ProjectDataError.InitializationInvalid)
                }
            }
            val payload = ProjectStorageLayout.stagingPayloadDirectory(projectId, journal.operationId)
            if (journal.durablePhase() == InitializationPhase.INITIAL_REVISION_CREATED) {
                if (fileSystem.moveDirectoryAtomically(
                        ProjectStorageLayout.stagingPayloadWorkspaceDirectory(projectId, journal.operationId),
                        ProjectStorageLayout.workspaceDirectory(projectId)
                    ).isFailure() || !initializationJournals.write(
                        projectId,
                        journal.withPhase(InitializationPhase.WORKSPACE_PUBLISHED, initialRevisionId)
                    )
                ) {
                    return@withLock failure(ProjectDataError.InitializationRecoveryRequired)
                }
            }
            val afterWorkspace = initializationJournals.read(projectId)
                ?: return@withLock failure(ProjectDataError.InitializationRecoveryRequired)
            if (afterWorkspace.durablePhase() == InitializationPhase.WORKSPACE_PUBLISHED) {
                if (fileSystem.moveDirectoryAtomically(
                        ProjectStorageLayout.stagingPayloadGitDirectory(projectId, journal.operationId),
                        ProjectStorageLayout.gitDirectory(projectId)
                    ).isFailure() || !initializationJournals.write(
                        projectId,
                        afterWorkspace.withPhase(InitializationPhase.PAYLOAD_PUBLISHED, initialRevisionId)
                    )
                ) {
                    return@withLock failure(ProjectDataError.InitializationRecoveryRequired)
                }
            }
            val iconRevision = readMetadata(projectId)?.iconRevision
            val ready = current.copy(
                status = ProjectStatus.READY,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
            if (!writeProject(ready, iconRevision) || !initializationJournals.write(
                    projectId,
                    afterWorkspace.withPhase(InitializationPhase.READY_PUBLISHED, initialRevisionId)
                ) || !initializationJournals.delete(projectId)
            ) {
                return@withLock failure(ProjectDataError.InitializationRecoveryRequired)
            }
            fileSystem.deleteDirectory(payload)
            publish(currentProjects().map { if (it.id == projectId) ready else it })
            OperationResult.Success(ready)
        }
    }

    override suspend fun resolveInitializationFailure(
        projectId: ProjectId,
        error: com.aeibi.avd.core.common.AppError
    ): OperationResult<Unit> = mutationLease.withLease(projectId) {
        mutex.withLock {
            ensureLoadedLocked().failureOrNull()?.let { return@withLock it }
            val current = currentProjects().firstOrNull { it.id == projectId }
                ?: return@withLock failure(ProjectDataError.ProjectNotFound)
            val journal = initializationJournals.read(projectId)
                ?: return@withLock failure(ProjectDataError.InitializationRecoveryRequired)
            if (journal.revisionId() != null ||
                journal.durablePhase() !in setOf(InitializationPhase.PREPARING, InitializationPhase.STAGED)
            ) {
                initializationJournals.write(projectId, journal.withFailure(error))
                return@withLock failure(ProjectDataError.InitializationRecoveryRequired)
            }
            val failed = current.copy(
                status = ProjectStatus.FAILED,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
            val iconRevision = readMetadata(projectId)?.iconRevision
            if (fileSystem.deleteDirectory(
                    ProjectStorageLayout.stagingProjectDirectory(projectId)
                ).isFailure() || !writeProject(failed, iconRevision) ||
                !initializationJournals.delete(projectId)
            ) {
                return@withLock storageFailure()
            }
            publish(currentProjects().map { if (it.id == projectId) failed else it })
            OperationResult.Success(Unit)
        }
    }

    override suspend fun recoverInitialization(
        projectId: ProjectId
    ): OperationResult<Project?> {
        val journal = initializationJournals.read(projectId) ?: return OperationResult.Success(
            getProject(projectId)
        )
        val revision = journal.revisionId()
        return when (journal.durablePhase()) {
            InitializationPhase.INITIAL_REVISION_CREATED,
            InitializationPhase.WORKSPACE_PUBLISHED,
            InitializationPhase.PAYLOAD_PUBLISHED -> if (revision == null) {
                failure(ProjectDataError.InitializationRecoveryRequired)
            } else {
                publishInitialization(projectId, revision).map { it }
            }
            InitializationPhase.READY_PUBLISHED -> mutationLease.withLease(projectId) {
                mutex.withLock {
                    val current = currentProjects().firstOrNull { it.id == projectId }
                        ?: return@withLock failure(ProjectDataError.ProjectNotFound)
                    if (current.status != ProjectStatus.READY || !initializationJournals.delete(projectId)) {
                        return@withLock failure(ProjectDataError.InitializationRecoveryRequired)
                    }
                    OperationResult.Success(current)
                }
            }
            InitializationPhase.PREPARING,
            InitializationPhase.STAGED,
            null -> failure(ProjectDataError.InitializationRecoveryRequired)
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

    private suspend fun failPreparation(
        projectId: ProjectId,
        current: Project,
        iconRevision: String?,
        payload: RelativePath
    ): OperationResult.Failure {
        fileSystem.deleteDirectory(payload)
        initializationJournals.delete(projectId)
        val failed = current.copy(status = ProjectStatus.FAILED, updatedAtEpochMillis = System.currentTimeMillis())
        writeProject(failed, iconRevision)
        publish(currentProjects().map { if (it.id == projectId) failed else it })
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

private fun <T, R> OperationResult<T>.map(transform: (T) -> R): OperationResult<R> = when (this) {
    is OperationResult.Failure -> this
    is OperationResult.Success -> OperationResult.Success(transform(value))
}

@Serializable
private data class ProjectMetadata(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val description: String,
    val iconRevision: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val status: String
) {
    fun toProject(): Project? {
        val projectStatus = ProjectStatus.entries.firstOrNull { it.name == status } ?: return null
        return Project(
            id = ProjectId(id),
            name = name,
            description = description,
            hasCustomIcon = iconRevision != null,
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = updatedAtEpochMillis,
            status = projectStatus
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
            updatedAtEpochMillis = project.updatedAtEpochMillis,
            status = project.status.name
        )

        fun fromJson(json: String): ProjectMetadata? = runCatching {
            projectMetadataJson.decodeFromString(ProjectMetadata.serializer(), json)
        }.getOrNull()?.takeIf { it.schemaVersion == SCHEMA_VERSION }
    }
}

private const val SCHEMA_VERSION = 1
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
