package com.aeibi.avd.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsRoute(onNavigateBack: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel()
    SettingsRouteContent(onNavigateBack, viewModel)
}

@Composable
private fun SettingsRouteContent(onNavigateBack: () -> Unit, viewModel: SettingsViewModel) {
    var destinationName by rememberSaveable { mutableStateOf(SettingsDestination.HOME.name) }
    val destination = SettingsDestination.valueOf(destinationName)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (destination) {
        SettingsDestination.HOME -> SettingsHomeScreen(
            uiState = uiState,
            onNavigateBack = onNavigateBack,
            onAppearanceSelected = { destinationName = SettingsDestination.APPEARANCE.name },
            onLanguageSelected = { destinationName = SettingsDestination.LANGUAGE.name },
            onDismissError = viewModel::dismissError
        )
        SettingsDestination.APPEARANCE -> AppearanceSettingsScreen(
            uiState = uiState,
            onNavigateBack = { destinationName = SettingsDestination.HOME.name },
            onThemeModeSelected = viewModel::selectThemeMode,
            onThemePaletteSelected = viewModel::selectThemePalette,
            onDismissError = viewModel::dismissError
        )
        SettingsDestination.LANGUAGE -> LanguageSettingsScreen(
            uiState = uiState,
            onNavigateBack = { destinationName = SettingsDestination.HOME.name },
            onLanguageSelected = viewModel::selectLanguage,
            onDismissError = viewModel::dismissError
        )
    }
}

private enum class SettingsDestination {
    HOME,
    APPEARANCE,
    LANGUAGE
}
