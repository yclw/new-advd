package com.aeibi.avd.feature.version

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.aeibi.avd.core.common.ProjectId

@Composable
fun VersionRoute(projectId: ProjectId, onNavigateBack: () -> Unit) {
    key(projectId.value) {
        VersionScreen(onNavigateBack = onNavigateBack)
    }
}
