package com.aeibi.avd.domain.settings

import com.aeibi.avd.core.common.OperationResult
import com.aeibi.avd.core.model.AppLanguage
import com.aeibi.avd.core.model.ThemeMode
import com.aeibi.avd.core.model.ThemePalette
import com.aeibi.avd.core.model.ThemePreference
import com.aeibi.avd.data.settings.AppLanguageRepository
import com.aeibi.avd.data.settings.ThemePreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearancePreferenceUseCasesTest {
    @Test
    fun observeThemePreferenceReturnsRepositoryValue() = runBlocking {
        val preference = ThemePreference(ThemePalette.GOLD, ThemeMode.DARK)
        val repository = FakeThemePreferenceRepository(preference)

        assertEquals(preference, ObserveThemePreferenceUseCase(repository)().first())
    }

    @Test
    fun updateThemePreferenceForwardsCompletePreference() = runBlocking {
        val repository = FakeThemePreferenceRepository()
        val preference = ThemePreference(ThemePalette.SAGE, ThemeMode.LIGHT)

        val result = UpdateThemePreferenceUseCase(repository)(preference)

        assertEquals(OperationResult.Success(Unit), result)
        assertEquals(preference, repository.preference.value)
    }

    @Test
    fun observeAndUpdateLanguageUseRepository() = runBlocking {
        val repository = FakeAppLanguageRepository(AppLanguage.ENGLISH)

        assertEquals(AppLanguage.ENGLISH, ObserveAppLanguageUseCase(repository)().first())

        val result = UpdateAppLanguageUseCase(repository)(AppLanguage.SIMPLIFIED_CHINESE)

        assertEquals(OperationResult.Success(Unit), result)
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, repository.language.value)
    }
}

private class FakeThemePreferenceRepository(initialValue: ThemePreference = ThemePreference()) :
    ThemePreferenceRepository {
    val preference = MutableStateFlow(initialValue)

    override fun observeThemePreference(): Flow<ThemePreference> = preference

    override suspend fun updateThemePreference(preference: ThemePreference): OperationResult<Unit> {
        this.preference.value = preference
        return OperationResult.Success(Unit)
    }
}

private class FakeAppLanguageRepository(initialValue: AppLanguage = AppLanguage.SYSTEM) :
    AppLanguageRepository {
    val language = MutableStateFlow(initialValue)

    override fun observeLanguage(): Flow<AppLanguage> = language

    override suspend fun updateLanguage(language: AppLanguage): OperationResult<Unit> {
        this.language.value = language
        return OperationResult.Success(Unit)
    }
}
