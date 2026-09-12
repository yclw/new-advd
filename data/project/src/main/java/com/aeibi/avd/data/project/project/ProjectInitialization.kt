package com.aeibi.avd.data.project.project

import com.aeibi.avd.core.common.AppError
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.common.SnapshotId
import com.aeibi.avd.core.filesystem.ControlledFileSystem
import com.aeibi.avd.core.filesystem.FileSystemResult
import com.aeibi.avd.core.filesystem.RelativePath
import com.aeibi.avd.data.project.ProjectStorageLayout
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable

data class InitialWorkspaceContent(val files: List<InitialWorkspaceFile>) {
    init {
        require(files.size <= MAX_FILE_COUNT) { "Initial workspace has too many files." }
        require(files.map { it.relativePath }.distinct().size == files.size) {
            "Initial workspace contains duplicate paths."
        }
        require(files.sumOf { it.content.toByteArray(Charsets.UTF_8).size } <= MAX_TOTAL_BYTES) {
            "Initial workspace is too large."
        }
    }
}

data class InitialWorkspaceFile(val relativePath: String, val content: String) {
    init {
        require(RelativePath.of(relativePath) != null) { "Initial workspace path is invalid." }
        require(content.toByteArray(Charsets.UTF_8).size <= MAX_FILE_BYTES) {
            "Initial workspace file is too large."
        }
    }
}

@Serializable
internal data class InitializationJournal(
    val operationId: String,
    val source: String,
    val phase: String,
    val initialRevisionId: String? = null,
    val failureCode: String? = null
) {
    fun operationUuid(): UUID? = runCatching { UUID.fromString(operationId) }.getOrNull()

    fun revisionId(): SnapshotId? = initialRevisionId?.takeIf { it.isNotBlank() }?.let(::SnapshotId)

    fun durablePhase(): InitializationPhase? = InitializationPhase.entries.firstOrNull {
        it.name == phase
    }
}

internal enum class InitializationPhase {
    PREPARING,
    STAGED,
    INITIAL_REVISION_CREATED,
    WORKSPACE_PUBLISHED,
    PAYLOAD_PUBLISHED,
    READY_PUBLISHED
}

internal fun newInitializationJournal(): InitializationJournal = InitializationJournal(
    operationId = UUID.randomUUID().toString(),
    source = "BLANK",
    phase = InitializationPhase.PREPARING.name
)

internal fun InitializationJournal.withPhase(
    phase: InitializationPhase,
    revisionId: SnapshotId? = revisionId()
): InitializationJournal = copy(
    phase = phase.name,
    initialRevisionId = revisionId?.value,
    failureCode = null
)

internal fun InitializationJournal.withFailure(error: AppError): InitializationJournal = copy(
    failureCode = error.code.value
)

@Singleton
internal class InitializationJournalStore @Inject constructor(
    private val fileSystem: ControlledFileSystem
) {
    suspend fun read(projectId: ProjectId): InitializationJournal? = when (
        val result = fileSystem.readText(ProjectStorageLayout.initializationJournalPath(projectId))
    ) {
        is FileSystemResult.Failure -> null
        is FileSystemResult.Success -> result.value?.let(initializationJournalJson::fromJson)
    }

    suspend fun write(projectId: ProjectId, journal: InitializationJournal): Boolean =
        fileSystem.writeTextAtomically(
            ProjectStorageLayout.initializationJournalPath(projectId),
            initializationJournalJson.encodeToString(InitializationJournal.serializer(), journal)
        ) is FileSystemResult.Success

    suspend fun delete(projectId: ProjectId): Boolean = fileSystem.deleteFile(
        ProjectStorageLayout.initializationJournalPath(projectId)
    ) is FileSystemResult.Success
}

private const val MAX_FILE_COUNT = 1_000
private const val MAX_FILE_BYTES = 1 * 1024 * 1024
private const val MAX_TOTAL_BYTES = 10 * 1024 * 1024

private val initializationJournalJson = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = false
}

private fun kotlinx.serialization.json.Json.fromJson(value: String): InitializationJournal? =
    runCatching {
        decodeFromString(InitializationJournal.serializer(), value)
    }.getOrNull()?.takeIf {
        it.source == "BLANK" && it.operationUuid() != null && it.durablePhase() != null
    }
