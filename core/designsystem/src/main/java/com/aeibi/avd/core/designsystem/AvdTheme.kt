package com.aeibi.avd.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        shapes = AvdShapes
    ) {
        Surface(modifier = Modifier.fillMaxSize(), content = content)
    }
}
