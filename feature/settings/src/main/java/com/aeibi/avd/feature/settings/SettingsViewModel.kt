package com.aeibi.avd.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.avd.core.model.AppLanguage
import com.aeibi.avd.core.model.ThemePreference
import com.aeibi.avd.domain.settings.ObserveAppLanguageUseCase
import com.aeibi.avd.domain.settings.ObserveThemePreferenceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    observeThemePreference: ObserveThemePreferenceUseCase,
    observeAppLanguage: ObserveAppLanguageUseCase
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        observeThemePreference(),
        observeAppLanguage()
    ) { preference, language ->
        SettingsUiState(themePreference = preference, language = language, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )
}

internal data class SettingsUiState(
    val themePreference: ThemePreference = ThemePreference(),
    val language: AppLanguage = AppLanguage.SYSTEM,
    val isLoading: Boolean = true
)
