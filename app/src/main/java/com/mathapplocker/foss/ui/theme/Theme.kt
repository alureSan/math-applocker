package com.mathapplocker.foss.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightScheme = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = Sand40,
    secondaryContainer = Sand90,
    onSecondaryContainer = Sand10,
    tertiary = Slate40,
    tertiaryContainer = Slate90,
    onTertiaryContainer = Slate10,
)

private val DarkScheme = darkColorScheme(
    primary = Green80,
    onPrimary = Green10,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary = Sand80,
    secondaryContainer = Sand40,
    onSecondaryContainer = Sand90,
    tertiary = Slate80,
    tertiaryContainer = Slate40,
    onTertiaryContainer = Slate90,
)

/**
 * Material You dynamic color on Android 12+, with a green fallback palette on
 * Android 10–11.
 */
@Composable
fun MathAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
