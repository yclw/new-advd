package com.aeibi.avd.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.feature.projects.ProjectInitializationRoute
import com.aeibi.avd.feature.projects.ProjectSetupRoute
import com.aeibi.avd.feature.projects.ProjectsRoute
import com.aeibi.avd.feature.settings.AppearanceSettingsRoute
import com.aeibi.avd.feature.settings.LanguageSettingsRoute
import com.aeibi.avd.feature.settings.SettingsRoute
import com.aeibi.avd.feature.workbench.WorkbenchRoute
import kotlinx.serialization.Serializable

@Composable
fun AppRoot() {
    val backStack = rememberNavBackStack(AppDestination.List)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<AppDestination.List> {
                ProjectsRoute(
                    onProjectSelected = { backStack.add(AppDestination.Workbench(it.value)) },
                    onProjectSetupRequested = { projectId, retry ->
                        backStack.add(AppDestination.Setup(projectId.value, retry))
                    },
                    onInitializationProgressRequested = {
                        backStack.add(AppDestination.Initializing(it.value))
                    },
                    onSettingsRequested = { backStack.add(AppDestination.Settings) }
                )
            }
            entry<AppDestination.Setup> { destination ->
                ProjectSetupRoute(
                    projectId = ProjectId(destination.projectId),
                    retry = destination.retry,
                    onProjectReady = { projectId ->
                        backStack.removeLastOrNull()
                        backStack.add(AppDestination.Workbench(projectId.value))
                    },
                    onNavigateBack = { backStack.removeLastOrNull() }
                )
            }
            entry<AppDestination.Initializing> { destination ->
                ProjectInitializationRoute(
                    projectId = ProjectId(destination.projectId),
                    onProjectReady = { projectId ->
                        backStack.removeLastOrNull()
                        backStack.add(AppDestination.Workbench(projectId.value))
                    },
                    onNavigateBack = { backStack.removeLastOrNull() }
                )
            }
            entry<AppDestination.Workbench> { destination ->
                WorkbenchRoute(
                    ProjectId(destination.projectId),
                    onNavigateBack = { backStack.removeLastOrNull() }
                )
            }
            entry<AppDestination.Settings> {
                SettingsRoute(
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onAppearanceSelected = { backStack.add(AppDestination.AppearanceSettings) },
                    onLanguageSelected = { backStack.add(AppDestination.LanguageSettings) }
                )
            }
            entry<AppDestination.AppearanceSettings> {
                AppearanceSettingsRoute(onNavigateBack = { backStack.removeLastOrNull() })
            }
            entry<AppDestination.LanguageSettings> {
                LanguageSettingsRoute(onNavigateBack = { backStack.removeLastOrNull() })
            }
        }
    )
}

@Serializable
private sealed interface AppDestination : NavKey {
    @Serializable
    data object List : AppDestination

    @Serializable
    data class Setup(val projectId: String, val retry: Boolean) : AppDestination

    @Serializable
    data class Initializing(val projectId: String) : AppDestination

    @Serializable
    data class Workbench(val projectId: String) : AppDestination

    @Serializable
    data object Settings : AppDestination

    @Serializable
    data object AppearanceSettings : AppDestination

    @Serializable
    data object LanguageSettings : AppDestination
}
