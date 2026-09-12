package com.aeibi.avd.domain.project

import com.aeibi.avd.contract.projectruntime.AgentProjectRuntimeControl
import com.aeibi.avd.contract.projectruntime.PreviewProjectRuntimeControl
import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.ProjectId
import kotlin.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectRuntimeLifecycleUseCasesTest {
    @Test
    fun `close stops agent before preview and then completes close admission`() = runBlocking {
        val events = mutableListOf<String>()
        val agent = RecordingAgentControl(events)
        val preview = object : PreviewProjectRuntimeControl {
            override suspend fun stop(projectId: ProjectId): OperationResult<Unit> {
                events += "preview.stop"
                return OperationResult.Success(Unit)
            }
        }

        val result = ConfirmProjectRuntimeCloseUseCase(agent, preview)(ProjectId("project"))

        assertEquals(OperationResult.Success(Unit), result)
        assertEquals(
            listOf(
                "agent.requestStop",
                "agent.awaitStopped",
                "preview.stop",
                "agent.completeClose"
            ),
            events
        )
    }

    private class RecordingAgentControl(private val events: MutableList<String>) :
        AgentProjectRuntimeControl {
        override suspend fun hasRunningTurn(projectId: ProjectId): Boolean = false

        override suspend fun requestStop(projectId: ProjectId): OperationResult<Unit> {
            events += "agent.requestStop"
            return OperationResult.Success(Unit)
        }

        override suspend fun awaitStopped(
            projectId: ProjectId,
            timeout: Duration
        ): OperationResult<Unit> {
            events += "agent.awaitStopped"
            return OperationResult.Success(Unit)
        }

        override suspend fun completeClose(projectId: ProjectId): OperationResult<Unit> {
            events += "agent.completeClose"
            return OperationResult.Success(Unit)
        }

        override suspend fun markInterruptedAfterProcessDeath(
            projectId: ProjectId
        ): OperationResult<Unit> = OperationResult.Success(Unit)
    }
}
