package aeeL.shngmWeb

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SurfaceDark = Color(0xFF121212)
val SurfaceContainerDark = Color(0xFF1E1E1E)
val OnSurface = Color(0xFFE6E1E5)
val OnSurfaceVariant = Color(0xFFCAC4D0)
val PrimaryAccent = Color(0xFFD0BCFF)
val Hint = Color(0xFF938F96)
val PlaceholderTint = Color(0xFF605D62)

// The app is dark-only: the WebView, splash and sheets all use the same dark surfaces.
private val AppColorScheme = darkColorScheme(
  primary = PrimaryAccent,
  secondary = Color(0xFFCCC2DC),
  tertiary = Color(0xFFEFB8C8),
  background = SurfaceDark,
  onBackground = OnSurface,
  surface = SurfaceContainerDark,
  onSurface = OnSurface,
  surfaceVariant = SurfaceContainerDark,
  onSurfaceVariant = OnSurfaceVariant,
  outline = PlaceholderTint,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = AppColorScheme,
    content = content,
  )
}
