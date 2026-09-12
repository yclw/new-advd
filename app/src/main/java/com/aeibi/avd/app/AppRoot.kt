package com.aeibi.avd.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.feature.projects.ProjectsRoute
import com.aeibi.avd.feature.projects.ProjectInitializationRoute
import com.aeibi.avd.feature.projects.ProjectSetupRoute
import com.aeibi.avd.feature.workbench.WorkbenchRoute

@Composable
fun AppRoot() {
    var destination by remember { mutableStateOf<AppDestination>(AppDestination.List) }
    when (val current = destination) {
        AppDestination.List -> ProjectsRoute(
            onProjectSelected = { destination = AppDestination.Workbench(it.value) },
            onProjectSetupRequested = { projectId, retry ->
                destination = AppDestination.Setup(projectId.value, retry)
            },
            onInitializationProgressRequested = {
                destination = AppDestination.Initializing(it.value)
            }
        )
        is AppDestination.Setup -> ProjectSetupRoute(
            projectId = ProjectId(current.projectId),
            retry = current.retry,
            onProjectReady = { destination = AppDestination.Workbench(it.value) },
            onNavigateBack = { destination = AppDestination.List }
        )
        is AppDestination.Initializing -> ProjectInitializationRoute(
            projectId = ProjectId(current.projectId),
            onProjectReady = { destination = AppDestination.Workbench(it.value) },
            onNavigateBack = { destination = AppDestination.List }
        )
        is AppDestination.Workbench -> WorkbenchRoute(
            ProjectId(current.projectId),
            onNavigateBack = { destination = AppDestination.List }
        )
    }
}

private sealed interface AppDestination {
    data object List : AppDestination
    data class Setup(val projectId: String, val retry: Boolean) : AppDestination
    data class Initializing(val projectId: String) : AppDestination
    data class Workbench(val projectId: String) : AppDestination
}
