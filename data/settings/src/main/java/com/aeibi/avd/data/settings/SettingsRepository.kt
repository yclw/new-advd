package com.aeibi.avd.data.settings

import com.aeibi.avd.core.common.AppError
import com.aeibi.avd.core.common.ErrorCode
import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.model.AppLanguage
import com.aeibi.avd.core.model.ThemePreference
import kotlinx.coroutines.flow.Flow

interface ThemePreferenceRepository {
    fun observeThemePreference(): Flow<ThemePreference>

    suspend fun updateThemePreference(preference: ThemePreference): OperationResult<Unit>
}

/**
 * Android's per-app locale APIs are the source of truth for this preference so that
 * selections made in Android Settings and in-app stay synchronized.
 */
interface AppLanguageRepository {
    fun observeLanguage(): Flow<AppLanguage>

    suspend fun updateLanguage(language: AppLanguage): OperationResult<Unit>
}

enum class SettingsDataError(override val code: ErrorCode, override val retryable: Boolean) :
    AppError {
    STORAGE_UNAVAILABLE(ErrorCode("settings_storage_unavailable"), retryable = true),
    LANGUAGE_UPDATE_FAILED(ErrorCode("settings_language_update_failed"), retryable = true)
}
