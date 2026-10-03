package com.aeibi.avd.feature.settings.language

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aeibi.avd.core.model.AppLanguage
import com.aeibi.avd.feature.settings.R
import com.aeibi.avd.feature.settings.SettingSection
import com.aeibi.avd.feature.settings.SettingsBackButton
import com.aeibi.avd.feature.settings.SettingsErrorDialog
import com.aeibi.avd.feature.settings.SettingsLoadingScreen
import com.aeibi.avd.feature.settings.SingleChoiceSettingRow
import com.aeibi.avd.feature.settings.languageName

@Composable
fun LanguageSettingsRoute(onNavigateBack: () -> Unit) {
    val viewModel: LanguageSettingsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LanguageSettingsScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onLanguageSelected = viewModel::selectLanguage,
        onDismissError = viewModel::dismissError
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LanguageSettingsScreen(
    uiState: LanguageSettingsUiState,
    onNavigateBack: () -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
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
                title = { Text(stringResource(R.string.settings_language_title)) },
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
                SettingSection(title = stringResource(R.string.settings_language_title)) {
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
    SettingsErrorDialog(uiState.error, onDismissError)
}
