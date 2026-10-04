package com.aeibi.avd.feature.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.aeibi.avd.core.common.ProjectId

@Composable
fun PreviewRoute(projectId: ProjectId, onNavigateBack: () -> Unit) {
    key(projectId.value) {
        PreviewScreen(onNavigateBack = onNavigateBack)
    }
}
