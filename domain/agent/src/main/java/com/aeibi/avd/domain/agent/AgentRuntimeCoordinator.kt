package com.aeibi.avd.domain.agent

import com.aeibi.avd.contract.agent.AgentRunRequest
import com.aeibi.avd.contract.agent.AgentRuntime
import com.aeibi.avd.contract.agent.AgentRuntimeEvent
import com.aeibi.avd.contract.projectruntime.AgentProjectRuntimeControl
import com.aeibi.avd.core.common.AppError
import com.aeibi.avd.core.common.ApplicationCoroutineScope
import com.aeibi.avd.core.common.ErrorCode
import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.common.SessionId
import com.aeibi.avd.core.common.TurnId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Process-lifetime owner of Agent execution jobs. It deliberately exposes use-case-shaped
 * operations rather than its jobs, scope, or provider runtime to UI callers.
 */
@Singleton
class AgentRuntimeCoordinator @Inject constructor(
    private val agentRuntime: AgentRuntime,
    @param:ApplicationCoroutineScope private val applicationScope: kotlinx.coroutines.CoroutineScope
) : AgentProjectRuntimeControl {
    private val gate = Mutex()
    private val stateLock = Any()
    private val states = mutableMapOf<ProjectId, MutableStateFlow<AgentRuntimeSnapshot>>()
    private val activeExecutions = mutableMapOf<ProjectId, ActiveExecution>()
    private val receiptsByRequest = mutableMapOf<AgentRequestKey, AgentTurnReceipt>()
    private val closeAdmission = mutableSetOf<ProjectId>()

    fun observe(projectId: ProjectId): StateFlow<AgentRuntimeSnapshot> = stateFor(projectId)

    suspend fun run(request: RunAgentTurnRequest): OperationResult<AgentTurnReceipt> =
        gate.withLock {
            val requestKey = AgentRequestKey(request.projectId, request.idempotencyKey)
            receiptsByRequest[requestKey]?.let { return OperationResult.Success(it) }

            if (request.projectId in closeAdmission) {
                return OperationResult.Failure(AgentRuntimeError.ProjectIsClosing)
            }
            if (activeExecutions.containsKey(request.projectId)) {
                return OperationResult.Failure(AgentRuntimeError.TurnAlreadyRunning)
            }

            val receipt = AgentTurnReceipt(TurnId(UUID.randomUUID().toString()))
            val execution = ActiveExecution(
                request = request,
                receipt = receipt
            )
            val job = applicationScope.launch(start = CoroutineStart.LAZY) {
                supervise(execution)
            }
            execution.job = job
            activeExecutions[request.projectId] = execution
            receiptsByRequest[requestKey] = receipt
            stateFor(request.projectId).value = AgentRuntimeSnapshot.Preparing(receipt.turnId)
            job.start()
            OperationResult.Success(receipt)
        }

    suspend fun cancel(projectId: ProjectId, turnId: TurnId): OperationResult<Unit> =
        gate.withLock {
            val execution = activeExecutions[projectId]
                ?: return OperationResult.Failure(AgentRuntimeError.TurnNotRunning)
            if (execution.receipt.turnId != turnId) {
                return OperationResult.Failure(AgentRuntimeError.TurnNotRunning)
            }
            stateFor(projectId).value = AgentRuntimeSnapshot.Cancelling(turnId)
            execution.job.cancel()
            OperationResult.Success(Unit)
        }

    override suspend fun hasRunningTurn(projectId: ProjectId): Boolean = gate.withLock {
        activeExecutions.containsKey(projectId)
    }

    override suspend fun requestStop(projectId: ProjectId): OperationResult<Unit> = gate.withLock {
        closeAdmission += projectId
        activeExecutions[projectId]?.let { execution ->
            stateFor(projectId).value = AgentRuntimeSnapshot.Cancelling(execution.receipt.turnId)
            execution.job.cancel()
        }
        OperationResult.Success(Unit)
    }

    override suspend fun awaitStopped(
        projectId: ProjectId,
        timeout: Duration
    ): OperationResult<Unit> {
        val job = gate.withLock { activeExecutions[projectId]?.job }
            ?: return OperationResult.Success(Unit)
        return try {
            withTimeout(timeout) { job.join() }
            OperationResult.Success(Unit)
        } catch (_: TimeoutCancellationException) {
            OperationResult.Failure(AgentRuntimeError.StopTimedOut)
        }
    }

    override suspend fun completeClose(projectId: ProjectId): OperationResult<Unit> =
        gate.withLock {
            if (activeExecutions.containsKey(projectId)) {
                return OperationResult.Failure(AgentRuntimeError.TurnStillRunning)
            }
            closeAdmission -= projectId
            stateFor(projectId).value = AgentRuntimeSnapshot.Idle
            OperationResult.Success(Unit)
        }

    override suspend fun markInterruptedAfterProcessDeath(
        projectId: ProjectId
    ): OperationResult<Unit> = gate.withLock {
        if (activeExecutions.containsKey(projectId)) {
            return OperationResult.Failure(AgentRuntimeError.TurnStillRunning)
        }
        closeAdmission -= projectId
        stateFor(projectId).value = AgentRuntimeSnapshot.Interrupted(turnId = null)
        OperationResult.Success(Unit)
    }

    private suspend fun supervise(execution: ActiveExecution) {
        val projectId = execution.request.projectId
        try {
            agentRuntime.run(
                AgentRunRequest(prompt = execution.request.prompt, tools = emptyList())
            ).collect { event ->
                when (event) {
                    is AgentRuntimeEvent.Started -> markRunningIfPreparing(projectId, execution)
                    is AgentRuntimeEvent.TextDelta,
                    is AgentRuntimeEvent.ToolStarted,
                    is AgentRuntimeEvent.ToolFinished,
                    AgentRuntimeEvent.Completed,
                    is AgentRuntimeEvent.Failed -> Unit
                }
            }
        } catch (error: CancellationException) {
            throw error
        } finally {
            gate.withLock {
                if (activeExecutions[projectId] === execution) {
                    activeExecutions.remove(projectId)
                    stateFor(projectId).value = AgentRuntimeSnapshot.Idle
                }
            }
        }
    }

    private suspend fun markRunningIfPreparing(projectId: ProjectId, execution: ActiveExecution) {
        gate.withLock {
            if (activeExecutions[projectId] !== execution) return
            if (stateFor(projectId).value is AgentRuntimeSnapshot.Preparing) {
                stateFor(projectId).value = AgentRuntimeSnapshot.Running(execution.receipt.turnId)
            }
        }
    }

    private fun stateFor(projectId: ProjectId): MutableStateFlow<AgentRuntimeSnapshot> =
        synchronized(stateLock) {
            states.getOrPut(projectId) { MutableStateFlow(AgentRuntimeSnapshot.Idle) }
        }

    private data class AgentRequestKey(val projectId: ProjectId, val idempotencyKey: String)

    private class ActiveExecution(val request: RunAgentTurnRequest, val receipt: AgentTurnReceipt) {
        lateinit var job: Job
    }
}

data class RunAgentTurnRequest(
    val projectId: ProjectId,
    val sessionId: SessionId,
    val prompt: String,
    val idempotencyKey: String
)

data class AgentTurnReceipt(val turnId: TurnId)

sealed interface AgentRuntimeSnapshot {
    data object Idle : AgentRuntimeSnapshot
    data class Preparing(val turnId: TurnId) : AgentRuntimeSnapshot
    data class Running(val turnId: TurnId) : AgentRuntimeSnapshot
    data class Cancelling(val turnId: TurnId) : AgentRuntimeSnapshot
    data class Interrupted(val turnId: TurnId?) : AgentRuntimeSnapshot
}

sealed class AgentRuntimeError(override val code: ErrorCode, override val retryable: Boolean) :
    AppError {
    data object ProjectIsClosing : AgentRuntimeError(ErrorCode("project_runtime_closing"), true)
    data object TurnAlreadyRunning : AgentRuntimeError(
        ErrorCode("agent_turn_already_running"),
        true
    )
    data object TurnNotRunning : AgentRuntimeError(ErrorCode("agent_turn_not_running"), false)
    data object TurnStillRunning : AgentRuntimeError(ErrorCode("agent_turn_still_running"), true)
    data object StopTimedOut : AgentRuntimeError(ErrorCode("agent_stop_timed_out"), true)
}

class ObserveAgentRuntimeUseCase @Inject constructor(
    private val coordinator: AgentRuntimeCoordinator
) {
    operator fun invoke(projectId: ProjectId): Flow<AgentRuntimeSnapshot> =
        coordinator.observe(projectId)
}

class RunAgentTurnUseCase @Inject constructor(private val coordinator: AgentRuntimeCoordinator) {
    suspend operator fun invoke(request: RunAgentTurnRequest): OperationResult<AgentTurnReceipt> =
        coordinator.run(request)
}

class CancelAgentTurnUseCase @Inject constructor(private val coordinator: AgentRuntimeCoordinator) {
    suspend operator fun invoke(projectId: ProjectId, turnId: TurnId): OperationResult<Unit> =
        coordinator.cancel(projectId, turnId)
}
