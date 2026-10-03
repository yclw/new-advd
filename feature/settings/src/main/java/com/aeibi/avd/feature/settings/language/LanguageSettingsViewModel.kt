package com.aeibi.avd.feature.settings.language

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.avd.core.common.AppError
import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.model.AppLanguage
import com.aeibi.avd.core.ui.UiError
import com.aeibi.avd.domain.settings.ObserveAppLanguageUseCase
import com.aeibi.avd.domain.settings.UpdateAppLanguageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
internal class LanguageSettingsViewModel @Inject constructor(
    observeAppLanguage: ObserveAppLanguageUseCase,
    private val updateAppLanguage: UpdateAppLanguageUseCase
) : ViewModel() {
    private val isUpdating = MutableStateFlow(false)
    private val error = MutableStateFlow<UiError?>(null)

    val uiState: StateFlow<LanguageSettingsUiState> = combine(
        observeAppLanguage(),
        isUpdating,
        error
    ) { language, updating, currentError ->
        LanguageSettingsUiState(
            language = language,
            isLoading = false,
            isUpdating = updating,
            error = currentError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LanguageSettingsUiState()
    )

    fun selectLanguage(language: AppLanguage) {
        if (isUpdating.value) return

        isUpdating.value = true
        error.value = null
        viewModelScope.launch {
            when (val result = updateAppLanguage(language)) {
                is OperationResult.Success -> Unit
                is OperationResult.Failure -> error.value = result.error.toUiError()
            }
            isUpdating.value = false
        }
    }

    fun dismissError() {
        error.value = null
    }
}

internal data class LanguageSettingsUiState(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val isLoading: Boolean = true,
    val isUpdating: Boolean = false,
    val error: UiError? = null
)

private fun AppError.toUiError(): UiError = UiError(
    messageKey = code.value,
    retryable = retryable
)
