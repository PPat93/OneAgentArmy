package com.parrotworks.oneagentarmy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = OnNeonCyan,
    primaryContainer = NeonCyanContainer,
    onPrimaryContainer = OnNeonCyanContainer,
    secondary = NeonRed,
    onSecondary = OnNeonRed,
    secondaryContainer = NeonRedContainer,
    onSecondaryContainer = OnNeonRedContainer,
    tertiary = NeonYellow,
    onTertiary = OnNeonYellow,
    tertiaryContainer = NeonYellowContainer,
    onTertiaryContainer = OnNeonYellowContainer,
    background = DeepBlack,
    onBackground = OnDeepBlack,
    surface = DeepSurface,
    onSurface = OnDeepBlack,
    surfaceVariant = DeepSurfaceVariant,
    onSurfaceVariant = OnDeepSurfaceVariant,
    outline = DarkOutline,
)

private val LightColorScheme = lightColorScheme(
    primary = DeepTeal,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = OnTealContainer,
    secondary = DeepRed,
    onSecondary = Color.White,
    secondaryContainer = RedContainer,
    onSecondaryContainer = OnRedContainer,
    tertiary = DeepGold,
    onTertiary = Color.White,
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = OnGoldContainer,
    background = LightBackground,
    onBackground = OnLightBackground,
    surface = LightBackground,
    onSurface = OnLightBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = OnLightSurfaceVariant,
    outline = LightOutline,
)

@Composable
fun OneAgentArmyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
