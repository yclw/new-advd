package com.aeibi.avd.core.model

import com.aeibi.avd.core.common.ProjectId

data class Project(
    val id: ProjectId,
    val name: String,
    val description: String,
    val hasCustomIcon: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val status: ProjectStatus
)

enum class ProjectStatus {
    DRAFT,
    INITIALIZING,
    READY,
    FAILED,
    DELETING
}
