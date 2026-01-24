package com.example.dashtune.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DashTuneColorScheme = darkColorScheme(
    primary = DashTuneTeal,           // Icons, Music Wave, Active Buttons
    secondary = VibrantCyan,           // Highlights and secondary details
    tertiary = LightTeal,              // Tertiary accents
    background = MidnightNavy,         // Main app surface
    surface = MidnightNavy,            // Card surfaces
    surfaceVariant = DarkNavy,         // Now Playing Bar and elevated surfaces
    onPrimary = OffWhite,              // Text on primary color
    onSecondary = MidnightNavy,        // Text on secondary color
    onTertiary = MidnightNavy,         // Text on tertiary color
    onBackground = OffWhite,           // Text on background
    onSurface = OffWhite,              // Text on surface
    onSurfaceVariant = OffWhite        // Text on surface variant
)

@Composable
fun RadioAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color disabled - using custom DashTune theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> DashTuneColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}