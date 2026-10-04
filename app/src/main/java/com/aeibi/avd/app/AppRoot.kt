package com.aeibi.avd.app

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
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
import com.aeibi.avd.feature.settings.SettingsRoute
import com.aeibi.avd.feature.settings.appearance.AppearanceSettingsRoute
import com.aeibi.avd.feature.settings.language.LanguageSettingsRoute
import com.aeibi.avd.feature.version.VersionRoute
import kotlinx.serialization.Serializable

@Composable
fun AppRoot() {
    val backStack = rememberNavBackStack(AppDestination.List)
    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) },
        popTransitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) },
        predictivePopTransitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<AppDestination.List> {
                ProjectsRoute(
                    onNavigateToProject = { backStack.add(AppDestination.Chat(it.value)) },
                    onNavigateToSettings = { backStack.add(AppDestination.Settings) }
                )
            }
            entry<AppDestination.Chat> { destination ->
                ChatRoute(
                    projectId = ProjectId(destination.projectId),
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onNavigateToPreview = {
                        backStack.add(AppDestination.Preview(destination.projectId))
                    },
                    onNavigateToBuild = {
                        backStack.add(AppDestination.Build(destination.projectId))
                    },
                    onNavigateToVersion = {
                        backStack.add(AppDestination.Version(destination.projectId))
                    }
                )
            }
            entry<AppDestination.Preview> { destination ->
                PreviewRoute(
                    projectId = ProjectId(destination.projectId),
                    onNavigateBack = { backStack.removeLastOrNull() }
                )
            }
            entry<AppDestination.Build> { destination ->
                BuildRoute(
                    projectId = ProjectId(destination.projectId),
                    onNavigateBack = { backStack.removeLastOrNull() }
                )
            }
            entry<AppDestination.Version> { destination ->
                VersionRoute(
                    projectId = ProjectId(destination.projectId),
                    onNavigateBack = { backStack.removeLastOrNull() }
                )
            }
            entry<AppDestination.Settings> {
                SettingsRoute(
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onNavigateToAppearanceSettings = {
                        backStack.add(AppDestination.AppearanceSettings)
                    },
                    onNavigateToLanguageSettings = {
                        backStack.add(AppDestination.LanguageSettings)
                    }
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
    data class Chat(val projectId: String) : AppDestination

    @Serializable
    data class Preview(val projectId: String) : AppDestination

    @Serializable
    data class Build(val projectId: String) : AppDestination

    @Serializable
    data class Version(val projectId: String) : AppDestination

    @Serializable
    data object Settings : AppDestination

    @Serializable
    data object AppearanceSettings : AppDestination

    @Serializable
    data object LanguageSettings : AppDestination
}
