package com.aeibi.avd.data.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aeibi.avd.core.model.AppLanguage
import com.aeibi.avd.core.model.ThemeMode
import com.aeibi.avd.core.model.ThemePalette
import com.aeibi.avd.core.model.ThemePreference
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.appearancePreferences by preferencesDataStore(name = "appearance_preferences")

internal class DataStoreThemePreferenceRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ThemePreferenceRepository {
    override fun observeThemePreference(): Flow<ThemePreference> =
        context.appearancePreferences.data.map { preferences ->
            ThemePreference(
                palette = preferences[ThemePaletteKey].toThemePalette(),
                mode = preferences[ThemeModeKey].toThemeMode()
            )
        }

    override suspend fun updateThemePreference(preference: ThemePreference) {
        context.appearancePreferences.edit { preferences ->
            preferences[ThemePaletteKey] = preference.palette.name
            preferences[ThemeModeKey] = preference.mode.name
        }
    }
}

internal class AppCompatAppLanguageRepository @Inject constructor() : AppLanguageRepository {
    override fun currentLanguage(): AppLanguage = AppLanguage.fromLanguageTag(
        AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag()
    )

    override suspend fun updateLanguage(language: AppLanguage) {
        withContext(Dispatchers.Main.immediate) {
            val locales = language.languageTag
                ?.let(LocaleListCompat::forLanguageTags)
                ?: LocaleListCompat.getEmptyLocaleList()
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsDataModule {
    @Binds
    abstract fun bindThemePreferenceRepository(
        implementation: DataStoreThemePreferenceRepository
    ): ThemePreferenceRepository

    @Binds
    abstract fun bindAppLanguageRepository(
        implementation: AppCompatAppLanguageRepository
    ): AppLanguageRepository
}

private val ThemeModeKey: Preferences.Key<String> = stringPreferencesKey("theme_mode")
private val ThemePaletteKey: Preferences.Key<String> = stringPreferencesKey("theme_palette")

private fun String?.toThemeMode(): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == this } ?: ThemeMode.SYSTEM

private fun String?.toThemePalette(): ThemePalette =
    ThemePalette.entries.firstOrNull { it.name == this } ?: ThemePalette.TERRACOTTA
