package aeeL.shngmWeb

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Shinigami palette, mirrored from the website's Tailwind tokens.
 *
 * base-dark is the page background, base-bg is the raised chrome (header, footer, sheets) and
 * base-card is the card surface. primary-500 is the purple brand accent used by the site.
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

/** Base-dark at 80%: dims the WebView without introducing a neutral grey scrim. */
val AppScrim = Color(0xCC09090B)

// The app is dark-only: the WebView, splash and sheets all use the same palette.
private val AppColorScheme = darkColorScheme(
  primary = Primary500,
  onPrimary = BaseWhite,
  primaryContainer = Color(0xFF3A246E),
  onPrimaryContainer = Primary100,
  inversePrimary = Primary300,
  secondary = Primary300,
  onSecondary = BaseDark,
  secondaryContainer = Color(0xFF30244F),
  onSecondaryContainer = Primary100,
  tertiary = AccentMagenta,
  onTertiary = BaseWhite,
  tertiaryContainer = Color(0xFF4B134C),
  onTertiaryContainer = Color(0xFFF7D5F7),
  background = BaseDark,
  onBackground = General100,
  surface = BaseBg,
  onSurface = General100,
  surfaceVariant = BaseCard,
  onSurfaceVariant = General300,
  surfaceTint = Primary500,
  inverseSurface = General100,
  inverseOnSurface = BaseDark,
  error = Danger500,
  onError = BaseWhite,
  errorContainer = Color(0xFF4A1414),
  onErrorContainer = Color(0xFFFFDDDD),
  outline = General700,
  outlineVariant = General800,
  scrim = Color.Black,
  surfaceBright = BaseCard,
  surfaceDim = BaseDark,
  surfaceContainer = BaseBg,
  surfaceContainerHigh = BaseCard,
  surfaceContainerHighest = Color(0xFF323236),
  surfaceContainerLow = BaseBg,
  surfaceContainerLowest = BaseDark,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = AppColorScheme,
    content = content,
  )
}
