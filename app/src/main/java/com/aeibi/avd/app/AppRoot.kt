package com.aeibi.avd.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.aeibi.avd.core.common.ProjectId
import com.aeibi.avd.feature.build.BuildRoute
import com.aeibi.avd.feature.chat.ChatRoute
import com.aeibi.avd.feature.preview.PreviewRoute
import com.aeibi.avd.feature.projects.ProjectsRoute
import com.aeibi.avd.feature.settings.AppearanceSettingsRoute
import com.aeibi.avd.feature.settings.LanguageSettingsRoute
import com.aeibi.avd.feature.settings.SettingsRoute
import com.aeibi.avd.feature.versions.VersionsRoute
import com.aeibi.avd.feature.workbench.WorkbenchRoute
import com.aeibi.avd.feature.workbench.WorkbenchSection
import java.util.UUID
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
        sceneStrategy = WorkbenchSceneStrategy(),
        entryProvider = entryProvider {
            entry<AppDestination.List> {
                ProjectsRoute(
                    onProjectSelected = { backStack.add(AppDestination.Workbench(it.value)) },
                    onSettingsRequested = { backStack.add(AppDestination.Settings) }
                )
            }
            entry<AppDestination.Workbench>(
                metadata = mapOf(WORKBENCH_SCENE_ANCHOR to true)
            ) { destination ->
                ProjectRoute(destination.projectId, onNavigateBack = {
                    backStack.removeLastOrNull()
                }) {
                    WorkbenchRoute(
                        projectId = it,
                        onNavigateBack = { backStack.removeLastOrNull() },
                        onVersionsRequested = { id ->
                            backStack.add(AppDestination.Versions(id.value))
                        },
                        onBuildRequested = { id -> backStack.add(AppDestination.Build(id.value)) },
                        onSettingsRequested = { backStack.add(AppDestination.Settings) }
                    ) { section, _ ->
                        when (section) {
                            WorkbenchSection.Chat -> ChatRoute()
                            WorkbenchSection.Preview -> PreviewRoute()
                        }
                    }
                }
            }
            entry<AppDestination.Versions>(
                metadata = mapOf(WORKBENCH_SCENE_PAGE to true)
            ) { destination ->
                ProjectRoute(destination.projectId, onNavigateBack = {
                    backStack.removeLastOrNull()
                }) {
                    VersionsRoute(it, onNavigateBack = { backStack.removeLastOrNull() })
                }
            }
            entry<AppDestination.Build>(
                metadata = mapOf(WORKBENCH_SCENE_PAGE to true)
            ) { destination ->
                ProjectRoute(destination.projectId, onNavigateBack = {
                    backStack.removeLastOrNull()
                }) {
                    BuildRoute(it, onNavigateBack = { backStack.removeLastOrNull() })
                }
            }
            entry<AppDestination.Settings>(metadata = mapOf(WORKBENCH_SCENE_PAGE to true)) {
                SettingsRoute(
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onAppearanceSelected = { backStack.add(AppDestination.AppearanceSettings) },
                    onLanguageSelected = { backStack.add(AppDestination.LanguageSettings) }
                )
            }
            entry<AppDestination.AppearanceSettings>(
                metadata = mapOf(WORKBENCH_SCENE_PAGE to true)
            ) {
                AppearanceSettingsRoute(onNavigateBack = { backStack.removeLastOrNull() })
            }
            entry<AppDestination.LanguageSettings>(metadata = mapOf(WORKBENCH_SCENE_PAGE to true)) {
                LanguageSettingsRoute(onNavigateBack = { backStack.removeLastOrNull() })
            }
        }
    )
}

@Composable
private fun ProjectRoute(
    projectId: String,
    onNavigateBack: () -> Unit,
    content: @Composable (ProjectId) -> Unit
) {
    val validId = runCatching {
        UUID.fromString(projectId).toString() == projectId
    }.getOrDefault(false)
    if (validId) {
        content(ProjectId(projectId))
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.app_invalid_project))
            TextButton(onClick = onNavigateBack) { Text(stringResource(R.string.app_back)) }
        }
    }
}

@Serializable
private sealed interface AppDestination : NavKey {
    @Serializable
    data object List : AppDestination

    @Serializable
    data class Workbench(val projectId: String) : AppDestination

    @Serializable
    data class Versions(val projectId: String) : AppDestination

    @Serializable
    data class Build(val projectId: String) : AppDestination

    @Serializable
    data object Settings : AppDestination

    @Serializable
    data object AppearanceSettings : AppDestination

    @Serializable
    data object LanguageSettings : AppDestination
}
