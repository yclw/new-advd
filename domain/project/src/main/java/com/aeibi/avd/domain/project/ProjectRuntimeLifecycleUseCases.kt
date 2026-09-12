package com.aeibi.avd.domain.project

import com.aeibi.avd.contract.projectruntime.AgentProjectRuntimeControl
import com.aeibi.avd.contract.projectruntime.PreviewProjectRuntimeControl
import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.ProjectId
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Admission only. Confirmation performs the side effects so a dismissed confirmation dialog does
 * not accidentally cancel a running turn.
 */
class RequestProjectRuntimeCloseUseCase @Inject constructor(
    private val agentControl: AgentProjectRuntimeControl
) {
    suspend operator fun invoke(projectId: ProjectId): CloseRequestResult =
        if (agentControl.hasRunningTurn(projectId)) {
            CloseRequestResult.NeedsConfirmation
        } else {
            CloseRequestResult.ReadyToClose
        }
}

sealed interface CloseRequestResult {
    data object ReadyToClose : CloseRequestResult
    data object NeedsConfirmation : CloseRequestResult
}

/**
 * Coordinates only runtime owners. Workspace write/Git quiescence and durable close records are
 * intentionally added here when their data-level lifecycle APIs exist; this use case must not
 * acquire or release file locks itself.
 */
class ConfirmProjectRuntimeCloseUseCase @Inject constructor(
    private val agentControl: AgentProjectRuntimeControl,
    private val previewControl: PreviewProjectRuntimeControl
) {
    private val agentStopTimeout: Duration = 10.seconds

    suspend operator fun invoke(projectId: ProjectId): OperationResult<Unit> {
        when (val result = agentControl.requestStop(projectId)) {
            is OperationResult.Failure -> return result
            is OperationResult.Success -> Unit
        }
        when (val result = agentControl.awaitStopped(projectId, agentStopTimeout)) {
            is OperationResult.Failure -> return result
            is OperationResult.Success -> Unit
        }
        when (val result = previewControl.stop(projectId)) {
            is OperationResult.Failure -> return result
            is OperationResult.Success -> Unit
        }
        return agentControl.completeClose(projectId)
    }
}

/**
 * The data layer determines whether an unfinished durable turn record exists. This use case only
 * reflects that already-established fact into the in-memory Agent runtime state.
 */
class RecoverProjectUseCase @Inject constructor(
    private val agentControl: AgentProjectRuntimeControl
) {
    suspend operator fun invoke(
        request: RecoverProjectRequest
    ): OperationResult<ProjectRecoveryResult> {
        if (!request.hasInterruptedAgentTurn) {
            return OperationResult.Success(ProjectRecoveryResult.NoInterruptedRuntime)
        }
        return when (
            val result = agentControl.markInterruptedAfterProcessDeath(
                request.projectId
            )
        ) {
            is OperationResult.Success -> OperationResult.Success(
                ProjectRecoveryResult.AgentTurnInterrupted
            )
            is OperationResult.Failure -> OperationResult.Failure(result.error)
        }
    }
}

data class RecoverProjectRequest(val projectId: ProjectId, val hasInterruptedAgentTurn: Boolean)

sealed interface ProjectRecoveryResult {
    data object NoInterruptedRuntime : ProjectRecoveryResult
    data object AgentTurnInterrupted : ProjectRecoveryResult
}
