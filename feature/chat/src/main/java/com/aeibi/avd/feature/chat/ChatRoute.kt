package com.aeibi.avd.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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
        ChatScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToPreview = onNavigateToPreview,
            onNavigateToBuild = onNavigateToBuild,
            onNavigateToVersion = onNavigateToVersion
        )
    }
}
