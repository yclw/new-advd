package com.aeibi.avd.core.model

import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.common.SnapshotId

data class VersionSnapshot(
    val id: SnapshotId,
    val projectId: ProjectId,
    val creator: VersionCreator,
    val type: VersionSnapshotType,
    val createdAtEpochMillis: Long,
    val restoredFromSnapshotId: SnapshotId? = null
)

enum class VersionCreator {
    USER,
    SYSTEM,
    AGENT
}

enum class VersionSnapshotType {
    MANUAL,
    PRE_RESTORE,
    INITIALIZATION,
    RESTORATION,
    REPAIR
}
