package com.aeibi.avd.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aeibi.avd.core.common.ProjectId

@Composable
fun ChatRoute(
    projectId: ProjectId,
    onNavigateBack: () -> Unit,
    onNavigateToPreview: () -> Unit,
    onNavigateToBuild: () -> Unit,
    onNavigateToVersion: () -> Unit
) {
    key(projectId.value) {
        val viewModel: ChatViewModel = viewModel(key = "chat-${projectId.value}")
        val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
        ChatScreen(
            uiState = uiState,
            onDraftChange = viewModel::updateDraft,
            onClearDraft = viewModel::clearDraft,
            onNewSession = viewModel::newSession,
            onSelectSession = viewModel::selectSession,
            onSend = viewModel::send,
            onNavigateBack = onNavigateBack,
            onNavigateToPreview = onNavigateToPreview,
            onNavigateToBuild = onNavigateToBuild,
            onNavigateToVersion = onNavigateToVersion
        )
    }
}
