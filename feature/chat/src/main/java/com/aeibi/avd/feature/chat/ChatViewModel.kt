package com.aeibi.avd.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class ChatRole { User, Assistant }

internal data class ChatMessageUi(
    val id: String,
    val role: ChatRole,
    val markdown: String,
    val streaming: Boolean = false
)

internal data class ChatSessionUi(
    val id: String,
    val title: String,
    val messages: List<ChatMessageUi> = emptyList()
)

internal data class ChatUiState(
    val sessions: List<ChatSessionUi> = emptyList(),
    val selectedSessionId: String? = null,
    val draft: String = ""
) {
    val selectedSession: ChatSessionUi?
        get() = sessions.firstOrNull { it.id == selectedSessionId }
}

/** Feature-only state owner. Replace [streamDemoReply] with agent events when the runtime is wired. */
internal class ChatViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(ChatUiState())
    val uiState = mutableUiState.asStateFlow()
    private val streamJobs = mutableMapOf<String, Job>()

    fun updateDraft(value: String) = mutableUiState.update { it.copy(draft = value) }

    fun clearDraft() = updateDraft("")

    fun newSession() {
        if (mutableUiState.value.selectedSession?.messages?.isEmpty() == true) {
            clearDraft()
            return
        }
        val session = ChatSessionUi(id = UUID.randomUUID().toString(), title = "")
        mutableUiState.update {
            it.copy(
                sessions = listOf(session) + it.sessions,
                selectedSessionId = session.id,
                draft = ""
            )
        }
    }

    fun selectSession(id: String) {
        mutableUiState.update { state ->
            if (state.sessions.none { it.id == id }) {
                state
            } else {
                state.copy(selectedSessionId = id, draft = "")
            }
        }
    }

    fun send() {
        val prompt = mutableUiState.value.draft.trim()
        if (prompt.isEmpty()) return
        val selected = mutableUiState.value.selectedSession
        val sessionId = selected?.id ?: UUID.randomUUID().toString()
        val user = ChatMessageUi(UUID.randomUUID().toString(), ChatRole.User, prompt)
        val assistant =
            ChatMessageUi(UUID.randomUUID().toString(), ChatRole.Assistant, "", streaming = true)
        mutableUiState.update { state ->
            val session = selected ?: ChatSessionUi(sessionId, "")
            val updated = session.copy(
                title = session.title.ifBlank { prompt.lineSequence().first().take(48) },
                messages = session.messages + user + assistant
            )
            state.copy(
                sessions = listOf(updated) + state.sessions.filterNot { it.id == sessionId },
                selectedSessionId = sessionId,
                draft = ""
            )
        }
        streamJobs[sessionId]?.cancel()
        streamJobs[sessionId] = viewModelScope.launch { streamDemoReply(sessionId, assistant.id) }
    }

    private suspend fun streamDemoReply(sessionId: String, messageId: String) {
        val demo = """
            ## 界面演示界面演示界面演示界面演示界面演示界面演示界面演示界面演示界面演示界面演示界面演示界面演示界面演示界面演示界面演示界面演示界面演示界面演示界面演示界面演示界面演示界面演示界面演示界面演示界面演示

            这是一段 **本地模拟流**，用于检查 Markdown 排版、双缓冲过渡和滚轮跟随。尚未接入 Agent。

            - 逐字追加时，底部会自动跟随。
            - 向上滚动后会暂停跟随；点右下角按钮可回到底部。
            - 切换 session 后，每个会话保留自己的消息。

            ```kotlin
            fun greet() = "Hello, AVD!"
            ```
            
            ## 界面演示

            这是一段 **本地模拟流**，用于检查 Markdown 排版、双缓冲过渡和滚轮跟随。尚未接入 Agent。

            - 逐字追加时，底部会自动跟随。
            - 向上滚动后会暂停跟随；点右下角按钮可回到底部。
            - 切换 session 后，每个会话保留自己的消息。

            ```kotlin
            fun greet() = "Hello, AVD!"
            ```
            
            ## 界面演示

            这是一段 **本地模拟流**，用于检查 Markdown 排版、双缓冲过渡和滚轮跟随。尚未接入 Agent。

            - 逐字追加时，底部会自动跟随。
            - 向上滚动后会暂停跟随；点右下角按钮可回到底部。
            - 切换 session 后，每个会话保留自己的消息。

            ```kotlin
            fun greet() = "Hello, AVD!"
            ```
            
            ## 界面演示

            这是一段 **本地模拟流**，用于检查 Markdown 排版、双缓冲过渡和滚轮跟随。尚未接入 Agent。

            - 逐字追加时，底部会自动跟随。
            - 向上滚动后会暂停跟随；点右下角按钮可回到底部。
            - 切换 session 后，每个会话保留自己的消息。

            ```kotlin
            fun greet() = "Hello, AVD!"
            ```
            
            ## 界面演示

            这是一段 **本地模拟流**，用于检查 Markdown 排版、双缓冲过渡和滚轮跟随。尚未接入 Agent。

            - 逐字追加时，底部会自动跟随。
            - 向上滚动后会暂停跟随；点右下角按钮可回到底部。
            - 切换 session 后，每个会话保留自己的消息。

            ```kotlin
            fun greet() = "Hello, AVD!"
            fun greet() = "Hello, AVD!"
            fun greet() = "Hello, AVD!"
            fun greet() = "Hello, AVD!"
            fun greet() = "Hello, AVD!"
            ```
        """.trimIndent()
        try {
            demo.chunked(3).forEach { chunk ->
                delay(45.milliseconds)
                updateMessage(sessionId, messageId) { it.copy(markdown = it.markdown + chunk) }
            }
        } finally {
            updateMessage(sessionId, messageId) { it.copy(streaming = false) }
            if (streamJobs[sessionId] == currentCoroutineContext()[Job]) {
                streamJobs.remove(sessionId)
            }
        }
    }

    private fun updateMessage(
        sessionId: String,
        messageId: String,
        transform: (ChatMessageUi) -> ChatMessageUi
    ) {
        mutableUiState.update { state ->
            state.copy(
                sessions = state.sessions.map { session ->
                    if (session.id != sessionId) {
                        session
                    } else {
                        session.copy(
                            messages = session.messages.map { message ->
                                if (message.id == messageId) transform(message) else message
                            }
                        )
                    }
                }
            )
        }
    }
}
