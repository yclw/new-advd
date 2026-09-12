package com.aeibi.avd.domain.agent

import com.aeibi.avd.contract.agent.AgentRunRequest
import com.aeibi.avd.contract.agent.AgentRuntime
import com.aeibi.avd.contract.agent.AgentRuntimeEvent
import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.core.common.SessionId
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRuntimeCoordinatorTest {
    @Test
    fun `accepted turn survives caller and is cancelled only by runtime command`() = runTest {
        val runtime = BlockingAgentRuntime()
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = AgentRuntimeCoordinator(runtime, applicationScope)
        val projectId = ProjectId("project")

        val first = success(
            coordinator.run(
                RunAgentTurnRequest(
                    projectId = projectId,
                    sessionId = SessionId("session"),
                    prompt = "hello",
                    idempotencyKey = "request-1"
                )
            )
        )
        withTimeout(1.seconds) {
            coordinator.observe(projectId).filterIsInstance<AgentRuntimeSnapshot.Running>().first()
        }

        val retry = success(
            coordinator.run(
                RunAgentTurnRequest(projectId, SessionId("session"), "hello", "request-1")
            )
        )
        assertEquals(first, retry)

        success(coordinator.requestStop(projectId))
        assertTrue(
            coordinator.run(
                RunAgentTurnRequest(projectId, SessionId("session"), "second", "request-2")
            ) is OperationResult.Failure
        )
        success(coordinator.awaitStopped(projectId, 1.seconds))
        assertTrue(runtime.cancelled.isCompleted)
        assertEquals(AgentRuntimeSnapshot.Idle, coordinator.observe(projectId).value)
        applicationScope.cancel()
    }

    private fun runTest(block: suspend () -> Unit) = kotlinx.coroutines.runBlocking { block() }

    private fun <T> success(result: OperationResult<T>): T =
        (result as? OperationResult.Success<T>)?.value ?: error("Expected success: $result")

    private class BlockingAgentRuntime : AgentRuntime {
        val cancelled = CompletableDeferred<Unit>()

        override fun run(request: AgentRunRequest): Flow<AgentRuntimeEvent> = flow {
            emit(AgentRuntimeEvent.Started("provider-operation"))
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }
    }
}
