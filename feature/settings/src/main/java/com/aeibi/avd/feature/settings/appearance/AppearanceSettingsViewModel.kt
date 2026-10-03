package com.aeibi.avd.feature.settings.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.avd.core.common.AppError
import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.model.ThemeMode
import com.aeibi.avd.core.model.ThemePalette
import com.aeibi.avd.core.model.ThemePreference
import com.aeibi.avd.core.ui.UiError
import com.aeibi.avd.domain.settings.ObserveThemePreferenceUseCase
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
internal class AppearanceSettingsViewModel @Inject constructor(
    observeThemePreference: ObserveThemePreferenceUseCase,
    private val updateThemePreference: UpdateThemePreferenceUseCase
) : ViewModel() {
    private val isUpdating = MutableStateFlow(false)
    private val error = MutableStateFlow<UiError?>(null)

    val uiState: StateFlow<AppearanceSettingsUiState> = combine(
        observeThemePreference(),
        isUpdating,
        error
    ) { preference, updating, currentError ->
        AppearanceSettingsUiState(
            themePreference = preference,
            isLoading = false,
            isUpdating = updating,
            error = currentError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppearanceSettingsUiState()
    )

    fun selectThemeMode(mode: ThemeMode) {
        updateTheme(uiState.value.themePreference.copy(mode = mode))
    }

    fun selectThemePalette(palette: ThemePalette) {
        updateTheme(uiState.value.themePreference.copy(palette = palette))
    }

    fun dismissError() {
        error.value = null
    }

    private fun updateTheme(preference: ThemePreference) {
        if (isUpdating.value) return

        isUpdating.value = true
        error.value = null
        viewModelScope.launch {
            when (val result = updateThemePreference(preference)) {
                is OperationResult.Success -> Unit
                is OperationResult.Failure -> error.value = result.error.toUiError()
            }
            isUpdating.value = false
        }
    }
}

internal data class AppearanceSettingsUiState(
    val themePreference: ThemePreference = ThemePreference(),
    val isLoading: Boolean = true,
    val isUpdating: Boolean = false,
    val error: UiError? = null
)

private fun AppError.toUiError(): UiError = UiError(
    messageKey = code.value,
    retryable = retryable
)
