package com.aeibi.avd.agent.runtimekoog

import com.aeibi.avd.contract.agent.AgentRunRequest
import com.aeibi.avd.contract.agent.AgentRuntime
import com.aeibi.avd.contract.agent.AgentRuntimeEvent
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Temporary runtime shell. The real implementation owns the Koog ReAct graph
 * and converts request-bound contract tools to Koog tools without importing data modules.
 */
internal class KoogAgentRuntime @Inject constructor() : AgentRuntime {
    override fun run(request: AgentRunRequest): Flow<AgentRuntimeEvent> = flow {
        emit(AgentRuntimeEvent.Started(operationId = "agent-run"))
        emit(AgentRuntimeEvent.Completed)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AgentRuntimeKoogModule {
    @Binds
    abstract fun bindAgentRuntime(implementation: KoogAgentRuntime): AgentRuntime
}
