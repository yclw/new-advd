package com.aeibi.avd.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.aeibi.avd.core.model.ThemePalette

internal fun colorSchemeFor(palette: ThemePalette, darkTheme: Boolean): ColorScheme =
    when (palette) {
        ThemePalette.TERRACOTTA -> if (darkTheme) TerracottaDarkScheme else TerracottaLightScheme
        ThemePalette.SAGE -> if (darkTheme) SageDarkScheme else SageLightScheme
        ThemePalette.GOLD -> if (darkTheme) GoldDarkScheme else GoldLightScheme
    }

private val TerracottaLightScheme = lightColorScheme(
    primary = Color(0xFF8F4C38), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBD1), onPrimaryContainer = Color(0xFF723523),
    inversePrimary = Color(0xFFFFB5A0),
    secondary = Color(0xFF77574E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBD1), onSecondaryContainer = Color(0xFF5D4037),
    tertiary = Color(0xFF6C5D2F), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF5E1A7), onTertiaryContainer = Color(0xFF534619),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFFF8F6), onBackground = Color(0xFF231917),
    surface = Color(0xFFFFF8F6), onSurface = Color(0xFF231917),
    surfaceVariant = Color(0xFFF5DED8), onSurfaceVariant = Color(0xFF53433F),
    outline = Color(0xFF85736E), outlineVariant = Color(0xFFD8C2BC), scrim = Color.Black,
    inverseSurface = Color(0xFF392E2B), inverseOnSurface = Color(0xFFFFEDE8),
    surfaceDim = Color(0xFFE8D6D2), surfaceBright = Color(0xFFFFF8F6),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFF1ED),
    surfaceContainer = Color(0xFFFCEAE5), surfaceContainerHigh = Color(0xFFF7E4E0),
    surfaceContainerHighest = Color(0xFFF1DFDA)
)

private val TerracottaDarkScheme = darkColorScheme(
    primary = Color(0xFFFFB5A0), onPrimary = Color(0xFF561F0F),
    primaryContainer = Color(0xFF723523), onPrimaryContainer = Color(0xFFFFDBD1),
    inversePrimary = Color(0xFF8F4C38),
    secondary = Color(0xFFE7BDB2), onSecondary = Color(0xFF442A22),
    secondaryContainer = Color(0xFF5D4037), onSecondaryContainer = Color(0xFFFFDBD1),
    tertiary = Color(0xFFD8C58D), onTertiary = Color(0xFF3B2F05),
    tertiaryContainer = Color(0xFF534619), onTertiaryContainer = Color(0xFFF5E1A7),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A110F), onBackground = Color(0xFFF1DFDA),
    surface = Color(0xFF1A110F), onSurface = Color(0xFFF1DFDA),
    surfaceVariant = Color(0xFF53433F), onSurfaceVariant = Color(0xFFD8C2BC),
    outline = Color(0xFFA08C87), outlineVariant = Color(0xFF53433F), scrim = Color.Black,
    inverseSurface = Color(0xFFF1DFDA), inverseOnSurface = Color(0xFF392E2B),
    surfaceDim = Color(0xFF1A110F), surfaceBright = Color(0xFF423734),
    surfaceContainerLowest = Color(0xFF140C0A), surfaceContainerLow = Color(0xFF231917),
    surfaceContainer = Color(0xFF271D1B), surfaceContainerHigh = Color(0xFF322825),
    surfaceContainerHighest = Color(0xFF3D322F)
)

private val SageLightScheme = lightColorScheme(
    primary = Color(0xFF4C662B), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEDA3), onPrimaryContainer = Color(0xFF354E16),
    inversePrimary = Color(0xFFB1D18A),
    secondary = Color(0xFF586249), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE7C8), onSecondaryContainer = Color(0xFF404A33),
    tertiary = Color(0xFF386663), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBCECE7), onTertiaryContainer = Color(0xFF1F4E4B),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF9FAEF), onBackground = Color(0xFF1A1C16),
    surface = Color(0xFFF9FAEF), onSurface = Color(0xFF1A1C16),
    surfaceVariant = Color(0xFFE1E4D5), onSurfaceVariant = Color(0xFF44483D),
    outline = Color(0xFF75796C), outlineVariant = Color(0xFFC5C8BA), scrim = Color.Black,
    inverseSurface = Color(0xFF2F312A), inverseOnSurface = Color(0xFFF1F2E6),
    surfaceDim = Color(0xFFDADBD0), surfaceBright = Color(0xFFF9FAEF),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF3F4E9),
    surfaceContainer = Color(0xFFEEEFE3), surfaceContainerHigh = Color(0xFFE8E9DE),
    surfaceContainerHighest = Color(0xFFE2E3D8)
)

private val SageDarkScheme = darkColorScheme(
    primary = Color(0xFFB1D18A), onPrimary = Color(0xFF1F3701),
    primaryContainer = Color(0xFF354E16), onPrimaryContainer = Color(0xFFCDEDA3),
    inversePrimary = Color(0xFF4C662B),
    secondary = Color(0xFFBFCBAD), onSecondary = Color(0xFF2A331E),
    secondaryContainer = Color(0xFF404A33), onSecondaryContainer = Color(0xFFDCE7C8),
    tertiary = Color(0xFFA0D0CB), onTertiary = Color(0xFF003735),
    tertiaryContainer = Color(0xFF1F4E4B), onTertiaryContainer = Color(0xFFBCECE7),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF12140E), onBackground = Color(0xFFE2E3D8),
    surface = Color(0xFF12140E), onSurface = Color(0xFFE2E3D8),
    surfaceVariant = Color(0xFF44483D), onSurfaceVariant = Color(0xFFC5C8BA),
    outline = Color(0xFF8F9285), outlineVariant = Color(0xFF44483D), scrim = Color.Black,
    inverseSurface = Color(0xFFE2E3D8), inverseOnSurface = Color(0xFF2F312A),
    surfaceDim = Color(0xFF12140E), surfaceBright = Color(0xFF383A32),
    surfaceContainerLowest = Color(0xFF0C0F09), surfaceContainerLow = Color(0xFF1A1C16),
    surfaceContainer = Color(0xFF1E201A), surfaceContainerHigh = Color(0xFF282B24),
    surfaceContainerHighest = Color(0xFF33362E)
)

private val GoldLightScheme = lightColorScheme(
    primary = Color(0xFF6D5E0F), onPrimary = Color.White,
    primaryContainer = Color(0xFFF8E287), onPrimaryContainer = Color(0xFF534600),
    inversePrimary = Color(0xFFDBC66E),
    secondary = Color(0xFF665E40), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEE2BC), onSecondaryContainer = Color(0xFF4E472A),
    tertiary = Color(0xFF43664E), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC5ECCE), onTertiaryContainer = Color(0xFF2C4E38),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFFF9EE), onBackground = Color(0xFF1E1B13),
    surface = Color(0xFFFFF9EE), onSurface = Color(0xFF1E1B13),
    surfaceVariant = Color(0xFFEAE2D0), onSurfaceVariant = Color(0xFF4B4739),
    outline = Color(0xFF7C7767), outlineVariant = Color(0xFFCDC6B4), scrim = Color.Black,
    inverseSurface = Color(0xFF333027), inverseOnSurface = Color(0xFFF7F0E2),
    surfaceDim = Color(0xFFE0D9CC), surfaceBright = Color(0xFFFFF9EE),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAF3E5),
    surfaceContainer = Color(0xFFF4EDDF), surfaceContainerHigh = Color(0xFFEEE8DA),
    surfaceContainerHighest = Color(0xFFE8E2D4)
)

private val GoldDarkScheme = darkColorScheme(
    primary = Color(0xFFDBC66E), onPrimary = Color(0xFF3A3000),
    primaryContainer = Color(0xFF534600), onPrimaryContainer = Color(0xFFF8E287),
    inversePrimary = Color(0xFF6D5E0F),
    secondary = Color(0xFFD1C6A1), onSecondary = Color(0xFF363016),
    secondaryContainer = Color(0xFF4E472A), onSecondaryContainer = Color(0xFFEEE2BC),
    tertiary = Color(0xFFA9D0B3), onTertiary = Color(0xFF143723),
    tertiaryContainer = Color(0xFF2C4E38), onTertiaryContainer = Color(0xFFC5ECCE),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF15130B), onBackground = Color(0xFFE8E2D4),
    surface = Color(0xFF15130B), onSurface = Color(0xFFE8E2D4),
    surfaceVariant = Color(0xFF4B4739), onSurfaceVariant = Color(0xFFCDC6B4),
    outline = Color(0xFF969080), outlineVariant = Color(0xFF4B4739), scrim = Color.Black,
    inverseSurface = Color(0xFFE8E2D4), inverseOnSurface = Color(0xFF333027),
    surfaceDim = Color(0xFF15130B), surfaceBright = Color(0xFF3C3930),
    surfaceContainerLowest = Color(0xFF100E07), surfaceContainerLow = Color(0xFF1E1B13),
    surfaceContainer = Color(0xFF222017), surfaceContainerHigh = Color(0xFF2D2A21),
    surfaceContainerHighest = Color(0xFF38352B)
)
