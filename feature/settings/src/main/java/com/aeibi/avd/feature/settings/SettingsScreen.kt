package com.aeibi.avd.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsRoute(
    onNavigateBack: () -> Unit,
    onNavigateToAppearanceSettings: () -> Unit,
    onNavigateToLanguageSettings: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNavigateToAppearanceSettings = onNavigateToAppearanceSettings,
        onNavigateToLanguageSettings = onNavigateToLanguageSettings
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    onNavigateToAppearanceSettings: () -> Unit,
    onNavigateToLanguageSettings: () -> Unit
) {
    if (uiState.isLoading) {
        SettingsLoadingScreen()
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { SettingsBackButton(onNavigateBack) }
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
                    onClick = onNavigateToAppearanceSettings
                )
            }
            item {
                SettingsNavigationRow(
                    title = stringResource(R.string.settings_language_title),
                    summary = languageName(uiState.language),
                    onClick = onNavigateToLanguageSettings
                )
            }
        }
    }
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
