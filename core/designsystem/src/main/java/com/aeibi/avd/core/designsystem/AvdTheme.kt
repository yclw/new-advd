package com.aeibi.avd.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.aeibi.avd.core.model.ThemeMode
import com.aeibi.avd.core.model.ThemePreference

@Composable
fun AvdTheme(preference: ThemePreference, content: @Composable () -> Unit) {
    val darkTheme = when (preference.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = colorSchemeFor(preference.palette, darkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AvdTypography,
        shapes = AvdShapes,
        content = content
    )
}
