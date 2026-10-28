package io.github.univjh.pleosapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A66),
    background = Color(0xFFF5FAF8),
    surface = Color(0xFFF5FAF8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DD5CF),
    background = Color(0xFF101918),
    surface = Color(0xFF101918),
)

@Composable
fun PleosAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
