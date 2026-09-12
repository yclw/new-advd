package com.aeibi.avd.domain.preview

import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.common.ProjectId
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewRuntimeCoordinatorTest {
    @Test
    fun `preview handle is retained after start request and released by explicit stop`() =
        runBlocking {
            val backend = FakePreviewBackend()
            val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val coordinator = PreviewRuntimeCoordinator(backend, applicationScope)
            val projectId = ProjectId("project")

            success(coordinator.start(projectId))
            withTimeout(1.seconds) {
                coordinator.observe(
                    projectId
                ).filterIsInstance<PreviewRuntimeSnapshot.Running>().first()
            }

            success(coordinator.stop(projectId))
            assertTrue(backend.session.stopped)
            assertEquals(PreviewRuntimeSnapshot.Stopped, coordinator.observe(projectId).value)
            applicationScope.cancel()
        }

    private fun <T> success(result: OperationResult<T>): T =
        (result as? OperationResult.Success<T>)?.value ?: error("Expected success: $result")

    private class FakePreviewBackend : PreviewBackend {
        val session = FakePreviewSession()

        override suspend fun start(projectId: ProjectId): OperationResult<PreviewBackendSession> =
            OperationResult.Success(session)
    }

    private class FakePreviewSession : PreviewBackendSession {
        override val endpoint = PreviewEndpoint("http://127.0.0.1:8080")
        var stopped = false

        override suspend fun stop(): OperationResult<Unit> {
            stopped = true
            return OperationResult.Success(Unit)
        }
    }
}
