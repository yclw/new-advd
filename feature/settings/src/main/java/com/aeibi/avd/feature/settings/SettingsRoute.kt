package com.aeibi.avd.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsRoute(
    onNavigateBack: () -> Unit,
    onAppearanceSelected: () -> Unit,
    onLanguageSelected: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsHomeScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onAppearanceSelected = onAppearanceSelected,
        onLanguageSelected = onLanguageSelected,
        onDismissError = viewModel::dismissError
    )
}

@Composable
fun AppearanceSettingsRoute(onNavigateBack: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AppearanceSettingsScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onThemeModeSelected = viewModel::selectThemeMode,
        onThemePaletteSelected = viewModel::selectThemePalette,
        onDismissError = viewModel::dismissError
    )
}

@Composable
fun LanguageSettingsRoute(onNavigateBack: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LanguageSettingsScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onLanguageSelected = viewModel::selectLanguage,
        onDismissError = viewModel::dismissError
    )
}
