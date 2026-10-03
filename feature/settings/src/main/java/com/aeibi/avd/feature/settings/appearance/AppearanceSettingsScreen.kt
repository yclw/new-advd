package com.aeibi.avd.feature.settings.appearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aeibi.avd.core.model.ThemeMode
import com.aeibi.avd.core.model.ThemePalette
import com.aeibi.avd.feature.settings.R
import com.aeibi.avd.feature.settings.SettingSection
import com.aeibi.avd.feature.settings.SettingsBackButton
import com.aeibi.avd.feature.settings.SettingsErrorDialog
import com.aeibi.avd.feature.settings.SettingsLoadingScreen
import com.aeibi.avd.feature.settings.SingleChoiceSettingRow
import com.aeibi.avd.feature.settings.themeModeName
import com.aeibi.avd.feature.settings.themePaletteName

@Composable
fun AppearanceSettingsRoute(onNavigateBack: () -> Unit) {
    val viewModel: AppearanceSettingsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AppearanceSettingsScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onThemeModeSelected = viewModel::selectThemeMode,
        onThemePaletteSelected = viewModel::selectThemePalette,
        onDismissError = viewModel::dismissError
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceSettingsScreen(
    uiState: AppearanceSettingsUiState,
    onNavigateBack: () -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onThemePaletteSelected: (ThemePalette) -> Unit,
    onDismissError: () -> Unit
) {
    if (uiState.isLoading) {
        SettingsLoadingScreen()
        return
    }

    val enabled = !uiState.isUpdating
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_appearance_title)) },
                navigationIcon = { SettingsBackButton(onNavigateBack) }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SettingSection(title = stringResource(R.string.settings_theme_mode_title)) {
                    ThemeMode.entries.forEach { mode ->
                        SingleChoiceSettingRow(
                            title = themeModeName(mode),
                            selected = uiState.themePreference.mode == mode,
                            enabled = enabled,
                            onClick = { onThemeModeSelected(mode) }
                        )
                    }
                }
            }
            item {
                SettingSection(title = stringResource(R.string.settings_theme_palette_title)) {
                    ThemePalette.entries.forEach { palette ->
                        SingleChoiceSettingRow(
                            title = themePaletteName(palette),
                            selected = uiState.themePreference.palette == palette,
                            enabled = enabled,
                            onClick = { onThemePaletteSelected(palette) }
                        )
                    }
                }
            }
        }
    }
    SettingsErrorDialog(uiState.error, onDismissError)
}
