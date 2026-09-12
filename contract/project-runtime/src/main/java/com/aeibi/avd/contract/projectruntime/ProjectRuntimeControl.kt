package com.aeibi.avd.contract.projectruntime

import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.ProjectId
import kotlin.time.Duration

/**
 * The only Agent lifecycle capabilities that the project-close workflow needs.
 * This is deliberately not an Agent runtime handle or a general command bus.
 */
interface AgentProjectRuntimeControl {
    suspend fun hasRunningTurn(projectId: ProjectId): Boolean

    /** Closes admission for new turns and cooperatively cancels the current turn, if any. */
    suspend fun requestStop(projectId: ProjectId): OperationResult<Unit>

    suspend fun awaitStopped(projectId: ProjectId, timeout: Duration): OperationResult<Unit>

    /** Reopens admission after a completed close workflow. */
    suspend fun completeClose(projectId: ProjectId): OperationResult<Unit>

    /** Publishes the durable recovery result of a turn interrupted by process death. */
    suspend fun markInterruptedAfterProcessDeath(projectId: ProjectId): OperationResult<Unit>
}

/** The Preview lifecycle capability needed by the project-close workflow. */
interface PreviewProjectRuntimeControl {
    suspend fun stop(projectId: ProjectId): OperationResult<Unit>
}
