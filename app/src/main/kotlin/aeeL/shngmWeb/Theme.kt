package aeeL.shngmWeb

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shinigami palette, mirrored from the website's Tailwind tokens.
 *
 * base-dark is the page background, base-bg is the raised chrome (header, footer, sheets) and
 * base-card is the card surface. primary-500 is the purple brand accent used by the site.
 * The light scheme keeps the same brand hues and derives only the surface/contrast steps.
 */
val BaseDark = Color(0xFF09090B)
val BaseBg = Color(0xFF18181B)
val BaseCard = Color(0xFF27272A)
val BaseWhite = Color(0xFFFFFFFF)

val General100 = Color(0xFFF4F4F5)
val General300 = Color(0xFFD4D4D8)
val General400 = Color(0xFFA1A1AA)
val General500 = Color(0xFF71717A)
val General700 = Color(0xFF3F3F46)
val General800 = Color(0xFF27272A)

val Primary100 = Color(0xFFDAD6FE)
val Primary200 = Color(0xFFBFB5FD)
val Primary300 = Color(0xFF9F8AFB)
val Primary400 = Color(0xFF805BF7)
val Primary500 = Color(0xFF6F39EE)

val Success500 = Color(0xFF50C793)
val Warning500 = Color(0xFFFA916B)
val Danger500 = Color(0xFFFF5757)
val AccentMagenta = Color(0xFFAE14B0)

private val AppDarkColorScheme = darkColorScheme(
  primary = Primary300,
  onPrimary = BaseDark,
  primaryContainer = Color(0xFF3A246E),
  onPrimaryContainer = Primary100,
  inversePrimary = Primary500,
  secondary = Primary300,
  onSecondary = BaseDark,
  secondaryContainer = Color(0xFF30244F),
  onSecondaryContainer = Primary100,
  tertiary = Color(0xFFF0A6F0),
  onTertiary = Color(0xFF4B134C),
  tertiaryContainer = Color(0xFF4B134C),
  onTertiaryContainer = Color(0xFFF7D5F7),
  background = BaseDark,
  onBackground = General100,
  surface = BaseBg,
  onSurface = General100,
  surfaceVariant = BaseCard,
  onSurfaceVariant = General300,
  surfaceTint = Primary300,
  inverseSurface = General100,
  inverseOnSurface = BaseDark,
  error = Danger500,
  onError = BaseDark,
  errorContainer = Color(0xFF4A1414),
  onErrorContainer = Color(0xFFFFDDDD),
  outline = General500,
  outlineVariant = General700,
  scrim = Color.Black,
  surfaceBright = BaseCard,
  surfaceDim = BaseDark,
  surfaceContainer = BaseBg,
  surfaceContainerHigh = BaseCard,
  surfaceContainerHighest = Color(0xFF323236),
  surfaceContainerLow = BaseBg,
  surfaceContainerLowest = BaseDark,
)

private val AppLightColorScheme = lightColorScheme(
  primary = Primary500,
  onPrimary = BaseWhite,
  primaryContainer = Primary100,
  onPrimaryContainer = Color(0xFF2A0A66),
  inversePrimary = Primary300,
  secondary = Primary400,
  onSecondary = BaseWhite,
  secondaryContainer = Color(0xFFE7E0FF),
  onSecondaryContainer = Color(0xFF2B1663),
  tertiary = AccentMagenta,
  onTertiary = BaseWhite,
  tertiaryContainer = Color(0xFFF7D5F7),
  onTertiaryContainer = Color(0xFF4B134C),
  background = BaseWhite,
  onBackground = BaseDark,
  surface = BaseWhite,
  onSurface = BaseDark,
  surfaceVariant = General100,
  onSurfaceVariant = General700,
  surfaceTint = Primary500,
  inverseSurface = BaseBg,
  inverseOnSurface = General100,
  error = Color(0xFFB3261E),
  onError = BaseWhite,
  errorContainer = Color(0xFFFFDAD6),
  onErrorContainer = Color(0xFF410002),
  outline = General500,
  outlineVariant = General300,
  scrim = Color.Black,
  surfaceBright = BaseWhite,
  surfaceDim = Color(0xFFE4E4E7),
  surfaceContainer = General100,
  surfaceContainerHigh = Color(0xFFECECEE),
  surfaceContainerHighest = Color(0xFFE4E4E7),
  surfaceContainerLow = Color(0xFFFAFAFA),
  surfaceContainerLowest = BaseWhite,
)

/** Expressive type scale: stronger headings and comfortable reading line lengths. */
private val AppTypography = Typography(
  headlineMedium = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 36.sp,
    letterSpacing = (-0.3).sp,
  ),
  headlineSmall = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 24.sp,
    lineHeight = 32.sp,
    letterSpacing = (-0.2).sp,
  ),
  titleLarge = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
    letterSpacing = (-0.1).sp,
  ),
  titleMedium = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.1.sp,
  ),
  bodyLarge = TextStyle(
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.2.sp,
  ),
  bodyMedium = TextStyle(
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.15.sp,
  ),
  bodySmall = TextStyle(
    fontSize = 12.sp,
    lineHeight = 17.sp,
    letterSpacing = 0.2.sp,
  ),
  labelLarge = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.1.sp,
  ),
)

private val AppShapes = Shapes(
  extraSmall = RoundedCornerShape(8.dp),
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(24.dp),
  extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun AppTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = if (darkTheme) AppDarkColorScheme else AppLightColorScheme,
    typography = AppTypography,
    shapes = AppShapes,
    content = content,
  )
}
