package io.github.rickpro.stride.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkScheme = darkColorScheme(
    primary = StrideColors.Accent,
    onPrimary = StrideColors.OnAccent,
    primaryContainer = StrideColors.AccentDim,
    onPrimaryContainer = StrideColors.Text,
    secondary = StrideColors.Muted,
    onSecondary = StrideColors.Background,
    secondaryContainer = StrideColors.SurfaceHigh,
    onSecondaryContainer = StrideColors.Text,
    tertiary = StrideColors.Warning,
    onTertiary = StrideColors.Background,
    tertiaryContainer = StrideColors.WarningDim,
    onTertiaryContainer = StrideColors.Text,
    error = StrideColors.Stop,
    onError = StrideColors.Text,
    errorContainer = StrideColors.StopDim,
    onErrorContainer = StrideColors.Text,
    background = StrideColors.Background,
    onBackground = StrideColors.Text,
    surface = StrideColors.Background,
    onSurface = StrideColors.Text,
    surfaceVariant = StrideColors.Surface,
    onSurfaceVariant = StrideColors.Muted,
    surfaceContainer = StrideColors.Surface,
    surfaceContainerHigh = StrideColors.SurfaceHigh,
    surfaceContainerHighest = StrideColors.SurfaceHigh,
    surfaceContainerLow = StrideColors.Surface,
    surfaceContainerLowest = StrideColors.Background,
    outline = StrideColors.Outline,
    outlineVariant = StrideColors.Outline,
    inverseSurface = StrideColors.Text,
    inverseOnSurface = StrideColors.Background,
    inversePrimary = StrideColors.AccentDim,
    scrim = StrideColors.Background,
)

/** Tabular figures so the big numbers do not jump as they count. */
val NumberStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
    textAlign = TextAlign.Center,
)

private val StrideTypography = Typography(
    displayLarge = NumberStyle.copy(fontSize = 112.sp, lineHeight = 112.sp, letterSpacing = (-2).sp),
    displayMedium = NumberStyle.copy(fontSize = 44.sp, lineHeight = 48.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp),
)

private val StrideShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun StrideTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = StrideTypography, shapes = StrideShapes, content = content)
}
