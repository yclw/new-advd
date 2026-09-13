package com.aeibi.avd.domain.settings

import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.model.AppLanguage
import com.aeibi.avd.core.model.ThemePreference
import com.aeibi.avd.data.settings.AppLanguageRepository
import com.aeibi.avd.data.settings.ThemePreferenceRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveThemePreferenceUseCase @Inject constructor(
    private val repository: ThemePreferenceRepository
) {
    operator fun invoke(): Flow<ThemePreference> = repository.observeThemePreference()
}

class UpdateThemePreferenceUseCase @Inject constructor(
    private val repository: ThemePreferenceRepository
) {
    suspend operator fun invoke(preference: ThemePreference): OperationResult<Unit> =
        repository.updateThemePreference(preference)
}

class ObserveAppLanguageUseCase @Inject constructor(private val repository: AppLanguageRepository) {
    operator fun invoke(): Flow<AppLanguage> = repository.observeLanguage()
}

class UpdateAppLanguageUseCase @Inject constructor(private val repository: AppLanguageRepository) {
    suspend operator fun invoke(language: AppLanguage): OperationResult<Unit> =
        repository.updateLanguage(language)
}
