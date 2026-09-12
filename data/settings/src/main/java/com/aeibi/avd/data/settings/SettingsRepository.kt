package com.aeibi.avd.data.settings

import com.aeibi.avd.core.model.AppLanguage
import com.aeibi.avd.core.model.ThemePreference
import kotlinx.coroutines.flow.Flow

interface ThemePreferenceRepository {
    fun observeThemePreference(): Flow<ThemePreference>

    suspend fun updateThemePreference(preference: ThemePreference)
}

/**
 * Android's per-app locale APIs are the source of truth for this preference so that
 * selections made in Android Settings and in-app stay synchronized.
 */
interface AppLanguageRepository {
    fun currentLanguage(): AppLanguage

    suspend fun updateLanguage(language: AppLanguage)
}
