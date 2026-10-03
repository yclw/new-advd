package com.aeibi.avd.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.aeibi.avd.core.model.AppLanguage
import com.aeibi.avd.core.model.ThemeMode
import com.aeibi.avd.core.model.ThemePalette
import com.aeibi.avd.core.ui.NavigateBackButton
import com.aeibi.avd.core.ui.UiError

@Composable
internal fun SettingSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        Text(text = title, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        content()
    }
}

@Composable
internal fun SingleChoiceSettingRow(
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
internal fun SettingsBackButton(onNavigateBack: () -> Unit) {
    NavigateBackButton(
        contentDescription = stringResource(R.string.settings_back),
        onClick = dropUnlessResumed { onNavigateBack() }
    )
}

@Composable
internal fun SettingsLoadingScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
internal fun SettingsErrorDialog(error: UiError?, onDismiss: () -> Unit) {
    if (error == null) return

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
internal fun themeModeName(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_mode_system
        ThemeMode.LIGHT -> R.string.settings_theme_mode_light
        ThemeMode.DARK -> R.string.settings_theme_mode_dark
    }
)

@Composable
internal fun themePaletteName(palette: ThemePalette): String = stringResource(
    when (palette) {
        ThemePalette.TERRACOTTA -> R.string.settings_theme_palette_terracotta
        ThemePalette.SAGE -> R.string.settings_theme_palette_sage
        ThemePalette.GOLD -> R.string.settings_theme_palette_gold
    }
)

@Composable
internal fun languageName(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.ENGLISH -> R.string.settings_language_english
        AppLanguage.SIMPLIFIED_CHINESE -> R.string.settings_language_simplified_chinese
    }
)
