package com.aeibi.avd.data.project.preview.log

import com.aeibi.avd.core.common.ProjectId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

interface PreviewLogRepository {
    fun observe(projectId: ProjectId): Flow<List<PreviewLogEntry>>
    suspend fun record(projectId: ProjectId, entry: PreviewLogEntry)
    suspend fun clear(projectId: ProjectId)
    suspend fun snapshot(projectId: ProjectId): List<PreviewLogEntry>
}

data class PreviewLogEntry(
    val timestampMillis: Long,
    val level: PreviewLogLevel,
    val message: String
)

enum class PreviewLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

internal class InMemoryPreviewLogRepository(private val capacityPerProject: Int = 500) :
    PreviewLogRepository {
    private val entries = MutableStateFlow<Map<ProjectId, List<PreviewLogEntry>>>(emptyMap())

    override fun observe(projectId: ProjectId): Flow<List<PreviewLogEntry>> =
        entries.map { it[projectId].orEmpty() }

    override suspend fun record(projectId: ProjectId, entry: PreviewLogEntry) {
        entries.value += (
            projectId to (entries.value[projectId].orEmpty() + entry).takeLast(capacityPerProject)
            )
    }

    override suspend fun clear(projectId: ProjectId) {
        entries.value -= projectId
    }

    override suspend fun snapshot(projectId: ProjectId): List<PreviewLogEntry> =
        entries.value[projectId].orEmpty()
}
