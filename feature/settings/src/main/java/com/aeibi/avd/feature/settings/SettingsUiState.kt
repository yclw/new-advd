package com.aeibi.avd.feature.settings

import com.aeibi.avd.core.model.AppLanguage
import com.aeibi.avd.core.model.ThemePreference
import com.aeibi.avd.core.ui.UiError

internal data class SettingsUiState(
    val themePreference: ThemePreference = ThemePreference(),
    val language: AppLanguage = AppLanguage.SYSTEM,
    val isLoading: Boolean = true,
    val pendingUpdate: SettingsUpdate? = null,
    val error: UiError? = null
)

internal enum class SettingsUpdate {
    APPEARANCE,
    LANGUAGE
}
