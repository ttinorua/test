package com.financetracker.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.financetracker.app.data.prefs.ThemeMode
import com.financetracker.app.data.prefs.ThemeSettings

private val LightColors = lightColorScheme(
    primary = GreenPrimary,
    secondary = GreenSecondary,
    background = LightBackground,
    surface = LightSurface,
    error = ExpenseRed
)

private val DarkColors = darkColorScheme(
    primary = GreenPrimaryDark,
    secondary = GreenSecondary,
    background = DarkBackground,
    surface = DarkSurface,
    error = ExpenseRed
)

@Composable
fun PersonalFinanceTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val themeMode by ThemeSettings.themeMode.collectAsState()
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
