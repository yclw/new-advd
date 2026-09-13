package com.aeibi.avd.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.avd.core.common.AppError
import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.model.AppLanguage
import com.aeibi.avd.core.model.ThemeMode
import com.aeibi.avd.core.model.ThemePalette
import com.aeibi.avd.core.ui.UiError
import com.aeibi.avd.domain.settings.ObserveAppLanguageUseCase
import com.aeibi.avd.domain.settings.ObserveThemePreferenceUseCase
import com.aeibi.avd.domain.settings.UpdateAppLanguageUseCase
import com.aeibi.avd.domain.settings.UpdateThemePreferenceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    observeThemePreference: ObserveThemePreferenceUseCase,
    observeAppLanguage: ObserveAppLanguageUseCase,
    private val updateThemePreference: UpdateThemePreferenceUseCase,
    private val updateAppLanguage: UpdateAppLanguageUseCase
) : ViewModel() {
    private val pendingUpdate = MutableStateFlow<SettingsUpdate?>(null)
    private val error = MutableStateFlow<UiError?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        observeThemePreference(),
        observeAppLanguage(),
        pendingUpdate,
        error
    ) { preference, language, pending, currentError ->
        SettingsUiState(
            themePreference = preference,
            language = language,
            isLoading = false,
            pendingUpdate = pending,
            error = currentError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun selectThemeMode(mode: ThemeMode) {
        updateTheme(uiState.value.themePreference.copy(mode = mode))
    }

    fun selectThemePalette(palette: ThemePalette) {
        updateTheme(uiState.value.themePreference.copy(palette = palette))
    }

    fun selectLanguage(language: AppLanguage) {
        perform(SettingsUpdate.LANGUAGE) { updateAppLanguage(language) }
    }

    fun dismissError() {
        error.value = null
    }

    private fun updateTheme(preference: com.aeibi.avd.core.model.ThemePreference) {
        perform(SettingsUpdate.APPEARANCE) { updateThemePreference(preference) }
    }

    private fun perform(update: SettingsUpdate, block: suspend () -> OperationResult<Unit>) {
        if (pendingUpdate.value != null) return

        pendingUpdate.value = update
        error.value = null
        viewModelScope.launch {
            when (val result = block()) {
                is OperationResult.Success -> Unit
                is OperationResult.Failure -> error.value = result.error.toUiError()
            }
            pendingUpdate.value = null
        }
    }
}

private fun AppError.toUiError(): UiError = UiError(
    messageKey = code.value,
    retryable = retryable
)
