package com.aeibi.avd.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aeibi.avd.core.model.AppLanguage
import com.aeibi.avd.core.model.ThemeMode
import com.aeibi.avd.core.model.ThemePalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsHomeScreen(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    onAppearanceSelected: () -> Unit,
    onLanguageSelected: () -> Unit,
    onDismissError: () -> Unit
) {
    if (uiState.isLoading) {
        SettingsLoadingScreen()
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { BackButton(onNavigateBack) }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                SettingsNavigationRow(
                    title = stringResource(R.string.settings_appearance_title),
                    summary = stringResource(
                        R.string.settings_appearance_summary,
                        themeModeName(uiState.themePreference.mode),
                        themePaletteName(uiState.themePreference.palette)
                    ),
                    onClick = onAppearanceSelected
                )
            }
            item {
                SettingsNavigationRow(
                    title = stringResource(R.string.settings_language_title),
                    summary = languageName(uiState.language),
                    onClick = onLanguageSelected
                )
            }
        }
    }
    SettingsErrorDialog(uiState, onDismissError)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceSettingsScreen(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onThemePaletteSelected: (ThemePalette) -> Unit,
    onDismissError: () -> Unit
) {
    if (uiState.isLoading) {
        SettingsLoadingScreen()
        return
    }

    val enabled = uiState.pendingUpdate == null
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_appearance_title)) },
                navigationIcon = { BackButton(onNavigateBack) }
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
                SettingSection(
                    title = stringResource(R.string.settings_theme_mode_title),
                    enabled = enabled
                ) {
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
                SettingSection(
                    title = stringResource(R.string.settings_theme_palette_title),
                    enabled = enabled
                ) {
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
    SettingsErrorDialog(uiState, onDismissError)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LanguageSettingsScreen(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    onDismissError: () -> Unit
) {
    if (uiState.isLoading) {
        SettingsLoadingScreen()
        return
    }

    val enabled = uiState.pendingUpdate == null
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_language_title)) },
                navigationIcon = { BackButton(onNavigateBack) }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                SettingSection(
                    title = stringResource(R.string.settings_language_title),
                    enabled = enabled
                ) {
                    AppLanguage.entries.forEach { language ->
                        SingleChoiceSettingRow(
                            title = languageName(language),
                            selected = uiState.language == language,
                            enabled = enabled,
                            onClick = { onLanguageSelected(language) }
                        )
                    }
                }
            }
        }
    }
    SettingsErrorDialog(uiState, onDismissError)
}

@Composable
private fun SettingsNavigationRow(title: String, summary: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) }
    )
}

@Composable
private fun SettingSection(title: String, enabled: Boolean, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        Text(text = title, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        content()
    }
}

@Composable
private fun SingleChoiceSettingRow(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            ),
        headlineContent = { Text(title) },
        trailingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) }
    )
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(stringResource(R.string.settings_back)) }
}

@Composable
private fun SettingsLoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SettingsErrorDialog(uiState: SettingsUiState, onDismiss: () -> Unit) {
    if (uiState.error == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_error_title)) },
        text = { Text(stringResource(R.string.settings_error_update)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_ok))
            }
        }
    )
}

@Composable
private fun themeModeName(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_mode_system
        ThemeMode.LIGHT -> R.string.settings_theme_mode_light
        ThemeMode.DARK -> R.string.settings_theme_mode_dark
    }
)

@Composable
private fun themePaletteName(palette: ThemePalette): String = stringResource(
    when (palette) {
        ThemePalette.TERRACOTTA -> R.string.settings_theme_palette_terracotta
        ThemePalette.SAGE -> R.string.settings_theme_palette_sage
        ThemePalette.GOLD -> R.string.settings_theme_palette_gold
    }
)

@Composable
private fun languageName(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.ENGLISH -> R.string.settings_language_english
        AppLanguage.SIMPLIFIED_CHINESE -> R.string.settings_language_simplified_chinese
    }
)
