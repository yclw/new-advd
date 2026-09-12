package com.aeibi.avd.core.model

/** The source used to resolve the application's Material color scheme. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/** A complete Material Theme Builder palette supplied by the application. */
enum class ThemePalette {
    TERRACOTTA,
    SAGE,
    GOLD
}

data class ThemePreference(
    val palette: ThemePalette = ThemePalette.TERRACOTTA,
    val mode: ThemeMode = ThemeMode.SYSTEM
)

/** The supported per-app language choices. SYSTEM delegates to the device locale. */
enum class AppLanguage(val languageTag: String?) {
    SYSTEM(languageTag = null),
    ENGLISH(languageTag = "en-US"),
    SIMPLIFIED_CHINESE(languageTag = "zh-CN");

    companion object {
        fun fromLanguageTag(languageTag: String?): AppLanguage =
            entries.firstOrNull { it.languageTag == languageTag } ?: SYSTEM
    }
}
