package com.aeibi.avd.contract.agent

import kotlinx.coroutines.flow.Flow

/** SDK-neutral runtime port implemented by a ReAct runtime such as Koog. */
interface AgentRuntime {
    fun run(request: AgentRunRequest): Flow<AgentRuntimeEvent>
}

data class AgentRunRequest(val prompt: String, val tools: List<AgentTool>)

sealed interface AgentRuntimeEvent {
    data class Started(val operationId: String) : AgentRuntimeEvent
    data class TextDelta(val text: String) : AgentRuntimeEvent
    data class ToolStarted(val name: String) : AgentRuntimeEvent
    data class ToolFinished(val name: String) : AgentRuntimeEvent
    data object Completed : AgentRuntimeEvent
    data class Failed(val message: String, val retryable: Boolean) : AgentRuntimeEvent
}

/** SDK-neutral description and invocation contract for an Agent-visible tool. */
interface AgentTool {
    val definition: AgentToolDefinition

    suspend fun execute(call: AgentToolCall): AgentToolResult
}

data class AgentToolDefinition(
    val name: String,
    val description: String,
    val inputSchemaJson: String
)

data class AgentToolCall(val argumentsJson: String)

sealed interface AgentToolResult {
    data class Success(val content: String) : AgentToolResult
    data class Failure(val message: String, val retryable: Boolean) : AgentToolResult
}
