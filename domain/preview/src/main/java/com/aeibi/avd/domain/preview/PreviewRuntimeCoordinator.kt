package com.aeibi.avd.domain.preview

import com.aeibi.avd.contract.projectruntime.PreviewProjectRuntimeControl
import com.aeibi.avd.core.common.AppError
import com.aeibi.avd.core.common.ApplicationCoroutineScope
import com.aeibi.avd.core.common.ErrorCode
import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.PreviewRequestId
import com.aeibi.avd.core.common.ProjectId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * A server adapter may use Ktor or another implementation, but it must not expose its handle
 * outside this domain. The coordinator owns the returned session until it is stopped.
 */
interface PreviewBackend {
    suspend fun start(projectId: ProjectId): OperationResult<PreviewBackendSession>
}

interface PreviewBackendSession {
    val endpoint: PreviewEndpoint
    suspend fun stop(): OperationResult<Unit>
}

/**
 * Deliberate composition-root fallback until the controlled workspace-backed server adapter is
 * migrated from the legacy app. It fails visibly instead of making a ViewModel own a server again.
 */
@Singleton
class UnavailablePreviewBackend @Inject constructor() : PreviewBackend {
    override suspend fun start(projectId: ProjectId): OperationResult<PreviewBackendSession> =
        OperationResult.Failure(PreviewRuntimeError.BackendNotConfigured)
}

@Singleton
class PreviewRuntimeCoordinator @Inject constructor(
    private val backend: PreviewBackend,
    @param:ApplicationCoroutineScope private val applicationScope: kotlinx.coroutines.CoroutineScope
) : PreviewProjectRuntimeControl {
    private val stopTimeout: Duration = 5.seconds

    private val gate = Mutex()
    private val stateLock = Any()
    private val states = mutableMapOf<ProjectId, MutableStateFlow<PreviewRuntimeSnapshot>>()
    private val executions = mutableMapOf<ProjectId, PreviewExecution>()

    fun observe(projectId: ProjectId): StateFlow<PreviewRuntimeSnapshot> = stateFor(projectId)

    suspend fun start(projectId: ProjectId): OperationResult<PreviewRequestReceipt> =
        gate.withLock {
            executions[projectId]?.let { return OperationResult.Success(it.receipt) }

            val execution =
                PreviewExecution(
                    PreviewRequestReceipt(PreviewRequestId(UUID.randomUUID().toString()))
                )
            val startJob = applicationScope.launch(start = CoroutineStart.LAZY) {
                startBackend(projectId, execution)
            }
            execution.startJob = startJob
            executions[projectId] = execution
            stateFor(projectId).value = PreviewRuntimeSnapshot.Starting
            startJob.start()
            OperationResult.Success(execution.receipt)
        }

    override suspend fun stop(projectId: ProjectId): OperationResult<Unit> {
        val stopJob = gate.withLock {
            val execution = executions[projectId] ?: run {
                stateFor(projectId).value = PreviewRuntimeSnapshot.Stopped
                return OperationResult.Success(Unit)
            }
            execution.stopJob ?: applicationScope.launch {
                stateFor(projectId).value = PreviewRuntimeSnapshot.Stopping
                execution.startJob.cancel()
                execution.startJob.join()
                val result = execution.session?.stop() ?: OperationResult.Success(Unit)
                gate.withLock {
                    if (executions[projectId] === execution) {
                        executions.remove(projectId)
                        stateFor(projectId).value = when (result) {
                            is OperationResult.Success -> PreviewRuntimeSnapshot.Stopped
                            is OperationResult.Failure -> PreviewRuntimeSnapshot.Failed(
                                PreviewRuntimeError.BackendStopFailed(result.error.code)
                            )
                        }
                    }
                }
            }.also { execution.stopJob = it }
        }
        return try {
            withTimeout(stopTimeout) { stopJob.join() }
            when (stateFor(projectId).value) {
                is PreviewRuntimeSnapshot.Failed -> OperationResult.Failure(
                    PreviewRuntimeError.StopFailed
                )
                else -> OperationResult.Success(Unit)
            }
        } catch (_: TimeoutCancellationException) {
            OperationResult.Failure(PreviewRuntimeError.StopTimedOut)
        }
    }

    private suspend fun startBackend(projectId: ProjectId, execution: PreviewExecution) {
        val result = try {
            backend.start(projectId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            OperationResult.Failure(PreviewRuntimeError.UnexpectedBackendFailure)
        }
        when (result) {
            is OperationResult.Success -> {
                var stopImmediately = false
                gate.withLock {
                    if (executions[projectId] === execution && execution.stopJob == null) {
                        execution.session = result.value
                        stateFor(projectId).value = PreviewRuntimeSnapshot.Running(
                            endpoint = result.value.endpoint,
                            contentRevision = 0L
                        )
                    } else {
                        stopImmediately = true
                    }
                }
                if (stopImmediately) result.value.stop()
            }
            is OperationResult.Failure -> gate.withLock {
                if (executions[projectId] === execution) {
                    executions.remove(projectId)
                    stateFor(projectId).value = PreviewRuntimeSnapshot.Failed(
                        PreviewRuntimeError.BackendStartFailed(result.error.code)
                    )
                }
            }
        }
    }

    private fun stateFor(projectId: ProjectId): MutableStateFlow<PreviewRuntimeSnapshot> =
        synchronized(stateLock) {
            states.getOrPut(projectId) { MutableStateFlow(PreviewRuntimeSnapshot.Stopped) }
        }

    private class PreviewExecution(val receipt: PreviewRequestReceipt) {
        lateinit var startJob: Job
        var stopJob: Job? = null
        var session: PreviewBackendSession? = null
    }
}

data class PreviewRequestReceipt(val requestId: PreviewRequestId)

data class PreviewEndpoint(val value: String)

sealed interface PreviewRuntimeSnapshot {
    data object Stopped : PreviewRuntimeSnapshot
    data object Starting : PreviewRuntimeSnapshot
    data class Running(val endpoint: PreviewEndpoint, val contentRevision: Long) :
        PreviewRuntimeSnapshot
    data object Stopping : PreviewRuntimeSnapshot
    data class Failed(val error: PreviewRuntimeError) : PreviewRuntimeSnapshot
}

sealed class PreviewRuntimeError(override val code: ErrorCode, override val retryable: Boolean) :
    AppError {
    data class BackendStartFailed(val backendCode: ErrorCode) :
        PreviewRuntimeError(ErrorCode("preview_start_failed"), true)

    data class BackendStopFailed(val backendCode: ErrorCode) :
        PreviewRuntimeError(ErrorCode("preview_stop_failed"), true)

    data object StopTimedOut : PreviewRuntimeError(ErrorCode("preview_stop_timed_out"), true)
    data object StopFailed : PreviewRuntimeError(ErrorCode("preview_stop_failed"), true)
    data object BackendNotConfigured : PreviewRuntimeError(
        ErrorCode("preview_backend_not_configured"),
        false
    )
    data object UnexpectedBackendFailure : PreviewRuntimeError(
        ErrorCode("preview_backend_unavailable"),
        true
    )
}

class ObservePreviewRuntimeUseCase @Inject constructor(
    private val coordinator: PreviewRuntimeCoordinator
) {
    operator fun invoke(projectId: ProjectId): Flow<PreviewRuntimeSnapshot> =
        coordinator.observe(projectId)
}

class StartPreviewUseCase @Inject constructor(private val coordinator: PreviewRuntimeCoordinator) {
    suspend operator fun invoke(projectId: ProjectId): OperationResult<PreviewRequestReceipt> =
        coordinator.start(projectId)
}

class StopPreviewUseCase @Inject constructor(private val coordinator: PreviewRuntimeCoordinator) {
    suspend operator fun invoke(projectId: ProjectId): OperationResult<Unit> =
        coordinator.stop(projectId)
}
