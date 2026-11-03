package io.github.univjh.pleosapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// All screens use semantic colors; light and dark modes retain the same hierarchy.
private val LightColors = lightColorScheme(
    primary = Color(0xFF17634E), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0EEE6), onPrimaryContainer = Color(0xFF173E30),
    secondary = Color(0xFF825310), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEDD1), onSecondaryContainer = Color(0xFF70450A),
    tertiary = Color(0xFF5C4C85), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFECE6F6), onTertiaryContainer = Color(0xFF493969),
    background = Color(0xFFF2F5F1), onBackground = Color(0xFF1C3029),
    surface = Color.White, onSurface = Color(0xFF1C3029),
    surfaceVariant = Color(0xFFEBF0EB), onSurfaceVariant = Color(0xFF53645B),
    surfaceContainer = Color.White, surfaceContainerLow = Color(0xFFF8FAF7),
    surfaceContainerHigh = Color(0xFFEBF0EB), surfaceContainerHighest = Color(0xFFE3EAE3),
    outline = Color(0xFF6F8176), outlineVariant = Color(0xFFD6E0D7),
    inverseSurface = Color(0xFF163D30), inverseOnSurface = Color(0xFFF4FAF5),
    inversePrimary = Color(0xFFA9DEC0),
    error = Color(0xFFA33232), onError = Color.White,
    errorContainer = Color(0xFFFFE7E5), onErrorContainer = Color(0xFF7E2425),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFA1DABB), onPrimary = Color(0xFF143A2B),
    primaryContainer = Color(0xFF244C39), onPrimaryContainer = Color(0xFFD4F1DF),
    secondary = Color(0xFFF0C481), onSecondary = Color(0xFF412B0B),
    secondaryContainer = Color(0xFF4B381C), onSecondaryContainer = Color(0xFFFFDCA6),
    tertiary = Color(0xFFCEBDED), onTertiary = Color(0xFF36264F),
    tertiaryContainer = Color(0xFF413451), onTertiaryContainer = Color(0xFFEBDEFF),
    background = Color(0xFF101A16), onBackground = Color(0xFFEAF0E9),
    surface = Color(0xFF1A2821), onSurface = Color(0xFFEAF0E9),
    surfaceVariant = Color(0xFF24372D), onSurfaceVariant = Color(0xFFB7C8BC),
    surfaceContainer = Color(0xFF1A2821), surfaceContainerLow = Color(0xFF16231C),
    surfaceContainerHigh = Color(0xFF24372D), surfaceContainerHighest = Color(0xFF304438),
    outline = Color(0xFF8B9D90), outlineVariant = Color(0xFF3B5042),
    inverseSurface = Color(0xFFD8ECDC), inverseOnSurface = Color(0xFF173B2C),
    inversePrimary = Color(0xFF226544),
    error = Color(0xFFFFB4AB), onError = Color(0xFF561E1B),
    errorContainer = Color(0xFF542D2A), onErrorContainer = Color(0xFFFFDAD6),
)
private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = line.sp,
    fontWeight = weight, letterSpacing = 0.sp,
)
private val CoachTypography = Typography(
    displaySmall = type(48, 58, FontWeight.Bold),
    headlineLarge = type(40, 52, FontWeight.Bold),
    headlineMedium = type(34, 44, FontWeight.Bold),
    headlineSmall = type(28, 38, FontWeight.Bold),
    titleLarge = type(26, 36, FontWeight.SemiBold),
    titleMedium = type(22, 32, FontWeight.SemiBold),
    titleSmall = type(20, 30, FontWeight.SemiBold),
    bodyLarge = type(22, 34), bodyMedium = type(20, 30), bodySmall = type(18, 28),
    labelLarge = type(20, 28, FontWeight.SemiBold),
    labelMedium = type(18, 26, FontWeight.SemiBold), labelSmall = type(16, 24, FontWeight.Medium),
)
@Composable
fun PleosAppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CoachTypography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp)), content = content)
}
