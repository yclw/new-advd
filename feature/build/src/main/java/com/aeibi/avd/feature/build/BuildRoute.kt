package com.aeibi.avd.feature.build

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.aeibi.avd.core.common.ProjectId

@Composable
fun BuildRoute(projectId: ProjectId, onNavigateBack: () -> Unit) {
    key(projectId.value) {
        BuildScreen(onNavigateBack = onNavigateBack)
    }
}
